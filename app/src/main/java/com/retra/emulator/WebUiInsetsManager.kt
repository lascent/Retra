package com.retra.emulator

import android.view.View
import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

/**
 * Keeps the packaged WebView UI clear of Android system navigation controls.
 *
 * Android 15+ can lay app content edge-to-edge even when the classic 3-button
 * navigation bar is visible. CSS safe-area-inset-bottom is not guaranteed to
 * expose that bar inside WebView, so Retra forwards the real native inset and
 * lets the web layer combine it with its viewport/safe-area fallback.
 */
class WebUiInsetsManager(
    private val rootView: View,
    private val webView: WebView
) {
    private var pageReady = false
    private var bottomInsetCssPx = 0

    fun install() {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout()).bottom
            val bottomPhysicalPx = maxOf(navigation, cutout)
            val density = rootView.resources.displayMetrics.density.coerceAtLeast(1f)
            val nextCssPx = (bottomPhysicalPx / density).roundToInt().coerceAtLeast(0)

            if (nextCssPx != bottomInsetCssPx) {
                bottomInsetCssPx = nextCssPx
                pushInsetsToWebUi()
            }
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
    }

    fun onPageReady() {
        pageReady = true
        pushInsetsToWebUi()
    }

    fun refresh() {
        ViewCompat.requestApplyInsets(rootView)
        pushInsetsToWebUi()
    }

    private fun pushInsetsToWebUi() {
        if (!pageReady) return
        webView.post {
            webView.evaluateJavascript(
                "window.retraSetNativeBottomInset && window.retraSetNativeBottomInset($bottomInsetCssPx);",
                null
            )
        }
    }
}
