package com.retra.emulator

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale

/**
 * Real Google Drive REST v3 backup transport.
 *
 * Retra stores complete, validated .retra snapshots in:
 *   My Drive / Retra Backups /
 *
 * This repository never uses Android's Storage Access Framework. OAuth access
 * tokens are supplied by Google Identity Services (AuthorizationClient).
 * Every upload creates a NEW snapshot; an older good backup is never patched or
 * deleted as part of Backup Now / automatic backup.
 */
class GoogleDriveApiRepository(
    private val cacheDir: File
) {
    data class CloudBackup(
        val id: String,
        val name: String,
        val createdAt: Long,
        val modifiedAt: Long,
        val size: Long,
        val md5: String,
        val sha256: String
    )

    data class UploadResult(
        val backup: CloudBackup,
        val deduplicated: Boolean = false
    )

    class AuthExpiredException : Exception("Google Drive authorization expired")

    /** Return backups without creating a folder. Restore and fresh-install checks use this. */
    fun listBackups(accessToken: String): List<CloudBackup> {
        val folderId = findBackupFolder(accessToken) ?: return emptyList()
        return listBackups(accessToken, folderId)
    }

    /**
     * Upload a new immutable snapshot. Existing backups are never PATCHed.
     * A failed or unverifiable upload is removed only if it is the newly-created file.
     */
    fun uploadBackup(
        accessToken: String,
        source: File,
        sha256: String,
        onProgress: (Int) -> Unit = {}
    ): UploadResult {
        require(source.isFile && source.length() > 0L) { "Backup archive is empty" }
        val existing = listBackups(accessToken)
        val newest = existing.maxByOrNull { it.createdAt }
        if (newest != null && newest.sha256.equals(sha256, ignoreCase = true)) {
            onProgress(100)
            return UploadResult(newest, deduplicated = true)
        }

        val folderId = findOrCreateBackupFolder(accessToken)
        val localMd5 = digestFile(source, "MD5")
        val metadata = JSONObject()
            .put("name", source.name)
            .put("parents", JSONArray().put(folderId))
            .put("mimeType", BACKUP_MIME)
            .put("appProperties", JSONObject()
                .put("retraBackup", "1")
                .put("retraSchema", "1")
                .put("retraSha256", sha256.lowercase(Locale.US))
                .put("retraCreatedAt", System.currentTimeMillis().toString()))

        var createdId: String? = null
        try {
            val boundary = "RetraBackupBoundary${System.nanoTime()}"
            val header = ("--$boundary\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                metadata.toString() + "\r\n" +
                "--$boundary\r\n" +
                "Content-Type: $BACKUP_MIME\r\n\r\n").toByteArray(Charsets.UTF_8)
            val footer = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
            val endpoint = "$DRIVE_UPLOAD/files?uploadType=multipart&fields=id,name,size,createdTime,modifiedTime,md5Checksum,appProperties"
            val json = requestMultipart(endpoint, accessToken, boundary, header, source, footer, onProgress)
            createdId = json.optString("id").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Drive did not return the uploaded file ID")
            val uploaded = jsonToBackup(json)

            if (uploaded.size != source.length()) {
                throw IllegalStateException("Drive upload size verification failed")
            }
            if (uploaded.md5.isBlank() || !uploaded.md5.equals(localMd5, ignoreCase = true)) {
                throw IllegalStateException("Drive upload checksum verification failed")
            }
            if (!uploaded.sha256.equals(sha256, ignoreCase = true)) {
                throw IllegalStateException("Drive backup metadata verification failed")
            }
            onProgress(100)
            return UploadResult(uploaded)
        } catch (error: Exception) {
            // Protect every prior good backup. At most, clean up the NEW failed object.
            createdId?.let { id -> runCatching { deleteFile(accessToken, id) } }
            throw error
        }
    }

    /** Download one chosen snapshot and verify Drive size/checksum before returning it. */
    fun downloadBackup(
        accessToken: String,
        backup: CloudBackup,
        target: File,
        onProgress: (Int) -> Unit = {}
    ): File {
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile ?: cacheDir, ".${target.name}.${System.nanoTime()}.part")
        try {
            val connection = open("GET", "$DRIVE_API/files/${backup.id}?alt=media", accessToken)
            ensureSuccess(connection)
            val total = backup.size.takeIf { it > 0L } ?: connection.contentLengthLong.coerceAtLeast(0L)
            var copied = 0L
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0L) onProgress(((copied * 100L) / total).toInt().coerceIn(0, 99))
                    }
                    output.flush()
                    runCatching { output.fd.sync() }
                }
            }
            if (backup.size > 0L && partial.length() != backup.size) {
                throw IllegalStateException("Downloaded backup size does not match Google Drive")
            }
            if (backup.md5.isNotBlank()) {
                val actual = digestFile(partial, "MD5")
                if (!actual.equals(backup.md5, ignoreCase = true)) {
                    throw IllegalStateException("Downloaded backup checksum is invalid")
                }
            }
            if (target.exists() && !target.delete()) throw IllegalStateException("Could not replace temporary restore file")
            if (!partial.renameTo(target)) partial.copyTo(target, overwrite = true)
            onProgress(100)
            return target
        } finally {
            if (partial.exists()) partial.delete()
        }
    }

    /** No-op for snapshot storage: the next archive reflects deletions without mutating old backups. */
    fun queueDelete(paths: List<String>) = Unit
    fun deletePaths(accessToken: String, paths: List<String>) = Unit

    private fun findBackupFolder(token: String): String? {
        // Exact My Drive root + exact name prevents accidentally binding to a
        // same-name nested folder. If duplicates already exist, oldest is reused.
        val q = "'root' in parents and trashed = false and mimeType = '$FOLDER_MIME' and name = '$BACKUP_FOLDER_NAME'"
        val fields = "files(id,name,createdTime,appProperties)"
        val result = requestJson("GET", "$DRIVE_API/files?q=${encode(q)}&spaces=drive&orderBy=createdTime&fields=${encode(fields)}&pageSize=100", token)
        val files = result.optJSONArray("files") ?: JSONArray()
        if (files.length() == 0) return null

        var selectedId: String? = null
        var selectedCreated = Long.MAX_VALUE
        for (i in 0 until files.length()) {
            val item = files.optJSONObject(i) ?: continue
            val id = item.optString("id")
            if (id.isBlank()) continue
            val created = parseDriveTime(item.optString("createdTime"))
            if (selectedId == null || (created > 0L && created < selectedCreated)) {
                selectedId = id
                selectedCreated = if (created > 0L) created else selectedCreated
            }
        }
        return selectedId
    }

    private fun findOrCreateBackupFolder(token: String): String {
        findBackupFolder(token)?.let { return it }
        val body = JSONObject()
            .put("name", BACKUP_FOLDER_NAME)
            .put("mimeType", FOLDER_MIME)
            .put("parents", JSONArray().put("root"))
            .put("appProperties", JSONObject().put("retraBackupRoot", "1"))
        val created = requestJson("POST", "$DRIVE_API/files?fields=id,name,parents,appProperties", token, body.toString().toByteArray(Charsets.UTF_8))
        return created.optString("id").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Could not create My Drive/$BACKUP_FOLDER_NAME")
    }

    private fun listBackups(token: String, folderId: String): List<CloudBackup> {
        val q = "'$folderId' in parents and trashed = false"
        val fields = "nextPageToken,files(id,name,size,createdTime,modifiedTime,md5Checksum,mimeType,appProperties)"
        val backups = mutableListOf<CloudBackup>()
        var pageToken: String? = null
        do {
            val tokenPart = pageToken?.let { "&pageToken=${encode(it)}" }.orEmpty()
            val url = "$DRIVE_API/files?q=${encode(q)}&spaces=drive&orderBy=createdTime%20desc&fields=${encode(fields)}&pageSize=100$tokenPart"
            val result = requestJson("GET", url, token)
            val files = result.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) {
                val json = files.optJSONObject(i) ?: continue
                val name = json.optString("name")
                val props = json.optJSONObject("appProperties")
                val isRetra = props?.optString("retraBackup") == "1" || name.endsWith(".retra", ignoreCase = true)
                if (isRetra) backups += jsonToBackup(json)
            }
            pageToken = result.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)
        return backups.sortedByDescending { if (it.createdAt > 0L) it.createdAt else it.modifiedAt }
    }

    private fun jsonToBackup(json: JSONObject): CloudBackup {
        val props = json.optJSONObject("appProperties") ?: JSONObject()
        return CloudBackup(
            id = json.optString("id"),
            name = json.optString("name", "Retra backup.retra"),
            createdAt = parseDriveTime(json.optString("createdTime")),
            modifiedAt = parseDriveTime(json.optString("modifiedTime")),
            size = json.optString("size", "0").toLongOrNull() ?: 0L,
            md5 = json.optString("md5Checksum"),
            sha256 = props.optString("retraSha256")
        )
    }

    private fun requestJson(method: String, url: String, token: String, body: ByteArray? = null): JSONObject {
        val connection = open(method, url, token)
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.outputStream.use { it.write(body) }
        }
        ensureSuccess(connection)
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun requestMultipart(
        url: String,
        token: String,
        boundary: String,
        header: ByteArray,
        source: File,
        footer: ByteArray,
        onProgress: (Int) -> Unit
    ): JSONObject {
        val connection = open("POST", url, token)
        connection.doOutput = true
        connection.setChunkedStreamingMode(BUFFER_SIZE)
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        connection.outputStream.use { output ->
            output.write(header)
            var copied = 0L
            val total = source.length().coerceAtLeast(1L)
            FileInputStream(source).use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    onProgress(((copied * 100L) / total).toInt().coerceIn(0, 99))
                }
            }
            output.write(footer)
            output.flush()
        }
        ensureSuccess(connection)
        return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    }

    private fun deleteFile(token: String, id: String) {
        val connection = open("DELETE", "$DRIVE_API/files/$id", token)
        ensureSuccess(connection)
    }

    private fun open(method: String, url: String, token: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 120_000
            useCaches = false
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }

    private fun ensureSuccess(connection: HttpURLConnection) {
        val code = connection.responseCode
        if (code == 401) throw AuthExpiredException()
        if (code !in 200..299) {
            val message = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
            throw IllegalStateException("Google Drive API $code${if (message.isNullOrBlank()) "" else ": $message"}")
        }
    }

    fun sha256(file: File): String = digestFile(file, "SHA-256")

    private fun digestFile(file: File, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
    }

    private fun parseDriveTime(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    companion object {
        const val BACKUP_FOLDER_NAME = "Retra Backups"
        private const val BACKUP_MIME = "application/x-retra-backup"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val DRIVE_API = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val BUFFER_SIZE = 64 * 1024
    }
}
