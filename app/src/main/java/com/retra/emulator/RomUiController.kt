package com.retra.emulator

import android.net.Uri
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import com.retra.emulator.MainActivity.Companion.IMPORT_EXTENSIONS
import com.retra.emulator.MainActivity.Companion.MAX_PATCH_BYTES
import com.retra.emulator.MainActivity.Companion.MAX_ROM_BYTES
import com.retra.emulator.MainActivity.Companion.MAX_ZIP_ENTRIES
import com.retra.emulator.MainActivity.Companion.PATCH_EXTENSIONS
import com.retra.emulator.MainActivity.Companion.PLAYABLE_EXTENSIONS
import com.retra.emulator.MainActivity.Companion.ROM_PATCHING_PREF

/**
 * ROM import, patch discovery, archive recovery and native-library UI handoff.
 * Extracted from MainActivity so import/storage work stays off the UI lifecycle.
 */
internal data class ExtractedArchivePayload(
    val romFile: File?,
    val patchFile: File?,
    val romEntryName: String?,
    val patchEntryName: String?
)

internal fun MainActivity.findAutomaticPatchForRom(romId: String, fileName: String, romFile: File): File? {
    if (!prefs.getBoolean(ROM_PATCHING_PREF, true)) return null
    val base = normalizedSaveBase(fileName)
    romFile.parentFile?.let { dir ->
        PATCH_EXTENSIONS.forEach { ext ->
            File(dir, "${romFile.nameWithoutExtension}.$ext").takeIf { it.exists() && it.length() > 0L }?.let { return it }
        }
    }
    prefs.all.keys.filter { it.startsWith("file_name_") }.forEach { key ->
        val id = key.removePrefix("file_name_")
        if (id == romId) return@forEach
        val candidateName = prefs.getString(fileNameKey(id), null) ?: return@forEach
        if (candidateName.substringAfterLast('.', "").lowercase(Locale.US) !in PATCH_EXTENSIONS) return@forEach
        if (normalizedSaveBase(candidateName) != base) return@forEach
        val path = prefs.getString(patchPathKey(id), null) ?: return@forEach
        val patch = File(path)
        if (patch.exists() && patch.length() > 0L) return patch
    }
    return null
}

internal fun MainActivity.launchNativeItem(id: String, title: String, fileName: String, system: String) {
    val launchPath = prefs.getString(contentPathKey(id), null)
    val patchPath = prefs.getString(patchPathKey(id), null)

    if (!patchPath.isNullOrBlank() && launchPath.isNullOrBlank()) {
        val patchFile = File(patchPath)
        if (!patchFile.exists()) {
            requestLocate(id, title, fileName, system)
            return
        }

        val storedBasePath = prefs.getString(basePathKey(id), null)
        if (!storedBasePath.isNullOrBlank()) {
            val base = File(storedBasePath)
            if (base.exists()) {
                loadRomFile(base, title, patchFile, id)
                return
            }
        }

        pendingPatchLaunch = PendingPatchLaunch(id, title, patchFile)
        RetraNotice.makeText(
            this,
            "Select the clean/base ROM for this ${patchFile.extension.uppercase(Locale.US)} patch",
            RetraNotice.LENGTH_LONG
        ).show()
        patchBasePicker.launch(arrayOf("*/*"))
        return
    }

    if (!launchPath.isNullOrBlank()) {
        val romFile = File(launchPath)
        if (romFile.exists()) {
            val explicitPatch = patchPath?.let(::File)?.takeIf { it.exists() && it.length() > 0L }
            val patchFile = explicitPatch ?: findAutomaticPatchForRom(id, fileName, romFile)
            loadRomFile(romFile, title, patchFile, id)
            return
        }
    }

    requestLocate(id, title, fileName, system)
}

