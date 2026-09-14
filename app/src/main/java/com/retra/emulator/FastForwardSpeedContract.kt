package com.retra.emulator

import kotlin.math.floor

/**
 * Mathematical fast-forward contract shared by all GBA ROMs.
 *
 * The selected multiplier controls emulated GBA time only. Presentation work is
 * budgeted separately so a heavier ROM does not spend most of its 8x/16x budget
 * rendering internal frames that the Android display can never show.
 */
internal object FastForwardSpeedContract {
    private const val NANOS_PER_SECOND = 1_000_000_000.0
    private const val MIN_PRESENTATION_HZ = 60.0
    private const val MAX_UNIQUE_PRESENTATION_HZ = 165.0
    private const val MAX_TURBO_FRAME_SKIP = 10

    fun baseFps(frameTimeNs: Long): Double {
        if (frameTimeNs <= 0L) return 0.0
        return NANOS_PER_SECOND / frameTimeNs.toDouble()
    }

    fun targetEmulatedFps(speed: Double, frameTimeNs: Long): Double =
        baseFps(frameTimeNs) * speed.coerceAtLeast(0.0)

    fun targetElapsedNs(frameCount: Long, speed: Double, frameTimeNs: Long): Long {
        if (frameCount <= 0L || speed <= 0.0 || frameTimeNs <= 0L) return 0L
        return ((frameCount.toDouble() * frameTimeNs.toDouble()) / speed)
            .toLong()
            .coerceAtLeast(1L)
    }

    /**
     * mGBA frameskip removes only renderer work; CPU, timers, DMA, input and game
     * logic still advance for every emulated frame. Budget unique renderer work to
     * the actual presentation path (up to 165 Hz). Use a smoother-first floor
     * interval so a high-refresh display receives a fresh state whenever practical;
     * measured throughput fallback may add skipping if a ROM/device cannot sustain it.
     */
    fun rendererFrameSkip(
        speed: Double,
        userFrameSkip: Int,
        frameTimeNs: Long,
        presentationHz: Float
    ): Int {
        val user = userFrameSkip.coerceIn(0, MAX_TURBO_FRAME_SKIP)
        if (speed <= 1.0 || frameTimeNs <= 0L) return user

        val targetFps = targetEmulatedFps(speed, frameTimeNs)
        val renderBudgetHz = presentationHz.toDouble()
            .coerceAtLeast(MIN_PRESENTATION_HZ)
            .coerceAtMost(MAX_UNIQUE_PRESENTATION_HZ)
        // floor(), rather than round(), intentionally favors a fresh rendered state
        // at the next presentation tick. If this costs too much on a heavy ROM,
        // TurboPerformanceMonitor raises frameskip without altering emulated time.
        val renderEveryNFrames = floor(targetFps / renderBudgetHz)
            .toInt()
            .coerceAtLeast(1)
        val speedProtectingSkip = (renderEveryNFrames - 1)
            .coerceIn(0, MAX_TURBO_FRAME_SKIP)
        return maxOf(user, speedProtectingSkip)
    }
}
