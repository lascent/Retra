package com.retra.emulator

import android.os.Process

/**
 * Work scheduler for fast-forward emulation.
 *
 * Game-speed throughput and display presentation are deliberately independent:
 * mGBA advances at the selected multiplier, while Android only receives the newest
 * useful snapshots at panel cadence.
 */
internal object TurboFramePolicy {
    private const val MIN_PRESENTATION_HZ = 60f
    private const val MAX_PRESENTATION_HZ = 165f
    private const val MAX_TURBO_BATCH_FRAMES = 16 // defensive native ceiling; not used to reduce visual cadence
    private const val MAX_DISPLAY_SYNC_BATCH_FRAMES = 8

    fun framesPerSlice(speed: Double, frameTimeNs: Long): Int {
        if (speed <= 1.0 || frameTimeNs <= 0L) return 1
        // Throughput-first baseline: 2x=1, 4x=2, 8x=4, 16x=8.
        return ((speed / 2.0) + 0.5).toInt().coerceIn(1, MAX_DISPLAY_SYNC_BATCH_FRAMES)
    }


    /**
     * Never trade visible cadence for JNI efficiency. Older builds doubled the
     * native batch when throughput dipped (4->8 at 8x, 8->16 at 16x), which cut
     * fresh visual states to roughly 60/s. Keep the normal short batch even while
     * quality fallbacks are active; renderer/audio work is shed separately.
     */
    fun smoothFramesPerSlice(speed: Double, frameTimeNs: Long): Int =
        framesPerSlice(speed, frameTimeNs)

    fun sliceCadenceNs(speed: Double, frameTimeNs: Long, framesPerSlice: Int): Long {
        if (speed <= 0.0 || frameTimeNs <= 0L) return frameTimeNs.coerceAtLeast(1L)
        return FastForwardSpeedContract.targetElapsedNs(
            frameCount = framesPerSlice.coerceAtLeast(1).toLong(),
            speed = speed,
            frameTimeNs = frameTimeNs
        ).coerceAtLeast(1L)
    }

    @Suppress("UNUSED_PARAMETER")
    fun presentationIntervalNs(refreshRateHz: Float, speed: Double): Long {
        val panelHz = refreshRateHz.coerceIn(MIN_PRESENTATION_HZ, MAX_PRESENTATION_HZ)
        return (1_000_000_000.0 / panelHz.toDouble()).toLong().coerceAtLeast(1L)
    }

    /**
     * Fixed 4x/8x/16x short batches naturally land near ~119.455 native batches/s.
     * On 120/144/165 Hz panels that can create a slow beat or leave refreshes without
     * a fresh emulator state. When one display interval represents 1..8 GBA frames,
     * a fractional batch sequence aligns one native batch to one display tick while
     * the cumulative governor keeps the exact selected game-speed multiplier.
     */
    fun canSynchronizeExtremeTurboBatchToDisplay(
        speed: Double,
        frameTimeNs: Long,
        refreshRateHz: Float
    ): Boolean {
        if (speed < 4.0 || frameTimeNs <= 0L || refreshRateHz < MIN_PRESENTATION_HZ) {
            return false
        }
        val baseFps = 1_000_000_000.0 / frameTimeNs.toDouble()
        val framesPerDisplay = (baseFps * speed) / refreshRateHz.toDouble()
        return framesPerDisplay in 1.0..MAX_DISPLAY_SYNC_BATCH_FRAMES.toDouble()
    }

    fun processThreadPriority(speed: Double, profile: String?): Int = when {
        // Keep RenderThread above the emulator worker so 16x cannot starve VSync.
        speed >= 8.0 -> Process.THREAD_PRIORITY_DISPLAY
        speed >= 4.0 -> Process.THREAD_PRIORITY_MORE_FAVORABLE
        profile.equals("Performance", ignoreCase = true) -> Process.THREAD_PRIORITY_URGENT_DISPLAY
        profile.equals("Compatibility", ignoreCase = true) -> Process.THREAD_PRIORITY_DEFAULT
        else -> Process.THREAD_PRIORITY_DISPLAY
    }

    /**
     * Protect the selected multiplier immediately by avoiding renderer work the
     * Android display cannot show. This never skips emulated CPU/game frames.
     */
    fun speedProtectingCoreFrameSkip(
        speed: Double,
        userFrameSkip: Int,
        frameTimeNs: Long,
        presentationHz: Float
    ): Int = FastForwardSpeedContract.rendererFrameSkip(
        speed = speed,
        userFrameSkip = userFrameSkip,
        frameTimeNs = frameTimeNs,
        presentationHz = presentationHz
    )

    /** Compatibility helper for existing callers/tests. */
    fun coreFrameSkip(speed: Double, userFrameSkip: Int): Int =
        speedProtectingCoreFrameSkip(speed, userFrameSkip, 16_742_706L, 120f)

    /** Legacy hard fallback retained for compatibility/tests. */
    fun constrainedCoreFrameSkip(speed: Double, userFrameSkip: Int): Int {
        val fallback = when {
            speed >= 16.0 -> 7
            speed >= 8.0 -> 3
            else -> 0
        }
        return maxOf(userFrameSkip.coerceIn(0, 10), fallback)
    }

    /**
     * Smoothness-preserving adaptive fallback. Mild throughput misses keep full
     * renderer density; only sustained, severe misses add a small amount of
     * renderer-only skipping. The selected emulation multiplier is untouched.
     */
    fun adaptiveCoreFrameSkip(
        speed: Double,
        userFrameSkip: Int,
        utilization: Double,
        frameTimeNs: Long = 16_742_706L,
        presentationHz: Float = 120f
    ): Int {
        val baseline = speedProtectingCoreFrameSkip(
            speed = speed,
            userFrameSkip = userFrameSkip,
            frameTimeNs = frameTimeNs,
            presentationHz = presentationHz
        )
        // If a ROM is still constrained after the normal presentation budget,
        // remove a little more renderer work before sacrificing turbo audio.
        val extra = when {
            speed >= 16.0 && utilization < 0.72 -> 3
            speed >= 16.0 && utilization < 0.82 -> 2
            speed >= 16.0 -> 1
            speed >= 8.0 && utilization < 0.72 -> 2
            speed >= 8.0 && utilization < 0.82 -> 1
            speed >= 8.0 && utilization < 0.97 -> 1
            speed >= 4.0 && utilization < 0.70 -> 1
            speed >= 4.0 && utilization < 0.97 -> 1
            speed > 1.0 && utilization < 0.97 -> 1
            else -> 0
        }
        return maxOf(baseline, userFrameSkip.coerceIn(0, 10))
            .plus(extra)
            .coerceIn(0, 10)
    }

    /** All fast-forward multipliers use the cumulative governor for ROM-independent timing. */
    fun precisionWindowNs(speed: Double, normalWindowNs: Long): Long = when {
        speed >= 4.0 -> 0L
        speed > 1.0 -> normalWindowNs.coerceAtMost(50_000L)
        else -> normalWindowNs
    }
}
