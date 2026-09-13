package com.retra.emulator

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import com.retra.emulator.MainActivity.Companion.ROM_IDENTITY_MIGRATION_PREF
import com.retra.emulator.MainActivity.Companion.ROM_IDENTITY_SCHEMA_VERSION

/**
 * Persistent ROM identity, save commit and migration coordination.
 * Extracted from MainActivity; repositories continue to own durable storage.
 */
internal fun MainActivity.legacyBundleIdentityHash(romFile: File, patchFile: File): String {
    val identity = "bundle-v1|${fileOps.sha256File(romFile)}|${fileOps.sha256File(patchFile)}"
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

internal fun MainActivity.materializedPatchedHash(romFile: File, patchFile: File): String {
    val workDir = File(cacheDir, "patched_identity").apply { mkdirs() }
    val output = File(workDir, "${UUID.randomUUID()}.rom")
    try {
        if (!materializePatchedRom(romFile.absolutePath, patchFile.absolutePath, output.absolutePath) ||
            !output.exists() || output.length() <= 0L
        ) {
            throw IOException("Could not materialize patched ROM for identity")
        }
        return fileOps.sha256File(output)
    } finally {
        output.delete()
    }
}

internal fun MainActivity.contentIdentityHash(romFile: File?, patchFile: File?): String {
    return when {
        romFile != null && patchFile != null -> materializedPatchedHash(romFile, patchFile)
        romFile != null -> fileOps.sha256File(romFile)
        patchFile != null -> fileOps.sha256File(patchFile)
        else -> throw IllegalArgumentException("No file to hash")
    }
}

internal fun MainActivity.reconcilePatchedIdentity(romId: String, romFile: File, patchFile: File) {
    val previous = romIdentityStore.getById(romId)
    val finalHash = materializedPatchedHash(romFile, patchFile)
    val legacyBundle = legacyBundleIdentityHash(romFile, patchFile)
    previous?.contentHash?.let { romIdentityStore.addIdentityAlias(romId, it, "pre_final_patch_identity") }
    romIdentityStore.addIdentityAlias(romId, legacyBundle, "legacy_bundle_v1")
    romIdentityStore.updatePatchedIdentity(romId, finalHash, legacyBundle)
    prefs.edit().putString(contentHashKey(romId), finalHash).apply()
}

internal fun MainActivity.commitActiveWorkingSaves() {
    val first = activeLinkFirstRomId
    val second = activeLinkSecondRomId
    if (!first.isNullOrBlank()) {
        runCatching { saveData.commitWorkingSave(first, 0) }
        if (!second.isNullOrBlank()) runCatching { saveData.commitWorkingSave(second, activeLinkSecondSavePlayer) }
    } else if (currentRomId.isNotBlank()) {
        runCatching { saveData.commitWorkingSave(currentRomId, 0) }
    }
    // A committed battery save is durable locally; mirror it to the user's
    // persisted Retra folder without requiring another picker confirmation.
    syncAppFolderAsync(showResult = false)
}

internal fun MainActivity.clearActiveLinkSaveTracking() {
    activeLinkFirstRomId = null
    activeLinkSecondRomId = null
    activeLinkSecondSavePlayer = 0
}

internal fun MainActivity.romIdForLaunchPath(path: String?): String? {
    if (path.isNullOrBlank()) return null
    return romIdentityStore.all().firstOrNull { it.launchPath == path }?.romId
        ?: prefs.all.keys.firstOrNull { key ->
            key.startsWith("content_path_") && prefs.getString(key, null) == path
        }?.removePrefix("content_path_")
}

internal fun MainActivity.archiveRomRecord(romId: String): Boolean {
    if (romId.isBlank()) return false
    prefs.edit().putBoolean(archivedKey(romId), true).apply()
    romIdentityStore.setArchived(romId, true)
    return true
}

internal fun MainActivity.deleteGameDataInternal(romId: String): Boolean {
    if (romId.isBlank()) return false
    if (romLoaded && currentRomId == romId) return false
    val safeId = fileOps.sanitizeFileName(romId)
    val targets = listOf(
        File(fileOps.persistentCategoryDir("Saves"), safeId),
        File(fileOps.persistentCategoryDir("SaveStates"), safeId),
        File(fileOps.persistentCategoryDir("Cheats"), safeId),
        File(fileOps.persistentCategoryDir("Config"), safeId),
        File(fileOps.persistentCategoryDir("Layouts"), safeId),
        File(fileOps.persistentCategoryDir("Covers"), safeId),
        File(fileOps.persistentCategoryDir("Backgrounds"), safeId),
        File(fileOps.persistentCategoryDir("Metadata"), safeId),
        File(fileOps.persistentCategoryDir("Backups"), safeId),
        File(filesDir, "save_work/$safeId")
    )
    targets.forEach { it.deleteRecursively() }
    artworkRepository.clearMetadata(romId)
    prefs.getString(contentPathKey(romId), null)?.let(::File)?.let { legacyLaunch ->
        runCatching { saveData.legacyBatterySaveForLaunch(legacyLaunch).delete() }
        runCatching { saveData.legacyRtcForLaunch(legacyLaunch).delete() }
    }
    runCatching { File(File(filesDir, "states"), safeId).deleteRecursively() }
    statistics.clearPlaytime(romId)
    prefs.edit()
        .remove("cheats_$safeId")
        .putBoolean(dataDeletedKey(romId), true)
        .apply()
    notifySaveStatesChanged(romId)
    return true
}

internal fun MainActivity.migrateLegacyRomIdentityIfNeeded() {
    val migratedVersion = prefs.getInt(ROM_IDENTITY_MIGRATION_PREF, 0)
    if (migratedVersion >= ROM_IDENTITY_SCHEMA_VERSION) return

    val ids = prefs.all.keys
        .filter { it.startsWith("file_name_") }
        .map { it.removePrefix("file_name_") }
        .distinct()

    var migrationVerified = true
    ids.forEach { id ->
        val fileName = prefs.getString(fileNameKey(id), null) ?: return@forEach
        val launchPath = prefs.getString(contentPathKey(id), null)
        val patchPath = prefs.getString(patchPathKey(id), null)
        val rawLaunchFile = launchPath?.let(::File)
        val launchFile = rawLaunchFile?.takeIf { it.exists() && it.isFile }
        val patchFile = patchPath?.let(::File)?.takeIf { it.exists() && it.isFile }

        // Preserve legacy user data even when the ROM file itself is missing.
        // This is the unmatched-legacy-data path: it stays under the old UUID
        // and reconnects automatically when that ROM is located/imported later.
        saveData.migrateLegacyBatteryData(id, rawLaunchFile)
        saveData.migrateLegacyStateData(id)
        if (!saveData.legacyMigrationVerified(id, rawLaunchFile)) {
            migrationVerified = false
        }

        val identityFile = launchFile ?: patchFile
        val hash = prefs.getString(contentHashKey(id), null)
            ?.takeIf { it.isNotBlank() }
            ?: runCatching { contentIdentityHash(launchFile, patchFile) }.getOrNull()
            ?: return@forEach
        val system = prefs.getString(systemKey(id), null)
            ?: launchFile?.let { inferSystemFromExtension(it.extension) }
            ?: "PATCH"
        val title = prefs.getString(titleKey(id), null) ?: fileName.substringBeforeLast('.')
        val now = System.currentTimeMillis()
        val previous = romIdentityStore.getById(id)

        romIdentityStore.upsert(
            RomIdentityStore.Record(
                romId = id,
                contentHash = hash,
                platform = system,
                displayName = title,
                fileName = fileName,
                sourceUri = prefs.getString(sourceUriKey(id), null),
                launchPath = launchPath,
                patchPath = patchPath,
                fileSize = identityFile?.length() ?: 0L,
                archived = prefs.getBoolean(archivedKey(id), false),
                fileAvailable = identityFile?.exists() == true,
                createdAt = previous?.createdAt ?: now,
                updatedAt = now
            )
        )
        prefs.edit().putString(contentHashKey(id), hash).apply()
    }

    // Mark only after every discovered legacy copy has been verified. If
    // anything failed, leave the version unchanged so the next launch retries.
    if (migrationVerified) {
        prefs.edit().putInt(ROM_IDENTITY_MIGRATION_PREF, ROM_IDENTITY_SCHEMA_VERSION).commit()
    }
}

internal fun MainActivity.writePortableMetadataFiles() {
    val metadataDir = fileOps.persistentCategoryDir("Metadata")
    listOf("Saves", "SaveStates", "Cheats", "Config", "Layouts", "Covers", "Backgrounds", "Backups")
        .forEach { fileOps.persistentCategoryDir(it) }

    val libraryJson = JSONObject().apply {
        put("schemaVersion", ROM_IDENTITY_SCHEMA_VERSION)
        put("generatedAt", System.currentTimeMillis())
        put("roms", JSONArray().apply {
            romIdentityStore.all().forEach { record ->
                put(JSONObject().apply {
                    put("romId", record.romId)
                    put("contentHash", record.contentHash)
                    put("hashAlgorithm", record.hashAlgorithm)
                    put("platform", record.platform)
                    put("displayName", record.displayName)
                    put("fileName", record.fileName)
                    put("currentFileUri", record.currentFileUri ?: record.sourceUri ?: "")
                    put("fileSize", record.fileSize)
                    put("lastModified", record.lastModified)
                    put("favorite", record.favorite)
                    put("categories", runCatching { JSONArray(record.categoriesJson) }.getOrElse { JSONArray() })
                    put("playtimeMs", record.playtimeMs)
                    put("lastPlayedAt", prefs.getLong("last_played_at_v1_${record.romId}", 0L).coerceAtLeast(0L))
                    put("playCount", prefs.getInt("play_count_v1_${record.romId}", 0).coerceAtLeast(0))
                    put("completed", prefs.getBoolean("completed_v1_${record.romId}", false))
                    put("archived", record.archived)
                    put("sourceExtension", prefs.getString(sourceExtensionKey(record.romId), "") ?: "")
                    put("finalContentHash", record.finalContentHash ?: "")
                    put("legacyIdentityHash", record.legacyIdentityHash ?: "")
                    put("createdAt", record.createdAt)
                })
            }
        })
    }
    fileOps.atomicWriteText(File(metadataDir, "library.json"), libraryJson.toString(2))

    // Keep a portable snapshot of Retra's device-independent settings beside
    // saves. This reads only the preference cache so it is safe during startup
    // migration before UI/render/audio controllers finish initializing.
    val portableSettings = JSONObject().apply {
        prefs.portableSettingsSnapshot().forEach { (key, value) -> put(key, value) }
    }
    fileOps.atomicWriteText(
        File(metadataDir, "settings.json"),
        JSONObject()
            .put("schemaVersion", 1)
            .put("generatedAt", System.currentTimeMillis())
            .put("settings", portableSettings)
            .toString(2)
    )

    val indexedIds = romIdentityStore.all().map { it.romId }.toSet()
    val unmatched = JSONArray()
    prefs.all.keys.filter { it.startsWith("file_name_") }.forEach { key ->
        val id = key.removePrefix("file_name_")
        if (id in indexedIds) return@forEach
        unmatched.put(JSONObject().apply {
            put("legacyRomId", id)
            put("fileName", prefs.getString(fileNameKey(id), "") ?: "")
            put("title", prefs.getString(titleKey(id), "") ?: "")
            put("platform", prefs.getString(systemKey(id), "") ?: "")
            put("previousLaunchPath", prefs.getString(contentPathKey(id), "") ?: "")
            put("dataPreservedUnderRomId", true)
        })
    }
    fileOps.atomicWriteText(
        File(metadataDir, "unmatched_legacy.json"),
        JSONObject().put("schemaVersion", 1).put("items", unmatched).toString(2)
    )

    romIdentityStore.all().forEach { record ->
        val safeId = fileOps.sanitizeFileName(record.romId)
        val cheatRaw = prefs.getString("cheats_$safeId", null)
        if (!cheatRaw.isNullOrBlank()) {
            fileOps.atomicWriteText(File(File(fileOps.persistentCategoryDir("Cheats"), safeId), "cheats.json"), cheatRaw)
        }
    }
}

internal fun MainActivity.contentPathKey(id: String) = "content_path_$id"
internal fun MainActivity.patchPathKey(id: String) = "patch_path_$id"
internal fun MainActivity.basePathKey(id: String) = "base_path_$id"
internal fun MainActivity.titleKey(id: String) = "title_$id"
internal fun MainActivity.fileNameKey(id: String) = "file_name_$id"
internal fun MainActivity.systemKey(id: String) = "system_$id"
internal fun MainActivity.sourceExtensionKey(id: String) = "source_ext_$id"
internal fun MainActivity.contentHashKey(id: String) = "content_hash_$id"
internal fun MainActivity.sourceUriKey(id: String) = "source_uri_$id"
internal fun MainActivity.archivedKey(id: String) = "archived_$id"
internal fun MainActivity.fileAvailableKey(id: String) = "file_available_$id"
internal fun MainActivity.dataDeletedKey(id: String) = "data_deleted_$id"

// JSON quoting covers quotes, backslashes and every control character.
