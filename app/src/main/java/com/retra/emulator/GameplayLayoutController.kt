package com.retra.emulator

import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONObject
import kotlin.math.roundToInt
import com.retra.emulator.MainActivity.Companion.CONFIRM_CLOSE_RESET_PREF
import com.retra.emulator.MainActivity.Companion.FULLSCREEN_PREF
import com.retra.emulator.MainActivity.Companion.FAST_FORWARD_BUTTON_MODE_PREF
import com.retra.emulator.MainActivity.Companion.IMMERSIVE_PREF
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
import com.retra.emulator.MainActivity.Companion.LANDSCAPE_100_MIGRATION_PREF
import com.retra.emulator.MainActivity.Companion.ORIENTATION_PREF
import com.retra.emulator.MainActivity.Companion.PLATFORM_GBA

/**
 * On-screen controller binding, orientation, presentation and per-ROM layout
 * application extracted from MainActivity. Layout persistence stays in
 * GameplayLayoutRepository; this module only coordinates Android views.
 */
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
 * Continuous D-pad tracking. A normal per-button touch listener keeps the
 * original button as the touch target, which makes sliding from Up to Right
 * feel sticky. Instead every part of the D-pad forwards its MotionEvents here
 * and we hit-test against the whole pad on every MOVE. This lets the thumb
 * roll between cardinal and diagonal directions without lifting.
 */
internal fun MainActivity.bindDpad() {
    val handler = View.OnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                updateDpadFromRawPoint(event.rawX, event.rawY)
                true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                setActiveDpadKeys(emptySet())
                true
            }

            else -> true
        }
    }

    // The container covers the gaps/centre, while assigning the same handler
    // to each visible direction keeps movement tracking alive after a child
    // receives ACTION_DOWN and the thumb slides outside that child.
    binding.dpadContainer.setOnTouchListener(handler)
    binding.buttonUp.setOnTouchListener(handler)
    binding.buttonDown.setOnTouchListener(handler)
    binding.buttonLeft.setOnTouchListener(handler)
    binding.buttonRight.setOnTouchListener(handler)
}

internal fun MainActivity.updateDpadFromRawPoint(rawX: Float, rawY: Float) {
    val pad = binding.dpadContainer
    if (pad.width <= 0 || pad.height <= 0) return

    val location = IntArray(2)
    pad.getLocationOnScreen(location)
    val centerX = location[0] + pad.width / 2f
    val centerY = location[1] + pad.height / 2f
    val radius = (minOf(pad.width, pad.height) / 2f).coerceAtLeast(1f)
    val nx = (rawX - centerX) / radius
    val ny = (rawY - centerY) / radius
    val absX = kotlin.math.abs(nx)
    val absY = kotlin.math.abs(ny)

    // Small centre dead-zone prevents accidental direction changes when the
    // thumb crosses the middle. Outside it, choose a cardinal direction or
    // a natural diagonal. The broad diagonal transition makes rolling the
    // thumb around the pad smooth and forgiving on phones.
    if (maxOf(absX, absY) < 0.14f) {
        setActiveDpadKeys(emptySet())
        return
    }

    val next = mutableSetOf<Int>()
    val maxAxis = maxOf(absX, absY).coerceAtLeast(0.0001f)
    val diagonal = minOf(absX, absY) / maxAxis >= 0.42f

    if (diagonal || absX > absY) {
        next += if (nx < 0f) KEY_LEFT else KEY_RIGHT
    }
    if (diagonal || absY >= absX) {
        next += if (ny < 0f) KEY_UP else KEY_DOWN
    }

    setActiveDpadKeys(next)
}

internal fun MainActivity.setActiveDpadKeys(next: Set<Int>) {
    // Only send JNI changes when a direction actually changes. This avoids
    // flooding the emulator core with duplicate key events during ACTION_MOVE.
    (activeDpadKeys - next).forEach { key -> setGameplayKey(key, false) }
    (next - activeDpadKeys).forEach { key -> setGameplayKey(key, true) }
    activeDpadKeys.clear()
    activeDpadKeys.addAll(next)

    binding.buttonUp.isPressed = KEY_UP in next
    binding.buttonDown.isPressed = KEY_DOWN in next
    binding.buttonLeft.isPressed = KEY_LEFT in next
    binding.buttonRight.isPressed = KEY_RIGHT in next
}

internal fun MainActivity.bindKey(view: View, key: Int) {
    view.setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.isPressed = true
                setGameplayKey(key, true)
                true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                v.isPressed = false
                setGameplayKey(key, false)
                true
            }

            else -> true
        }
    }
}

internal fun MainActivity.enterEmulatorPresentation() {
    preferredOrientationValue = prefs.getString(ORIENTATION_PREF, preferredOrientationValue) ?: preferredOrientationValue
    applyPreferredOrientation(preferredOrientationValue)

    val fullscreen = prefs.getBoolean(FULLSCREEN_PREF, true)
    val immersive = prefs.getBoolean(IMMERSIVE_PREF, true)
    val controller = WindowInsetsControllerCompat(window, binding.root)
    if (fullscreen || immersive) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (fullscreen) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
        if (immersive) controller.hide(WindowInsetsCompat.Type.navigationBars()) else controller.show(WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(window, true)
    }
    applyGameplayVisualSettings()
}

