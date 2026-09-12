package com.retra.emulator

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class CheatEntry(
    val id: String,
    var name: String,
    var type: Int,
    var code: String,
    var enabled: Boolean = true
)

/** Filesystem-authoritative per-ROM cheat persistence with legacy preference fallback. */
internal class CheatRepository(
    private val prefs: RetraPreferences,
    private val fileOps: RetraFileOps
) {
    fun load(romId: String): MutableList<CheatEntry> {
        val file = cheatFile(romId)
        val legacyRaw = prefs.getString(prefKey(romId), "[]") ?: "[]"
        val raw = if (file.exists() && file.length() > 0L) {
            runCatching { file.readText() }.getOrDefault(legacyRaw)
        } else {
            legacyRaw.also { existing ->
                if (existing.isNotBlank()) runCatching { fileOps.atomicWriteText(file, existing) }
            }
        }
        return try {
            val array = JSONArray(raw)
            MutableList(array.length()) { index ->
                val item = array.optJSONObject(index) ?: JSONObject()
                CheatEntry(
                    id = item.optString("id", UUID.randomUUID().toString()),
                    name = item.optString("name", "Cheat"),
                    type = item.optInt("type", 0).coerceIn(0, 4),
                    code = item.optString("code", ""),
                    enabled = item.optBoolean("enabled", true)
                )
            }
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun save(romId: String, cheats: List<CheatEntry>) {
        val array = JSONArray()
        cheats.forEach { cheat ->
            array.put(JSONObject().apply {
                put("id", cheat.id)
                put("name", cheat.name)
                put("type", cheat.type)
                put("code", cheat.code)
                put("enabled", cheat.enabled)
            })
        }
        val json = array.toString()
        fileOps.atomicWriteText(cheatFile(romId), json)
        prefs.edit().putString(prefKey(romId), json).apply()
    }

    fun cheatFile(romId: String): File =
        File(
            File(fileOps.persistentCategoryDir("Cheats"), fileOps.sanitizeFileName(romId)).apply { mkdirs() },
            "cheats.json"
        )

    private fun prefKey(romId: String): String = "cheats_${fileOps.sanitizeFileName(romId)}"
}