internal fun MainActivity.requestLocate(id: String, title: String, fileName: String, system: String) {
    pendingLocate = PendingLocate(id, title, fileName, system)
    prefs.edit().putBoolean(fileAvailableKey(id), false).apply()
    romIdentityStore.setFileAvailable(id, false)
    RetraNotice.makeText(this, "ROM file unavailable • locate $fileName to reconnect your existing data", RetraNotice.LENGTH_LONG).show()
    locatePicker.launch(arrayOf("*/*"))
}

internal fun MainActivity.recoverInterruptedImports() {
    importJournal.pending().forEach { entry ->
        try {
            when (entry.state) {
                ImportJournal.State.PREPARING -> {
                    // No database commit occurred. For a brand-new romId, remove
                    // only transaction-owned library files. Existing records/files
                    // are never deleted during recovery.
                    val existing = entry.romId?.let { romIdentityStore.getById(it) }
                    if (existing == null) {
                        listOfNotNull(entry.launchPath, entry.patchPath).forEach { path ->
                            val file = File(path)
                            val contentRoot = File(filesDir, "library_content").canonicalFile
                            runCatching {
                                if (file.exists() && file.canonicalFile.parentFile == contentRoot) file.delete()
                            }
                        }
                    }
                    importJournal.discard(entry)
                }

                ImportJournal.State.ROM_READY -> {
                    val romId = entry.romId
                    val hash = entry.contentHash
                    if (romId.isNullOrBlank() || hash.isNullOrBlank()) {
                        importJournal.discard(entry)
                        return@forEach
                    }
                    val launch = entry.launchPath?.let(::File)
                    val patch = entry.patchPath?.let(::File)
                    val fileAvailable = launch?.exists() == true || patch?.exists() == true
                    if (!fileAvailable) {
                        importJournal.discard(entry)
                        return@forEach
                    }
                    val previous = romIdentityStore.getById(romId)
                    romIdentityStore.upsert(
                        RomIdentityStore.Record(
                            romId = romId,
                            contentHash = hash,
                            platform = entry.platform.ifBlank { previous?.platform ?: "ROM" },
                            displayName = previous?.displayName ?: entry.displayName.substringBeforeLast('.'),
                            fileName = previous?.fileName ?: entry.displayName,
                            sourceUri = entry.sourceUri,
                            currentFileUri = entry.sourceUri,
                            launchPath = entry.launchPath,
                            patchPath = entry.patchPath,
                            fileSize = entry.fileSize,
                            lastModified = entry.lastModified,
                            archived = false,
                            fileAvailable = true,
                            favorite = previous?.favorite ?: false,
                            categoriesJson = previous?.categoriesJson ?: "[]",
                            playtimeMs = previous?.playtimeMs ?: 0L,
                            finalContentHash = previous?.finalContentHash,
                            legacyIdentityHash = entry.legacyIdentityHash ?: previous?.legacyIdentityHash,
                            createdAt = previous?.createdAt ?: entry.createdAt,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    romIdentityStore.addIdentityAlias(romId, entry.legacyIdentityHash, "recovered_legacy_identity")
                    val committed = importJournal.transition(entry, ImportJournal.State.DATABASE_COMMITTED)
                    importJournal.finish(committed)
                }

                ImportJournal.State.DATABASE_COMMITTED -> importJournal.finish(entry)
                ImportJournal.State.COMPLETE -> importJournal.discard(entry)
            }
        } catch (_: Exception) {
            // Keep the journal for a future retry rather than guessing or
            // deleting user data after an unexpected recovery failure.
        }
    }
}

internal fun MainActivity.importUri(
    uri: Uri,
    forcedId: String? = null,
    forcedTitle: String? = null
): NativeLibraryItem {
    val fileInfo = fileOps.queryFileInfo(uri)
    val displayName = fileInfo.first ?: "game.gba"
    val reportedSize = fileInfo.second
    val sourceLastModified = fileOps.queryLastModified(uri)
    val extension = displayName.substringAfterLast('.', "").lowercase(Locale.US)

    if (extension !in IMPORT_EXTENSIONS) {
        throw IllegalArgumentException(
            "Supported: .mgba, .gba, .gbc, .gb, .zip, .ips, .ups, .bps"
        )
    }

    var journal = importJournal.begin(uri.toString(), displayName)
    val stagingId = journal.transactionId
    val stagingDir = File(cacheDir, "rom_imports/${fileOps.sanitizeFileName(stagingId)}").apply { mkdirs() }
    val sourceFile = File(stagingDir, "source.$extension")

    try {
        copyUriToFile(uri, sourceFile, maxBytesForExtension(extension))
        val copiedSize = sourceFile.length()

        var stagedLaunch: File? = null
        var stagedPatch: File? = null
        var archiveRomEntryName: String? = null
        var system = inferSystemFromExtension(extension)

        when {
            extension == "zip" -> {
                val extracted = extractSupportedFromZip(sourceFile, "payload", stagingDir)
                stagedLaunch = extracted.romFile
                stagedPatch = extracted.patchFile
                archiveRomEntryName = extracted.romEntryName
                if (stagedLaunch == null && stagedPatch == null) {
                    throw IllegalArgumentException("ZIP has no supported ROM or patch")
                }
                system = stagedLaunch?.let { inferSystemFromExtension(it.extension.lowercase(Locale.US)) } ?: "PATCH"
            }

            extension == "mgba" && looksLikeZip(sourceFile) -> {
                val extracted = extractSupportedFromZip(sourceFile, "payload", stagingDir)
                stagedLaunch = extracted.romFile
                stagedPatch = extracted.patchFile
                archiveRomEntryName = extracted.romEntryName
                if (stagedLaunch == null && stagedPatch == null) {
                    throw IllegalArgumentException(".mgba bundle has no supported ROM or patch")
                }
                system = stagedLaunch?.let { inferSystemFromExtension(it.extension.lowercase(Locale.US)) } ?: "PATCH"
            }

            extension in PATCH_EXTENSIONS -> {
                stagedPatch = sourceFile
                system = "PATCH"
            }

            extension in PLAYABLE_EXTENSIONS -> stagedLaunch = sourceFile
        }

        val identityFile = stagedLaunch ?: stagedPatch
            ?: throw IllegalArgumentException("Import has no usable ROM or patch")

        // Fingerprint cache is only an optimization. SHA-256 remains the
        // authoritative identity and is recomputed when size or mtime changes.
        val cachedRecord = if (
            forcedId == null && stagedPatch == null && extension in PLAYABLE_EXTENSIONS &&
            reportedSize > 0L && sourceLastModified > 0L
        ) {
            romIdentityStore.findByFingerprint(uri.toString(), reportedSize, sourceLastModified)
        } else null

        val legacyIdentityHash = if (stagedLaunch != null && stagedPatch != null) {
            legacyBundleIdentityHash(stagedLaunch, stagedPatch)
        } else null
        val contentHash = cachedRecord?.contentHash ?: contentIdentityHash(stagedLaunch, stagedPatch)
        val existingByHash = cachedRecord ?: romIdentityStore.findByHash(system, contentHash)
            ?: legacyIdentityHash?.let { romIdentityStore.findByHash(system, it) }
        val forcedRecord = forcedId?.let { romIdentityStore.getById(it) }
        val expectedForcedHash = forcedRecord?.contentHash?.takeIf { it.isNotBlank() }
            ?: forcedId?.let { prefs.getString(contentHashKey(it), null) }?.takeIf { it.isNotBlank() }

        if (!expectedForcedHash.isNullOrBlank() &&
            !expectedForcedHash.equals(contentHash, ignoreCase = true) &&
            forcedRecord?.legacyIdentityHash?.equals(contentHash, ignoreCase = true) != true
        ) {
            throw IllegalArgumentException("Selected file does not match this ROM")
        }

        val resolvedRecord = if (forcedId == null) existingByHash else forcedRecord
        val resolvedId = forcedId ?: resolvedRecord?.romId ?: UUID.randomUUID().toString()

        // ZIP/.mgba bundles used to discard the inner ROM filename and keep only
        // the archive name (for example, "Harvest Moon.zip"). Automatic artwork
        // lookup is exact-title based, so that prevented Retra from trying the
        // much more useful inner name such as
        // "Harvest Moon - Friends of Mineral Town (USA).gba". Keep the source
        // URI/sourceExtension and outer archive filename for reconnects, but use
        // the playable entry name as the automatic title/artwork hint.
        val gameFileName = archiveRomEntryName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: displayName
        val title = forcedTitle
            ?: resolvedRecord?.displayName?.takeIf { it.isNotBlank() }
            ?: gameFileName.substringBeforeLast('.').ifBlank { "ROM" }
        val restoredArchived = resolvedRecord?.archived == true
        val reusedExisting = resolvedRecord != null

        val contentDir = File(filesDir, "library_content").apply { mkdirs() }
        fun targetFor(staged: File?, role: String): File? {
            if (staged == null) return null
            val ext = staged.extension.lowercase(Locale.US).ifBlank { if (role == "rom") "gba" else "bin" }
            val suffix = when {
                role == "rom" && stagedPatch != null -> "_rom"
                role == "patch" && stagedLaunch != null -> "_patch"
                else -> ""
            }
            val previousPath = when (role) {
                "rom" -> resolvedRecord?.launchPath
                "patch" -> resolvedRecord?.patchPath
                else -> null
            }
            val previousFile = previousPath?.let(::File)?.takeIf {
                it.parentFile?.absolutePath == contentDir.absolutePath &&
                    it.extension.equals(ext, ignoreCase = true)
            }
            return previousFile ?: File(contentDir, "${fileOps.sanitizeFileName(resolvedId)}$suffix.$ext")
        }

        val launchTarget = targetFor(stagedLaunch, "rom")
        val patchTarget = targetFor(stagedPatch, "patch")
        journal = importJournal.transition(
            journal,
            ImportJournal.State.PREPARING,
            platform = system,
            romId = resolvedId,
            contentHash = contentHash,
            legacyIdentityHash = legacyIdentityHash,
            launchPath = launchTarget?.absolutePath,
            patchPath = patchTarget?.absolutePath,
            fileSize = reportedSize.coerceAtLeast(copiedSize),
            lastModified = sourceLastModified
        )

        if (stagedLaunch != null && launchTarget != null) fileOps.atomicCopyVerified(stagedLaunch, launchTarget)
        if (stagedPatch != null && patchTarget != null) fileOps.atomicCopyVerified(stagedPatch, patchTarget)

        journal = importJournal.transition(journal, ImportJournal.State.ROM_READY)
        val sourceUri = uri.toString()
        fileOps.tryPersistReadPermission(uri)

        return NativeLibraryItem(
            id = resolvedId,
            title = title,
            fileName = displayName,
            size = reportedSize.coerceAtLeast(copiedSize),
            lastModified = sourceLastModified,
            system = system,
            sourceExtension = extension,
            launchPath = launchTarget?.absolutePath,
            patchPath = patchTarget?.absolutePath,
            contentHash = contentHash,
            sourceUri = sourceUri,
            legacyIdentityHash = legacyIdentityHash,
            importTransactionId = journal.transactionId,
            reusedExisting = reusedExisting,
            restoredArchived = restoredArchived
        )
    } catch (error: Exception) {
        // Synchronous failures can be discarded immediately; process/device
        // death leaves the journal on disk for recoverInterruptedImports().
        importJournal.discard(journal)
        throw error
    } finally {
        stagingDir.deleteRecursively()
    }
}

internal fun MainActivity.preparePatchBaseRom(uri: Uri, patchId: String): File {
    val displayName = fileOps.queryDisplayName(uri) ?: "base.gba"
    val extension = displayName.substringAfterLast('.', "").lowercase(Locale.US)

    if (extension !in PLAYABLE_EXTENSIONS && extension != "zip") {
        throw IllegalArgumentException("Choose a .gba, .gbc, .gb, .mgba, or .zip base ROM")
    }

    val baseDir = File(filesDir, "patch_bases").apply { mkdirs() }
    val sourceFile = File(baseDir, "${fileOps.sanitizeFileName(patchId)}_source.$extension")
    copyUriToFile(uri, sourceFile, maxBytesForExtension(extension))

    if (extension == "zip" || (extension == "mgba" && looksLikeZip(sourceFile))) {
        val extracted = extractSupportedFromZip(sourceFile, "${patchId}_base", baseDir)
        sourceFile.delete()
        return extracted.romFile
            ?: throw IllegalArgumentException("Archive does not contain a playable ROM")
    }

    return sourceFile
}

internal fun MainActivity.extractSupportedFromZip(
    zipFile: File,
    id: String,
    outputDir: File
): ExtractedArchivePayload {
    var romFile: File? = null
    var patchFile: File? = null
    var romEntryName: String? = null
    var patchEntryName: String? = null
    var entryCount = 0

    ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            entryCount++
            if (entryCount > MAX_ZIP_ENTRIES) {
                throw IllegalArgumentException("Archive has too many files")
            }

            if (entry.isDirectory) {
                zip.closeEntry()
                continue
            }

            val entryName = entry.name.substringAfterLast('/')
            val ext = entryName.substringAfterLast('.', "").lowercase(Locale.US)

            if (romFile == null && ext in PLAYABLE_EXTENSIONS) {
                val out = File(
                    outputDir,
                    "${fileOps.sanitizeFileName(id)}_rom.${if (ext.isBlank()) "gba" else ext}"
                )
                copyLimited(zip, out, MAX_ROM_BYTES)
                romFile = out
                romEntryName = entryName
            } else if (patchFile == null && ext in PATCH_EXTENSIONS) {
                val out = File(outputDir, "${fileOps.sanitizeFileName(id)}_patch.$ext")
                copyLimited(zip, out, MAX_PATCH_BYTES)
                patchFile = out
                patchEntryName = entryName
            }

            zip.closeEntry()
        }
    }

    return ExtractedArchivePayload(
        romFile = romFile,
        patchFile = patchFile,
        romEntryName = romEntryName,
        patchEntryName = patchEntryName
    )
}

