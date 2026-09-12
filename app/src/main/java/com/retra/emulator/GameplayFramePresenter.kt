package com.retra.emulator

import android.view.Choreographer
import android.view.View
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Presents the newest completed emulator frame on the display's next VSync.
 *
 * The emulator core remains at its native cadence (~59.73 FPS for GBA). On
 * 90/120 Hz panels this shortens the wait between a completed emulator frame
 * and the next display refresh without fabricating frames or changing game
 * speed. A callback is scheduled only when a new emulator frame exists, so
 * low-end/60 Hz devices do not pay for a continuous high-frequency render loop.
 */
class GameplayFramePresenter(
    private val targetView: View,
    private val presentLatestFrame: () -> Unit
) : Choreographer.FrameCallback {
    private val pendingGeneration = AtomicLong(0L)
    private val callbackScheduled = AtomicBoolean(false)

    @Volatile
    private var active = false

    private var presentedGeneration = 0L

    fun start() {
        active = true
    }

    fun stop() {
        active = false
        callbackScheduled.set(false)
        targetView.post {
            Choreographer.getInstance().removeFrameCallback(this)
        }
    }

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

    override fun doFrame(frameTimeNanos: Long) {
        callbackScheduled.set(false)
        if (!active) return

        val generation = pendingGeneration.get()
        if (generation != presentedGeneration) {
            presentLatestFrame()
            presentedGeneration = generation
        }

        // If the emulator finished another frame while we were presenting,
        // schedule exactly one more VSync rather than spinning continuously.
        if (active && pendingGeneration.get() != presentedGeneration) {
            scheduleNextVsync()
        }
    }
}
