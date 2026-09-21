package com.retra.emulator

import java.util.concurrent.locks.LockSupport

/**
 * Absolute-deadline frame pacer for the native mGBA loop.
 *
 * Android's scheduler can oversleep short park/sleep requests by a noticeable
 * fraction of a 16.7 ms GBA frame. Retra therefore parks for the bulk of the
 * wait and, on faster CPU profiles, uses only a very small final spin window.
 * This keeps native-speed emulation stable without turning 120 Hz display
 * presentation into 120 Hz emulation.
 */
internal class EmulationFramePacer(
    private val nanoTime: () -> Long = System::nanoTime
) {
    private var nextDeadlineNanos = 0L
    private var schedulerOversleepEstimateNanos = 0L

    fun reset() {
        nextDeadlineNanos = nanoTime()
        schedulerOversleepEstimateNanos = 0L
    }

    fun waitForNext(frameDurationNanos: Long, precisionWindowNanos: Long) {
        if (frameDurationNanos <= 0L) return
        if (nextDeadlineNanos == 0L) reset()

        nextDeadlineNanos += frameDurationNanos
        var remaining = nextDeadlineNanos - nanoTime()

        // If Android paused/descheduled Retra for several frames, do not try to
        // "catch up" in a burst. Re-anchor timing and resume normal cadence.
        if (remaining < -(frameDurationNanos * MAX_LATE_FRAMES)) {
            nextDeadlineNanos = nanoTime()
            return
        }

        val requestedPrecisionWindow = precisionWindowNanos.coerceIn(0L, MAX_PRECISION_WINDOW_NS)
        // Learn the scheduler's recent park overshoot and wake slightly earlier on
        // devices that consistently resume late. Compatibility mode keeps a zero
        // precision window, so it never enables a spin that the user did not ask for.
        val adaptivePrecisionWindow = if (requestedPrecisionWindow > 0L) {
            (requestedPrecisionWindow + schedulerOversleepEstimateNanos)
                .coerceAtMost(MAX_PRECISION_WINDOW_NS)
        } else {
            0L
        }

        if (remaining > adaptivePrecisionWindow) {
            val parkNanos = remaining - adaptivePrecisionWindow
            val parkStarted = nanoTime()
            LockSupport.parkNanos(parkNanos)
            val parkFinished = nanoTime()
            val oversleep = (parkFinished - parkStarted - parkNanos)
                .coerceIn(0L, MAX_PRECISION_WINDOW_NS)
            // Cheap EWMA: react quickly to a noisy Android scheduler, then decay
            // naturally when wake-ups become accurate again.
            schedulerOversleepEstimateNanos =
                (schedulerOversleepEstimateNanos * 3L + oversleep) / 4L
        }

        if (adaptivePrecisionWindow > 0L) {
            while (true) {
                remaining = nextDeadlineNanos - nanoTime()
                if (remaining <= 0L) break
                // Intentionally busy-wait only inside the tiny bounded
                // precision window; nanoTime() prevents the loop from being
                // optimized away and keeps compatibility with older Android.
            }
        }

        val now = nanoTime()
        val lateness = now - nextDeadlineNanos
        // A sporadic GC/audio/OS hitch should not be repaid with a burst of
        // back-to-back game frames. Re-anchor after a substantial single-frame
        // miss, preserving visual cadence while tiny misses still self-correct.
        val smoothRecoveryThreshold =
            (frameDurationNanos * SMOOTH_RECOVERY_NUMERATOR) / SMOOTH_RECOVERY_DENOMINATOR
        if (lateness > smoothRecoveryThreshold ||
            lateness > frameDurationNanos * MAX_LATE_FRAMES) {
            nextDeadlineNanos = now
        }
    }

    companion object {
        private const val MAX_LATE_FRAMES = 4L
        private const val SMOOTH_RECOVERY_NUMERATOR = 3L
        private const val SMOOTH_RECOVERY_DENOMINATOR = 4L
        private const val MAX_PRECISION_WINDOW_NS = 300_000L

        fun precisionWindowForProfile(profile: String?): Long = when {
            profile.equals("Performance", ignoreCase = true) -> 250_000L
            profile.equals("Compatibility", ignoreCase = true) -> 0L
            else -> 120_000L
        }
    }
}
