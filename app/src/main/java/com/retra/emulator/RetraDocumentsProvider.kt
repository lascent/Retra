package com.retra.emulator

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Base64

/**
 * Exposes Retra's managed data directly in Android's system Files/Documents UI.
 *
 * Retra itself always reads and writes the private persistent_data directory, so
 * gameplay never depends on a user-selected SAF tree or a repeated "Use this
 * folder" confirmation. The system Files app reaches the exact same files
 * through this DocumentsProvider, similar to emulator apps that appear as their
 * own storage location in the Files navigation drawer.
 */
class RetraDocumentsProvider : DocumentsProvider() {

    override fun onCreate(): Boolean {
        ensurePortableStructure(dataRoot())
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val columns = copyProjection(projection, DEFAULT_ROOT_PROJECTION)
        val result = MatrixCursor(columns)
        val root = dataRoot()
        ensurePortableStructure(root)
        val row = result.newRow()
        columns.forEach { column ->
            when (column) {
                Root.COLUMN_ROOT_ID -> row.add(ROOT_ID)
                Root.COLUMN_DOCUMENT_ID -> row.add(ROOT_DOCUMENT_ID)
                Root.COLUMN_TITLE -> row.add("Retra")
                Root.COLUMN_SUMMARY -> row.add(rootSummary(root))
                Root.COLUMN_FLAGS -> row.add(Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD)
                Root.COLUMN_ICON -> row.add(R.mipmap.ic_launcher)
                Root.COLUMN_AVAILABLE_BYTES -> row.add(availableBytes(root))
                Root.COLUMN_MIME_TYPES -> row.add("*/*")
                else -> row.add(null)
            }
        }
        context?.let { ctx ->
            result.setNotificationUri(ctx.contentResolver, DocumentsContract.buildRootsUri(AUTHORITY))
        }
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val columns = copyProjection(projection, DEFAULT_DOCUMENT_PROJECTION)
        val result = MatrixCursor(columns)
        includeDocument(result, columns, documentId, fileForDocumentId(documentId))
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val columns = copyProjection(projection, DEFAULT_DOCUMENT_PROJECTION)
        val result = MatrixCursor(columns)
        val parent = fileForDocumentId(parentDocumentId)
        if (!parent.exists() || !parent.isDirectory) return result

        parent.listFiles()
            ?.asSequence()
            ?.filterNot { it.name.startsWith(".") }
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            ?.forEach { child -> includeDocument(result, columns, documentIdForFile(child), child) }
        context?.let { ctx ->
            // Every open Retra directory observes one provider-wide change URI,
            // so a save written by the emulator refreshes Files even when the
            // user is currently inside a nested ROM folder.
            result.setNotificationUri(ctx.contentResolver, DocumentsContract.buildRootsUri(AUTHORITY))
        }
        return result
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = fileForDocumentId(documentId)
        if (!file.exists() || !file.isFile) throw FileNotFoundException(documentId)
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        return ParcelFileDescriptor.open(file, accessMode)
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = fileForDocumentId(parentDocumentId)
        if (!parent.exists() || !parent.isDirectory) throw FileNotFoundException(parentDocumentId)
        val safeName = safeDisplayName(displayName)
        val target = uniqueChild(parent, safeName)
        val created = if (mimeType == Document.MIME_TYPE_DIR) target.mkdir() else {
            target.parentFile?.mkdirs()
            target.createNewFile()
        }
        if (!created) throw IOException("Could not create $displayName")
        notifyProviderChanged()
        return documentIdForFile(target)
    }

    override fun deleteDocument(documentId: String) {
        if (documentId == ROOT_DOCUMENT_ID) throw FileNotFoundException("Retra root cannot be deleted")
        val file = fileForDocumentId(documentId)
        val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
        if (!deleted && file.exists()) throw IOException("Could not delete ${file.name}")
        notifyProviderChanged()
    }

    override fun renameDocument(documentId: String, displayName: String): String? {
        if (documentId == ROOT_DOCUMENT_ID) return null
        val file = fileForDocumentId(documentId)
        if (!file.exists()) throw FileNotFoundException(documentId)
        val target = uniqueChild(file.parentFile ?: dataRoot(), safeDisplayName(displayName), file)
        if (target == file) return null
        if (!file.renameTo(target)) throw IOException("Could not rename ${file.name}")
        notifyProviderChanged()
        return documentIdForFile(target)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = runCatching { fileForDocumentId(parentDocumentId).canonicalFile }.getOrNull() ?: return false
        val child = runCatching { fileForDocumentId(documentId).canonicalFile }.getOrNull() ?: return false
        if (parent == child) return false
        var current: File? = child.parentFile
        while (current != null) {
            if (current == parent) return true
            if (current == dataRoot().parentFile) break
            current = current.parentFile
        }
        return false
    }

    private fun copyProjection(projection: Array<out String>?, fallback: Array<String>): Array<String> {
        return projection?.let { source ->
            Array(source.size) { index -> source[index] }
        } ?: fallback
    }

    private fun includeDocument(cursor: MatrixCursor, columns: Array<String>, documentId: String, file: File) {
        val row = cursor.newRow()
        columns.forEach { column ->
            when (column) {
                Document.COLUMN_DOCUMENT_ID -> row.add(documentId)
                Document.COLUMN_DISPLAY_NAME -> row.add(if (documentId == ROOT_DOCUMENT_ID) "Retra" else file.name)
                Document.COLUMN_MIME_TYPE -> row.add(if (file.isDirectory) Document.MIME_TYPE_DIR else mimeType(file))
                Document.COLUMN_FLAGS -> row.add(documentFlags(file, documentId == ROOT_DOCUMENT_ID))
                Document.COLUMN_SIZE -> row.add(if (file.isFile) file.length() else null)
                Document.COLUMN_LAST_MODIFIED -> row.add(file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis())
                Document.COLUMN_ICON -> row.add(if (documentId == ROOT_DOCUMENT_ID) R.mipmap.ic_launcher else null)
                else -> row.add(null)
            }
        }
    }

