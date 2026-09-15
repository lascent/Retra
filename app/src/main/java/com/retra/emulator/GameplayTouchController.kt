package com.retra.emulator

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.retra.emulator.MainActivity.Companion.KEY_A
import com.retra.emulator.MainActivity.Companion.KEY_B
import com.retra.emulator.MainActivity.Companion.KEY_SELECT
import com.retra.emulator.MainActivity.Companion.KEY_START
import com.retra.emulator.MainActivity.Companion.KEY_RIGHT
import com.retra.emulator.MainActivity.Companion.KEY_LEFT
import com.retra.emulator.MainActivity.Companion.KEY_UP
import com.retra.emulator.MainActivity.Companion.KEY_DOWN
import com.retra.emulator.MainActivity.Companion.KEY_R
import com.retra.emulator.MainActivity.Companion.KEY_L
import com.retra.emulator.MainActivity.Companion.CONTROLLER_HAPTICS_PREF
import com.retra.emulator.MainActivity.Companion.CONTROLLER_SOUND_PREF

/**
 * Low-latency, pointer-owned gameplay touch input.
 *
 * Kept separate from layout/presentation code so multi-touch correctness can be
 * hardened independently without growing GameplayLayoutController.
 */
@Suppress("UNUSED_PARAMETER")
internal fun MainActivity.performControllerHaptic(view: View, directional: Boolean = false) {
    if (prefs.getBoolean(CONTROLLER_HAPTICS_PREF, true)) {
        performUnifiedControllerHaptic(view)
    }
    performControllerSound(view)
}

/**
 * Immediate feedback for a fresh D-pad press.
 *
 * The D-pad and every other on-screen controller now share exactly the same
 * tactile and sound profile. A fresh D-pad touch bypasses the tiny debounce so
 * even a press that begins in the centre dead zone still feels identical to A/B.
 */
internal fun MainActivity.performDpadPressFeedback(view: View) {
    if (prefs.getBoolean(CONTROLLER_HAPTICS_PREF, true)) {
        performUnifiedControllerHaptic(view, force = true)
    }
    performControllerSound(view)
}

/**
 * Shared Retra UI haptic used by WebView menus and native gameplay menus.
 * It intentionally reuses the exact gameplay pulse so taps feel consistent
 * across controller buttons, D-pad transitions, sheets, toggles and menus.
 */
internal fun MainActivity.performUiTapHaptic(view: View, force: Boolean = false) {
    if (!prefs.getBoolean(CONTROLLER_HAPTICS_PREF, true)) return
    performUnifiedControllerHaptic(view, force)
}

/**
 * One unified low-intensity controller haptic profile.
 *
 * D-pad, A/B, L/R, Start/Select, combo/turbo controls, Menu, Quick Save/Load,
 * Screenshot and Speed all use the same 16 ms responsive medium-strong pulse. Keeping one
 * profile avoids the D-pad feeling heavier or lighter than the face buttons.
 */
