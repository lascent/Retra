package com.retra.emulator

import java.util.Locale
import kotlin.math.abs

/** Shared supported-speed policy for normal, slow-motion and turbo modes. */
object EmulationSpeedPolicy {
    private val choices = doubleArrayOf(0.2, 0.5, 1.0, 2.0, 4.0, 8.0, 16.0)

    fun sanitize(value: Double): Double = choices.minByOrNull { abs(it - value) } ?: 1.0
    fun isNormal(value: Double): Boolean = abs(value - 1.0) < 0.0001

    fun format(value: Double): String {
        val normalized = sanitize(value)
        return if (abs(normalized - normalized.toInt()) < 0.0001) {
            "${normalized.toInt()}×"
        } else {
            "${String.format(Locale.US, "%.1f", normalized)}×"
        }
    }

    fun token(value: Double): String = format(value).replace("×", "x")

    fun loadAndMigrate(
        prefs: RetraPreferences,
        currentKey: String,
        legacyIntKey: String,
        defaultLegacy: Int = 4
    ): Double {
        prefs.getString(currentKey, null)?.toDoubleOrNull()?.let { return sanitize(it) }
        val migrated = sanitize(prefs.getInt(legacyIntKey, defaultLegacy).coerceIn(2, 16).toDouble())
        prefs.edit().putString(currentKey, migrated.toString()).apply()
        return migrated
    }
}
