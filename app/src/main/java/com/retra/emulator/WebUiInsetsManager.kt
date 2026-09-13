package com.retra.emulator

import android.view.View
import android.view.Window
import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.roundToInt

/**
 * Single owner for Retra's packaged WebView safe-area geometry.
 *
 * The Web UI always runs edge-to-edge. Android supplies the real system-bar /
 * display-cutout insets here, and the web layer consumes those values exactly
 * once. This avoids the old layout path where Android first shrank the window
 * for a navigation bar and CSS then reserved the same navigation bar again.
 *
 * IME insets are deliberately excluded: opening the keyboard must not mutate
 * the persistent bottom-navigation safe area.
 */
class WebUiInsetsManager(
    private val window: Window,
    private val rootView: View,
    private val webView: WebView
) {
    private var pageReady = false
    private var safeTopCssPx = 0
    private var safeRightCssPx = 0
    private var safeBottomCssPx = 0
    private var safeLeftCssPx = 0

    fun install() {
        activateForWebUi()
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val safeDrawing = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val density = rootView.resources.displayMetrics.density.coerceAtLeast(1f)

            val nextTop = (safeDrawing.top / density).roundToInt().coerceAtLeast(0)
            val nextRight = (safeDrawing.right / density).roundToInt().coerceAtLeast(0)
            val nextBottom = (safeDrawing.bottom / density).roundToInt().coerceAtLeast(0)
            val nextLeft = (safeDrawing.left / density).roundToInt().coerceAtLeast(0)

            if (
                nextTop != safeTopCssPx ||
                nextRight != safeRightCssPx ||
                nextBottom != safeBottomCssPx ||
                nextLeft != safeLeftCssPx
            ) {
                safeTopCssPx = nextTop
                safeRightCssPx = nextRight
                safeBottomCssPx = nextBottom
                safeLeftCssPx = nextLeft
                pushInsetsToWebUi()
            }
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
    }

    /**
     * Normal Retra pages are edge-to-edge on every Android version. System bars
     * remain visible; the web layout receives their real safe area through this
     * manager instead of depending on device models or fixed navigation sizes.
     */
    fun activateForWebUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, rootView).show(WindowInsetsCompat.Type.systemBars())
        ViewCompat.requestApplyInsets(rootView)
    }

    fun onPageReady() {
        pageReady = true
        pushInsetsToWebUi()
    }

    fun refresh() {
        activateForWebUi()
        pushInsetsToWebUi()
    }

    private fun pushInsetsToWebUi() {
        if (!pageReady) return
        webView.post {
            webView.evaluateJavascript(
                "window.retraSetNativeSafeInsets && window.retraSetNativeSafeInsets(" +
                    "$safeTopCssPx,$safeRightCssPx,$safeBottomCssPx,$safeLeftCssPx);",
                null
            )
        }
    }
}
