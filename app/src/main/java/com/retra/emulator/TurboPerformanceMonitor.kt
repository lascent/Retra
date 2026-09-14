package com.retra.emulator

/** Measures achieved core throughput instead of trusting the selected speed label. */
internal class TurboPerformanceMonitor(
    private val frameTimeNs: Long,
    private val nanoTime: () -> Long = System::nanoTime
) {
    data class Sample(
        val utilization: Double,
        val achievedMultiplier: Double,
        val constrained: Boolean,
        val recovered: Boolean
    )

    private var startNs = 0L
    private var frames = 0L
    private var lowWindows = 0
    private var healthyWindows = 0
    private var constrained = false

    fun reset() {
        startNs = nanoTime()
        frames = 0L
        lowWindows = 0
        healthyWindows = 0
        constrained = false
    }

    fun onFramesCompleted(speed: Double, count: Int): Sample? {
        if (startNs == 0L) reset()
        frames += count.coerceAtLeast(1).toLong()
        val now = nanoTime()
        val elapsed = now - startNs
        if (elapsed < SAMPLE_WINDOW_NS) return null

        val baseFps = 1_000_000_000.0 / frameTimeNs.coerceAtLeast(1L).toDouble()
        val actualFps = frames.toDouble() * 1_000_000_000.0 / elapsed.coerceAtLeast(1L).toDouble()
        val requested = speed.coerceAtLeast(1.0)
        val utilization = (actualFps / baseFps / requested).coerceAtLeast(0.0)
        var recovered = false

        val achievedMultiplier = actualFps / baseFps
        if (speed > 1.0) {
            when {
                utilization < 0.97 -> {
                    lowWindows++
                    healthyWindows = 0
                    // Two consecutive 250 ms windows below 97% means the selected
                    // multiplier is materially under target. Protect speed quickly
                    // instead of allowing a heavy ROM to sit around 7.x while 8x is selected.
                    if (lowWindows >= 2) constrained = true
                }
                constrained && utilization >= 0.995 -> {
                    lowWindows = 0
                    healthyWindows++
                    if (healthyWindows >= 2) {
                        constrained = false
                        healthyWindows = 0
                        recovered = true
                    }
                }
                else -> {
                    lowWindows = 0
                    healthyWindows = 0
                }
            }
        } else {
            lowWindows = 0
            healthyWindows = 0
            constrained = false
        }

        startNs = now
        frames = 0L
        return Sample(utilization, achievedMultiplier, constrained, recovered)
    }

    companion object {
        private const val SAMPLE_WINDOW_NS = 250_000_000L
    }
}
