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

    fun reset() {
        nextDeadlineNanos = nanoTime()
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

        val precisionWindow = precisionWindowNanos.coerceIn(0L, MAX_PRECISION_WINDOW_NS)
        if (remaining > precisionWindow) {
            LockSupport.parkNanos(remaining - precisionWindow)
        }

        if (precisionWindow > 0L) {
            while (true) {
                remaining = nextDeadlineNanos - nanoTime()
                if (remaining <= 0L) break
                // Intentionally busy-wait only inside the tiny bounded
                // precision window; nanoTime() prevents the loop from being
                // optimized away and keeps compatibility with older Android.
            }
        }

        val now = nanoTime()
        if (now - nextDeadlineNanos > frameDurationNanos * MAX_LATE_FRAMES) {
            nextDeadlineNanos = now
        }
    }

    companion object {
        private const val MAX_LATE_FRAMES = 4L
        private const val MAX_PRECISION_WINDOW_NS = 300_000L

        fun precisionWindowForProfile(profile: String?): Long = when {
            profile.equals("Performance", ignoreCase = true) -> 250_000L
            profile.equals("Compatibility", ignoreCase = true) -> 0L
            else -> 120_000L
        }
    }
}