internal fun MainActivity.leaveEmulatorPresentation() {
    if (screenEditorPresentationActive) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        WindowInsetsControllerCompat(window, binding.root).show(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(window, true)
    }
    applyPreferredOrientation(preferredOrientationValue)
}

internal fun MainActivity.setScreenEditorPresentation(active: Boolean) {
    screenEditorPresentationActive = active
    if (binding.emulatorOverlay.visibility == View.VISIBLE) return

    if (active) {
        // Match the exact system-bar canvas gameplay will use so editor
        // coordinates stay 1:1 with the real game layout.
        val fullscreen = prefs.getBoolean(FULLSCREEN_PREF, true)
        val immersive = prefs.getBoolean(IMMERSIVE_PREF, true)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        if (fullscreen || immersive) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (fullscreen) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
            if (immersive) controller.hide(WindowInsetsCompat.Type.navigationBars()) else controller.show(WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    } else {
        WindowInsetsControllerCompat(window, binding.root).show(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(window, true)
    }
}

internal fun MainActivity.applyPreferredOrientation(value: String) {
    requestedOrientation = when (value) {
        // Sensor mode is Retra's explicit auto-rotate option. It follows the
        // physical device orientation instead of pinning the Activity.
        "Auto rotate" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        "Landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        "Reverse landscape" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        "Portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // Let Android/windowing decide when the user explicitly requests the
        // system default (important for tablets, foldables and desktop modes).
        "System default" -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

internal fun MainActivity.applyNativeEmulatorLayout() {
    val viewport = binding.emulatorViewport
    if (viewport.width <= 0 || viewport.height <= 0) {
        viewport.post { applyNativeEmulatorLayout() }
        return
    }

    val portrait = currentNativePortrait()

    // Screen Editor-only controls never appear while a game is running.
    binding.emulatorBackButton.visibility = View.GONE
    binding.addControlButton.visibility = View.GONE

    // Menu is a built-in control. Quick Load / Quick Save / Fast Forward
    // are optional controls: they become visible only when the user added
    // them in Settings > Video > Screen size for this orientation.
    binding.quickLoadButton.visibility = View.GONE
    binding.quickSaveButton.visibility = View.GONE
    binding.speedButton.visibility = View.GONE
    binding.menuButton.visibility = View.VISIBLE

    // A/B can either be the original grouped control or two independent
    // editor controls. Always restore the grouped hierarchy before applying
    // defaults so a previous separated layout cannot leak into this pass.
    ensureGroupedAbButtons()

    applyNativeScreenFrame(viewport.width, viewport.height, portrait)
    applyDefaultControllerLayout(viewport.width, viewport.height, portrait)

    // User changes from the Screen Editor override the matching orientation
    // default. Portrait and landscape are stored separately on Android.
    applySavedControlState("menu", binding.utilityBar, portrait)
    applySavedControlState("shoulderLeft", binding.buttonL, portrait, currentPlatform == PLATFORM_GBA)
    applySavedControlState("shoulderRight", binding.buttonR, portrait, currentPlatform == PLATFORM_GBA)
    applySavedControlState("dpad", binding.dpadContainer, portrait)
    applySavedControlState("startSelect", binding.startSelectContainer, portrait)
    applySavedAbControls(portrait)
    applySavedExtraUtilityControls(portrait)
    applySavedExtraActionControls(portrait)
}

internal fun MainActivity.applyNativeScreenFrame(parentWidth: Int, parentHeight: Int, portrait: Boolean) {
    val aspect =
        if (videoWidth > 0 && videoHeight > 0) videoWidth.toFloat() / videoHeight.toFloat()
        else 1.5f

    fun fitted(maxWidth: Float, maxHeight: Float): Pair<Int, Int> {
        var width = maxWidth.coerceAtLeast(1f)
        var height = width / aspect
        if (height > maxHeight) {
            height = maxHeight.coerceAtLeast(1f)
            width = height * aspect
        }
        return width.toInt().coerceAtLeast(1) to height.toInt().coerceAtLeast(1)
    }

    var width: Int
    var height: Int
    var left: Float
    var top: Float
    var mode = "best"

    // Portrait and landscape keep completely independent screen states.
    // This prevents changing one orientation from moving/resizing the other.
    val raw = gameplayLayouts.screenJson(currentRomId, portrait)
    val state =
        try { if (raw.isNullOrBlank()) null else JSONObject(raw) }
        catch (_: Exception) { null }
    mode = state?.optString("mode", "best") ?: "best"

    if (mode == "custom" && state?.optJSONObject("customFrame") != null) {
        val custom = state.optJSONObject("customFrame")!!
        width = (custom.optDouble("width", if (portrait) 0.98 else 0.68) * parentWidth)
            .toInt()
            .coerceIn((parentWidth * 0.20f).toInt().coerceAtLeast(1), parentWidth)
        height = (custom.optDouble("height", if (portrait) 0.32 else 0.90) * parentHeight)
            .toInt()
            .coerceIn((parentHeight * 0.16f).toInt().coerceAtLeast(1), parentHeight)
        left = (custom.optDouble("left", if (portrait) 0.01 else 0.16) * parentWidth)
            .toFloat()
            .coerceIn(0f, (parentWidth - width).coerceAtLeast(0).toFloat())
        top = (custom.optDouble("top", 0.0) * parentHeight)
            .toFloat()
            .coerceIn(0f, (parentHeight - height).coerceAtLeast(0).toFloat())
    } else if (mode == "fullscreen" || mode == "stretch") {
        width = parentWidth
        height = parentHeight
        left = 0f
        top = 0f
    } else if (portrait) {
        // Target portrait reference: screen is at the very top and uses
        // almost the entire available width at the console's real aspect.
        val widthRatio = if (mode == "centered") 0.78f else 0.985f
        val fit = fitted(parentWidth * widthRatio, parentHeight * 0.42f)
        width = fit.first
        height = fit.second
        left = (parentWidth - width) / 2f
        top = if (mode == "centered") parentHeight * 0.035f else 0f
    } else {
        // Target landscape reference: screen is centered and almost fills
        // the viewport vertically. At 3:2 this naturally creates the two
        // translucent side controller zones visible in the reference.
        val widthRatio = if (mode == "centered") 0.60f else 0.90f
        val heightRatio = if (mode == "centered") 0.64f else 0.90f
        val fit = fitted(parentWidth * widthRatio, parentHeight * heightRatio)
        width = fit.first
        height = fit.second
        left = (parentWidth - width) / 2f
        top = (parentHeight - height) / 2f
    }

    if (portrait) {
        binding.leftSideShade.visibility = View.GONE
        binding.rightSideShade.visibility = View.GONE
    } else {
        binding.leftSideShade.visibility = View.VISIBLE
        binding.rightSideShade.visibility = View.VISIBLE
    }

    binding.gameScreenFrame.layoutParams = FrameLayout.LayoutParams(width, height).apply {
        gravity = Gravity.NO_GRAVITY
        leftMargin = left.roundToInt().coerceAtLeast(0)
        topMargin = top.roundToInt().coerceAtLeast(0)
    }
    binding.gameScreenFrame.translationX = 0f
    binding.gameScreenFrame.translationY = 0f
    binding.gameScreen.scaleType =
        if (mode == "stretch") ImageView.ScaleType.FIT_XY
        else ImageView.ScaleType.FIT_CENTER

    if (!portrait) {
        val leftGap = left.toInt().coerceAtLeast(0)
        val rightGap = (parentWidth - (left + width)).toInt().coerceAtLeast(0)
        binding.leftSideShade.layoutParams =
            (binding.leftSideShade.layoutParams as FrameLayout.LayoutParams).apply {
                this.width = leftGap
                height = FrameLayout.LayoutParams.MATCH_PARENT
                gravity = Gravity.START
            }
        binding.rightSideShade.layoutParams =
            (binding.rightSideShade.layoutParams as FrameLayout.LayoutParams).apply {
                this.width = rightGap
                height = FrameLayout.LayoutParams.MATCH_PARENT
                gravity = Gravity.END
            }
    }
}

internal fun MainActivity.migrateLandscapeControllerScaleTo100() {
    if (prefs.getBoolean(LANDSCAPE_100_MIGRATION_PREF, false)) return

    val key = gameplayLayouts.legacyControllerKey(false)
    val raw = prefs.getString(key, null)
    if (!raw.isNullOrBlank()) {
        try {
            val root = JSONObject(raw)
            val controls = root.optJSONObject("controls")
            if (controls != null) {
                val names = controls.keys()
                while (names.hasNext()) {
                    val name = names.next()
                    val control = controls.optJSONObject(name) ?: continue
                    val oldScale = control.optDouble("scale", Double.NaN)
                    // v3.90-v3.93 used 125% as the untouched landscape
                    // default. Convert only those exact legacy-default values;
                    // deliberate custom sizes such as 90%, 110%, 140%, etc. stay intact.
                    if (!oldScale.isNaN() && kotlin.math.abs(oldScale - 1.25) < 0.0001) {
                        control.put("scale", 1.0)
                    }
                }
                root.put("orientation", "landscape")
                prefs.edit().putString(key, root.toString()).commit()
            }
        } catch (_: Exception) {
        }
    }

    prefs.edit().putBoolean(LANDSCAPE_100_MIGRATION_PREF, true).apply()
}

internal fun MainActivity.applyDefaultControllerLayout(parentWidth: Int, parentHeight: Int, portrait: Boolean) {
    val defaultScale = 1.0f
    fun width(view: View) = viewUnscaledWidth(view) * defaultScale
    fun height(view: View) = viewUnscaledHeight(view) * defaultScale

    // v3.94: portrait and landscape controls both start at 100%.
    // Portrait and landscape remain completely separate datasets;
    // saved values for one side are never transformed into the other.
    listOf(
        binding.utilityBar,
        binding.buttonL,
        binding.buttonR,
        binding.dpadContainer,
        binding.startSelectContainer,
        binding.abContainer
    ).forEach {
        it.translationX = 0f
        it.translationY = 0f
        it.pivotX = 0f
        it.pivotY = 0f
        it.scaleX = defaultScale
        it.scaleY = defaultScale
    }

    if (!portrait) {
        // LANDSCAPE DEFAULT (100%)
        // Menu top-center; L/R top corners; D-pad lower-left; A/B lower-right;
        // Start/Select bottom-center.
        val side = maxOf(dp(18f), parentWidth * 0.028f)
        val topInset = maxOf(dp(10f), parentHeight * 0.026f)
        val lowerInset = maxOf(dp(12f), parentHeight * 0.030f)

        placeNativeControl(binding.utilityBar, (parentWidth - width(binding.utilityBar)) / 2f, topInset, defaultScale)
        placeNativeControl(binding.buttonL, side, topInset, defaultScale)
        placeNativeControl(binding.buttonR, parentWidth - width(binding.buttonR) - side, topInset, defaultScale)
        placeNativeControl(
            binding.dpadContainer,
            maxOf(dp(18f), parentWidth * 0.035f),
            parentHeight - height(binding.dpadContainer) - lowerInset,
            defaultScale
        )
        placeNativeControl(
            binding.abContainer,
            parentWidth - width(binding.abContainer) - maxOf(dp(18f), parentWidth * 0.035f),
            parentHeight - height(binding.abContainer) - lowerInset,
            defaultScale
        )
        placeNativeControl(
            binding.startSelectContainer,
            (parentWidth - width(binding.startSelectContainer)) / 2f,
            parentHeight - height(binding.startSelectContainer) - maxOf(dp(10f), parentHeight * 0.018f),
            defaultScale
        )
    } else {
        // PORTRAIT DEFAULT (100%) — My Boy!-style zones.
        // Emulator screen remains at the top. L/Menu/R are below the screen,
        // Start/Select sit in the lower-middle, then D-pad and A/B occupy the
        // bottom left/right without touching Android system gesture areas.
        val side = maxOf(dp(10f), parentWidth * 0.030f)
        val dpadBottom = maxOf(dp(14f), parentHeight * 0.018f)
        val dpadY = parentHeight - height(binding.dpadContainer) - dpadBottom
        val abY = parentHeight - height(binding.abContainer) - dpadBottom
        val shoulderRowY = parentHeight * 0.64f

        placeNativeControl(binding.buttonL, side, shoulderRowY, defaultScale)
        placeNativeControl(
            binding.buttonR,
            parentWidth - width(binding.buttonR) - side,
            shoulderRowY,
            defaultScale
        )
        placeNativeControl(
            binding.utilityBar,
            (parentWidth - width(binding.utilityBar)) / 2f,
            shoulderRowY,
            defaultScale
        )

        placeNativeControl(binding.dpadContainer, side, dpadY, defaultScale)
        placeNativeControl(
            binding.abContainer,
            parentWidth - width(binding.abContainer) - side,
            abY,
            defaultScale
        )

        val startSelectY = minOf(
            parentHeight * 0.79f,
            parentHeight - height(binding.startSelectContainer) -
                maxOf(dp(94f), height(binding.startSelectContainer) + dp(18f))
        )
        placeNativeControl(
            binding.startSelectContainer,
            (parentWidth - width(binding.startSelectContainer)) / 2f,
            startSelectY,
            defaultScale
        )
    }

    binding.buttonL.visibility =
        if (currentPlatform == PLATFORM_GBA) View.VISIBLE else View.INVISIBLE
    binding.buttonR.visibility =
        if (currentPlatform == PLATFORM_GBA) View.VISIBLE else View.INVISIBLE
    binding.dpadContainer.visibility = View.VISIBLE
    binding.startSelectContainer.visibility = View.VISIBLE
    binding.abContainer.visibility = View.VISIBLE
    binding.utilityBar.visibility = View.VISIBLE
}

internal fun MainActivity.applySavedControlState(
    id: String,
    view: View,
    portrait: Boolean,
    platformAllowed: Boolean = true
) {
    val raw = gameplayLayouts.controllerJson(currentRomId, portrait)
    val state =
        try { if (raw.isNullOrBlank()) null else JSONObject(raw) }
        catch (_: Exception) { null }
    val control = state?.optJSONObject("controls")?.optJSONObject(id)

    if (control == null) {
        view.visibility = if (platformAllowed) View.VISIBLE else View.INVISIBLE
        return
    }

    val hidden = control.optBoolean("hidden", false)
    view.visibility = if (!hidden && platformAllowed) View.VISIBLE else View.INVISIBLE
    if (view.visibility != View.VISIBLE) return

    val fallbackScale = 1.0
    val scale = control.optDouble("scale", fallbackScale).toFloat().coerceIn(0.65f, 1.7f)
    val parentWidth = binding.emulatorViewport.width.toFloat()
    val parentHeight = binding.emulatorViewport.height.toFloat()
    val x = (control.optDouble("x", 0.0) * parentWidth).toFloat()
    val y = (control.optDouble("y", 0.0) * parentHeight).toFloat()

    val maxX = (parentWidth - viewUnscaledWidth(view) * scale).coerceAtLeast(0f)
    val maxY = (parentHeight - viewUnscaledHeight(view) * scale).coerceAtLeast(0f)
    placeNativeControl(
        view,
        x.coerceIn(0f, maxX),
        y.coerceIn(0f, maxY),
        scale
    )
}

internal fun MainActivity.ensureGroupedAbButtons() {
    fun moveIntoGroup(view: View, isA: Boolean) {
        if (view.parent !== binding.abContainer) {
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            binding.abContainer.addView(
                view,
                FrameLayout.LayoutParams(dp(54f).roundToInt(), dp(54f).roundToInt()).apply {
                    gravity = if (isA) Gravity.BOTTOM or Gravity.END else Gravity.TOP or Gravity.START
                }
            )
        } else {
            view.layoutParams = FrameLayout.LayoutParams(
                dp(54f).roundToInt(),
                dp(54f).roundToInt()
            ).apply {
                gravity = if (isA) Gravity.BOTTOM or Gravity.END else Gravity.TOP or Gravity.START
            }
        }
        view.translationX = 0f
        view.translationY = 0f
        view.pivotX = 0f
        view.pivotY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        view.alpha = 1f
    }

    moveIntoGroup(binding.buttonB, false)
    moveIntoGroup(binding.buttonA, true)
}

internal fun MainActivity.ensureSeparatedAbButtons() {
    fun moveToViewport(view: View) {
        if (view.parent !== binding.emulatorViewport) {
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            binding.emulatorViewport.addView(
                view,
                FrameLayout.LayoutParams(dp(48f).roundToInt(), dp(48f).roundToInt()).apply {
                    gravity = Gravity.NO_GRAVITY
                }
            )
        } else {
            val lp = view.layoutParams as? FrameLayout.LayoutParams
                ?: FrameLayout.LayoutParams(dp(48f).roundToInt(), dp(48f).roundToInt())
            lp.width = dp(48f).roundToInt()
            lp.height = dp(48f).roundToInt()
            lp.gravity = Gravity.NO_GRAVITY
            lp.rightMargin = 0
            lp.bottomMargin = 0
            view.layoutParams = lp
        }
    }

    moveToViewport(binding.buttonB)
    moveToViewport(binding.buttonA)
}

internal fun MainActivity.applySavedAbControls(portrait: Boolean) {
    val raw = gameplayLayouts.controllerJson(currentRomId, portrait)
    val root = try { if (raw.isNullOrBlank()) null else JSONObject(raw) } catch (_: Exception) { null }
    val controls = root?.optJSONObject("controls")
    val groupedState = controls?.optJSONObject("ab")
    val groupedVisible = groupedState == null || !groupedState.optBoolean("hidden", false)

    if (groupedVisible) {
        ensureGroupedAbButtons()
        binding.buttonA.visibility = View.VISIBLE
        binding.buttonB.visibility = View.VISIBLE
        applySavedControlState("ab", binding.abContainer, portrait)
        return
    }

    // The grouped A/B control was intentionally removed. Look for the first
    // visible independent A and B controls saved by the Screen Editor. Old
    // duplicate records are ignored deterministically so gameplay still has
    // exactly one native instance of each action.
    var buttonAState: JSONObject? = null
    var buttonBState: JSONObject? = null
    if (controls != null) {
        val names = controls.keys()
        while (names.hasNext()) {
            val controlId = names.next()
            val control = controls.optJSONObject(controlId) ?: continue
            if (control.optBoolean("base", false) || control.optBoolean("hidden", false)) continue
            when (control.optString("type", "")) {
                "buttonA" -> if (buttonAState == null) buttonAState = control
                "buttonB" -> if (buttonBState == null) buttonBState = control
            }
        }
    }

    if (buttonAState == null && buttonBState == null) {
        ensureGroupedAbButtons()
        binding.abContainer.visibility = View.GONE
        binding.buttonA.visibility = View.VISIBLE
        binding.buttonB.visibility = View.VISIBLE
        return
    }

    ensureSeparatedAbButtons()
    binding.abContainer.visibility = View.GONE
    applySavedIndependentAbButton(binding.buttonA, buttonAState)
    applySavedIndependentAbButton(binding.buttonB, buttonBState)
}

internal fun MainActivity.applySavedIndependentAbButton(view: View, control: JSONObject?) {
    if (control == null) {
        view.visibility = View.GONE
        return
    }

    val parentWidth = binding.emulatorViewport.width.toFloat()
    val parentHeight = binding.emulatorViewport.height.toFloat()
    if (parentWidth <= 0f || parentHeight <= 0f) {
        view.visibility = View.GONE
        return
    }

    val scale = control.optDouble("scale", 1.0).toFloat().coerceIn(0.65f, 1.7f)
    val x = (control.optDouble("x", 0.0) * parentWidth).toFloat()
    val y = (control.optDouble("y", 0.0) * parentHeight).toFloat()
    val maxX = (parentWidth - viewUnscaledWidth(view) * scale).coerceAtLeast(0f)
    val maxY = (parentHeight - viewUnscaledHeight(view) * scale).coerceAtLeast(0f)

    view.visibility = View.VISIBLE
    placeNativeControl(view, x.coerceIn(0f, maxX), y.coerceIn(0f, maxY), scale)
}

internal fun MainActivity.applySavedExtraUtilityControls(portrait: Boolean) {
    // These three views are direct children of emulatorViewport, exactly like
    // the editor canvas. That makes an extra control's saved x/y/scale land
    // at the same place during gameplay instead of being trapped in utilityBar.
    val utilityViews = mapOf(
        "quickLoad" to binding.quickLoadButton,
        "quickSave" to binding.quickSaveButton,
        "fastForward" to binding.speedButton
    )
    utilityViews.values.forEach { it.visibility = View.GONE }

    val raw = gameplayLayouts.controllerJson(currentRomId, portrait) ?: return
    val root = try { JSONObject(raw) } catch (_: Exception) { return }
    val controls = root.optJSONObject("controls") ?: return
    val parentWidth = binding.emulatorViewport.width.toFloat()
    val parentHeight = binding.emulatorViewport.height.toFloat()
    if (parentWidth <= 0f || parentHeight <= 0f) return

    val alreadyApplied = mutableSetOf<String>()
    val names = controls.keys()
    while (names.hasNext()) {
        val controlId = names.next()
        val control = controls.optJSONObject(controlId) ?: continue
        if (control.optBoolean("base", false)) continue
        if (control.optBoolean("hidden", false)) continue

        val type = control.optString("type", "")
        val view = utilityViews[type] ?: continue
        // Native currently exposes one gameplay instance per utility type.
        // If an old editor dataset contains duplicates, the first visible
        // instance wins deterministically instead of stacking buttons.
        if (!alreadyApplied.add(type)) continue

        val scale = control.optDouble("scale", 1.0).toFloat().coerceIn(0.65f, 1.7f)
        val x = (control.optDouble("x", 0.0) * parentWidth).toFloat()
        val y = (control.optDouble("y", 0.0) * parentHeight).toFloat()
        val maxX = (parentWidth - viewUnscaledWidth(view) * scale).coerceAtLeast(0f)
        val maxY = (parentHeight - viewUnscaledHeight(view) * scale).coerceAtLeast(0f)

        view.visibility = View.VISIBLE
        placeNativeControl(view, x.coerceIn(0f, maxX), y.coerceIn(0f, maxY), scale)
    }

    updateFastForwardUi()
    applyNativeButtonsOpacity()
}

internal fun MainActivity.clearSavedExtraActionControls() {
    for (index in binding.emulatorViewport.childCount - 1 downTo 0) {
        val child = binding.emulatorViewport.getChildAt(index)
        val tag = child.tag as? String ?: continue
        if (tag.startsWith("retra-extra-action:")) {
            binding.emulatorViewport.removeViewAt(index)
        }
    }
}

internal fun MainActivity.makeExtraActionTextView(label: String): TextView {
    return TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 16f
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setBackgroundResource(R.drawable.bg_controller_circle)
        includeFontPadding = false
        isClickable = false
        isFocusable = false
        layoutParams = FrameLayout.LayoutParams(dp(56f).roundToInt(), dp(56f).roundToInt())
    }
}

internal fun MainActivity.bindMultiKeyControl(view: View, keys: IntArray) {
    view.setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.isPressed = true
                keys.forEach { setGameplayKey(it, true) }
                true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                v.isPressed = false
                keys.forEach { setGameplayKey(it, false) }
                true
            }
            else -> true
        }
    }
}

