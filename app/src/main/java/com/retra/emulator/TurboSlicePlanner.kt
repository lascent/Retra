package com.retra.emulator

import kotlin.math.floor
import kotlin.math.min

/**
 * Fractional native-batch planner for Speed Mode.
 *
 * A fixed rounded batch (for example always 8 frames at 16x/120 Hz) produces
 * native batches at ~119.45 Hz while Android presents at 120 Hz. That tiny
 * mismatch creates a visible low-frequency beat over time. This planner keeps
 * the same 2x/4x/8x/16x emulated-time targets, but distributes integer batch
 * sizes (for example mostly 8 with an occasional 7) so the producer cadence
 * stays phase-friendly with the selected 60/120 Hz presentation rate.
 *
 * The cumulative TurboThroughputGovernor remains authoritative for wall-clock
 * speed. This class only chooses how many emulated frames belong to each native
 * slice; it never changes the requested multiplier.
 */
internal class TurboSlicePlanner {
    private var framesPerSliceExact = 1.0
    private var phase = 0.5

    fun reset(speed: Double, frameTimeNs: Long, presentationHz: Double) {
        if (speed <= 1.0 || frameTimeNs <= 0L || presentationHz <= 0.0) {
            framesPerSliceExact = 1.0
            phase = 0.0
            return
        }

        val targetFps = FastForwardSpeedContract.targetEmulatedFps(speed, frameTimeNs)
        val usefulSliceHz = min(targetFps, presentationHz.coerceAtLeast(1.0))
        framesPerSliceExact = (targetFps / usefulSliceHz)
            .coerceIn(1.0, MAX_NATIVE_BATCH_FRAMES.toDouble())

        // Half-frame phase starts nearest to the ideal cadence instead of making
        // the first slice systematically short after every Speed Mode toggle.
        phase = 0.5
    }

    fun nextFrames(): Int {
        phase += framesPerSliceExact
        var frames = floor(phase).toInt()
        if (frames < 1) frames = 1
        if (frames > MAX_NATIVE_BATCH_FRAMES) frames = MAX_NATIVE_BATCH_FRAMES
        phase -= frames.toDouble()

        // Bound numerical drift after very long sessions while retaining the
        // fractional phase used to alternate neighboring integer batch sizes.
        phase = phase.coerceIn(-0.999999, 0.999999)
        return frames
    }

    companion object {
        private const val MAX_NATIVE_BATCH_FRAMES = 16
    }
}
