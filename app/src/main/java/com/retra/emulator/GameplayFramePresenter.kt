package com.retra.emulator

import android.view.Choreographer
import android.view.View
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Presents the newest completed emulator frame on Android VSync.
 *
 * The emulation worker may run hundreds of GBA frames per second. The presenter
 * deliberately samples only the latest completed snapshot at panel cadence so
 * display work never throttles the selected game-speed multiplier.
 */
class GameplayFramePresenter(
    private val targetView: View,
    private val presentLatestFrame: () -> Unit
) : Choreographer.FrameCallback {
    private val pendingGeneration = AtomicLong(0L)
    private val callbackScheduled = AtomicBoolean(false)
    private val continuousVsync = AtomicBoolean(false)
    private val latestVsyncTimeNs = AtomicLong(0L)

    @Volatile
    private var active = false

    private var presentedGeneration = 0L

    fun start() {
        active = true
    }

    fun stop() {
        active = false
        continuousVsync.set(false)
        callbackScheduled.set(false)
        latestVsyncTimeNs.set(0L)
        targetView.post {
            Choreographer.getInstance().removeFrameCallback(this)
        }
    }

    fun setContinuousVsync(enabled: Boolean) {
        val changed = continuousVsync.getAndSet(enabled) != enabled
        if (enabled && active && changed) scheduleNextVsync()
        if (!enabled) latestVsyncTimeNs.set(0L)
    }

    /** Last real Choreographer VSync timestamp, readable by the emulation worker. */
    fun latestVsyncNanos(): Long = latestVsyncTimeNs.get()

    /** May be called from the emulator thread. */
    fun requestPresent() {
        pendingGeneration.incrementAndGet()
        if (!active) return
        scheduleNextVsync()
    }

    private fun scheduleNextVsync() {
        if (!callbackScheduled.compareAndSet(false, true)) return
        targetView.post {
            if (!active) {
                callbackScheduled.set(false)
                return@post
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /** Called only from Choreographer's UI-thread callback; avoids another View.post. */
    private fun scheduleNextVsyncDirect() {
        if (!callbackScheduled.compareAndSet(false, true)) return
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        callbackScheduled.set(false)
        if (!active) return
        latestVsyncTimeNs.set(frameTimeNanos)

        val generation = pendingGeneration.get()
        if (generation != presentedGeneration) {
            presentLatestFrame()
            presentedGeneration = generation
        }

        if (active && (continuousVsync.get() || pendingGeneration.get() != presentedGeneration)) {
            scheduleNextVsyncDirect()
        }
    }
}