internal fun MainActivity.bindTurboAbControl(view: View) {
    val handler = Handler(Looper.getMainLooper())
    var active = false
    var pressed = false
    val pulse = object : Runnable {
        override fun run() {
            if (!active) return
            pressed = !pressed
            setGameplayKey(KEY_A, pressed)
            setGameplayKey(KEY_B, pressed)
            handler.postDelayed(this, 65L)
        }
    }
    view.setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                active = true
                pressed = false
                v.isPressed = true
                handler.removeCallbacks(pulse)
                handler.post(pulse)
                true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                active = false
                v.isPressed = false
                handler.removeCallbacks(pulse)
                setGameplayKey(KEY_A, false)
                setGameplayKey(KEY_B, false)
                pressed = false
                true
            }
            else -> true
        }
    }
}

internal fun MainActivity.makeTurboAbControl(): FrameLayout {
    val container = FrameLayout(this).apply {
        layoutParams = FrameLayout.LayoutParams(dp(122f).roundToInt(), dp(58f).roundToInt())
    }
    fun addLabel(label: String, leftDp: Float) {
        container.addView(
            makeExtraActionTextView(label),
            FrameLayout.LayoutParams(dp(56f).roundToInt(), dp(56f).roundToInt()).apply {
                leftMargin = dp(leftDp).roundToInt()
                topMargin = dp(1f).roundToInt()
            }
        )
    }
    addLabel("B", 0f)
    addLabel("A", 66f)
    bindTurboAbControl(container)
    return container
}

