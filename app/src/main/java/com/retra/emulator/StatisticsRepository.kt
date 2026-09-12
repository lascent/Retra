package com.retra.emulator

import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Owns playtime accumulation/migration and aggregate local-save statistics. */
class StatisticsRepository(
    private val prefs: RetraPreferences,
    private val romIdentityStore: RomIdentityStore,
    private val fileOps: RetraFileOps
) {
    private val playtimeLock = Any()
    private var segmentStartedAtMs = 0L

    fun begin(romLoaded: Boolean) {
        synchronized(playtimeLock) {
            if (!romLoaded || segmentStartedAtMs > 0L) return
            segmentStartedAtMs = SystemClock.elapsedRealtime()
        }
    }

    fun commit(romLoaded: Boolean, romId: String) {
        synchronized(playtimeLock) {
            val startedAt = segmentStartedAtMs
            if (startedAt <= 0L || !romLoaded) {
                segmentStartedAtMs = 0L
                return
            }
            val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
            segmentStartedAtMs = 0L
            if (elapsed <= 0L || romId.isBlank()) return

            val key = playtimeKey(romId)
            val storedRoom = romIdentityStore.getById(romId)?.playtimeMs ?: 0L
            val storedLegacy = prefs.getLong(key, 0L).coerceAtLeast(0L)
            val total = maxOf(storedRoom, storedLegacy) + elapsed
            romIdentityStore.updatePlaytime(romId, total)
            // Keep one-version legacy mirror for downgrade safety.
            prefs.edit().putLong(key, total).apply()
        }
    }

    fun storedPlaytimeMs(romId: String): Long {
        if (romId.isBlank()) return 0L
        return maxOf(
            romIdentityStore.getById(romId)?.playtimeMs ?: 0L,
            prefs.getLong(playtimeKey(romId), 0L).coerceAtLeast(0L)
        )
    }

    fun clearPlaytime(romId: String) {
        if (romId.isBlank()) return
        synchronized(playtimeLock) {
            if (segmentStartedAtMs > 0L) segmentStartedAtMs = 0L
            romIdentityStore.updatePlaytime(romId, 0L)
            prefs.edit().remove(playtimeKey(romId)).apply()
        }
    }

    fun migrateLegacyToRoom() {
        if (prefs.getBoolean(MIGRATION_PREF, false)) return
        prefs.all.forEach { (key, value) ->
            if (!key.startsWith(PLAYTIME_PREF_PREFIX)) return@forEach
            val romId = key.removePrefix(PLAYTIME_PREF_PREFIX)
            val legacyMs = (value as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
            if (romId.isBlank() || legacyMs <= 0L) return@forEach
            val existing = romIdentityStore.getById(romId) ?: return@forEach
            if (legacyMs > existing.playtimeMs) romIdentityStore.updatePlaytime(romId, legacyMs)
        }
        prefs.edit().putBoolean(MIGRATION_PREF, true).commit()
    }

    fun json(romLoaded: Boolean, currentRomId: String): String {
        val playtimes = JSONObject()
        romIdentityStore.all().forEach { record ->
            if (record.playtimeMs > 0L) playtimes.put(record.romId, record.playtimeMs)
        }
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(PLAYTIME_PREF_PREFIX)) {
                val romId = key.removePrefix(PLAYTIME_PREF_PREFIX)
                val millis = (value as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
                if (romId.isNotBlank() && millis > playtimes.optLong(romId, 0L)) playtimes.put(romId, millis)
            }
        }

        synchronized(playtimeLock) {
            if (romLoaded && currentRomId.isNotBlank() && segmentStartedAtMs > 0L) {
                val live = (SystemClock.elapsedRealtime() - segmentStartedAtMs).coerceAtLeast(0L)
                val stored = maxOf(
                    prefs.getLong(playtimeKey(currentRomId), 0L).coerceAtLeast(0L),
                    romIdentityStore.getById(currentRomId)?.playtimeMs ?: 0L
                )
                playtimes.put(currentRomId, stored + live)
            }
        }

        return JSONObject()
            .put("playtimeMsByRom", playtimes)
            .put("saveStates", countFiles(fileOps.persistentCategoryDir("SaveStates"), setOf("ss", "state")))
            .put("batterySaves", countFiles(fileOps.persistentCategoryDir("Saves"), setOf("sav", "srm")))
            .put("backups", countFiles(fileOps.persistentCategoryDir("Backups"), setOf("sav", "bak", "backup")))
            .toString()
    }

    private fun playtimeKey(romId: String): String = PLAYTIME_PREF_PREFIX + romId

    private fun countFiles(root: File, extensions: Set<String>): Int {
        if (!root.exists()) return 0
        var count = 0
        root.walkTopDown().forEach { file ->
            if (file.isFile && file.length() > 0L && file.extension.lowercase(Locale.US) in extensions) count++
        }
        return count
    }

    companion object {
        private const val PLAYTIME_PREF_PREFIX = "playtime_ms_v1_"
        private const val MIGRATION_PREF = "playtime_room_migration_v1"
    }
}