internal fun MainActivity.copyLimited(input: java.io.InputStream, target: File, maxBytes: Long) {
    target.parentFile?.mkdirs()
    var total = 0L
    val buffer = ByteArray(64 * 1024)

    try {
        FileOutputStream(target).use { output ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) {
                    throw IllegalArgumentException("File is too large")
                }
                output.write(buffer, 0, read)
            }
        }
    } catch (e: Exception) {
        target.delete()
        throw e
    }
}

internal fun MainActivity.copyUriToFile(uri: Uri, target: File, maxBytes: Long) {
    val input = contentResolver.openInputStream(uri)
        ?: throw IllegalStateException("Unable to read selected file")
    input.use { copyLimited(it, target, maxBytes) }
}

internal fun MainActivity.looksLikeZip(file: File): Boolean {
    if (!file.exists() || file.length() < 4) return false
    FileInputStream(file).use { input ->
        val header = ByteArray(4)
        if (input.read(header) != 4) return false
        return header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte() &&
            (header[2] == 0x03.toByte() || header[2] == 0x05.toByte() || header[2] == 0x07.toByte()) &&
            (header[3] == 0x04.toByte() || header[3] == 0x06.toByte() || header[3] == 0x08.toByte())
    }
}

internal fun MainActivity.maxBytesForExtension(extension: String): Long {
    return when (extension) {
        in PATCH_EXTENSIONS -> MAX_PATCH_BYTES
        "zip", "mgba" -> MAX_ROM_BYTES + MAX_PATCH_BYTES
        else -> MAX_ROM_BYTES
    }
}