internal fun MainActivity.makeScreenshotControl(): ImageButton {
    val activity = this
    return ImageButton(this).apply {
        layoutParams = FrameLayout.LayoutParams(dp(56f).roundToInt(), dp(56f).roundToInt())
        setBackgroundResource(R.drawable.bg_controller_round_rect)
        setImageResource(R.drawable.ic_controller_screenshot)
        setPadding(dp(13f).roundToInt(), dp(13f).roundToInt(), dp(13f).roundToInt(), dp(13f).roundToInt())
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        contentDescription = "Screenshot"
        setOnClickListener {
            val saved = saveGameplayScreenshot()
            RetraNotice.makeText(
                activity,
                if (saved) "Screenshot saved" else "Could not save screenshot",
                RetraNotice.LENGTH_SHORT
            ).show()
        }
    }
}

internal fun MainActivity.applySavedExtraActionControls(portrait: Boolean) {
    clearSavedExtraActionControls()

    val raw = gameplayLayouts.controllerJson(currentRomId, portrait) ?: return
    val root = try { JSONObject(raw) } catch (_: Exception) { return }
    val controls = root.optJSONObject("controls") ?: return
    val parentWidth = binding.emulatorViewport.width.toFloat()
    val parentHeight = binding.emulatorViewport.height.toFloat()
    if (parentWidth <= 0f || parentHeight <= 0f) return

    val supportedTypes = setOf(
        "comboAB", "comboLR", "comboLA", "comboLB", "comboRA", "comboRB", "turboAB", "screenshot"
    )
    val alreadyApplied = mutableSetOf<String>()
    val names = controls.keys()
    while (names.hasNext()) {
        val controlId = names.next()
        val control = controls.optJSONObject(controlId) ?: continue
        if (control.optBoolean("base", false) || control.optBoolean("hidden", false)) continue
        val type = control.optString("type", "")
        if (type !in supportedTypes || !alreadyApplied.add(type)) continue

        val view: View = when (type) {
            "comboAB" -> makeExtraActionTextView("AB").also { bindMultiKeyControl(it, intArrayOf(KEY_A, KEY_B)) }
            "comboLR" -> makeExtraActionTextView("LR").also { bindMultiKeyControl(it, intArrayOf(KEY_L, KEY_R)) }
            "comboLA" -> makeExtraActionTextView("LA").also { bindMultiKeyControl(it, intArrayOf(KEY_L, KEY_A)) }
            "comboLB" -> makeExtraActionTextView("LB").also { bindMultiKeyControl(it, intArrayOf(KEY_L, KEY_B)) }
            "comboRA" -> makeExtraActionTextView("RA").also { bindMultiKeyControl(it, intArrayOf(KEY_R, KEY_A)) }
            "comboRB" -> makeExtraActionTextView("RB").also { bindMultiKeyControl(it, intArrayOf(KEY_R, KEY_B)) }
            "turboAB" -> makeTurboAbControl()
            "screenshot" -> makeScreenshotControl()
            else -> continue
        }

        view.tag = "retra-extra-action:$type"
        view.alpha = preferredButtonsOpacity.coerceIn(0.25f, 1f)
        binding.emulatorViewport.addView(view)

        val scale = control.optDouble("scale", 1.0).toFloat().coerceIn(0.65f, 1.7f)
        val x = (control.optDouble("x", 0.0) * parentWidth).toFloat()
        val y = (control.optDouble("y", 0.0) * parentHeight).toFloat()
        val maxX = (parentWidth - viewUnscaledWidth(view) * scale).coerceAtLeast(0f)
        val maxY = (parentHeight - viewUnscaledHeight(view) * scale).coerceAtLeast(0f)
        placeNativeControl(view, x.coerceIn(0f, maxX), y.coerceIn(0f, maxY), scale)
    }
}

