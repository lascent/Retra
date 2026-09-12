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
    private val latestFrame: () -> FrameSnapshot?
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
        applySelection(showToast)
        onSettingsChanged()
    }

    fun configure(stretch: Boolean, linearFiltering: Boolean) {
        shaderView.configure(stretch, linearFiltering)
    }

    fun presentIfActive(pixels: IntArray, width: Int, height: Int): Boolean {
        if (shaderView.visibility != View.VISIBLE) return false
        shaderView.submitFrame(pixels, width, height)
        return true
    }

    fun onPause() = runCatching { shaderView.onPause() }.getOrNull()
    fun onResume() = runCatching { shaderView.onResume() }.getOrNull()

    fun applySelection(showToast: Boolean) {
        val selected = repository.selectedId()
        val source = repository.sourceFor(selected)
        if (selected == "none" || source == null) {
            shaderView.visibility = View.GONE
            normalView.visibility = View.VISIBLE
            if (selected != "none") repository.select("none")
            if (showToast) RetraNotice.makeText(activity, "GLSL shader: None", RetraNotice.LENGTH_SHORT).show()
            return
        }

        normalView.visibility = View.INVISIBLE
        shaderView.visibility = View.VISIBLE
        shaderView.setFragmentShader(source) { ok, error ->
            if (!ok) {
                repository.select("none")
                shaderView.visibility = View.GONE
                normalView.visibility = View.VISIBLE
                RetraNotice.makeText(activity, "Shader compile failed: ${error ?: "invalid GLSL"}", RetraNotice.LENGTH_LONG).show()
                onSettingsChanged()
            } else {
                latestFrame()?.let { shaderView.submitFrame(it.pixels, it.width, it.height) }
                if (showToast) {
                    val label = repository.optionFor(selected)?.label ?: "Custom"
                    RetraNotice.makeText(activity, "GLSL shader: $label", RetraNotice.LENGTH_SHORT).show()
                }
            }
        }
    }
}