    private fun documentFlags(file: File, isRoot: Boolean): Int {
        if (isRoot) return Document.FLAG_DIR_SUPPORTS_CREATE
        return if (file.isDirectory) {
            Document.FLAG_DIR_SUPPORTS_CREATE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        } else {
            Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        }
    }

    private fun dataRoot(): File {
        val ctx = context ?: throw IllegalStateException("Provider context unavailable")
        return File(ctx.filesDir, "persistent_data").apply { mkdirs() }
    }

    private fun fileForDocumentId(documentId: String): File {
        val root = dataRoot().canonicalFile
        if (documentId == ROOT_DOCUMENT_ID) return root
        if (!documentId.startsWith(PATH_PREFIX)) throw FileNotFoundException(documentId)
        val encoded = documentId.removePrefix(PATH_PREFIX)
        val relative = try {
            String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
        } catch (_: Exception) {
            throw FileNotFoundException(documentId)
        }
        val candidate = File(root, relative).canonicalFile
        if (candidate != root && !candidate.path.startsWith(root.path + File.separator)) {
            throw FileNotFoundException(documentId)
        }
        return candidate
    }

    private fun documentIdForFile(file: File): String {
        val root = dataRoot().canonicalFile
        val canonical = file.canonicalFile
        if (canonical == root) return ROOT_DOCUMENT_ID
        if (!canonical.path.startsWith(root.path + File.separator)) throw FileNotFoundException(file.path)
        val relative = canonical.relativeTo(root).invariantSeparatorsPath
        return PATH_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(relative.toByteArray(Charsets.UTF_8))
    }

    private fun mimeType(file: File): String {
        val extension = file.extension.lowercase()
        if (extension.isBlank()) return "application/octet-stream"
        return when (extension) {
            "json" -> "application/json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "sav", "srm", "rtc", "ss", "state" -> "application/octet-stream"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        }
    }

    private fun safeDisplayName(name: String): String = name
        .replace('/', '_')
        .replace('\\', '_')
        .trim()
        .ifBlank { "Untitled" }

    private fun uniqueChild(parent: File, requestedName: String, current: File? = null): File {
        val direct = File(parent, requestedName)
        if (direct == current || !direct.exists()) return direct
        val dot = requestedName.lastIndexOf('.')
        val stem = if (dot > 0) requestedName.substring(0, dot) else requestedName
        val extension = if (dot > 0) requestedName.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = File(parent, "$stem ($index)$extension")
            if (!candidate.exists() || candidate == current) return candidate
            index++
        }
    }

    private fun notifyProviderChanged() {
        val ctx = context ?: return
        ctx.contentResolver.notifyChange(DocumentsContract.buildRootsUri(AUTHORITY), null)
        ctx.contentResolver.notifyChange(DocumentsContract.buildDocumentUri(AUTHORITY, ROOT_DOCUMENT_ID), null)
        ctx.contentResolver.notifyChange(DocumentsContract.buildChildDocumentsUri(AUTHORITY, ROOT_DOCUMENT_ID), null)
    }

    private fun availableBytes(root: File): Long = runCatching { StatFs(root.path).availableBytes }.getOrDefault(0L)

    private fun rootSummary(root: File): String {
        val freeBytes = availableBytes(root)
        return "Retra storage • ${formatFreeSpace(freeBytes)} free"
    }

    private fun formatFreeSpace(bytes: Long): String {
        if (bytes <= 0L) return "0 GB"
        // Android/device storage UIs report decimal GB (1 GB = 1,000,000,000 bytes).
        // Round to a whole number so 161.6 GB is shown as 162 GB, matching My Boy!-style display.
        val decimalGb = bytes.toDouble() / 1_000_000_000.0
        return "${kotlin.math.round(decimalGb).toLong()} GB"
    }

    private fun ensurePortableStructure(root: File) {
        DEFAULT_DIRECTORIES.forEach { File(root, it).mkdirs() }
    }

    companion object {
        const val AUTHORITY = "com.retra.emulator.documents"
        const val ROOT_ID = "retra"
        const val ROOT_DOCUMENT_ID = "root"
        private const val PATH_PREFIX = "p:"

        private val DEFAULT_DIRECTORIES = listOf(
            "Saves", "SaveStates", "Cheats", "Config", "Layouts",
            "Covers", "Backgrounds", "Backups", "Metadata"
        )

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_AVAILABLE_BYTES,
            Root.COLUMN_MIME_TYPES
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_ICON
        )

        fun rootDocumentUri() = DocumentsContract.buildDocumentUri(AUTHORITY, ROOT_DOCUMENT_ID)

        fun rootUri() = DocumentsContract.buildRootUri(AUTHORITY, ROOT_ID)

        fun notifyDataChanged(context: Context) {
            context.contentResolver.notifyChange(DocumentsContract.buildRootsUri(AUTHORITY), null)
            context.contentResolver.notifyChange(rootDocumentUri(), null)
            context.contentResolver.notifyChange(DocumentsContract.buildChildDocumentsUri(AUTHORITY, ROOT_DOCUMENT_ID), null)
        }
    }
}