internal fun MainActivity.performUnifiedControllerHaptic(view: View, force: Boolean = false) {
    val now = SystemClock.uptimeMillis()
    if (!force && now - lastControllerHapticAtMs < 20L) return
    lastControllerHapticAtMs = now

    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    if (vibrator?.hasVibrator() == true) {
        val amplitude = if (vibrator.hasAmplitudeControl()) 150 else VibrationEffect.DEFAULT_AMPLITUDE
        vibrator.vibrate(VibrationEffect.createOneShot(16L, amplitude))
    } else {
        // Fallback for devices that expose touch haptics but not a vibrator service.
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}

/**
 * One shared low-volume click profile for every gameplay control.
 *
 * A small global gap prevents overlapping multi-touch taps from becoming loud,
 * while still letting fast D-pad rolls and repeated A/B presses sound responsive.
 */
internal fun MainActivity.performControllerSound(view: View) {
    if (!prefs.getBoolean(CONTROLLER_SOUND_PREF, true)) return
    if (!view.isSoundEffectsEnabled) return

    val now = SystemClock.uptimeMillis()
    if (now - lastControllerSoundAtMs < 28L) return
    lastControllerSoundAtMs = now

    val audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    audio.playSoundEffect(SoundEffectConstants.CLICK, 0.060f)
}

internal fun MainActivity.bindControls() {
    bindKey(binding.buttonA, KEY_A)
    bindKey(binding.buttonB, KEY_B)
    bindDpad()
    bindKey(binding.buttonL, KEY_L)
    bindKey(binding.buttonR, KEY_R)
    bindKey(binding.buttonStart, KEY_START)
    bindKey(binding.buttonSelect, KEY_SELECT)
}

/**
 * Low-latency continuous D-pad tracking.
 *
 * The D-pad container is the only touch owner. Direction is derived from the
 * owned pointer in container-local coordinates, keeping high-rate MOVE handling
 * allocation-free while preventing A/B fingers from changing D-pad direction.
 */
internal fun MainActivity.bindDpad() {
    // The four arrow views are visual state only. If they remain clickable,
    // Android can assign the first D-pad pointer to a child while a second
    // pointer is assigned to A/B. That creates separate transformed MotionEvent
    // streams and can corrupt the D-pad position during multi-touch.
    val directionViews = arrayOf(
        binding.buttonUp,
        binding.buttonDown,
        binding.buttonLeft,
        binding.buttonRight,
    )
    directionViews.forEach { direction ->
        direction.setOnTouchListener(null)
        direction.isClickable = false
        direction.isLongClickable = false
        direction.isFocusable = false
    }

    // One surface owns the complete D-pad gesture. emulatorViewport already has
    // splitMotionEvents=true, so A/B keep their own pointer streams while this
    // listener tracks only the pointer that originally pressed the D-pad.
    binding.dpadContainer.setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // ACTION_DOWN starts a new gesture. Clear any stale ownership that
                // survived a malformed/interrupted stream before claiming it.
                if (activeDpadPointerId != MotionEvent.INVALID_POINTER_ID || activeDpadMask != 0) {
                    finishDpadGesture()
                }
                activeDpadPointerId = event.getPointerId(event.actionIndex)
                view.parent?.requestDisallowInterceptTouchEvent(true)
                // Give every fresh D-pad touch immediate feedback. The direction
                // update may land in the centre dead zone, so it must not be the
                // only trigger for vibration/sound.
                performDpadPressFeedback(view)
                updateDpadFromMotionEvent(event)
                true
            }

            MotionEvent.ACTION_MOVE -> {
                updateDpadFromMotionEvent(event)
                true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // Never transfer movement to another finger implicitly. If the
                // controlling pointer leaves, release immediately so no input
                // can remain latched.
                if (event.getPointerId(event.actionIndex) == activeDpadPointerId) {
                    finishDpadGesture(view)
                }
                true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_OUTSIDE -> {
                finishDpadGesture(view)
                true
            }

            // A second finger inside the D-pad never steals ownership. Fingers
            // on A/B are split by emulatorViewport and never enter this stream.
            MotionEvent.ACTION_POINTER_DOWN -> true
            else -> true
        }
    }
}

internal fun MainActivity.finishDpadGesture(view: View? = null) {
    activeDpadPointerId = MotionEvent.INVALID_POINTER_ID
    setActiveDpadMask(0)
    view?.parent?.requestDisallowInterceptTouchEvent(false)
}

internal fun MainActivity.updateDpadFromMotionEvent(event: MotionEvent) {
    if (activeDpadPointerId == MotionEvent.INVALID_POINTER_ID) return
    val pointerIndex = event.findPointerIndex(activeDpadPointerId)
    if (pointerIndex < 0) {
        // Defensive release for interrupted/malformed pointer streams.
        finishDpadGesture()
        return
    }

    // Because dpadContainer is now the sole touch target, getX/getY are already
    // in its local coordinate space. Android applies the inverse View transform
    // for scaled/moved controls, so no screen-coordinate reconstruction is
    // needed and another pointer cannot change the origin used by the D-pad.
    updateDpadFromLocalPoint(
        event.getX(pointerIndex),
        event.getY(pointerIndex),
    )
}

