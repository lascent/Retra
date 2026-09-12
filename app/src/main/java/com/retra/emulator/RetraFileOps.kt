package com.retra.emulator

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Centralized filesystem/content-resolver primitives used across Retra.
 *
 * Keeping verified copy/write/hash logic in one component prevents import,
 * save, metadata and artwork code from each carrying slightly different
 * durability rules inside MainActivity.
 */
class RetraFileOps(private val context: Context) {

    fun queryDisplayName(uri: Uri): String? =
        queryFileInfo(uri).first ?: uri.lastPathSegment?.substringAfterLast('/')

    fun queryFileInfo(uri: Uri): Pair<String?, Long> {
        var result: Pair<String?, Long>? = null
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                result = name to size
            }
        }
        return result ?: (uri.lastPathSegment?.substringAfterLast('/') to 0L)
    }

    fun queryLastModified(uri: Uri): Long {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        var modified = 0L
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (index >= 0 && !cursor.isNull(index)) modified = cursor.getLong(index).coerceAtLeast(0L)
                }
            }
        }
        return modified
    }

    fun sanitizeFileName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .ifBlank { "rom" }

    fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun moveTempIntoPlace(tmp: File, target: File, errorMessage: String) {
        try {
            java.nio.file.Files.move(
                tmp.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
            return
        } catch (_: Exception) {}

        try {
            java.nio.file.Files.move(
                tmp.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
            return
        } catch (_: Exception) {}

        if (!target.exists() && tmp.renameTo(target)) return
        throw IOException(errorMessage)
    }

    fun atomicCopyVerified(source: File, target: File) {
        require(source.exists() && source.isFile) { "Import source is unavailable" }
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(tmp).use { output ->
                    input.copyTo(output, 64 * 1024)
                    output.flush()
                    try { output.fd.sync() } catch (_: Exception) {}
                }
            }
            if (tmp.length() != source.length() || !sha256File(tmp).equals(sha256File(source), ignoreCase = true)) {
                throw IOException("Import verification failed")
            }
            moveTempIntoPlace(tmp, target, "Could not replace verified file")
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    fun atomicWriteBytes(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(tmp).use { output ->
                output.write(bytes)
                output.flush()
                try { output.fd.sync() } catch (_: Exception) {}
            }
            val expected = MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
            if (tmp.length() != bytes.size.toLong() || !sha256File(tmp).equals(expected, true)) {
                throw IOException("Save verification failed")
            }
            moveTempIntoPlace(tmp, target, "Could not replace save")
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    fun atomicWriteText(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(tmp).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.flush()
                try { output.fd.sync() } catch (_: Exception) {}
            }
            moveTempIntoPlace(tmp, target, "Could not replace metadata")
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    fun tryPersistReadPermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // Retra keeps a managed copy, so providers that cannot persist the
            // source URI are not fatal.
        }
    }

    fun persistentDataRoot(): File = File(context.filesDir, "persistent_data").apply { mkdirs() }

    fun persistentCategoryDir(name: String): File = File(persistentDataRoot(), name).apply { mkdirs() }
}
