package com.retra.emulator

import kotlin.math.floor

/**
 * Bresenham-style fractional frame batcher for display-synchronized 8x/16x.
 *
 * Examples at 120 Hz:
 *   8x  -> GBA 59.7275 fps * 8 / 120 = 3.9818 frames/display
 *   16x -> GBA 59.7275 fps * 16 / 120 = 7.9637 frames/display
 * The carried fraction keeps the long-term average exactly at the selected
 * multiplier while producing one fresh completed state per useful display tick.
 */
internal class TurboBatchSequencer(
    private val frameTimeNs: Long
) {
    private var error = 0.0

    fun reset() {
        error = 0.0
    }

    fun nextFrames(speed: Double, refreshRateHz: Float, maxFrames: Int = 8): Int {
        if (speed <= 1.0 || frameTimeNs <= 0L || refreshRateHz <= 0f) return 1
        val baseFps = 1_000_000_000.0 / frameTimeNs.toDouble()
        val exactFrames = (baseFps * speed) / refreshRateHz.toDouble()
        val boundedExact = exactFrames.coerceIn(1.0, maxFrames.coerceAtLeast(1).toDouble())

        // Always floor the cumulative fractional target. That keeps each batch's
        // emulated-time deadline at or just before the upcoming VSync (never after
        // it because of rounding), while the carried fraction preserves the exact
        // long-term 16x average.
        val desired = boundedExact + error
        val frames = floor(desired).toInt().coerceIn(1, maxFrames.coerceAtLeast(1))
        error = (desired - frames.toDouble()).coerceIn(0.0, 1.0)
        return frames
    }
}
