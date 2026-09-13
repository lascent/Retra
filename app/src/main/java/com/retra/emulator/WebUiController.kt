package com.retra.emulator

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

/**
 * Owns WebView setup, asset routing, renderer recovery and file chooser state.
 * MainActivity only decides when the web surface is shown; WebView policy lives here.
 */
class WebUiController(
    private val activity: Activity,
    private val webView: WebView,
    private val fileOps: RetraFileOps,
    private val javascriptBridge: MainActivity.RetraBridge,
    private val onPageReady: () -> Unit,
    private val launchFileChooser: (Intent) -> Unit,
    private val onRendererGone: () -> Unit
) {
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun configure() {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(activity))
            .addPathHandler("/covers/", WebViewAssetLoader.InternalStoragePathHandler(activity, fileOps.persistentCategoryDir("Covers")))
            .addPathHandler("/backgrounds/", WebViewAssetLoader.InternalStoragePathHandler(activity, fileOps.persistentCategoryDir("Backgrounds")))
            .build()

        with(webView) {
            setBackgroundColor(Color.rgb(11, 12, 20))
            val isDebuggable = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            WebView.setWebContentsDebuggingEnabled(isDebuggable)

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = false
            settings.safeBrowsingEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.builtInZoomControls = false
            settings.displayZoomControls = false

            WebUiPerformanceTuner.apply(this)
            addJavascriptInterface(javascriptBridge, BRIDGE_NAME)

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val uri = request?.url ?: return null
                    return assetLoader.shouldInterceptRequest(uri)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    if (uri.scheme == "https" && uri.host == APP_ASSET_HOST) return false
                    if (uri.scheme in setOf("about", "data", "blob")) return false
                    return try {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        true
                    } catch (_: Exception) {
                        true
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    onPageReady()
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    view?.let { deadView ->
                        (deadView.parent as? ViewGroup)?.removeView(deadView)
                        deadView.destroy()
                    }
                    onRendererGone()
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = filePathCallback
                    return try {
                        val intent = fileChooserParams?.createIntent()
                            ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                type = "image/*"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            }
                        launchFileChooser(intent)
                        true
                    } catch (_: Exception) {
                        fileCallback?.onReceiveValue(null)
                        fileCallback = null
                        false
                    }
                }
            }

            loadUrl(APP_URL)
        }
    }

    fun deliverFileChooserResult(resultCode: Int, data: Intent?) {
        val callback = fileCallback
        fileCallback = null
        if (callback == null) return
        callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
    }

    fun destroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        webView.removeJavascriptInterface(BRIDGE_NAME)
        webView.stopLoading()
        webView.destroy()
    }

    private companion object {
        const val BRIDGE_NAME = "AndroidBridge"
        const val APP_ASSET_HOST = "appassets.androidplatform.net"
        const val APP_URL = "https://appassets.androidplatform.net/assets/retra/index.html"
    }
}
