package com.retra.emulator

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Owns all on-screen controller feedback.
 *
 * Input controllers report a press and move on immediately; this class applies
 * optional sound + haptics afterward. Keeping feedback outside touch handling
 * prevents AudioManager/haptic policy from leaking into pointer ownership and
 * lets rapid multi-touch be rate-limited consistently in one place.
 */
internal class ControllerFeedbackManager(
    context: Context,
    private val soundEnabled: () -> Boolean,
    private val hapticsEnabled: () -> Boolean
) {
    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var lastSoundAtMs = 0L
    private var lastHapticAtMs = 0L

    /**
     * One light, consistent profile for D-pad and every controller button.
     * The OS-tuned haptic is intentionally used instead of raw vibrator pulses
     * so amplitude/duration remain appropriate for each phone's haptic motor.
     */
    fun perform(view: View) {
        val now = SystemClock.uptimeMillis()

        if (hapticsEnabled() && now - lastHapticAtMs >= HAPTIC_GAP_MS) {
            lastHapticAtMs = now
            val feedback = if (Build.VERSION.SDK_INT >= 34) {
                HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
            // Do not ignore Android's global haptic setting. This keeps Retra
            // respectful of users who disable touch feedback system-wide.
            runCatching { view.performHapticFeedback(feedback) }
        }

        if (soundEnabled() && view.isSoundEffectsEnabled && now - lastSoundAtMs >= SOUND_GAP_MS) {
            lastSoundAtMs = now
            runCatching {
                audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK, CLICK_VOLUME)
            }
        }
    }

    companion object {
        private const val HAPTIC_GAP_MS = 26L
        private const val SOUND_GAP_MS = 28L
        private const val CLICK_VOLUME = 0.055f
    }
}
