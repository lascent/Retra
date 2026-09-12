package com.retra.emulator

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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
 * Google Drive REST v3 synchronization using an OAuth access token supplied by
 * Android's Google account authenticator. Files are scoped to an app-created
 * Retra Sync folder and carry path/hash appProperties for conflict-safe merging.
 *
 * No client secret is stored in the APK. Devices/accounts that cannot issue a
 * Drive token fall back to SaveTransferRepository's SAF Drive-folder sync.
 */
class GoogleDriveApiRepository(
    private val filesDir: File,
    private val fileOps: RetraFileOps,
    private val preparePortableMetadata: () -> Unit
) {
    data class SyncResult(
        val uploaded: Int = 0,
        val downloaded: Int = 0,
        val conflicts: Int = 0,
        val unchanged: Int = 0,
        val errors: Int = 0
    )

    class AuthExpiredException : Exception("Google Drive authorization expired")

    private data class RemoteFile(
        val id: String,
        val path: String,
        val hash: String,
        val modifiedAt: Long,
        val size: Long
    )

    fun queueDelete(paths: List<String>) {
        if (paths.isEmpty()) return
        val pending = readTombstones().toMutableSet()
        pending.addAll(paths)
        writeTombstones(pending)
    }

    fun deletePaths(accessToken: String, paths: List<String>) {
        if (paths.isEmpty()) return
        val rootId = findOrCreateRoot(accessToken)
        val wanted = paths.toSet()
        listRemoteFiles(accessToken, rootId).filter { it.path in wanted }.forEach { remote ->
            val connection = open("DELETE", "$DRIVE_API/files/${remote.id}", accessToken)
            ensureSuccess(connection)
        }
        val state = readState().toMutableMap()
        paths.forEach(state::remove)
        writeState(state)
        val pending = readTombstones().toMutableSet()
        pending.removeAll(paths.toSet())
        writeTombstones(pending)
    }

    fun sync(accessToken: String): SyncResult {
        preparePortableMetadata()
        val rootId = findOrCreateRoot(accessToken)
        val local = localFiles()
        val remote = listRemoteFiles(accessToken, rootId).associateBy { it.path }.toMutableMap()
        val lastState = readState().toMutableMap()
        val tombstones = readTombstones().toMutableSet()
        val completedDeletes = mutableSetOf<String>()
        tombstones.forEach { path ->
            val remoteFile = remote[path]
            if (remoteFile == null) {
                completedDeletes += path
                lastState.remove(path)
            } else {
                try {
                    val connection = open("DELETE", "$DRIVE_API/files/${remoteFile.id}", accessToken)
                    ensureSuccess(connection)
                    remote.remove(path)
                    completedDeletes += path
                    lastState.remove(path)
                } catch (e: AuthExpiredException) {
                    throw e
                } catch (_: Exception) {
                    // Keep tombstone for the next sync attempt.
                }
            }
        }
        if (completedDeletes.isNotEmpty()) {
            tombstones.removeAll(completedDeletes)
            writeTombstones(tombstones)
        }
        var uploaded = 0
        var downloaded = 0
        var conflicts = 0
        var unchanged = 0
        var errors = 0

        (local.keys + remote.keys).toSortedSet().forEach { path ->
            val localFile = local[path]
            val remoteFile = remote[path]
            try {
                when {
                    localFile == null && remoteFile != null -> {
                        val target = File(fileOps.persistentDataRoot(), path)
                        if (download(accessToken, remoteFile, target)) {
                            downloaded++
                            lastState[path] = remoteFile.hash.ifBlank { sha256File(target) }
                        } else errors++
                    }
                    localFile != null && remoteFile == null -> {
                        val hash = sha256File(localFile)
                        val created = upload(accessToken, rootId, path, hash, localFile, null)
                        if (created != null) {
                            uploaded++
                            lastState[path] = hash
                            remote[path] = created
                        } else errors++
                    }
                    localFile != null && remoteFile != null -> {
                        val localHash = sha256File(localFile)
                        val remoteHash = remoteFile.hash.ifBlank { downloadHash(accessToken, remoteFile) }
                        if (localHash == remoteHash && localHash.isNotBlank()) {
                            unchanged++
                            lastState[path] = localHash
                            return@forEach
                        }
                        val previous = lastState[path]
                        val localChanged = previous == null || localHash != previous
                        val remoteChanged = previous == null || remoteHash != previous
                        when {
                            previous != null && localChanged && !remoteChanged -> {
                                if (upload(accessToken, rootId, path, localHash, localFile, remoteFile.id) != null) {
                                    uploaded++
                                    lastState[path] = localHash
                                } else errors++
                            }
                            previous != null && !localChanged && remoteChanged -> {
                                if (download(accessToken, remoteFile, localFile)) {
                                    downloaded++
                                    lastState[path] = remoteHash
                                } else errors++
                            }
                            else -> {
                                conflicts++
                                if (remoteFile.modifiedAt > localFile.lastModified() + CLOCK_TOLERANCE_MS) {
                                    preserveLocalConflict(path, localFile)
                                    if (download(accessToken, remoteFile, localFile)) {
                                        downloaded++
                                        lastState[path] = remoteHash
                                    } else errors++
                                } else {
                                    preserveRemoteConflict(accessToken, path, remoteFile)
                                    if (upload(accessToken, rootId, path, localHash, localFile, remoteFile.id) != null) {
                                        uploaded++
                                        lastState[path] = localHash
                                    } else errors++
                                }
                            }
                        }
                    }
                }
            } catch (e: AuthExpiredException) {
                throw e
            } catch (_: Exception) {
                errors++
            }
        }
        writeState(lastState)
        return SyncResult(uploaded, downloaded, conflicts, unchanged, errors)
    }

    private fun localFiles(): Map<String, File> {
        val root = fileOps.persistentDataRoot()
        val out = linkedMapOf<String, File>()
        if (!root.exists()) return out
        root.walkTopDown().filter { it.isFile && it.length() > 0L }.forEach { file ->
            val path = file.relativeTo(root).invariantSeparatorsPath
            if (!path.startsWith("Backups/CloudConflicts/")) out[path] = file
        }
        return out
    }

    private fun findOrCreateRoot(token: String): String {
        val q = "trashed=false and mimeType='application/vnd.google-apps.folder' and appProperties has { key='retraRoot' and value='1' }"
        val url = "$DRIVE_API/files?q=${encode(q)}&spaces=drive&fields=files(id,name)&pageSize=10"
        val result = requestJson("GET", url, token)
        val files = result.optJSONArray("files") ?: JSONArray()
        if (files.length() > 0) return files.getJSONObject(0).getString("id")

        val metadata = JSONObject().apply {
            put("name", "Retra Sync")
            put("mimeType", "application/vnd.google-apps.folder")
            put("appProperties", JSONObject().put("retraRoot", "1"))
        }
        return requestJson("POST", "$DRIVE_API/files?fields=id", token, metadata.toString().toByteArray()).getString("id")
    }

    private fun listRemoteFiles(token: String, rootId: String): List<RemoteFile> {
        val out = mutableListOf<RemoteFile>()
        var pageToken: String? = null
        do {
            val q = "'$rootId' in parents and trashed=false"
            val suffix = buildString {
                append("?q=").append(encode(q))
                append("&spaces=drive&pageSize=1000")
                append("&fields=nextPageToken,files(id,name,size,modifiedTime,appProperties)")
                if (!pageToken.isNullOrBlank()) append("&pageToken=").append(encode(pageToken!!))
            }
            val json = requestJson("GET", "$DRIVE_API/files$suffix", token)
            val files = json.optJSONArray("files") ?: JSONArray()
            for (index in 0 until files.length()) {
                val item = files.getJSONObject(index)
                val props = item.optJSONObject("appProperties") ?: JSONObject()
                val path = props.optString("retraPath", "")
                if (path.isBlank()) continue
                out += RemoteFile(
                    id = item.getString("id"),
                    path = path,
                    hash = props.optString("retraSha256", ""),
                    modifiedAt = parseDriveTime(item.optString("modifiedTime", "")),
                    size = item.optString("size", "0").toLongOrNull() ?: 0L
                )
            }
            pageToken = json.optString("nextPageToken", "").ifBlank { null }
        } while (pageToken != null)
        return out
    }

    private fun upload(
        token: String,
        rootId: String,
        path: String,
        hash: String,
        source: File,
        existingId: String?
    ): RemoteFile? {
        val metadata = JSONObject().apply {
            put("name", path.substringAfterLast('/'))
            if (existingId == null) put("parents", JSONArray().put(rootId))
            put("appProperties", JSONObject().apply {
                put("retraPath", path)
                put("retraSha256", hash)
                put("retraSync", "1")
            })
        }
        val boundary = "RetraBoundary${System.nanoTime()}"
        val header = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
            metadata.toString() + "\r\n--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n").toByteArray()
        val footer = "\r\n--$boundary--\r\n".toByteArray()
        val endpoint = if (existingId == null) "$DRIVE_UPLOAD/files?uploadType=multipart&fields=id,size,modifiedTime,appProperties"
            else "$DRIVE_UPLOAD/files/$existingId?uploadType=multipart&fields=id,size,modifiedTime,appProperties"
        val method = if (existingId == null) "POST" else "PATCH"
        val json = requestMultipart(method, endpoint, token, boundary, header, source, footer)
        val props = json.optJSONObject("appProperties") ?: metadata.getJSONObject("appProperties")
        return RemoteFile(
            id = json.getString("id"),
            path = props.optString("retraPath", path),
            hash = props.optString("retraSha256", hash),
            modifiedAt = parseDriveTime(json.optString("modifiedTime", "")),
            size = json.optString("size", source.length().toString()).toLongOrNull() ?: source.length()
        )
    }

    private fun download(token: String, remote: RemoteFile, target: File): Boolean {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.drive.tmp")
        return try {
            val connection = open("GET", "$DRIVE_API/files/${remote.id}?alt=media", token)
            ensureSuccess(connection)
            connection.inputStream.use { input ->
                FileOutputStream(tmp).use { output ->
                    input.copyTo(output, 64 * 1024)
                    output.flush()
                    runCatching { output.fd.sync() }
                }
            }
            if (remote.size > 0L && tmp.length() != remote.size) return false
            if (remote.hash.isNotBlank() && !sha256File(tmp).equals(remote.hash, true)) return false
            fileOps.moveTempIntoPlace(tmp, target, "Could not replace Drive data")
            if (remote.modifiedAt > 0L) target.setLastModified(remote.modifiedAt)
            true
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun downloadHash(token: String, remote: RemoteFile): String {
        val temp = File(filesDir, ".drive_hash_${System.nanoTime()}.tmp")
        return try {
            if (!download(token, remote, temp)) "" else sha256File(temp)
        } finally {
            temp.delete()
        }
    }

    private fun preserveLocalConflict(path: String, file: File) {
        val target = conflictFile(path, "local")
        target.parentFile?.mkdirs()
        runCatching { fileOps.atomicCopyVerified(file, target) }
    }

    private fun preserveRemoteConflict(token: String, path: String, remote: RemoteFile) {
        val target = conflictFile(path, "drive")
        runCatching { download(token, remote, target) }
    }

    private fun conflictFile(path: String, side: String): File {
        val safePath = path.split('/').joinToString("/") { fileOps.sanitizeFileName(it) }
        return File(File(fileOps.persistentCategoryDir("Backups"), "CloudConflicts"), "${System.currentTimeMillis()}/$side/$safePath")
    }

    private fun readTombstones(): Set<String> {
        val file = File(filesDir, TOMBSTONE_FILE)
        if (!file.exists()) return emptySet()
        return runCatching {
            val array = JSONArray(file.readText())
            buildSet {
                for (index in 0 until array.length()) {
                    val path = array.optString(index, "")
                    if (path.isNotBlank()) add(path)
                }
            }
        }.getOrDefault(emptySet())
    }

    private fun writeTombstones(paths: Set<String>) {
        val array = JSONArray()
        paths.toSortedSet().forEach(array::put)
        runCatching { fileOps.atomicWriteText(File(filesDir, TOMBSTONE_FILE), array.toString()) }
    }

    private fun readState(): Map<String, String> {
        val file = File(filesDir, STATE_FILE)
        if (!file.exists()) return emptyMap()
        return runCatching {
            val obj = JSONObject(file.readText()).optJSONObject("files") ?: JSONObject()
            buildMap {
                val names = obj.keys()
                while (names.hasNext()) {
                    val name = names.next()
                    val hash = obj.optString(name, "")
                    if (hash.isNotBlank()) put(name, hash)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun writeState(state: Map<String, String>) {
        val json = JSONObject().apply {
            put("version", 1)
            put("updatedAt", System.currentTimeMillis())
            put("files", JSONObject().apply { state.toSortedMap().forEach { (path, hash) -> put(path, hash) } })
        }
        runCatching { fileOps.atomicWriteText(File(filesDir, STATE_FILE), json.toString()) }
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
        method: String,
        url: String,
        token: String,
        boundary: String,
        header: ByteArray,
        source: File,
        footer: ByteArray
    ): JSONObject {
        val connection = open(if (method == "PATCH") "POST" else method, url, token)
        if (method == "PATCH") connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        connection.doOutput = true
        connection.setChunkedStreamingMode(64 * 1024)
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        connection.outputStream.use { output ->
            output.write(header)
            FileInputStream(source).use { it.copyTo(output, 64 * 1024) }
            output.write(footer)
        }
        ensureSuccess(connection)
        return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    }

    private fun open(method: String, url: String, token: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            useCaches = false
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }

    private fun ensureSuccess(connection: HttpURLConnection) {
        val code = connection.responseCode
        if (code == 401 || code == 403) throw AuthExpiredException()
        if (code !in 200..299) {
            val message = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
            error("Drive API $code${if (message.isNullOrBlank()) "" else ": $message"}")
        }
    }

    private fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun parseDriveTime(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    companion object {
        private const val DRIVE_API = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val STATE_FILE = "drive_api_sync_state_v1.json"
        private const val TOMBSTONE_FILE = "drive_api_tombstones_v1.json"
        private const val CLOCK_TOLERANCE_MS = 1500L
    }
}
