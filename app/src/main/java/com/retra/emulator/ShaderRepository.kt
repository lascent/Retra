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
 * Built-in presets use the same small OpenGL ES 2.0 interface as user shaders:
 *   uniform sampler2D uTexture;
 *   uniform vec2 uTextureSize;
 *   uniform vec2 uOutputSize;
 *   uniform float uTime;
 *   varying vec2 vTexCoord;
 *
 * The normal renderer remains active when "none" is selected, so the feature
 * adds no shader-view GPU/CPU cost unless the user explicitly enables a preset.
 */
class ShaderRepository(
    context: Context,
    private val prefs: RetraPreferences
) {
    data class Option(
        val id: String,
        val label: String,
        val builtIn: Boolean,
        val impact: String,
        val description: String
    )

    private data class BuiltInShader(
        val id: String,
        val label: String,
        val impact: String,
        val description: String,
        val source: String?
    )

    private val appContext = context.applicationContext
    private val shaderDir = File(appContext.filesDir, "Shaders").apply { mkdirs() }

    fun selectedId(): String {
        val stored = prefs.getString(SELECTED_PREF, "none") ?: "none"
        if (BUILT_IN_BY_ID.containsKey(stored)) return stored
        if (stored.startsWith("custom:")) {
            val safe = sanitizeName(stored.removePrefix("custom:"))
            if (File(shaderDir, "$safe.$STORED_EXTENSION").exists()) return stored
        }
        // Unknown/stale selections are intentionally reset instead of trying to
        // compile an ID that no longer exists in this build.
        prefs.edit().putString(SELECTED_PREF, "none").apply()
        return "none"
    }

    fun select(id: String) {
        val resolved = optionFor(id)?.id ?: "none"
        prefs.edit().putString(SELECTED_PREF, resolved).apply()
    }

    fun options(): List<Option> = buildList {
        BUILT_INS.forEach { shader ->
            add(
                Option(
                    id = shader.id,
                    label = shader.label,
                    builtIn = true,
                    impact = shader.impact,
                    description = shader.description
                )
            )
        }
        shaderDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase(Locale.US) == STORED_EXTENSION }
            ?.sortedBy { it.name.lowercase(Locale.US) }
            ?.forEach { file ->
                val id = "custom:${file.nameWithoutExtension}"
                val label = file.nameWithoutExtension.replace('_', ' ').replace('-', ' ').trim()
                    .ifBlank { "Custom shader" }
                add(
                    Option(
                        id = id,
                        label = label,
                        builtIn = false,
                        impact = "Custom",
                        description = "Installed GLSL shader"
                    )
                )
            }
    }

    fun optionsJson(): JSONArray = JSONArray().apply {
        options().forEach { option ->
            put(JSONObject().apply {
                put("id", option.id)
                put("label", option.label)
                put("builtIn", option.builtIn)
                put("impact", option.impact)
                put("description", option.description)
            })
        }
    }

    fun sourceFor(id: String): String? {
        val builtIn = BUILT_IN_BY_ID[id]
        if (builtIn != null) return builtIn.source
        if (!id.startsWith("custom:")) return null
        val safe = sanitizeName(id.removePrefix("custom:"))
        return File(shaderDir, "$safe.$STORED_EXTENSION").takeIf { it.exists() }?.readText()
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
        return Option(
            id = "custom:$safeBase",
            label = safeBase.replace('_', ' ').replace('-', ' '),
            builtIn = false,
            impact = "Custom",
            description = "Installed GLSL shader"
        )
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
            BuiltInShader(
                id = "none",
                label = "Off",
                impact = "None",
                description = "Default renderer • no extra GPU cost",
                source = null
            ),
            BuiltInShader(
                id = "gba-color",
                label = "GBA Color Corrected",
                impact = "Low",
                description = "Balanced handheld-style color response",
                source = GBA_COLOR_CORRECTED_SHADER
            ),
            BuiltInShader(
                id = "sharp",
                label = "Sharp",
                impact = "Low",
                description = "Crisp pixels with subtle edge detail",
                source = SHARP_SHADER
            ),
            BuiltInShader(
                id = "smooth",
                label = "Smooth",
                impact = "Low",
                description = "Soft multi-sample scaling for uneven sizes",
                source = SMOOTH_SHADER
            ),
            BuiltInShader(
                id = "pixel-perfect",
                label = "Pixel Perfect",
                impact = "Low",
                description = "Locks sampling to original pixel centers",
                source = PIXEL_PERFECT_SHADER
            ),
            BuiltInShader(
                id = "lcd-grid",
                label = "LCD Grid",
                impact = "Medium",
                description = "Subtle handheld LCD cell structure",
                source = LCD_GRID_SHADER
            ),
            BuiltInShader(
                id = "lcd-response",
                label = "LCD Response",
                impact = "Medium",
                description = "Gentle LCD-style pixel response softness",
                source = LCD_RESPONSE_SHADER
            ),
            BuiltInShader(
                id = "scanlines",
                label = "Scanlines",
                impact = "Low",
                description = "Light retro horizontal scanline texture",
                source = SCANLINES_SHADER
            ),
            BuiltInShader(
                id = "crt-lite",
                label = "CRT Lite",
                impact = "Medium",
                description = "Light curvature, scanlines and vignette",
                source = CRT_LITE_SHADER
            ),
            BuiltInShader(
                id = "retro-warm",
                label = "Retro Warm",
                impact = "Low",
                description = "Warm palette with restrained contrast",
                source = RETRO_WARM_SHADER
            )
        )

        private val BUILT_IN_BY_ID = BUILT_INS.associateBy { it.id }

        private const val GBA_COLOR_CORRECTED_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec2 uTextureSize;
            uniform vec2 uOutputSize;
            uniform float uTime;
            varying vec2 vTexCoord;
            void main() {
                vec4 sampleColor = texture2D(uTexture, vTexCoord);
                vec3 c = sampleColor.rgb;
                vec3 corrected;
                corrected.r = 0.78 * c.r + 0.18 * c.g + 0.04 * c.b;
                corrected.g = 0.08 * c.r + 0.86 * c.g + 0.06 * c.b;
                corrected.b = 0.05 * c.r + 0.14 * c.g + 0.81 * c.b;
                float luma = dot(corrected, vec3(0.299, 0.587, 0.114));
                corrected = mix(corrected, vec3(luma), 0.05);
                corrected = pow(clamp(corrected, vec3(0.0), vec3(1.0)), vec3(0.94));
                gl_FragColor = vec4(corrected, sampleColor.a);
            }
        """

        private const val SHARP_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec2 uTextureSize;
            uniform vec2 uOutputSize;
            uniform float uTime;
            varying vec2 vTexCoord;
            void main() {
                vec2 texel = 1.0 / max(uTextureSize, vec2(1.0));
                vec4 center = texture2D(uTexture, vTexCoord);
                vec4 neighbors = texture2D(uTexture, vTexCoord + vec2(texel.x, 0.0))
                               + texture2D(uTexture, vTexCoord - vec2(texel.x, 0.0))
                               + texture2D(uTexture, vTexCoord + vec2(0.0, texel.y))
                               + texture2D(uTexture, vTexCoord - vec2(0.0, texel.y));
                gl_FragColor = clamp(center * 1.32 - neighbors * 0.08, vec4(0.0), vec4(1.0));
            }
        """

        private const val SMOOTH_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec2 uTextureSize;
            uniform vec2 uOutputSize;
            uniform float uTime;
            varying vec2 vTexCoord;
            void main() {
                vec2 texel = 1.0 / max(uTextureSize, vec2(1.0));
                vec4 center = texture2D(uTexture, vTexCoord) * 0.50;
                vec4 around = texture2D(uTexture, vTexCoord + vec2(texel.x, 0.0))
                            + texture2D(uTexture, vTexCoord - vec2(texel.x, 0.0))
                            + texture2D(uTexture, vTexCoord + vec2(0.0, texel.y))
                            + texture2D(uTexture, vTexCoord - vec2(0.0, texel.y));
                gl_FragColor = center + around * 0.125;
            }
        """

        private const val PIXEL_PERFECT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec2 uTextureSize;
            uniform vec2 uOutputSize;
            uniform float uTime;
            varying vec2 vTexCoord;
            void main() {
                vec2 safeSize = max(uTextureSize, vec2(1.0));
                vec2 pixel = floor(vTexCoord * safeSize) + vec2(0.5);
                vec2 uv = pixel / safeSize;
                gl_FragColor = texture2D(uTexture, uv);
            }
        """

        private const val LCD_GRID_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec2 uTextureSize;
            uniform vec2 uOutputSize;
            uniform float uTime;
            varying vec2 vTexCoord;
            void main() {
                vec4 c = texture2D(uTexture, vTexCoord);
                vec2 cell = fract(vTexCoord * max(uTextureSize, vec2(1.0)));
                float innerX = smoothstep(0.04, 0.18, cell.x) * smoothstep(0.04, 0.18, 1.0 - cell.x);
                float innerY = smoothstep(0.04, 0.18, cell.y) * smoothstep(0.04, 0.18, 1.0 - cell.y);
                float mask = 0.80 + 0.20 * innerX * innerY;
                gl_FragColor = vec4(c.rgb * mask, c.a);
            }
        """

        private const val LCD_RESPONSE_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec2 uTextureSize;
            uniform vec2 uOutputSize;
            uniform float uTime;
            varying vec2 vTexCoord;
            void main() {
                vec2 texel = 1.0 / max(uTextureSize, vec2(1.0));
                vec4 c = texture2D(uTexture, vTexCoord);
                vec4 horizontal = texture2D(uTexture, vTexCoord - vec2(texel.x, 0.0))
                                + texture2D(uTexture, vTexCoord + vec2(texel.x, 0.0));
                vec4 vertical = texture2D(uTexture, vTexCoord - vec2(0.0, texel.y))
                              + texture2D(uTexture, vTexCoord + vec2(0.0, texel.y));
                vec4 response = c * 0.74 + horizontal * 0.075 + vertical * 0.055;
                gl_FragColor = clamp(response, vec4(0.0), vec4(1.0));
            }
        """

        private const val SCANLINES_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec2 uTextureSize;
            uniform vec2 uOutputSize;
            uniform float uTime;
            varying vec2 vTexCoord;
            void main() {
                vec4 c = texture2D(uTexture, vTexCoord);
                float row = vTexCoord.y * max(uTextureSize.y, 1.0);
                float line = 0.94 + 0.06 * sin(row * 6.2831853);
                gl_FragColor = vec4(c.rgb * line, c.a);
            }
        """

        private const val CRT_LITE_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec2 uTextureSize;
            uniform vec2 uOutputSize;
            uniform float uTime;
            varying vec2 vTexCoord;
            void main() {
                vec2 p = vTexCoord * 2.0 - 1.0;
                float r2 = dot(p, p);
                p *= 1.0 + r2 * 0.035;
                vec2 uv = p * 0.5 + 0.5;
                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                    return;
                }
                vec4 c = texture2D(uTexture, uv);
                float row = uv.y * max(uOutputSize.y, 1.0);
                float scan = 0.94 + 0.06 * sin(row * 3.14159265);
                float vignette = 1.0 - 0.12 * clamp(r2, 0.0, 1.6);
                gl_FragColor = vec4(c.rgb * scan * vignette, c.a);
            }
        """

        private const val RETRO_WARM_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec2 uTextureSize;
            uniform vec2 uOutputSize;
            uniform float uTime;
            varying vec2 vTexCoord;
            void main() {
                vec4 sampleColor = texture2D(uTexture, vTexCoord);
                vec3 c = sampleColor.rgb;
                vec3 warm;
                warm.r = c.r * 1.05 + c.g * 0.025;
                warm.g = c.g * 0.99 + c.r * 0.010;
                warm.b = c.b * 0.90 + c.g * 0.030;
                float luma = dot(warm, vec3(0.299, 0.587, 0.114));
                warm = mix(vec3(luma), warm, 1.06);
                gl_FragColor = vec4(clamp(warm, vec3(0.0), vec3(1.0)), sampleColor.a);
            }
        """
    }
}
