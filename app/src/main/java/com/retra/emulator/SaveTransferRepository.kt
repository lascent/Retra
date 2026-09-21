package com.retra.emulator

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONObject

/**
 * Owns portable Retra-data tree traversal, cloud/file-tree synchronization and
 * external save import. Keeping SAF I/O here prevents MainActivity from
 * becoming a filesystem synchronization service.
 */
class SaveTransferRepository(
    context: Context,
    private val prefs: RetraPreferences,
    private val fileOps: RetraFileOps,
    private val romIdentityStore: RomIdentityStore,
    private val saveData: SaveDataRepository,
    private val saveStates: SaveStateRepository,
    private val preparePortableMetadata: () -> Unit
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    fun deleteRemotePaths(rootUri: Uri, paths: List<String>) {
        val root = DocumentFile.fromTreeUri(appContext, rootUri) ?: return
        paths.forEach { relative ->
            val parts = relative.split('/').filter { it.isNotBlank() }
            var node: DocumentFile? = root
            parts.forEach { segment -> node = node?.findFile(segment) }
            runCatching { node?.delete() }
        }
    }

    data class SyncResult(
        val uploaded: Int = 0,
        val downloaded: Int = 0,
        val conflicts: Int = 0,
        val unchanged: Int = 0,
        val errors: Int = 0
    )


    data class ImportSaveResult(
        val success: Boolean,
        val romId: String? = null,
        val title: String? = null,
        val message: String
    )

    /** Backward-compatible summary used by older callers/tests. */
    fun sync(rootUri: Uri): Pair<Int, Int> {
        val result = syncDetailed(rootUri)
        return result.uploaded to result.downloaded
    }

    /**
     * Conflict-safe bidirectional Drive/SAF synchronization.
     *
     * A device-local hash journal remembers the exact version this device last
     * synchronized. This avoids relying only on Drive timestamps, which can be
     * rounded or delayed. If both local and remote changed independently, the
     * losing copy is preserved under Backups/CloudConflicts before the newer
     * copy wins, so sync never silently destroys either side.
     */
    fun syncDetailed(rootUri: Uri, preferRemoteOnFirstSync: Boolean = false): SyncResult {
        val root = DocumentFile.fromTreeUri(appContext, rootUri) ?: return SyncResult(errors = 1)
        if (!root.canRead() || !root.canWrite()) return SyncResult(errors = 1)

        val local = localSyncFiles().toMutableMap()
        ensureTreeStructure(root)
        val remote = linkedMapOf<String, DocumentFile>()
        collectRemoteFiles(root).forEach { (path, document) ->
            val canonical = canonicalRemoteDataPath(path) ?: return@forEach
            if (!remote.containsKey(canonical) || path == canonical) remote[canonical] = document
        }

        val lastState = readSyncState().toMutableMap()
        val preferRemoteForUnpairedEmptyInstall = preferRemoteOnFirstSync || shouldPreferRemoteForFreshInstall(local, lastState)
        var uploaded = 0
        var downloaded = 0
        var conflicts = 0
        var unchanged = 0
        var errors = 0

        val allPaths = (local.keys + remote.keys).toSortedSet()
        allPaths.forEach { path ->
            val localFile = local[path]
            val remoteFile = remote[path]
            try {
                when {
                    localFile == null && remoteFile != null -> {
                        val target = File(fileOps.persistentDataRoot(), path)
                        if (copyDocumentToFile(remoteFile, target)) {
                            downloaded++
                            lastState[path] = sha256File(target)
                            local[path] = target
                        } else errors++
                    }
                    localFile != null && remoteFile == null -> {
                        if (preferRemoteOnFirstSync) {
                            unchanged++
                        } else {
                            val created = ensureRemoteFile(root, path)
                            if (created != null && copyFileToDocumentVerified(localFile, created)) {
                                uploaded++
                                lastState[path] = sha256File(localFile)
                                remote[path] = created
                            } else errors++
                        }
                    }
                    localFile != null && remoteFile != null -> {
                        val localHash = sha256File(localFile)
                        val remoteHash = sha256Document(remoteFile)
                        if (localHash.isBlank() || remoteHash.isBlank()) {
                            errors++
                            return@forEach
                        }
                        if (localHash == remoteHash) {
                            unchanged++
                            lastState[path] = localHash
                            return@forEach
                        }

                        val previous = lastState[path]
                        val localChanged = previous == null || localHash != previous
                        val remoteChanged = previous == null || remoteHash != previous

                        when {
                            preferRemoteOnFirstSync -> {
                                conflicts++
                                preserveLocalConflict(path, localFile)
                                if (copyDocumentToFile(remoteFile, localFile)) {
                                    downloaded++
                                    lastState[path] = remoteHash
                                } else errors++
                            }
                            previous != null && localChanged && !remoteChanged -> {
                                if (copyFileToDocumentVerified(localFile, remoteFile)) {
                                    uploaded++
                                    lastState[path] = localHash
                                } else errors++
                            }
                            previous != null && !localChanged && remoteChanged -> {
                                if (copyDocumentToFile(remoteFile, localFile)) {
                                    downloaded++
                                    lastState[path] = remoteHash
                                } else errors++
                            }
                            else -> {
                                conflicts++
                                val remoteTime = remoteFile.lastModified()
                                val localTime = localFile.lastModified()
                                // Explicit reinstall recovery is remote-first on an
                                // unpaired device. This is critical for Metadata/*:
                                // a clean install just generated fresh empty metadata,
                                // whose timestamp must never overwrite the real cloud
                                // library/settings before they can be restored.
                                if (previous == null && preferRemoteForUnpairedEmptyInstall) {
                                    preserveLocalConflict(path, localFile)
                                    if (copyDocumentToFile(remoteFile, localFile)) {
                                        downloaded++
                                        lastState[path] = remoteHash
                                    } else errors++
                                } else if (remoteTime > localTime + CLOCK_TOLERANCE_MS) {
                                    preserveLocalConflict(path, localFile)
                                    if (copyDocumentToFile(remoteFile, localFile)) {
                                        downloaded++
                                        lastState[path] = remoteHash
                                    } else errors++
                                } else {
                                    preserveRemoteConflict(path, remoteFile)
                                    if (copyFileToDocumentVerified(localFile, remoteFile)) {
                                        uploaded++
                                        lastState[path] = localHash
                                    } else errors++
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                errors++
            }
        }

        writeSyncState(lastState)
        return SyncResult(uploaded, downloaded, conflicts, unchanged, errors)
    }

    fun export(rootUri: Uri): Int {
        val root = DocumentFile.fromTreeUri(appContext, rootUri) ?: return 0
        if (!root.canWrite()) return 0
        ensureTreeStructure(root)
        var exported = 0
        localSyncFiles().forEach { (path, file) ->
            ensureRemoteFile(root, path)?.let { document ->
                if (copyFileToDocumentVerified(file, document)) exported++
            }
        }
        return exported
    }

    /**
     * Imports one raw battery save in .sav or .srm format. When targetRomId is
     * supplied (gameplay menu), the filename must match that exact ROM. From
     * Data & Storage, the filename must resolve to exactly one ROM already
     * present in the library.
     *
     * Raw .sav/.srm files do not contain a universal ROM identifier, so Retra
     * uses strict normalized filename matching plus save-size compatibility and
     * an automatic pre-replacement backup to prevent accidental cross-ROM imports.
     * .srm is treated as a raw battery-save container and is normalized into
     * Retra's canonical per-ROM .sav storage after validation.
     */
    fun importBatterySave(uri: Uri, targetRomId: String? = null): ImportSaveResult {
        val displayName = fileOps.queryDisplayName(uri)?.trim().orEmpty()
        val importExt = displayName.substringAfterLast('.', "").lowercase(Locale.US)
        if (displayName.isBlank() || importExt !in setOf("sav", "srm")) {
            return ImportSaveResult(false, message = "Choose a .sav or .srm battery save file")
        }

        val targets = romSaveTargets()
        val selectedBase = normalizedSaveBase(displayName)
        val target = if (!targetRomId.isNullOrBlank()) {
            targets.firstOrNull { it.id == targetRomId }
        } else {
            val matches = targets.filter { candidate ->
                val aliases = setOf(normalizedSaveBase(candidate.fileName), normalizedSaveBase(candidate.title))
                selectedBase in aliases
            }
            if (matches.size == 1) matches.first() else null
        } ?: return ImportSaveResult(
            false,
            message = if (targetRomId.isNullOrBlank())
                "No single ROM in your library matches ${displayName.substringBeforeLast('.')}"
            else "This game is not available in the Retra library"
        )

        val targetAliases = setOf(normalizedSaveBase(target.fileName), normalizedSaveBase(target.title))
        if (selectedBase !in targetAliases) {
            return ImportSaveResult(false, target.id, target.title, "This .$importExt does not match ${target.title}")
        }

        val tempDir = File(appContext.cacheDir, "save_import").apply { mkdirs() }
        val temp = File(tempDir, "${fileOps.sanitizeFileName(target.id)}_${System.nanoTime()}.sav")
        return try {
            val input = resolver.openInputStream(uri)
                ?: return ImportSaveResult(false, target.id, target.title, "Could not read the selected .$importExt")
            input.use { source ->
                FileOutputStream(temp).use { output ->
                    source.copyTo(output, 64 * 1024)
                    output.flush()
                    runCatching { output.fd.sync() }
                }
            }

            val importedSize = temp.length()
            if (!isPlausibleBatterySaveSize(importedSize)) {
                return ImportSaveResult(false, target.id, target.title, "The selected .$importExt has an unsupported save size")
            }
            val existing = saveData.batterySaveFile(target.id)
            if (existing.exists() && existing.length() > 0L && existing.length() != importedSize) {
                return ImportSaveResult(false, target.id, target.title, "This .$importExt size does not match ${target.title}")
            }

            if (!saveData.replaceBatterySaveFromFile(target.id, temp)) {
                ImportSaveResult(false, target.id, target.title, "Could not replace the save safely")
            } else {
                ImportSaveResult(true, target.id, target.title, "${target.title} save imported")
            }
        } catch (error: Exception) {
            ImportSaveResult(false, target.id, target.title, error.message ?: "Could not import this .$importExt")
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun isPlausibleBatterySaveSize(size: Long): Boolean {
        if (size < 512L || size > 512L * 1024L) return false
        return size and (size - 1L) == 0L
    }

    fun importSaves(rootUri: Uri): Int {
        val root = DocumentFile.fromTreeUri(appContext, rootUri) ?: return 0
        val targets = romSaveTargets()
        var imported = 0

        fun walk(dir: DocumentFile, relative: String = "") {
            dir.listFiles().forEach { child ->
                val name = child.name ?: return@forEach
                val rel = if (relative.isBlank()) name else "$relative/$name"
                if (child.isDirectory) {
                    walk(child, rel)
                    return@forEach
                }
                val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
                if (ext in setOf("sav", "srm", "rtc")) {
                    val base = normalizedSaveBase(name)
                    val parentName = rel.substringBeforeLast('/', "").substringAfterLast('/', "")
                    val target = targets.firstOrNull { fileOps.sanitizeFileName(it.id).equals(parentName, true) }
                        ?: targets.firstOrNull {
                            normalizedSaveBase(it.fileName) == base || normalizedSaveBase(it.title) == base
                        }
                    if (target != null) {
                        val targetFile = if (ext == "rtc") saveData.batteryRtcFile(target.id) else saveData.batterySaveFile(target.id)
                        if (copyDocumentToFile(child, targetFile)) imported++
                    }
                } else if (ext in setOf("ss", "state")) {
                    val parentName = rel.substringBeforeLast('/', "").substringAfterLast('/', "")
                    val target = targets.firstOrNull {
                        fileOps.sanitizeFileName(it.id).equals(parentName, true) ||
                            normalizedSaveBase(it.title) == normalizedSaveBase(parentName)
                    }
                    if (target != null) {
                        val lower = name.lowercase(Locale.US)
                        val match = Regex(".*?(?:slot[_ -]?)?(\\d{1,2}).*").matchEntire(lower)
                        val numbered = match?.groupValues?.getOrNull(1)?.toIntOrNull()
                        val slot = when {
                            lower.startsWith("quick") -> 0
                            numbered != null && numbered in 1..10 -> numbered
                            else -> null
                        }
                        if (slot != null && copyDocumentToFile(child, saveStates.stateFile(slot, target.id))) imported++
                    }
                }
            }
        }
        walk(root)
        return imported
    }


    private fun shouldPreferRemoteForFreshInstall(local: Map<String, File>, lastState: Map<String, String>): Boolean {
        if (lastState.isNotEmpty()) return false
        if (local.keys.any { path -> !path.startsWith("Metadata/") }) return false
        val library = local["Metadata/library.json"] ?: return true
        return runCatching {
            val root = JSONObject(library.readText(Charsets.UTF_8))
            (root.optJSONArray("roms")?.length() ?: 0) == 0
        }.getOrDefault(false)
    }

    private fun localSyncFiles(): Map<String, File> {
        preparePortableMetadata()
        val out = linkedMapOf<String, File>()
        val root = fileOps.persistentDataRoot()
        if (root.exists()) {
            root.walkTopDown().filter { it.isFile && it.length() > 0L }.forEach { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                if (!relative.startsWith("Backups/CloudConflicts/")) out[relative] = file
            }
        }
        return out
    }

    private fun ensureTreeStructure(root: DocumentFile) {
        portableRootDirectories.forEach { name ->
            ensureDirectory(root, name)
        }
    }

    /**
     * SAF providers are allowed to rename a newly-created directory when a
     * sibling with the requested display name already exists (for example,
     * `Saves` -> `Saves (1)`). Some providers are also inconsistent about
     * `DocumentFile.findFile()` immediately after a tree is re-selected.
     *
     * Always enumerate the current children first and reuse the canonical
     * directory. If an older Retra build already produced numbered duplicate
     * directories, reuse the lowest-numbered one only when the canonical
     * directory is absent. This makes repeated "Use this folder" selections
     * idempotent and prevents `Saves (2)`, `Saves (3)`, ... from accumulating.
     */
    private fun ensureDirectory(parent: DocumentFile, name: String): DocumentFile? {
        findExistingDirectory(parent, name)?.let { return it }

        val created = runCatching { parent.createDirectory(name) }.getOrNull()
        if (created != null) return created

        // A provider can report creation failure even though another process
        // or a delayed refresh made the directory visible. Resolve once more
        // before giving up rather than issuing another create request.
        return findExistingDirectory(parent, name)
    }

    private fun findExistingDirectory(parent: DocumentFile, name: String): DocumentFile? {
        val children = runCatching { parent.listFiles().toList() }.getOrDefault(emptyList())
        children.firstOrNull { child ->
            child.isDirectory && child.name == name
        }?.let { return it }

        children.firstOrNull { child ->
            child.isDirectory && child.name?.equals(name, ignoreCase = true) == true
        }?.let { return it }

        // Recover gracefully from duplicate folders created by previous builds.
        // Prefer the smallest suffix so Retra keeps using one stable directory.
        val duplicatePattern = Regex("^${Regex.escape(name)} \\((\\d+)\\)$", RegexOption.IGNORE_CASE)
        children.asSequence()
            .filter { it.isDirectory }
            .mapNotNull { child ->
                val childName = child.name ?: return@mapNotNull null
                val match = duplicatePattern.matchEntire(childName) ?: return@mapNotNull null
                val suffix = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                suffix to child
            }
            .minByOrNull { it.first }
            ?.second
            ?.let { return it }

        // Keep findFile as a final provider-specific fallback, but never rely on
        // it as the only existence check.
        return runCatching { parent.findFile(name) }.getOrNull()?.takeIf { it.isDirectory }
    }

    private fun collectRemoteFiles(
        root: DocumentFile,
        prefix: String = "",
        out: MutableMap<String, DocumentFile> = linkedMapOf()
    ): MutableMap<String, DocumentFile> {
        root.listFiles().forEach { child ->
            val name = child.name ?: return@forEach
            val rel = if (prefix.isBlank()) name else "$prefix/$name"
            if (child.isDirectory) collectRemoteFiles(child, rel, out)
            else if (child.isFile) out[rel] = child
        }
        return out
    }

    private fun ensureRemoteFile(root: DocumentFile, relative: String): DocumentFile? {
        val parts = relative.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        var dir = root
        parts.dropLast(1).forEach { segment ->
            dir = ensureDirectory(dir, segment) ?: return null
        }
        val name = parts.last()
        return findExistingFile(dir, name)
            ?: dir.createFile("application/octet-stream", name)
    }

    private fun findExistingFile(parent: DocumentFile, name: String): DocumentFile? {
        val children = runCatching { parent.listFiles().toList() }.getOrDefault(emptyList())
        children.firstOrNull { child ->
            child.isFile && child.name == name
        }?.let { return it }

        children.firstOrNull { child ->
            child.isFile && child.name?.equals(name, ignoreCase = true) == true
        }?.let { return it }

        return runCatching { parent.findFile(name) }.getOrNull()?.takeIf { it.isFile }
    }

    private fun copyDocumentToFile(document: DocumentFile, target: File): Boolean {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        return try {
            val input = resolver.openInputStream(document.uri) ?: return false
            input.use { src ->
                FileOutputStream(tmp).use { dst ->
                    src.copyTo(dst, 64 * 1024)
                    dst.flush()
                    runCatching { dst.fd.sync() }
                }
            }
            val expectedSize = document.length()
            if (expectedSize > 0L && tmp.length() != expectedSize) throw IOException("Imported data verification failed")
            fileOps.moveTempIntoPlace(tmp, target, "Could not replace imported data")
            val modified = document.lastModified()
            if (modified > 0L) target.setLastModified(modified)
            true
        } catch (_: Exception) {
            false
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun copyFileToDocument(source: File, document: DocumentFile): Boolean {
        return try {
            val output = resolver.openOutputStream(document.uri, "wt") ?: return false
            FileInputStream(source).use { src -> output.use { dst -> src.copyTo(dst) } }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun copyFileToDocumentVerified(source: File, document: DocumentFile): Boolean {
        if (!copyFileToDocument(source, document)) return false
        val expectedSize = source.length()
        val remoteSize = document.length()
        if (expectedSize > 0L && remoteSize > 0L && expectedSize != remoteSize) return false
        val sourceHash = sha256File(source)
        val remoteHash = sha256Document(document)
        return sourceHash.isNotBlank() && sourceHash == remoteHash
    }

    private fun sha256File(file: File): String = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrDefault("")

    private fun sha256Document(document: DocumentFile): String = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = resolver.openInputStream(document.uri) ?: return@runCatching ""
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrDefault("")

    private fun readSyncState(): Map<String, String> {
        val file = File(appContext.filesDir, SYNC_STATE_FILE)
        if (!file.exists()) return emptyMap()
        return runCatching {
            val root = JSONObject(file.readText())
            val files = root.optJSONObject("files") ?: JSONObject()
            buildMap {
                val names = files.keys()
                while (names.hasNext()) {
                    val path = names.next()
                    val hash = files.optString(path, "")
                    if (hash.isNotBlank()) put(path, hash)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun writeSyncState(state: Map<String, String>) {
        val root = JSONObject().apply {
            put("version", 2)
            put("updatedAt", System.currentTimeMillis())
            put("files", JSONObject().apply { state.toSortedMap().forEach { (path, hash) -> put(path, hash) } })
        }
        runCatching { fileOps.atomicWriteText(File(appContext.filesDir, SYNC_STATE_FILE), root.toString()) }
    }

    private fun preserveLocalConflict(path: String, localFile: File) {
        val target = conflictFile(path, "local")
        target.parentFile?.mkdirs()
        runCatching { fileOps.atomicCopyVerified(localFile, target) }
    }

    private fun preserveRemoteConflict(path: String, remoteFile: DocumentFile) {
        val target = conflictFile(path, "drive")
        target.parentFile?.mkdirs()
        copyDocumentToFile(remoteFile, target)
    }

    private fun conflictFile(path: String, side: String): File {
        val safePath = path.split('/').joinToString("/") { fileOps.sanitizeFileName(it) }
        val root = File(fileOps.persistentCategoryDir("Backups"), "CloudConflicts")
        val stamp = System.currentTimeMillis()
        return File(root, "$stamp/$side/$safePath")
    }

    private fun canonicalRemoteDataPath(path: String): String? {
        if (path.startsWith("Backups/CloudConflicts/")) return null
        if (path.startsWith("Saves/") || path.startsWith("SaveStates/") ||
            path.startsWith("Cheats/") || path.startsWith("Config/") ||
            path.startsWith("Layouts/") || path.startsWith("Covers/") ||
            path.startsWith("Backgrounds/") || path.startsWith("Backups/") ||
            path.startsWith("Metadata/")
        ) return path

        if (path.startsWith("states/")) return "SaveStates/${path.removePrefix("states/")}"
        if (path.startsWith("battery/")) {
            val name = path.substringAfterLast('/')
            val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
            if (ext !in setOf("sav", "srm", "rtc")) return null
            val base = name.substringBeforeLast('.').removeSuffix("_rom")
            val record = romIdentityStore.all().firstOrNull {
                fileOps.sanitizeFileName(it.romId).equals(base, true)
            } ?: return null
            val targetName = if (ext == "rtc") "game.rtc" else "game.sav"
            return "Saves/${fileOps.sanitizeFileName(record.romId)}/$targetName"
        }
        return null
    }

    private fun romSaveTargets(): List<RomSaveTarget> {
        val targets = linkedMapOf<String, RomSaveTarget>()
        romIdentityStore.all().forEach { record ->
            val launch = record.launchPath ?: return@forEach
            targets[record.romId] = RomSaveTarget(record.romId, record.displayName, record.fileName, launch)
        }

        // Include legacy preference records too so partially migrated installs
        // remain import-compatible during the transition to Room ownership.
        prefs.all.keys.filter { it.startsWith("file_name_") }.forEach { key ->
            val id = key.removePrefix("file_name_")
            if (id in targets) return@forEach
            val fileName = prefs.getString("file_name_$id", null) ?: return@forEach
            val launch = prefs.getString("content_path_$id", null) ?: return@forEach
            val title = prefs.getString("title_$id", fileName.substringBeforeLast('.')) ?: fileName.substringBeforeLast('.')
            targets[id] = RomSaveTarget(id, title, fileName, launch)
        }
        return targets.values.toList()
    }

    private fun normalizedSaveBase(value: String): String {
        var base = value.trim().lowercase(Locale.US)
        val suffixes = listOf(".sav", ".srm", ".rtc", ".gba", ".gbc", ".gb", ".mgba", ".zip")
        var changed: Boolean
        do {
            changed = false
            for (suffix in suffixes) {
                if (base.endsWith(suffix)) {
                    base = base.removeSuffix(suffix)
                    changed = true
                    break
                }
            }
        } while (changed)
        return base.replace(Regex("[^a-z0-9]+"), "")
    }

    companion object {
        private val portableRootDirectories = listOf(
            "Saves", "SaveStates", "Cheats", "Config", "Layouts",
            "Covers", "Backgrounds", "Backups", "Metadata"
        )
        private const val SYNC_STATE_FILE = "cloud_sync_state_v2.json"
        private const val CLOCK_TOLERANCE_MS = 1500L
    }

    private data class RomSaveTarget(
        val id: String,
        val title: String,
        val fileName: String,
        val launchPath: String
    )
}
