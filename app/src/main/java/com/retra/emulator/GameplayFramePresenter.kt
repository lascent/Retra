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
    private val postFrameCallbackRunnable = Runnable {
        if (!active) {
            callbackScheduled.set(false)
        } else {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
    private val removeFrameCallbackRunnable = Runnable {
        Choreographer.getInstance().removeFrameCallback(this)
    }

    @Volatile
    private var active = false

    private var presentedGeneration = 0L

    fun start() {
        // Mailbox generation restarts for every ROM/video reconfiguration. Reset the
        // presenter's generation state as well so a new session can never inherit an
        // equal generation number and accidentally suppress its first completed frame.
        pendingGeneration.set(0L)
        presentedGeneration = -1L
        latestVsyncTimeNs.set(0L)
        callbackScheduled.set(false)
        active = true
    }

    fun stop() {
        active = false
        continuousVsync.set(false)
        callbackScheduled.set(false)
        latestVsyncTimeNs.set(0L)
        targetView.post(removeFrameCallbackRunnable)
    }

    fun setContinuousVsync(enabled: Boolean) {
        val changed = continuousVsync.getAndSet(enabled) != enabled
        if (enabled && active && changed) scheduleNextVsync()
        if (!enabled) latestVsyncTimeNs.set(0L)
    }

    /** Last real Choreographer VSync timestamp, readable by the emulation worker. */
    fun latestVsyncNanos(): Long = latestVsyncTimeNs.get()

    /** May be called from the emulator thread. Uses the mailbox generation so
     * repeated/coalesced requests cannot invent presentation work that does not
     * correspond to a completed emulator frame. */
    fun requestPresent(generation: Long) {
        updatePendingGeneration(generation)
        if (!active) return
        scheduleNextVsync()
    }

    /** Compatibility path for one-off callers without a mailbox generation. */
    fun requestPresent() = requestPresent(pendingGeneration.incrementAndGet())

    private fun updatePendingGeneration(generation: Long) {
        while (true) {
            val current = pendingGeneration.get()
            if (generation <= current) return
            if (pendingGeneration.compareAndSet(current, generation)) return
        }
    }

    private fun scheduleNextVsync() {
        if (!callbackScheduled.compareAndSet(false, true)) return
        targetView.post(postFrameCallbackRunnable)
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
