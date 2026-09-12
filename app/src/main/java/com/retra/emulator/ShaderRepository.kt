package com.retra.emulator

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Owns Retra's optional GLSL fragment shaders.
 *
 * Custom shaders are intentionally constrained to a small ES 2.0 interface:
 *   uniform sampler2D uTexture;
 *   uniform vec2 uTextureSize;
 *   uniform vec2 uOutputSize;
 *   uniform float uTime;
 *   varying vec2 vTexCoord;
 *
 * The normal renderer remains active when "none" is selected, so installing
 * shader support does not add GPU/CPU cost on low-end devices by default.
 */
class ShaderRepository(
    context: Context,
    private val prefs: RetraPreferences
) {
    data class Option(val id: String, val label: String, val builtIn: Boolean)

    private val appContext = context.applicationContext
    private val shaderDir = File(appContext.filesDir, "Shaders").apply { mkdirs() }

    fun selectedId(): String {
        val stored = prefs.getString(SELECTED_PREF, "none") ?: "none"
        if (stored == "none") return "none"
        if (stored.startsWith("custom:")) {
            val safe = sanitizeName(stored.removePrefix("custom:"))
            if (File(shaderDir, "$safe.$STORED_EXTENSION").exists()) return stored
        }
        // Older development builds exposed built-in shader IDs that are no longer
        // part of Retra. Migrate those stale selections back to the default renderer.
        prefs.edit().putString(SELECTED_PREF, "none").apply()
        return "none"
    }

    fun select(id: String) {
        val resolved = optionFor(id)?.id ?: "none"
        prefs.edit().putString(SELECTED_PREF, resolved).apply()
    }

    fun options(): List<Option> = buildList {
        addAll(BUILT_INS.map { Option(it.first, it.second, true) })
        shaderDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase(Locale.US) == STORED_EXTENSION }
            ?.sortedBy { it.name.lowercase(Locale.US) }
            ?.forEach { file ->
                val id = "custom:${file.nameWithoutExtension}"
                val label = file.nameWithoutExtension.replace('_', ' ').replace('-', ' ').trim()
                    .ifBlank { "Custom shader" }
                add(Option(id, label, false))
            }
    }

    fun optionsJson(): JSONArray = JSONArray().apply {
        options().forEach { option ->
            put(JSONObject().apply {
                put("id", option.id)
                put("label", option.label)
                put("builtIn", option.builtIn)
            })
        }
    }

    fun sourceFor(id: String): String? = when (id) {
        "none" -> null
        else -> {
            if (!id.startsWith("custom:")) {
                null
            } else {
                val safe = sanitizeName(id.removePrefix("custom:"))
                File(shaderDir, "$safe.$STORED_EXTENSION").takeIf { it.exists() }?.readText()
            }
        }
    }

    fun optionFor(id: String): Option? = options().firstOrNull { it.id == id }

    fun install(uri: Uri): Option {
        val displayName = queryDisplayName(uri) ?: "custom_shader.glsl"
        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.US)
        require(extension in ALLOWED_EXTENSIONS) { "Choose a .glsl, .frag, .fs, or .fsh shader" }

        val source = appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            val text = reader.readText()
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_SHADER_BYTES) { "Shader is too large" }
            text
        } ?: error("Could not read shader")

        validateSource(source)
        val safeBase = uniqueSafeBase(displayName.substringBeforeLast('.', displayName))
        val target = File(shaderDir, "$safeBase.$STORED_EXTENSION")
        target.writeText(source)
        return Option("custom:$safeBase", safeBase.replace('_', ' ').replace('-', ' '), false)
    }

    fun remove(id: String): Boolean {
        if (!id.startsWith("custom:")) return false
        val safe = sanitizeName(id.removePrefix("custom:"))
        val deleted = File(shaderDir, "$safe.$STORED_EXTENSION").delete()
        if (selectedId() == id) select("none")
        return deleted
    }

    private fun validateSource(source: String) {
        val lowered = source.lowercase(Locale.US)
        require("void main" in lowered) { "Shader must contain void main()" }
        require("#include" !in lowered) { "#include is not supported" }
        require("gl_fragcolor" in lowered) { "Shader must write gl_FragColor" }
        require("utexture" in lowered) {
            "Shader must sample uniform sampler2D uTexture"
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) null
                    else cursor.getString(0)
                }
        }.getOrNull()
    }

    private fun uniqueSafeBase(raw: String): String {
        val initial = sanitizeName(raw).ifBlank { "custom_shader" }
        if (!File(shaderDir, "$initial.$STORED_EXTENSION").exists()) return initial
        for (index in 2..999) {
            val candidate = "${initial}_$index"
            if (!File(shaderDir, "$candidate.$STORED_EXTENSION").exists()) return candidate
        }
        return "${initial}_${System.currentTimeMillis()}"
    }

    private fun sanitizeName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_', '.', '-')
        .take(64)

    companion object {
        const val SELECTED_PREF = "glsl_shader_selected_v1"
        private const val STORED_EXTENSION = "glsl"
        private const val MAX_SHADER_BYTES = 128 * 1024
        private val ALLOWED_EXTENSIONS = setOf("glsl", "frag", "fs", "fsh")

        private val BUILT_INS = listOf(
            "none" to "None"
        )
    }
}
