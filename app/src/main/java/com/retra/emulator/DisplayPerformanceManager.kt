package com.retra.emulator

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

/**
 * Chooses a smooth display mode without changing emulator timing.
 *
 * High-end devices may use 90/120 Hz presentation. Low-RAM, low-memory,
 * battery-saver or thermally constrained devices are capped automatically so
 * Retra does not trade stable emulation for an unnecessarily expensive panel
 * refresh rate.
 */
class DisplayPerformanceManager(
    private val activity: Activity,
    private val maxUiRefreshRateHz: Float = 120f
) {
    private var originalModeId: Int? = null
    private var originalRefreshRate: Float? = null
    private var lastAppliedModeId: Int = 0
    private var lastAppliedCapHz: Float = 0f
    private var gameplayModeRequested = false

    fun applyPreferredMode() {
        gameplayModeRequested = false
        applyAdaptiveMode("ui", gameplay = false)
    }

    fun applyGameplayMode() {
        gameplayModeRequested = true
        applyAdaptiveMode("gameplay", gameplay = true)
    }

    fun reapplyAfterConfigurationChange() {
        activity.window.decorView.post {
            applyAttachedDisplayMode(
                activity.window.decorView.display,
                "configuration",
                gameplayModeRequested
            )
        }
    }

    fun restoreSystemDefault() {
        val attrs = activity.window.attributes
        val previousMode = originalModeId ?: 0
        val previousRate = originalRefreshRate ?: 0f
        if (attrs.preferredDisplayModeId != previousMode || abs(attrs.preferredRefreshRate - previousRate) > 0.01f) {
            attrs.preferredDisplayModeId = previousMode
            attrs.preferredRefreshRate = previousRate
            activity.window.attributes = attrs
        }
        lastAppliedModeId = 0
        lastAppliedCapHz = 0f
        gameplayModeRequested = false
    }

    internal fun adaptiveRefreshCapHz(gameplay: Boolean = false): Float {
        val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val powerManager = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager

        if (powerManager?.isPowerSaveMode == true) return 60f
        if (activityManager?.isLowRamDevice == true) return 60f

        val memoryInfo = ActivityManager.MemoryInfo()
        runCatching { activityManager?.getMemoryInfo(memoryInfo) }
        val totalRamBytes = memoryInfo.totalMem

        var cap = when {
            totalRamBytes in 1 until FOUR_GIB -> 60f
            // Gameplay benefits from a clean 2:1 120 Hz presentation cadence.
            // Keep the more conservative 90 Hz cap for the general UI on
            // 4-6 GiB devices, but do not force ~60 FPS game content into the
            // uneven 90 Hz cadence when the panel can expose 120 Hz.
            gameplay -> maxUiRefreshRateHz
            totalRamBytes in FOUR_GIB until SIX_GIB -> 90f
            else -> maxUiRefreshRateHz
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            cap = when {
                powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> 60f
                powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE ->
                    if (gameplay) 60f else minOf(cap, 90f)
                else -> cap
            }
        }

        return cap.coerceIn(60f, maxUiRefreshRateHz)
    }

    private fun applyAdaptiveMode(reason: String, gameplay: Boolean) {
        val decor = activity.window.decorView
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        // Hardware acceleration is a window property. Forcing the entire
        // DecorView into a cached hardware layer makes a changing game screen
        // rebuild that off-screen layer every frame, so keep the root on the
        // normal hardware-accelerated display-list path instead.
        decor.setLayerType(View.LAYER_TYPE_NONE, null)
        decor.post { applyAttachedDisplayMode(decor.display, reason, gameplay) }
    }

    private fun applyAttachedDisplayMode(display: Display?, reason: String, gameplay: Boolean) {
        if (display == null || activity.isFinishing || activity.isDestroyed) return

        val attrs = activity.window.attributes
        if (originalModeId == null) originalModeId = attrs.preferredDisplayModeId
        if (originalRefreshRate == null) originalRefreshRate = attrs.preferredRefreshRate

        val capHz = adaptiveRefreshCapHz(gameplay)
        val currentMode = display.mode
        val target = if (gameplay) {
            selectBestGameplayMode(display.supportedModes, currentMode, capHz)
        } else {
            selectBestMode(display.supportedModes, currentMode, capHz)
        } ?: return
        if (
            target.modeId == lastAppliedModeId &&
            abs(lastAppliedCapHz - capHz) < 0.01f &&
            attrs.preferredDisplayModeId == target.modeId
        ) return

        attrs.preferredDisplayModeId = target.modeId
        attrs.preferredRefreshRate = target.refreshRate
        activity.window.attributes = attrs
        lastAppliedModeId = target.modeId
        lastAppliedCapHz = capHz

        Log.d(
            TAG,
            "$reason refresh requested: ${target.refreshRate} Hz " +
                "(adaptive cap $capHz Hz, mode ${target.modeId}, gameplay=$gameplay)"
        )
    }

    /**
     * GBA gameplay is a fixed ~59.73 FPS source. 60 Hz and 120 Hz panels map
     * cleanly to that cadence (roughly 1:1 and 2:1); 90 Hz does not and can
     * create an alternating 1/2-refresh cadence that looks like micro-stutter.
     * Prefer the highest same-resolution integer-multiple mode within the
     * device/thermal cap, then fall back to the normal adaptive policy.
     */
    internal fun selectBestGameplayMode(
        modes: Array<Display.Mode>,
        currentMode: Display.Mode,
        capHz: Float = maxUiRefreshRateHz
    ): Display.Mode? {
        if (modes.isEmpty()) return null

        val sameResolution = modes.filter {
            it.physicalWidth == currentMode.physicalWidth &&
                it.physicalHeight == currentMode.physicalHeight
        }
        val candidates = if (sameResolution.isNotEmpty()) sameResolution else modes.toList()
        val effectiveCap = minOf(capHz, maxUiRefreshRateHz)
        val capped = candidates.filter { it.refreshRate <= effectiveCap + RATE_TOLERANCE_HZ }
        val usable = if (capped.isNotEmpty()) capped else candidates

        val cadenceCompatible = usable.filter { mode ->
            val multiple = (mode.refreshRate / GBA_SOURCE_FPS).toInt().coerceAtLeast(1)
            val roundedMultiple = if (
                kotlin.math.abs(mode.refreshRate / GBA_SOURCE_FPS - multiple) > 0.5f
            ) multiple + 1 else multiple
            val normalizedError = kotlin.math.abs(
                mode.refreshRate - (GBA_SOURCE_FPS * roundedMultiple)
            ) / roundedMultiple.coerceAtLeast(1)
            normalizedError <= GAMEPLAY_CADENCE_TOLERANCE_HZ
        }

        return if (cadenceCompatible.isNotEmpty()) {
            cadenceCompatible.maxByOrNull { it.refreshRate }
        } else {
            selectBestMode(modes, currentMode, capHz)
        }
    }

    internal fun selectBestMode(
        modes: Array<Display.Mode>,
        currentMode: Display.Mode,
        capHz: Float = maxUiRefreshRateHz
    ): Display.Mode? {
        if (modes.isEmpty()) return null

        val sameResolution = modes.filter {
            it.physicalWidth == currentMode.physicalWidth &&
                it.physicalHeight == currentMode.physicalHeight
        }
        val candidates = if (sameResolution.isNotEmpty()) sameResolution else modes.toList()
        val effectiveCap = minOf(capHz, maxUiRefreshRateHz)

        val capped = candidates.filter { it.refreshRate <= effectiveCap + RATE_TOLERANCE_HZ }
        return (if (capped.isNotEmpty()) capped else candidates)
            .maxByOrNull { it.refreshRate }
    }

    companion object {
        private const val TAG = "Retra.Display"
        private const val RATE_TOLERANCE_HZ = 0.6f
        private const val GBA_SOURCE_FPS = 59.7275f
        private const val GAMEPLAY_CADENCE_TOLERANCE_HZ = 0.75f
        private const val FOUR_GIB = 4L * 1024L * 1024L * 1024L
        private const val SIX_GIB = 6L * 1024L * 1024L * 1024L
    }
}
