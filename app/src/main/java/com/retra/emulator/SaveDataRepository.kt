package com.retra.emulator

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Crash-safe battery-save working copy, backup and legacy migration layer.
 *
 * Emulator/session code asks this repository for canonical/working save files;
 * MainActivity no longer owns filesystem locking or verified save migration.
 */
class SaveDataRepository(
    context: Context,
    private val prefs: RetraPreferences,
    private val fileOps: RetraFileOps
) {
    private val filesDir = context.filesDir
    private val saveLocks = ConcurrentHashMap<String, Any>()

    fun batterySaveFile(romId: String, player: Int = 0): File {
        val safeId = fileOps.sanitizeFileName(romId)
        val name = if (player <= 0) "game.sav" else "link_player${player + 1}.sav"
        return File(File(fileOps.persistentCategoryDir("Saves"), safeId).apply { mkdirs() }, name)
    }

    fun batteryRtcFile(romId: String): File {
        val safeId = fileOps.sanitizeFileName(romId)
        return File(File(fileOps.persistentCategoryDir("Saves"), safeId).apply { mkdirs() }, "game.rtc")
    }

    fun workingSaveFile(romId: String, player: Int = 0): File {
        val safeId = fileOps.sanitizeFileName(romId)
        val name = if (player <= 0) "game.sav" else "link_player${player + 1}.sav"
        return File(File(filesDir, "save_work/$safeId").apply { mkdirs() }, name)
    }

    fun prepareWorkingSave(romId: String, player: Int = 0, launchFile: File? = null): File =
        synchronized(saveLockFor(romId)) {
            migrateLegacyBatteryData(romId, launchFile)
            val canonical = batterySaveFile(romId, player)
            val working = workingSaveFile(romId, player)

            // Recover a newer working copy left by an interrupted/abrupt session.
            if (working.exists() && working.length() > 0L) {
                val differs = !canonical.exists() || canonical.length() != working.length() ||
                    runCatching { fileOps.sha256File(canonical) != fileOps.sha256File(working) }.getOrDefault(true)
                val looksNewer = !canonical.exists() || working.lastModified() >= canonical.lastModified()
                if (differs && looksNewer) {
                    if (player == 0 && canonical.exists() && canonical.length() > 0L) backupBatterySaveIfChanged(romId)
                    fileOps.atomicCopyVerified(working, canonical)
                }
            }

            if (canonical.exists() && canonical.length() > 0L) {
                fileOps.atomicCopyVerified(canonical, working)
            } else if (working.exists()) {
                working.delete()
            }
            working
        }

    fun commitWorkingSave(romId: String, player: Int = 0) {
        if (romId.isBlank()) return
        synchronized(saveLockFor(romId)) {
            val working = workingSaveFile(romId, player)
            if (!working.exists() || working.length() <= 0L) return@synchronized
            val canonical = batterySaveFile(romId, player)
            val differs = !canonical.exists() || canonical.length() != working.length() ||
                runCatching { fileOps.sha256File(canonical) != fileOps.sha256File(working) }.getOrDefault(true)
            if (!differs) return@synchronized

            if (player == 0 && canonical.exists() && canonical.length() > 0L) {
                backupBatterySaveIfChanged(romId)
            }
            fileOps.atomicCopyVerified(working, canonical)
            canonical.setLastModified(working.lastModified().coerceAtLeast(System.currentTimeMillis()))
        }
    }

    fun migrateLegacyBatteryData(romId: String, launchFile: File?) {
        if (romId.isBlank() || launchFile == null) return
        if (prefs.getBoolean(dataDeletedKey(romId), false)) return
        if (prefs.getInt(ROM_IDENTITY_MIGRATION_PREF, 0) >= ROM_IDENTITY_SCHEMA_VERSION) return
        val target = batterySaveFile(romId)
        val legacy = legacyBatterySaveForLaunch(launchFile)
        if (!target.exists() && legacy.exists() && legacy.length() > 0L) {
            runCatching { fileOps.atomicCopyVerified(legacy, target) }
        }
        val rtcTarget = batteryRtcFile(romId)
        val legacyRtc = legacyRtcForLaunch(launchFile)
        if (!rtcTarget.exists() && legacyRtc.exists() && legacyRtc.length() > 0L) {
            runCatching { fileOps.atomicCopyVerified(legacyRtc, rtcTarget) }
        }
    }

    fun migrateLegacyStateData(romId: String) {
        if (romId.isBlank()) return
        if (prefs.getBoolean(dataDeletedKey(romId), false)) return
        if (prefs.getInt(ROM_IDENTITY_MIGRATION_PREF, 0) >= ROM_IDENTITY_SCHEMA_VERSION) return
        val safeId = fileOps.sanitizeFileName(romId)
        val legacy = File(File(filesDir, "states"), safeId)
        val target = File(fileOps.persistentCategoryDir("SaveStates"), safeId)
        if (legacy.exists()) runCatching { copyDirectoryVerified(legacy, target) }
    }

    fun legacyMigrationVerified(romId: String, launchFile: File?): Boolean {
        if (romId.isBlank() || prefs.getBoolean(dataDeletedKey(romId), false)) return true
        if (launchFile != null) {
            val legacySave = legacyBatterySaveForLaunch(launchFile)
            val migratedSave = batterySaveFile(romId)
            if (legacySave.exists() && legacySave.length() > 0L) {
                if (!migratedSave.exists() || migratedSave.length() != legacySave.length()) return false
                if (runCatching { fileOps.sha256File(migratedSave) != fileOps.sha256File(legacySave) }.getOrDefault(true)) return false
            }

            val legacyRtc = legacyRtcForLaunch(launchFile)
            val migratedRtc = batteryRtcFile(romId)
            if (legacyRtc.exists() && legacyRtc.length() > 0L) {
                if (!migratedRtc.exists() || migratedRtc.length() != legacyRtc.length()) return false
                if (runCatching { fileOps.sha256File(migratedRtc) != fileOps.sha256File(legacyRtc) }.getOrDefault(true)) return false
            }
        }

        val safeId = fileOps.sanitizeFileName(romId)
        val legacyStates = File(File(filesDir, "states"), safeId)
        val migratedStates = File(fileOps.persistentCategoryDir("SaveStates"), safeId)
        if (legacyStates.exists() && legacyStates.isDirectory) {
            for (source in legacyStates.walkTopDown().filter { it.isFile }) {
                val target = File(migratedStates, source.relativeTo(legacyStates).path)
                if (!target.exists() || target.length() != source.length()) return false
                if (runCatching { fileOps.sha256File(target) != fileOps.sha256File(source) }.getOrDefault(true)) return false
            }
        }
        return true
    }

    fun legacyBatterySaveForLaunch(launchFile: File): File =
        File(launchFile.parentFile, "${launchFile.nameWithoutExtension}.sav")

    fun legacyRtcForLaunch(launchFile: File): File =
        File(launchFile.parentFile, "${launchFile.nameWithoutExtension}.rtc")

    private fun saveLockFor(romId: String): Any =
        saveLocks.getOrPut(fileOps.sanitizeFileName(romId)) { Any() }

    private fun backupDirectory(romId: String): File =
        File(fileOps.persistentCategoryDir("Backups"), fileOps.sanitizeFileName(romId)).apply { mkdirs() }

    private fun copyDirectoryVerified(source: File, target: File) {
        if (!source.exists() || !source.isDirectory) return
        source.walkTopDown().forEach { file ->
            if (!file.isFile) return@forEach
            val relative = file.relativeTo(source)
            val destination = File(target, relative.path)
            if (!destination.exists() || destination.length() != file.length()) {
                fileOps.atomicCopyVerified(file, destination)
            }
        }
    }

    private fun backupBatterySaveIfChanged(romId: String) {
        if (romId.isBlank()) return
        val source = batterySaveFile(romId)
        if (!source.exists() || source.length() <= 0L) return
        val dir = backupDirectory(romId)
        val newest = dir.listFiles()
            ?.filter { it.isFile && it.extension.equals("sav", true) }
            ?.maxByOrNull { it.lastModified() }
        if (newest != null && newest.length() == source.length()) {
            val same = runCatching { fileOps.sha256File(newest) == fileOps.sha256File(source) }.getOrDefault(false)
            if (same) return
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val backup = File(dir, "game_$stamp.sav")
        runCatching { fileOps.atomicCopyVerified(source, backup) }.onSuccess {
            dir.listFiles()
                ?.filter { it.isFile && it.extension.equals("sav", true) }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(5)
                ?.forEach { it.delete() }
        }
    }

    private fun dataDeletedKey(id: String) = "data_deleted_$id"

    private companion object {
        const val ROM_IDENTITY_MIGRATION_PREF = "rom_identity_migration_v2"
        const val ROM_IDENTITY_SCHEMA_VERSION = 2
    }
}
