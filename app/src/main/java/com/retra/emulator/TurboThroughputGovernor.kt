package com.retra.emulator

import java.util.concurrent.locks.LockSupport

/**
 * Throughput-first cumulative clock for every fast-forward multiplier.
 *
 * Fast-forward runs a native batch first, then waits only when that batch
 * finished ahead of the selected multiplier. Deadlines are cumulative from one
 * epoch: if Android oversleeps, the next batch automatically gets a shorter wait
 * (or no wait at all) instead of permanently lowering 16x throughput.
 *
 * If the CPU cannot sustain the requested multiplier, remaining time is always
 * <= 0 and the core runs continuously at the hardware's maximum speed.
 */
internal class TurboThroughputGovernor(
    private val frameTimeNs: Long,
    private val nanoTime: () -> Long = System::nanoTime
) {
    private var epochNs = 0L
    private var completedFrames = 0L

    fun reset(epochTimeNs: Long = nanoTime()) {
        epochNs = epochTimeNs
        completedFrames = 0L
    }

    fun onBatchComplete(speed: Double, frameCount: Int) {
        if (speed <= 1.0 || frameTimeNs <= 0L) return
        if (epochNs == 0L) reset()

        val batchFrames = frameCount.coerceAtLeast(1)
        completedFrames += batchFrames.toLong()
        val targetNs = epochNs + FastForwardSpeedContract.targetElapsedNs(
            frameCount = completedFrames,
            speed = speed,
            frameTimeNs = frameTimeNs
        )
        val now = nanoTime()
        var remaining = targetNs - now

        // Do not repay a large Android/GC/background hitch with a burst of
        // back-to-back turbo batches. Normal small lateness still self-corrects
        // cumulatively, preserving the exact selected multiplier in steady state.
        val batchTargetNs = FastForwardSpeedContract.targetElapsedNs(
            frameCount = batchFrames.toLong(),
            speed = speed,
            frameTimeNs = frameTimeNs
        )
        val lateByNs = -remaining
        val recoveryThresholdNs = maxOf(MIN_HITCH_RECOVERY_NS, batchTargetNs * MAX_LATE_BATCHES)
        if (lateByNs > recoveryThresholdNs) {
            epochNs = now
            completedFrames = 0L
            return
        }

        if (remaining <= 0L) return

        // Park for the inexpensive bulk of the wait. The small final spin avoids
        // Android's sub-millisecond scheduler oversleep, which is large enough to
        // make any selected fast-forward mode measurably slower than its requested multiplier.
        if (remaining > SPIN_WINDOW_NS) {
            LockSupport.parkNanos(remaining - SPIN_WINDOW_NS)
        }

        while (true) {
            remaining = targetNs - nanoTime()
            if (remaining <= 0L) break
        }
    }

    companion object {
        private const val SPIN_WINDOW_NS = 200_000L
        private const val MIN_HITCH_RECOVERY_NS = 24_000_000L
        private const val MAX_LATE_BATCHES = 4L
    }
}
