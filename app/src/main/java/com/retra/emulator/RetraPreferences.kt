package com.retra.emulator

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/**
 * Compatibility facade used during Retra's SharedPreferences -> DataStore migration.
 *
 * App-wide preferences are authoritative in Preferences DataStore. Legacy ROM-specific
 * keys remain readable from the old SharedPreferences file only long enough for the
 * Room/filesystem migration to recover older installs.
 */
class RetraPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val legacy: SharedPreferences =
        appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
    private val dataStore: DataStore<Preferences> = dataStoreFor(appContext)
    private val cache = ConcurrentHashMap<String, Any>()

    init {
        runBlocking(Dispatchers.IO) {
            dataStore.data.first().asMap().forEach { (key, value) -> cache[key.name] = value }
            migrateGlobalPreferencesFromLegacy()
        }
    }

    val all: Map<String, *> get() {
        val merged = LinkedHashMap<String, Any?>()
        legacy.all.forEach { (key, value) ->
            if (!isGlobalKey(key)) merged[key] = value
        }
        cache.forEach { (key, value) -> merged[key] = value }
        return merged
    }

    fun getString(key: String, defaultValue: String?): String? =
        if (isGlobalKey(key)) cache[key] as? String ?: defaultValue
        else legacy.getString(key, defaultValue)

    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        if (isGlobalKey(key)) cache[key] as? Boolean ?: defaultValue
        else legacy.getBoolean(key, defaultValue)

    fun getInt(key: String, defaultValue: Int): Int =
        if (isGlobalKey(key)) (cache[key] as? Number)?.toInt() ?: defaultValue
        else legacy.getInt(key, defaultValue)

    fun getLong(key: String, defaultValue: Long): Long =
        if (isGlobalKey(key)) (cache[key] as? Number)?.toLong() ?: defaultValue
        else legacy.getLong(key, defaultValue)

    fun edit(): Editor = Editor()

    inner class Editor {
        private val puts = LinkedHashMap<String, Any>()
        private val removals = LinkedHashSet<String>()

        fun putString(key: String, value: String?): Editor = apply {
            if (value == null) remove(key) else {
                removals.remove(key)
                puts[key] = value
            }
        }

        fun putBoolean(key: String, value: Boolean): Editor = apply {
            removals.remove(key)
            puts[key] = value
        }

        fun putInt(key: String, value: Int): Editor = apply {
            removals.remove(key)
            puts[key] = value
        }

        fun putLong(key: String, value: Long): Editor = apply {
            removals.remove(key)
            puts[key] = value
        }

        fun remove(key: String): Editor = apply {
            puts.remove(key)
            removals += key
        }

        fun apply() {
            val globalPuts = puts.filterKeys(::isGlobalKey)
            val globalRemovals = removals.filter(::isGlobalKey).toSet()
            val legacyPuts = puts.filterKeys { !isGlobalKey(it) }
            val legacyRemovals = removals.filter { !isGlobalKey(it) }.toSet()

            updateCache(globalPuts, globalRemovals)
            applyLegacy(legacyPuts, legacyRemovals, commit = false)
            if (globalPuts.isNotEmpty() || globalRemovals.isNotEmpty()) {
                appScope.launch {
                    dataStore.edit { preferences ->
                        applyDataStoreChanges(preferences, globalPuts, globalRemovals)
                    }
                }
            }
        }

        fun commit(): Boolean {
            val globalPuts = puts.filterKeys(::isGlobalKey)
            val globalRemovals = removals.filter(::isGlobalKey).toSet()
            val legacyPuts = puts.filterKeys { !isGlobalKey(it) }
            val legacyRemovals = removals.filter { !isGlobalKey(it) }.toSet()

            val legacyOk = applyLegacy(legacyPuts, legacyRemovals, commit = true)
            val globalOk = runCatching {
                runBlocking(Dispatchers.IO) {
                    dataStore.edit { preferences ->
                        applyDataStoreChanges(preferences, globalPuts, globalRemovals)
                    }
                }
                updateCache(globalPuts, globalRemovals)
                true
            }.getOrDefault(false)
            return legacyOk && globalOk
        }
    }

    private suspend fun migrateGlobalPreferencesFromLegacy() {
        val pending = LinkedHashMap<String, Any>()
        GLOBAL_KEYS.forEach { key ->
            if (!cache.containsKey(key) && legacy.contains(key)) {
                legacy.all[key]?.let { pending[key] = it }
            }
        }
        legacy.all.forEach { (key, value) ->
            if (isGlobalKey(key) && !cache.containsKey(key) && value != null) pending[key] = value
        }
        if (pending.isEmpty()) return

        dataStore.edit { preferences -> applyDataStoreChanges(preferences, pending, emptySet()) }
        pending.forEach { (key, value) -> cache[key] = value }
        val editor = legacy.edit()
        pending.keys.forEach { editor.remove(it) }
        editor.commit()
    }

    private fun updateCache(puts: Map<String, Any>, removals: Set<String>) {
        removals.forEach(cache::remove)
        puts.forEach { (key, value) -> cache[key] = value }
    }

    private fun applyLegacy(puts: Map<String, Any>, removals: Set<String>, commit: Boolean): Boolean {
        if (puts.isEmpty() && removals.isEmpty()) return true
        val editor = legacy.edit()
        removals.forEach(editor::remove)
        puts.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                else -> editor.putString(key, value.toString())
            }
        }
        return if (commit) editor.commit() else {
            editor.apply()
            true
        }
    }

    private fun applyDataStoreChanges(
        preferences: MutablePreferences,
        puts: Map<String, Any>,
        removals: Set<String>
    ) {
        removals.forEach { key -> removeTyped(preferences, key) }
        puts.forEach { (key, value) ->
            when (value) {
                is String -> preferences[stringPreferencesKey(key)] = value
                is Boolean -> preferences[booleanPreferencesKey(key)] = value
                is Int -> preferences[intPreferencesKey(key)] = value
                is Long -> preferences[longPreferencesKey(key)] = value
                else -> preferences[stringPreferencesKey(key)] = value.toString()
            }
        }
    }

    private fun removeTyped(preferences: MutablePreferences, key: String) {
        preferences.remove(stringPreferencesKey(key))
        preferences.remove(booleanPreferencesKey(key))
        preferences.remove(intPreferencesKey(key))
        preferences.remove(longPreferencesKey(key))
    }

    companion object {
        private const val LEGACY_PREFS_NAME = "retra_library"
        private const val DATASTORE_NAME = "retra_global_preferences"

        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        @Volatile private var sharedDataStore: DataStore<Preferences>? = null

        private fun dataStoreFor(context: Context): DataStore<Preferences> =
            sharedDataStore ?: synchronized(this) {
                sharedDataStore ?: PreferenceDataStoreFactory.create(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    scope = appScope,
                    produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) }
                ).also { sharedDataStore = it }
            }

        private val GLOBAL_KEYS = setOf(
            "remote_link_host_v1",
            "screen_orientation",
            "buttons_opacity",
            "fast_forward_speed_v1",
            "emulation_speed_v2",
            "glsl_shader_selected_v1",
            "game_color_style_v1",
            "controller_layout_landscape_100_v394",
            "cloud_sync_enabled_v1",
            "cloud_sync_uri_v1",
            "cloud_sync_account_v1",
            "cloud_sync_mode_v2",
            "app_folder_uri_v1",
            "rom_identity_migration_v2",
            "auto_save_load_v1",
            "rom_patching_v1",
            "enable_cheats_v1",
            "confirm_close_reset_v1",
            "fullscreen_mode_v1",
            "immersive_mode_v1",
            "stretch_to_fit_v1",
            "hardware_rendering_v1",
            "linear_filtering_v1",
            "enable_sound_v1",
            "frame_skip_v1",
            "volume_v1",
            "sound_frequency_v1",
            "cpu_core_v1",
            "cartridge_save_type_v1",
            "use_bios_v1",
            "boot_bios_v1",
            "smc_check_v1",
            "speed_optimization_v1",
            "mosaic_effect_v1",
            "fast_forward_button_mode_v1",
            "bios_gba_path_v1",
            "bios_gb_path_v1",
            "bios_gbc_path_v1",
            "bios_last_label_v1",
            "automatic_artwork_v1",
            "artwork_wifi_only_v1",
            "layouts_files_migration_v1",
            "playtime_room_migration_v1",
            "library_metadata_room_migration_v1"
        )

        private fun isGlobalKey(key: String): Boolean =
            key in GLOBAL_KEYS || key.startsWith("ui_")
    }
}
