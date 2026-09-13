package com.retra.emulator

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Portable Retra backup/restore container.
 *
 * A .retra file is a normal ZIP container with a small manifest plus only the
 * user-selected Retra data. ROM files and BIOS files are deliberately excluded.
 */
class BackupRepository(
    private val context: Context,
    private val prefs: RetraPreferences,
    private val fileOps: RetraFileOps,
    private val romIdentityStore: RomIdentityStore,
    private val preparePortableMetadata: () -> Unit
) {
    data class Selection(
        val saves: Boolean = true,
        val saveStates: Boolean = true,
        val cheats: Boolean = true,
        val layouts: Boolean = true,
        val artwork: Boolean = true,
        val library: Boolean = true,
        val settings: Boolean = true
    ) {
        fun anySelected(): Boolean = saves || saveStates || cheats || layouts || artwork || library || settings

        fun toJson(): JSONObject = JSONObject()
            .put("saves", saves)
            .put("saveStates", saveStates)
            .put("cheats", cheats)
            .put("layouts", layouts)
            .put("artwork", artwork)
            .put("library", library)
            .put("settings", settings)
    }

    data class Result(
        val fileCount: Int,
        val byteCount: Long,
        val included: Set<String>
    )

    fun parseSelection(json: String): Selection {
        val source = runCatching { JSONObject(json) }.getOrElse { JSONObject() }
        return Selection(
            saves = source.optBoolean("saves", true),
            saveStates = source.optBoolean("saveStates", true),
            cheats = source.optBoolean("cheats", true),
            layouts = source.optBoolean("layouts", true),
            artwork = source.optBoolean("artwork", true),
            library = source.optBoolean("library", true),
            settings = source.optBoolean("settings", true)
        )
    }

    fun suggestedFileName(now: Date = Date()): String =
        SimpleDateFormat("'Retra_'yyyyMMdd_HHmm'.retra'", Locale.US).format(now)

    fun create(uri: Uri, selection: Selection): Result {
        require(selection.anySelected()) { "Choose at least one item to back up" }
        preparePortableMetadata()

        val included = linkedSetOf<String>()
        var fileCount = 0
        var byteCount = 0L

        val output = context.contentResolver.openOutputStream(uri, "w")
            ?: throw IOException("Could not open the selected backup destination")

        output.use { raw ->
            ZipOutputStream(BufferedOutputStream(raw, BUFFER_SIZE)).use { zip ->
                val manifest = JSONObject()
                    .put("format", FORMAT)
                    .put("schemaVersion", SCHEMA_VERSION)
                    .put("createdAt", System.currentTimeMillis())
                    .put("selection", selection.toJson())
                    .put("note", "Retra portable backup; ROM and BIOS files are not included")

                putTextEntry(zip, MANIFEST_ENTRY, manifest.toString(2))
                fileCount++

                fun addCategory(name: String) {
                    val dir = File(fileOps.persistentDataRoot(), name)
                    if (!dir.exists()) return
                    included += name
                    val result = addDirectory(zip, dir, "data/$name")
                    fileCount += result.first
                    byteCount += result.second
                }

                if (selection.saves) addCategory("Saves")
                if (selection.saveStates) addCategory("SaveStates")
                if (selection.cheats) addCategory("Cheats")
                if (selection.layouts) addCategory("Layouts")
                if (selection.artwork) {
                    addCategory("Covers")
                    addCategory("Backgrounds")
                }
                if (selection.settings) addCategory("Config")

                val metadataDir = fileOps.persistentCategoryDir("Metadata")
                if (selection.library) {
                    included += "Library"
                    listOf("library.json", "unmatched_legacy.json").forEach { name ->
                        val file = File(metadataDir, name)
                        if (file.isFile) {
                            addFile(zip, file, "data/Metadata/$name")
                            fileCount++
                            byteCount += file.length()
                        }
                    }
                }
                if (selection.settings) {
                    included += "Settings"
                    val file = File(metadataDir, "settings.json")
                    if (file.isFile) {
                        addFile(zip, file, "data/Metadata/settings.json")
                        fileCount++
                        byteCount += file.length()
                    }
                }

                // Write a compact inventory last so interrupted writes are easy
                // to distinguish from fully completed Retra backups.
                putTextEntry(
                    zip,
                    INVENTORY_ENTRY,
                    JSONObject()
                        .put("complete", true)
                        .put("files", fileCount)
                        .put("bytes", byteCount)
                        .put("included", JSONArray(included.toList()))
                        .toString(2)
                )
                fileCount++
            }
        }

        return Result(fileCount, byteCount, included)
    }

    fun restore(uri: Uri): Result {
        val staging = File(context.cacheDir, "retra-restore-${UUID.randomUUID()}")
        staging.mkdirs()

        var fileCount = 0
        var byteCount = 0L
        var manifest: JSONObject? = null
        var complete = false

        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open this backup")
            input.use { raw ->
                ZipInputStream(BufferedInputStream(raw, BUFFER_SIZE)).use { zip ->
                    var entries = 0
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        entries++
                        if (entries > MAX_ENTRIES) throw IOException("Backup contains too many files")
                        val safeName = sanitizeEntryName(entry.name)

                        when (safeName) {
                            MANIFEST_ENTRY -> {
                                val text = readEntryText(zip, MAX_METADATA_BYTES)
                                manifest = JSONObject(text)
                            }
                            INVENTORY_ENTRY -> {
                                val text = readEntryText(zip, MAX_METADATA_BYTES)
                                complete = JSONObject(text).optBoolean("complete", false)
                            }
                            else -> {
                                if (!safeName.startsWith("data/") || entry.isDirectory) {
                                    zip.closeEntry()
                                    continue
                                }
                                validateDataEntry(safeName)
                                val relative = safeName.removePrefix("data/")
                                val target = safeChild(staging, relative)
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { output ->
                                    val buffer = ByteArray(BUFFER_SIZE)
                                    while (true) {
                                        val read = zip.read(buffer)
                                        if (read <= 0) break
                                        byteCount += read
                                        if (byteCount > MAX_UNCOMPRESSED_BYTES) {
                                            throw IOException("Backup is too large")
                                        }
                                        output.write(buffer, 0, read)
                                    }
                                    output.flush()
                                    runCatching { output.fd.sync() }
                                }
                                fileCount++
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }

            val header = manifest ?: throw IOException("This is not a Retra backup")
            if (header.optString("format") != FORMAT || header.optInt("schemaVersion", 0) !in 1..SCHEMA_VERSION) {
                throw IOException("Unsupported Retra backup format")
            }
            if (!complete) throw IOException("This backup is incomplete or damaged")

            val selection = parseSelection(header.optJSONObject("selection")?.toString() ?: "{}")
            val included = linkedSetOf<String>()

            fun restoreCategory(name: String, enabled: Boolean) {
                if (!enabled) return
                val source = File(staging, name)
                if (!source.exists()) return
                mergeDirectory(source, fileOps.persistentCategoryDir(name))
                included += name
            }

            restoreCategory("Saves", selection.saves)
            restoreCategory("SaveStates", selection.saveStates)
            restoreCategory("Cheats", selection.cheats)
            restoreCategory("Layouts", selection.layouts)
            restoreCategory("Covers", selection.artwork)
            restoreCategory("Backgrounds", selection.artwork)
            restoreCategory("Config", selection.settings)

            val stagedMetadata = File(staging, "Metadata")
            val targetMetadata = fileOps.persistentCategoryDir("Metadata")

            if (selection.library) {
                listOf("library.json", "unmatched_legacy.json").forEach { name ->
                    val source = File(stagedMetadata, name)
                    if (source.isFile) fileOps.atomicCopyVerified(source, File(targetMetadata, name))
                }
                restoreLibraryMetadata(File(stagedMetadata, "library.json"))
                included += "Library"
            }

            if (selection.settings) {
                val settingsFile = File(stagedMetadata, "settings.json")
                if (settingsFile.isFile) {
                    fileOps.atomicCopyVerified(settingsFile, File(targetMetadata, "settings.json"))
                    restorePortableSettings(settingsFile)
                }
                included += "Settings"
            }

            preparePortableMetadata()
            return Result(fileCount, byteCount, included)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun restorePortableSettings(file: File) {
        val root = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull() ?: return
        val settings = root.optJSONObject("settings") ?: return
        val values = linkedMapOf<String, Any>()
        val keys = settings.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            when (val value = settings.opt(key)) {
                is String, is Boolean, is Int, is Long -> values[key] = value
                is Number -> {
                    val longValue = value.toLong()
                    if (value.toDouble() == longValue.toDouble()) {
                        values[key] = if (longValue >= Int.MIN_VALUE.toLong() && longValue <= Int.MAX_VALUE.toLong()) longValue.toInt() else longValue
                    }
                }
            }
        }
        prefs.restorePortableSettings(values)
    }

    private fun restoreLibraryMetadata(file: File) {
        if (!file.isFile) return
        val root = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull() ?: return
        val roms = root.optJSONArray("roms") ?: return
        for (index in 0 until roms.length()) {
            val item = roms.optJSONObject(index) ?: continue
            val romId = item.optString("romId").trim()
            if (romId.isBlank() || romIdentityStore.getById(romId) == null) continue
            val categories = item.optJSONArray("categories")?.toString() ?: "[]"
            romIdentityStore.updateLibraryMetadata(romId, item.optBoolean("favorite", false), categories)
            romIdentityStore.updatePlaytime(romId, item.optLong("playtimeMs", 0L))
            romIdentityStore.setArchived(romId, item.optBoolean("archived", false))
        }
    }

    private fun addDirectory(zip: ZipOutputStream, directory: File, prefix: String): Pair<Int, Long> {
        var count = 0
        var bytes = 0L
        directory.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.relativeTo(directory).invariantSeparatorsPath }
            .forEach { file ->
                val relative = file.relativeTo(directory).invariantSeparatorsPath
                addFile(zip, file, "$prefix/$relative")
                count++
                bytes += file.length()
            }
        return count to bytes
    }

    private fun addFile(zip: ZipOutputStream, file: File, entryName: String) {
        val entry = ZipEntry(entryName).apply { time = file.lastModified() }
        zip.putNextEntry(entry)
        FileInputStream(file).use { input -> input.copyTo(zip, BUFFER_SIZE) }
        zip.closeEntry()
    }

    private fun putTextEntry(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun readEntryText(zip: ZipInputStream, maxBytes: Int): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = zip.read(buffer)
            if (read <= 0) break
            if (out.size() + read > maxBytes) throw IOException("Backup metadata is too large")
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun sanitizeEntryName(raw: String): String {
        val normalized = raw.replace('\\', '/').trimStart('/')
        if (normalized.isBlank() || normalized.split('/').any { it == ".." }) {
            throw IOException("Unsafe backup path")
        }
        return normalized
    }

    private fun validateDataEntry(name: String) {
        val relative = name.removePrefix("data/")
        val top = relative.substringBefore('/')
        if (top !in ALLOWED_TOP_LEVEL) throw IOException("Unsupported backup content")
        if (top == "Metadata") {
            val leaf = relative.removePrefix("Metadata/")
            if (leaf !in ALLOWED_METADATA_FILES) throw IOException("Unsupported backup metadata")
        }
    }

    private fun safeChild(root: File, relative: String): File {
        val child = File(root, relative)
        val rootPath = root.canonicalFile.toPath()
        val childPath = child.canonicalFile.toPath()
        if (!childPath.startsWith(rootPath)) throw IOException("Unsafe backup path")
        return child
    }

    private fun mergeDirectory(source: File, destination: File) {
        source.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(source).invariantSeparatorsPath
            val target = safeChild(destination, relative)
            fileOps.atomicCopyVerified(file, target)
        }
    }

    companion object {
        private const val FORMAT = "RetraBackup"
        private const val SCHEMA_VERSION = 1
        private const val MANIFEST_ENTRY = "manifest.json"
        private const val INVENTORY_ENTRY = "inventory.json"
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_ENTRIES = 4096
        private const val MAX_METADATA_BYTES = 2 * 1024 * 1024
        private const val MAX_UNCOMPRESSED_BYTES = 2L * 1024L * 1024L * 1024L

        private val ALLOWED_TOP_LEVEL = setOf(
            "Saves", "SaveStates", "Cheats", "Layouts", "Covers", "Backgrounds", "Config", "Metadata"
        )
        private val ALLOWED_METADATA_FILES = setOf("settings.json", "library.json", "unmatched_legacy.json")
    }
}