internal fun MainActivity.dp(value: Float): Float = value * resources.displayMetrics.density

internal fun MainActivity.configureBackHandling() {
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (binding.emulatorOverlay.visibility == View.VISIBLE) {
                if (prefs.getBoolean(CONFIRM_CLOSE_RESET_PREF, true)) {
                    AlertDialog.Builder(this@configureBackHandling)
                        .setTitle("Close game?")
                        .setMessage("Your current auto-save will be written before the game closes.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Close") { _, _ -> closeEmulator() }
                        .show()
                } else {
                    closeEmulator()
                }
                return
            }

            binding.webView.evaluateJavascript(
                "window.retraHandleAndroidBack ? window.retraHandleAndroidBack() : false"
            ) { result ->
                if (result != "true") {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }
    })
}

// Native gameplay chrome and rotation/layout scheduling.
internal fun MainActivity.applyNativeButtonsOpacity() {
    val alpha = preferredButtonsOpacity.coerceIn(0.25f, 1f)
    listOf(
        binding.buttonL,
        binding.buttonR,
        binding.dpadContainer,
        binding.startSelectContainer,
        binding.abContainer,
        binding.menuButton,
        binding.quickLoadButton,
        binding.quickSaveButton,
        binding.speedButton
    ).forEach { it.alpha = alpha }

    // Grouped A/B inherit opacity from abContainer. Independent A/B are
    // direct viewport children and therefore need the opacity themselves.
    binding.buttonA.alpha = if (binding.buttonA.parent === binding.abContainer) 1f else alpha
    binding.buttonB.alpha = if (binding.buttonB.parent === binding.abContainer) 1f else alpha
}

