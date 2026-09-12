package com.retra.emulator

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.widget.ImageView

/**
 * Applies Retra's lightweight game-only color presets.
 *
 * The normal ImageView uses Android's GPU-accelerated ColorMatrixColorFilter.
 * ShaderGameView receives the same transform as a tiny post-process uniform so
 * custom GLSL shaders and Color Style can be used together without touching
 * emulator/JNI frame generation.
 */
class ColorStyleController(
    private val prefs: RetraPreferences,
    private val normalView: ImageView,
    private val shaderView: ShaderGameView
) {
    data class Style(
        val id: String,
        val label: String,
        val saturation: Float,
        val contrast: Float,
        val redGain: Float = 1f,
        val greenGain: Float = 1f,
        val blueGain: Float = 1f,
        val redLift: Float = 0f,
        val greenLift: Float = 0f,
        val blueLift: Float = 0f
    )

    fun selectedId(): String = normalize(prefs.getString(PREF_KEY, CLASSIC) ?: CLASSIC)

    fun select(id: String): String {
        val resolved = normalize(id)
        prefs.edit().putString(PREF_KEY, resolved).apply()
        apply()
        return resolved
    }

    fun apply() {
        val style = styleFor(selectedId())
        val matrix = buildColorMatrix(style)

        if (style.id == CLASSIC) {
            normalView.clearColorFilter()
        } else {
            normalView.colorFilter = ColorMatrixColorFilter(matrix)
        }

        shaderView.setColorTransform(matrix.array)
    }

    private fun normalize(id: String): String =
        STYLES.firstOrNull { it.id.equals(id, ignoreCase = true) }?.id ?: CLASSIC

    private fun styleFor(id: String): Style = STYLES.firstOrNull { it.id == id } ?: STYLES.first()

    private fun buildColorMatrix(style: Style): ColorMatrix {
        if (style.id == CLASSIC) return ColorMatrix()

        val saturation = style.saturation.coerceIn(0f, 2f)
        val inverseSat = 1f - saturation
        val r = 0.213f * inverseSat
        val g = 0.715f * inverseSat
        val b = 0.072f * inverseSat

        val saturationMatrix = floatArrayOf(
            r + saturation, g, b, 0f, 0f,
            r, g + saturation, b, 0f, 0f,
            r, g, b + saturation, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )

        val contrast = style.contrast.coerceIn(0.5f, 1.5f)
        val baseLift = 127.5f * (1f - contrast)
        val channel = ColorMatrix(floatArrayOf(
            contrast * style.redGain, 0f, 0f, 0f, baseLift + style.redLift,
            0f, contrast * style.greenGain, 0f, 0f, baseLift + style.greenLift,
            0f, 0f, contrast * style.blueGain, 0f, baseLift + style.blueLift,
            0f, 0f, 0f, 1f, 0f
        ))

        return ColorMatrix(saturationMatrix).apply { postConcat(channel) }
    }

    companion object {
        const val PREF_KEY = "game_color_style_v1"
        const val CLASSIC = "classic"

        val STYLES = listOf(
            Style(
                id = CLASSIC,
                label = "Classic",
                saturation = 1f,
                contrast = 1f
            ),
            Style(
                id = "vivid",
                label = "Vivid",
                saturation = 1.24f,
                contrast = 1.055f,
                redGain = 1.015f,
                greenGain = 1.01f,
                blueGain = 1.015f
            ),
            Style(
                id = "warm",
                label = "Warm",
                saturation = 1.07f,
                contrast = 1.015f,
                redGain = 1.055f,
                greenGain = 1.015f,
                blueGain = 0.915f,
                redLift = 2.5f,
                greenLift = 0.5f,
                blueLift = -1.5f
            ),
            Style(
                id = "muted",
                label = "Muted",
                saturation = 0.72f,
                contrast = 0.94f,
                redGain = 0.99f,
                greenGain = 0.99f,
                blueGain = 0.99f
            )
        )
    }
}
