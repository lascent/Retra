package com.retra.emulator

import android.os.Build
import android.view.View
import android.webkit.WebView

/** Lightweight WebView rendering policy for Retra's always-warm UI shell. */
object WebUiPerformanceTuner {
    fun apply(webView: WebView) {
        // Android normally hardware-accelerates WebView, but being explicit
        // prevents accidental software fallback after future UI changes.
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Retra keeps the WebView attached while gameplay is shown. Keeping
            // nearby tiles raster-ready makes returning Home less likely to
            // expose an unpainted/blank frame.
            webView.settings.offscreenPreRaster = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // The WebView is the primary application UI; don't waive renderer
            // priority just because native gameplay temporarily covers it.
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        }
    }
}