internal fun MainActivity.updateDpadFromLocalPoint(localX: Float, localY: Float) {
    val pad = binding.dpadContainer
    if (pad.width <= 0 || pad.height <= 0) {
        setActiveDpadMask(0)
        return
    }

    val centerX = pad.width / 2f
    val centerY = pad.height / 2f
    val radius = (minOf(pad.width, pad.height) / 2f).coerceAtLeast(1f)
    val nx = (localX - centerX) / radius
    val ny = (localY - centerY) / radius
    val absX = kotlin.math.abs(nx)
    val absY = kotlin.math.abs(ny)
    val radialDistance = kotlin.math.hypot(nx.toDouble(), ny.toDouble()).toFloat()

    // Centre dead-zone hysteresis: entering a direction needs 15% radius,
    // while an already-held direction releases only inside 10%. This removes
    // centre jitter without making direction changes feel sticky.
    val deadZone = if (activeDpadMask == 0) 0.15f else 0.10f
    if (radialDistance < deadZone) {
        setActiveDpadMask(0)
        return
    }

    // Diagonal hysteresis: enter at 0.44, remain diagonal down to 0.35.
    // Tiny thumb jitter can no longer flap RIGHT <-> UP+RIGHT every frame.
    val maxAxis = maxOf(absX, absY).coerceAtLeast(0.0001f)
    val axisRatio = minOf(absX, absY) / maxAxis
    val wasDiagonal = activeDpadMask != 0 &&
        (activeDpadMask and (activeDpadMask - 1)) != 0
    val diagonalThreshold = if (wasDiagonal) 0.35f else 0.44f
    val diagonal = axisRatio >= diagonalThreshold

    var nextMask = 0
    if (diagonal || absX > absY) {
        nextMask = nextMask or (1 shl (if (nx < 0f) KEY_LEFT else KEY_RIGHT))
    }
    if (diagonal || absY >= absX) {
        nextMask = nextMask or (1 shl (if (ny < 0f) KEY_UP else KEY_DOWN))
    }

    setActiveDpadMask(nextMask)
}

internal fun MainActivity.setActiveDpadMask(nextMask: Int) {
    if (activeDpadMask == nextMask) return

    // Only changed directions cross JNI or touch View state. Duplicate MOVE
    // events for the same direction therefore do essentially no work.
    fun syncDirection(key: Int, view: View) {
        val bit = 1 shl key
        val wasPressed = activeDpadMask and bit != 0
        val isPressed = nextMask and bit != 0
        if (wasPressed == isPressed) return
        setGameplayKeyHeld(key, isPressed)
        view.isPressed = isPressed
    }

    syncDirection(KEY_UP, binding.buttonUp)
    syncDirection(KEY_DOWN, binding.buttonDown)
    syncDirection(KEY_LEFT, binding.buttonLeft)
    syncDirection(KEY_RIGHT, binding.buttonRight)
    if (nextMask != 0) performControllerHaptic(binding.dpadContainer, directional = true)
    activeDpadMask = nextMask
}

/** Low-latency pointer-owned binding for A/B, L/R, Start and Select. */
internal fun MainActivity.bindKey(view: View, key: Int) {
    var activePointerId = MotionEvent.INVALID_POINTER_ID
    var gestureGeneration = -1L
    var pressed = false
    val retentionSlopPx = maxOf(ViewConfiguration.get(view.context).scaledTouchSlop.toFloat(), dp(10f))

    fun applyPressedState(v: View, nextPressed: Boolean) {
        if (pressed == nextPressed) return
        pressed = nextPressed
        setGameplayKeyHeld(key, nextPressed) // input first; haptic/visual feedback second
        if (nextPressed) performControllerHaptic(v)
        v.isPressed = nextPressed
    }

    fun abandonGesture(v: View) {
        activePointerId = MotionEvent.INVALID_POINTER_ID
        gestureGeneration = -1L
        pressed = false
        v.isPressed = false
        v.parent?.requestDisallowInterceptTouchEvent(false)
    }

    fun finishGesture(v: View) {
        if (gestureGeneration == controllerInputGeneration) {
            applyPressedState(v, false)
        } else {
            // releaseAllKeys() already cleared this old generation. Never
            // decrement a hold that may belong to a newer touch source.
            pressed = false
            v.isPressed = false
        }
        activePointerId = MotionEvent.INVALID_POINTER_ID
        gestureGeneration = -1L
        v.parent?.requestDisallowInterceptTouchEvent(false)
    }

    view.setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (activePointerId != MotionEvent.INVALID_POINTER_ID || pressed) {
                    if (gestureGeneration == controllerInputGeneration) finishGesture(v) else abandonGesture(v)
                }
                activePointerId = event.getPointerId(event.actionIndex)
                gestureGeneration = controllerInputGeneration
                v.parent?.requestDisallowInterceptTouchEvent(true)
                applyPressedState(v, true)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureGeneration != controllerInputGeneration) {
                    abandonGesture(v)
                    true
                } else {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex < 0) finishGesture(v) else {
                        val slop = if (pressed) retentionSlopPx else 0f
                        val x = event.getX(pointerIndex)
                        val y = event.getY(pointerIndex)
                        applyPressedState(v, x >= -slop && x <= v.width + slop && y >= -slop && y <= v.height + slop)
                    }
                    true
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) finishGesture(v)
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> { finishGesture(v); true }
            MotionEvent.ACTION_POINTER_DOWN -> true // never transfer ownership implicitly
            else -> true
        }
    }
}
