package com.retra.emulator

import android.app.Activity
import android.net.Uri
import android.view.View
import android.widget.ImageView
import org.json.JSONArray

/** Coordinates optional shader install/selection without putting GL policy in MainActivity. */
class ShaderController(
    private val activity: Activity,
    private val repository: ShaderRepository,
    private val normalView: ImageView,
    private val shaderView: ShaderGameView,
    private val onSettingsChanged: () -> Unit,
    private val latestFrame: () -> FrameSnapshot?,
    private val isGameplayVisible: () -> Boolean
) {
    data class FrameSnapshot(val pixels: IntArray, val width: Int, val height: Int)

    fun selectedId(): String = repository.selectedId()
    fun optionsJson(): JSONArray = repository.optionsJson()

    fun installAndSelect(uri: Uri) {
        val result = runCatching { repository.install(uri) }
        activity.runOnUiThread {
            result.onSuccess { option ->
                repository.select(option.id)
                applySelection(showToast = true)
                onSettingsChanged()
            }.onFailure { error ->
                RetraNotice.makeText(activity, "Could not install shader: ${error.message ?: "invalid GLSL"}", RetraNotice.LENGTH_LONG).show()
            }
        }
    }

    fun select(id: String, showToast: Boolean = true) {
        repository.select(id)
        onSettingsChanged()

        // Built-in shaders ship with Retra; there is nothing to install. When
        // selection happens from the normal Settings page the GLSurfaceView is
        // intentionally hidden and may not own a current EGL context yet. Store
        // the choice now and compile lazily when native gameplay becomes visible.
        if (!isGameplayVisible()) {
            normalView.visibility = View.VISIBLE
            shaderView.visibility = View.GONE
            if (showToast) {
                val label = repository.optionFor(repository.selectedId())?.label ?: "Off"
                val message = if (repository.selectedId() == "none") {
                    "Video shader: Off"
                } else {
                    "$label selected • applies when game starts"
                }
                RetraNotice.makeText(activity, message, RetraNotice.LENGTH_SHORT).show()
            }
            return
        }

        applySelection(showToast)
    }

    fun configure(stretch: Boolean, linearFiltering: Boolean) {
        shaderView.configure(stretch, linearFiltering)
    }

    @Suppress("UNUSED_PARAMETER")
    fun presentIfActive(pixels: IntArray, width: Int, height: Int): Boolean {
        if (shaderView.visibility != View.VISIBLE) return false
        // The active gameplay path is mailbox-backed. Keep the parameters for
        // source compatibility with older callers, but avoid copying the frame
        // on the UI thread; the GL thread acquires it directly.
        shaderView.requestLatestFrame()
        return true
    }

    fun requestPresentLatest(): Boolean {
        if (shaderView.visibility != View.VISIBLE) return false
        shaderView.requestLatestFrame()
        return true
    }

    fun onPause() = runCatching { shaderView.onPause() }.getOrNull()
    fun onResume() = runCatching { shaderView.onResume() }.getOrNull()

    fun applySelection(showToast: Boolean) {
        val selected = repository.selectedId()

        // Do not force a shader compile while Retra is on Home/Settings. Some
        // OEMs destroy the SurfaceView EGL surface while its parent is hidden,
        // and compiling there can return shader=0 with an empty info log.
        if (!isGameplayVisible()) {
            normalView.visibility = View.VISIBLE
            shaderView.visibility = View.GONE
            return
        }
        val source = repository.sourceFor(selected)
        if (selected == "none" || source == null) {
            // Normal gameplay also uses the dedicated GL surface. The ImageView is
            // retained only as an OEM compatibility fallback, so the UI thread no
            // longer performs Bitmap.setPixels() on every visible game frame.
            normalView.visibility = View.INVISIBLE
            shaderView.visibility = View.VISIBLE
            if (selected != "none") repository.select("none")
            shaderView.usePassthrough { ok, _ ->
                if (!ok) {
                    shaderView.visibility = View.GONE
                    normalView.visibility = View.VISIBLE
                }
            }
            if (showToast) RetraNotice.makeText(activity, "GLSL shader: None", RetraNotice.LENGTH_SHORT).show()
            return
        }

        normalView.visibility = View.INVISIBLE
        shaderView.visibility = View.VISIBLE
        shaderView.setFragmentShader(source) { ok, error ->
            if (!ok) {
                repository.select("none")
                val detail = error?.trim().takeUnless { it.isNullOrEmpty() } ?: "unsupported by this GPU"
                // Fall back to the same GPU presenter rather than moving gameplay
                // back onto the UI-thread ImageView path.
                shaderView.usePassthrough { passthroughOk, _ ->
                    if (!passthroughOk) {
                        shaderView.visibility = View.GONE
                        normalView.visibility = View.VISIBLE
                    }
                }
                RetraNotice.makeText(activity, "Shader unavailable on this device: $detail", RetraNotice.LENGTH_LONG).show()
                onSettingsChanged()
            } else {
                latestFrame()?.let { shaderView.submitFrame(it.pixels, it.width, it.height) }
                if (showToast) {
                    val label = repository.optionFor(selected)?.label ?: "Custom"
                    val suffix = if (error == "compatibility-mode") " • compatibility mode" else ""
                    RetraNotice.makeText(activity, "Video shader: $label$suffix", RetraNotice.LENGTH_SHORT).show()
                }
            }
        }
    }
}
