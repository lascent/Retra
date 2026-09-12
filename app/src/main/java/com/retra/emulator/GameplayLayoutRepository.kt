package com.retra.emulator

import org.json.JSONObject
import java.io.File

/**
 * Filesystem-backed controller + emulator-screen layout storage.
 *
 * MainActivity owns live View placement only; this repository owns persistence,
 * orientation separation and legacy-key migration.
 */
class GameplayLayoutRepository(
    private val prefs: RetraPreferences,
    private val fileOps: RetraFileOps,
    private val controllerLayoutPref: String = "controller_layout_v390",
    private val screenLayoutPref: String = "screen_layout_v388"
) {
    fun readBundle(romId: String, portrait: Boolean): JSONObject? {
        val requested = layoutFile(romId.ifBlank { "_default" }, portrait)
        val fallback = layoutFile("_default", portrait)
        val file = requested.takeIf { it.exists() } ?: fallback.takeIf { it.exists() }
        if (file != null) {
            runCatching { return JSONObject(file.readText()) }
        }

        // Read-only compatibility fallback for installs that predate per-ROM files.
        val controllerRaw = prefs.getString(controllerKey(portrait), null)
        val screenRaw = prefs.getString(screenKey(portrait), null)
        if (controllerRaw.isNullOrBlank() && screenRaw.isNullOrBlank()) return null
        return JSONObject().apply {
            put("orientation", if (portrait) "portrait" else "landscape")
            if (!controllerRaw.isNullOrBlank()) runCatching { put("controller", JSONObject(controllerRaw)) }
            if (!screenRaw.isNullOrBlank()) runCatching { put("screen", JSONObject(screenRaw)) }
        }
    }

    fun controllerJson(romId: String, portrait: Boolean): String? =
        readBundle(romId, portrait)?.optJSONObject("controller")?.toString()

    fun screenJson(romId: String, portrait: Boolean): String? =
        readBundle(romId, portrait)?.optJSONObject("screen")?.toString()

    fun writeBundle(
        romId: String,
        portrait: Boolean,
        controller: JSONObject?,
        screen: JSONObject?
    ): Boolean = runCatching {
        val normalizedId = romId.ifBlank { "_default" }
        val existing = readBundle(normalizedId, portrait)
        val out = JSONObject().apply {
            put("schemaVersion", 1)
            put("romId", normalizedId)
            put("orientation", if (portrait) "portrait" else "landscape")
            val finalController = controller ?: existing?.optJSONObject("controller")
            val finalScreen = screen ?: existing?.optJSONObject("screen")
            if (finalController != null) put("controller", finalController)
            if (finalScreen != null) put("screen", finalScreen)
            put("updatedAt", System.currentTimeMillis())
        }
        fileOps.atomicWriteText(layoutFile(normalizedId, portrait), out.toString(2))
        true
    }.getOrDefault(false)

    fun clear(romId: String, portrait: Boolean): Boolean = runCatching {
        val file = layoutFile(romId.ifBlank { "_default" }, portrait)
        !file.exists() || file.delete()
    }.getOrDefault(false)

    fun migrateLegacyGlobalLayouts() {
        if (prefs.getBoolean(MIGRATION_KEY, false)) return
        listOf(true, false).forEach { portrait ->
            val controllerRaw = prefs.getString(controllerKey(portrait), null)
            val screenRaw = prefs.getString(screenKey(portrait), null)
            if (controllerRaw.isNullOrBlank() && screenRaw.isNullOrBlank()) return@forEach
            val controller = runCatching { controllerRaw?.let(::JSONObject) }.getOrNull()
            val screen = runCatching { screenRaw?.let(::JSONObject) }.getOrNull()
            if (!writeBundle("_default", portrait, controller, screen)) return
        }
        // Keep the legacy values as rollback material; this marker makes files authoritative.
        prefs.edit().putBoolean(MIGRATION_KEY, true).commit()
    }

    fun layoutFile(romId: String, portrait: Boolean): File =
        File(layoutDirectory(romId), if (portrait) "portrait.json" else "landscape.json")

    private fun layoutDirectory(romId: String): File =
        File(
            fileOps.persistentCategoryDir("Layouts"),
            fileOps.sanitizeFileName(romId.ifBlank { "_default" })
        ).apply { mkdirs() }

    fun legacyControllerKey(portrait: Boolean): String = controllerKey(portrait)

    private fun controllerKey(portrait: Boolean): String =
        "${controllerLayoutPref}_${if (portrait) "portrait" else "landscape"}"

    private fun screenKey(portrait: Boolean): String =
        "${screenLayoutPref}_${if (portrait) "portrait" else "landscape"}"

    private companion object {
        const val MIGRATION_KEY = "layouts_files_migration_v1"
    }
}