internal fun MainActivity.inferSystemFromExtension(extension: String): String {
    return when (extension.lowercase(Locale.US)) {
        "gba" -> "GBA"
        "gbc" -> "GBC"
        "gb" -> "GB"
        "ips", "ups", "bps" -> "PATCH"
        "zip" -> "ARCHIVE"
        "mgba" -> "mGBA"
        else -> "ROM"
    }
}

internal fun MainActivity.persistNativeItem(item: NativeLibraryItem) {
    val editor = prefs.edit()
        .putString(titleKey(item.id), item.title)
        .putString(fileNameKey(item.id), item.fileName)
        .putString(systemKey(item.id), item.system)
        .putString(sourceExtensionKey(item.id), item.sourceExtension)
        .putString(contentHashKey(item.id), item.contentHash)
        .putString(sourceUriKey(item.id), item.sourceUri)
        .putBoolean(archivedKey(item.id), false)
        .putBoolean(fileAvailableKey(item.id), true)

    if (item.launchPath != null) editor.putString(contentPathKey(item.id), item.launchPath)
    else editor.remove(contentPathKey(item.id))

    if (item.patchPath != null) editor.putString(patchPathKey(item.id), item.patchPath)
    else editor.remove(patchPathKey(item.id))

    editor.apply()

    val now = System.currentTimeMillis()
    val previous = romIdentityStore.getById(item.id)
    romIdentityStore.upsert(
        RomIdentityStore.Record(
            romId = item.id,
            contentHash = item.contentHash,
            platform = item.system,
            displayName = item.title,
            fileName = item.fileName,
            sourceUri = item.sourceUri,
            currentFileUri = item.sourceUri,
            launchPath = item.launchPath,
            patchPath = item.patchPath,
            fileSize = item.size,
            lastModified = item.lastModified,
            archived = false,
            fileAvailable = item.launchPath?.let { File(it).exists() }
                ?: item.patchPath?.let { File(it).exists() }
                ?: false,
            favorite = previous?.favorite ?: false,
            categoriesJson = previous?.categoriesJson ?: "[]",
            playtimeMs = previous?.playtimeMs ?: statistics.storedPlaytimeMs(item.id),
            finalContentHash = if (item.launchPath != null && item.patchPath != null) item.contentHash else previous?.finalContentHash,
            legacyIdentityHash = item.legacyIdentityHash ?: previous?.legacyIdentityHash,
            createdAt = previous?.createdAt ?: now,
            updatedAt = now
        )
    )
    romIdentityStore.addIdentityAlias(item.id, item.legacyIdentityHash, "legacy_bundle_v1")
    previous?.contentHash?.takeIf { it.isNotBlank() && !it.equals(item.contentHash, true) }?.let {
        romIdentityStore.addIdentityAlias(item.id, it, "previous_content_hash")
    }

    item.importTransactionId?.let { transactionId ->
        importJournal.find(transactionId)?.let { pending ->
            val committed = importJournal.transition(pending, ImportJournal.State.DATABASE_COMMITTED)
            importJournal.finish(committed)
        }
    }
}

internal fun MainActivity.notifyWebImported(item: NativeLibraryItem, replaceExisting: Boolean = false) {
    val json = JSONObject().apply {
        put("id", item.id)
        put("title", item.title)
        put("fileName", item.fileName)
        put("size", item.size)
        put("lastModified", item.lastModified)
        put("system", item.system)
        put("native", true)
        put("replaceExisting", replaceExisting)
        put("contentHash", item.contentHash)
        put("reusedExisting", item.reusedExisting)
        put("restoredArchived", item.restoredArchived)
    }

    binding.webView.evaluateJavascript(
        "window.retraNativeFileImported && window.retraNativeFileImported(${jsQuote(json.toString())})",
        null
    )
}