internal fun MainActivity.bindEmulatorChrome() {
    // These two controls belong to the Screen Editor only. The actual
    // gameplay overlay intentionally hides them.
    binding.emulatorBackButton.visibility = View.GONE
    binding.addControlButton.visibility = View.GONE

    // v3.81 gameplay uses a single Menu button at the top center.
    binding.quickLoadButton.visibility = View.GONE
    binding.quickSaveButton.visibility = View.GONE
    binding.speedButton.visibility = View.GONE

    binding.menuButton.setOnClickListener {
        showGameplayMenu()
    }

    // Kept wired even though they are hidden in normal gameplay; this makes
    // it easy to expose them again from a custom layout later.
    binding.speedButton.setOnTouchListener { view, event ->
        val holdMode = (prefs.getString(FAST_FORWARD_BUTTON_MODE_PREF, "Press to toggle") ?: "Press to toggle")
            .equals("Hold down to activate", ignoreCase = true)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (holdMode) {
                    activeEmulationSpeed = preferredEmulationSpeed
                    updateFastForwardUi()
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                if (holdMode) {
                    activeEmulationSpeed = 1.0
                    updateFastForwardUi()
                } else {
                    view.performClick()
                    toggleFastForward()
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (holdMode) {
                    activeEmulationSpeed = 1.0
                    updateFastForwardUi()
                }
                true
            }
            else -> true
        }
    }
    binding.quickSaveButton.setOnClickListener { quickSave() }
    binding.quickLoadButton.setOnClickListener { quickLoad() }
}

internal fun MainActivity.configureNativeLayoutRotationHandling() {
    binding.emulatorViewport.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        val width = right - left
        val height = bottom - top
        val oldWidth = oldRight - oldLeft
        val oldHeight = oldBottom - oldTop

        if (binding.emulatorOverlay.visibility == View.VISIBLE &&
            width > 0 && height > 0 &&
            (width != oldWidth || height != oldHeight)
        ) {
            scheduleNativeEmulatorLayout(90L)
        }
    }
}

internal fun MainActivity.scheduleNativeEmulatorLayout(delayMs: Long = 80L) {
    if (!hasBinding() || binding.emulatorOverlay.visibility != View.VISIBLE) return
    val generation = ++nativeLayoutGeneration
    binding.emulatorViewport.postDelayed({
        if (generation != nativeLayoutGeneration) return@postDelayed
        if (binding.emulatorOverlay.visibility != View.VISIBLE) return@postDelayed
        if (binding.emulatorViewport.width <= 0 || binding.emulatorViewport.height <= 0) {
            scheduleNativeEmulatorLayout(60L)
            return@postDelayed
        }
        applyNativeEmulatorLayout()
    }, delayMs)
}

internal fun MainActivity.currentNativePortrait(): Boolean {
    // The measured emulator viewport is the source of truth. During an Android
    // rotation Configuration.orientation can briefly describe the old state while
    // the new viewport is already being measured. Using the viewport prevents a
    // portrait layout from ever being applied to a landscape frame (and vice versa).
    val viewport = binding.emulatorViewport
    if (viewport.width > 0 && viewport.height > 0) {
        return viewport.height >= viewport.width
    }

    return resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
}

internal fun MainActivity.viewUnscaledWidth(view: View): Float {
    if (view.width > 0) return view.width.toFloat()
    val width = view.layoutParams?.width ?: 0
    return if (width > 0) width.toFloat() else 1f
}

internal fun MainActivity.viewUnscaledHeight(view: View): Float {
    if (view.height > 0) return view.height.toFloat()
    val height = view.layoutParams?.height ?: 0
    return if (height > 0) height.toFloat() else 1f
}

internal fun MainActivity.placeNativeControl(view: View, x: Float, y: Float, scale: Float = 1f) {
    val lp = view.layoutParams as? FrameLayout.LayoutParams ?: return
    lp.gravity = Gravity.NO_GRAVITY
    lp.leftMargin = x.roundToInt().coerceAtLeast(0)
    lp.topMargin = y.roundToInt().coerceAtLeast(0)
    lp.rightMargin = 0
    lp.bottomMargin = 0
    view.layoutParams = lp

    // Never carry translation from the previous orientation. This was the
    // main cause of top controls drifting downward after repeated rotations.
    view.translationX = 0f
    view.translationY = 0f
    view.pivotX = 0f
    view.pivotY = 0f
    view.scaleX = scale
    view.scaleY = scale
}
