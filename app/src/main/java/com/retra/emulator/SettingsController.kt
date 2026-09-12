package com.retra.emulator

import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.ImageView
import org.json.JSONObject
import java.io.File
import com.retra.emulator.MainActivity.Companion.APP_FOLDER_URI_PREF
import com.retra.emulator.MainActivity.Companion.ARTWORK_WIFI_ONLY_PREF
import com.retra.emulator.MainActivity.Companion.AUTO_ARTWORK_PREF
import com.retra.emulator.MainActivity.Companion.AUTO_SAVE_LOAD_PREF
import com.retra.emulator.MainActivity.Companion.BIOS_GBA_PATH_PREF
import com.retra.emulator.MainActivity.Companion.BIOS_GBC_PATH_PREF
import com.retra.emulator.MainActivity.Companion.BIOS_GB_PATH_PREF
import com.retra.emulator.MainActivity.Companion.BIOS_LAST_LABEL_PREF
import com.retra.emulator.MainActivity.Companion.BOOT_BIOS_PREF
import com.retra.emulator.MainActivity.Companion.BUTTON_OPACITY_PREF
import com.retra.emulator.MainActivity.Companion.CARTRIDGE_SAVE_TYPE_PREF
import com.retra.emulator.MainActivity.Companion.CLOUD_SYNC_ACCOUNT_PREF
import com.retra.emulator.MainActivity.Companion.CLOUD_SYNC_ENABLED_PREF
import com.retra.emulator.MainActivity.Companion.CLOUD_SYNC_URI_PREF
import com.retra.emulator.MainActivity.Companion.COLOR_STYLE_PREF
import com.retra.emulator.MainActivity.Companion.CONFIRM_CLOSE_RESET_PREF
import com.retra.emulator.MainActivity.Companion.CPU_CORE_PREF
import com.retra.emulator.MainActivity.Companion.ENABLE_CHEATS_PREF
import com.retra.emulator.MainActivity.Companion.ENABLE_SOUND_PREF
import com.retra.emulator.MainActivity.Companion.FAST_FORWARD_BUTTON_MODE_PREF
import com.retra.emulator.MainActivity.Companion.FRAME_SKIP_PREF
import com.retra.emulator.MainActivity.Companion.FULLSCREEN_PREF
import com.retra.emulator.MainActivity.Companion.HARDWARE_RENDERING_PREF
import com.retra.emulator.MainActivity.Companion.IMMERSIVE_PREF
import com.retra.emulator.MainActivity.Companion.LINEAR_FILTERING_PREF
import com.retra.emulator.MainActivity.Companion.MOSAIC_EFFECT_PREF
import com.retra.emulator.MainActivity.Companion.ORIENTATION_PREF
import com.retra.emulator.MainActivity.Companion.ROM_PATCHING_PREF
import com.retra.emulator.MainActivity.Companion.SMC_CHECK_PREF
import com.retra.emulator.MainActivity.Companion.SOUND_FREQUENCY_PREF
import com.retra.emulator.MainActivity.Companion.SPEED_OPTIMIZATION_PREF
import com.retra.emulator.MainActivity.Companion.STRETCH_TO_FIT_PREF
import com.retra.emulator.MainActivity.Companion.USE_BIOS_PREF
import com.retra.emulator.MainActivity.Companion.VOLUME_PREF

/**
 * Settings, cloud-picker coordination and runtime preference application.
 * Extracted from MainActivity without changing preference keys or behavior.
 */
internal fun MainActivity.persistTreePermission(uri: Uri) {
    try {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    } catch (_: Exception) {
    }
}

internal fun MainActivity.settingsStateJson(): String = JSONObject().apply {
    put("cloudSync", prefs.getBoolean(CLOUD_SYNC_ENABLED_PREF, false))
    put("automaticArtwork", prefs.getBoolean(AUTO_ARTWORK_PREF, true))
    put("artworkWifiOnly", prefs.getBoolean(ARTWORK_WIFI_ONLY_PREF, false))
    val cloudMode = cloudSync.mode()
    val cloudAccount = prefs.getString(CLOUD_SYNC_ACCOUNT_PREF, "") ?: ""
    put("cloudSyncMode", cloudMode)
    put("cloudFolderConnected", if (cloudMode == "api") cloudAccount.isNotBlank() else !prefs.getString(CLOUD_SYNC_URI_PREF, null).isNullOrBlank())
    put("cloudAccount", cloudAccount)
    put("enableCheats", prefs.getBoolean(ENABLE_CHEATS_PREF, true))
    put("romPatching", prefs.getBoolean(ROM_PATCHING_PREF, true))
    put("autoSaveLoad", prefs.getBoolean(AUTO_SAVE_LOAD_PREF, true))
    put("confirmCloseReset", prefs.getBoolean(CONFIRM_CLOSE_RESET_PREF, true))
    put("fullScreenMode", prefs.getBoolean(FULLSCREEN_PREF, true))
    put("immersiveMode", prefs.getBoolean(IMMERSIVE_PREF, true))
    put("stretchToFit", prefs.getBoolean(STRETCH_TO_FIT_PREF, false))
    put("hardwareRendering", prefs.getBoolean(HARDWARE_RENDERING_PREF, true))
    put("linearFiltering", prefs.getBoolean(LINEAR_FILTERING_PREF, false))
    put("enableSound", prefs.getBoolean(ENABLE_SOUND_PREF, true))
    put("frameSkip", prefs.getInt(FRAME_SKIP_PREF, 0).coerceIn(0, 10))
    put("volume", prefs.getInt(VOLUME_PREF, 100).coerceIn(0, 100))
    put("soundFrequency", prefs.getInt(SOUND_FREQUENCY_PREF, 44100))
    put("cpuCore", prefs.getString(CPU_CORE_PREF, "Automatic") ?: "Automatic")
    put("cartridgeSaveType", prefs.getString(CARTRIDGE_SAVE_TYPE_PREF, "Automatic") ?: "Automatic")
    put("useBios", prefs.getBoolean(USE_BIOS_PREF, false))
    put("bootBios", prefs.getBoolean(BOOT_BIOS_PREF, false))
    put("smcCheck", prefs.getInt(SMC_CHECK_PREF, 4).coerceIn(0, 10))
    put("speedOptimization", prefs.getBoolean(SPEED_OPTIMIZATION_PREF, true))
    put("mosaicEffect", prefs.getBoolean(MOSAIC_EFFECT_PREF, true))
    put("fastForwardButtonMode", prefs.getString(FAST_FORWARD_BUTTON_MODE_PREF, "Press to toggle") ?: "Press to toggle")
    put("fastForwardSpeed", EmulationSpeedPolicy.token(preferredEmulationSpeed))
    put("emulationSpeed", EmulationSpeedPolicy.token(preferredEmulationSpeed))
    put("colorStyle", colorStyleController.selectedId())
    put("glslShader", shaderController.selectedId())
    put("glslShaderOptions", shaderController.optionsJson())
    put("screenOrientation", prefs.getString(ORIENTATION_PREF, "Auto rotate") ?: "Auto rotate")
    put("buttonsOpacity", prefs.getInt(BUTTON_OPACITY_PREF, 70).coerceIn(25, 100))
    put("biosFileLabel", prefs.getString(BIOS_LAST_LABEL_PREF, "No BIOS selected") ?: "No BIOS selected")
}.toString()

internal fun MainActivity.notifyWebSettingsState() {
    if (!hasBinding()) return
    runOnUiThread {
        val json = settingsStateJson()
        binding.webView.evaluateJavascript(
            "window.retraNativeSettingsChanged && window.retraNativeSettingsChanged(${jsQuote(json)})",
            null
        )
    }
}

internal fun MainActivity.applyRuntimeSettingsToNative() {
    val frameSkip = prefs.getInt(FRAME_SKIP_PREF, 0).coerceIn(0, 10)
    val sampleRate = prefs.getInt(SOUND_FREQUENCY_PREF, 44100).coerceIn(8000, 96000)
    val soundEnabled = prefs.getBoolean(ENABLE_SOUND_PREF, true)
    val speedOptimization = prefs.getBoolean(SPEED_OPTIMIZATION_PREF, true)
    val cpuMode = prefs.getString(CPU_CORE_PREF, "Automatic") ?: "Automatic"

    try { setCoreConfigOption("frameskip", frameSkip.toString()) } catch (_: Throwable) {}
    // Keep mGBA's mixer at full scale and apply the user's master volume once
    // at Android AudioTrack. Applying the same percentage in both places would
    // square the attenuation (50% would become ~25%) and make games too quiet.
    try { setCoreConfigOption("volume", "256") } catch (_: Throwable) {}
    try { setCoreConfigOption("mute", if (soundEnabled) "0" else "1") } catch (_: Throwable) {}
    try { setCoreConfigOption("sampleRate", sampleRate.toString()) } catch (_: Throwable) {}
    try { setCoreConfigOption("useBios", if (prefs.getBoolean(USE_BIOS_PREF, false)) "1" else "0") } catch (_: Throwable) {}
    try { setCoreConfigOption("skipBios", if (prefs.getBoolean(BOOT_BIOS_PREF, false)) "0" else "1") } catch (_: Throwable) {}
    prefs.getString(BIOS_GBA_PATH_PREF, null)?.takeIf { File(it).exists() }?.let {
        try { setCoreConfigOption("gba.bios", it) } catch (_: Throwable) {}
    }
    prefs.getString(BIOS_GB_PATH_PREF, null)?.takeIf { File(it).exists() }?.let {
        try { setCoreConfigOption("gb.bios", it) } catch (_: Throwable) {}
    }
    prefs.getString(BIOS_GBC_PATH_PREF, null)?.takeIf { File(it).exists() }?.let {
        try { setCoreConfigOption("gbc.bios", it) } catch (_: Throwable) {}
    }

    val idleMode = when {
        !speedOptimization -> "ignore"
        cpuMode.equals("Compatibility", ignoreCase = true) -> "ignore"
        cpuMode.equals("Performance", ignoreCase = true) -> "detect"
        else -> "detect"
    }
    try { setCoreConfigOption("idleOptimization", idleMode) } catch (_: Throwable) {}
    // Retra-native settings that mGBA does not expose as generic config
    // keys are applied directly in native-lib.cpp.
    try { setCoreConfigOption("retra.cartridgeSaveType", prefs.getString(CARTRIDGE_SAVE_TYPE_PREF, "Automatic") ?: "Automatic") } catch (_: Throwable) {}
    try { setCoreConfigOption("retra.mosaicEffect", if (prefs.getBoolean(MOSAIC_EFFECT_PREF, true)) "1" else "0") } catch (_: Throwable) {}
    try { setCoreConfigOption("retra.syncCheckLevel", prefs.getInt(SMC_CHECK_PREF, 4).coerceIn(0, 10).toString()) } catch (_: Throwable) {}
    applyGameplayVisualSettings()
}

internal fun MainActivity.applyGameplayVisualSettings() {
    if (!hasBinding()) return
    val stretch = prefs.getBoolean(STRETCH_TO_FIT_PREF, false)
    binding.gameScreen.scaleType = if (stretch) ImageView.ScaleType.FIT_XY else ImageView.ScaleType.FIT_CENTER
    val filter = prefs.getBoolean(LINEAR_FILTERING_PREF, false)
    (binding.gameScreen.drawable as? android.graphics.drawable.BitmapDrawable)?.isFilterBitmap = filter
    val hardware = prefs.getBoolean(HARDWARE_RENDERING_PREF, true)
    // The Activity window is already hardware accelerated. A forced hardware
    // layer is counterproductive for a bitmap that changes every frame because
    // Android must rebuild/re-upload that cached layer continuously. NONE uses
    // the normal GPU-accelerated display list; SOFTWARE remains an explicit
    // compatibility fallback for problematic devices.
    binding.gameScreen.setLayerType(if (hardware) View.LAYER_TYPE_NONE else View.LAYER_TYPE_SOFTWARE, null)
    colorStyleController.apply()
    shaderController.configure(stretch = stretch, linearFiltering = filter)
}

internal fun MainActivity.updateSetting(key: String, value: String): Boolean {
    val editor = prefs.edit()
    when (key) {
        "cloudSync" -> editor.putBoolean(CLOUD_SYNC_ENABLED_PREF, value.toBoolean())
        "automaticArtwork" -> editor.putBoolean(AUTO_ARTWORK_PREF, value.toBoolean())
        "artworkWifiOnly" -> editor.putBoolean(ARTWORK_WIFI_ONLY_PREF, value.toBoolean())
        "enableCheats" -> editor.putBoolean(ENABLE_CHEATS_PREF, value.toBoolean())
        "romPatching" -> editor.putBoolean(ROM_PATCHING_PREF, value.toBoolean())
        "autoSaveLoad" -> editor.putBoolean(AUTO_SAVE_LOAD_PREF, value.toBoolean())
        "confirmCloseReset" -> editor.putBoolean(CONFIRM_CLOSE_RESET_PREF, value.toBoolean())
        "fullScreenMode" -> editor.putBoolean(FULLSCREEN_PREF, value.toBoolean())
        "immersiveMode" -> editor.putBoolean(IMMERSIVE_PREF, value.toBoolean())
        "stretchToFit" -> editor.putBoolean(STRETCH_TO_FIT_PREF, value.toBoolean())
        "hardwareRendering" -> editor.putBoolean(HARDWARE_RENDERING_PREF, value.toBoolean())
        "linearFiltering" -> editor.putBoolean(LINEAR_FILTERING_PREF, value.toBoolean())
        "colorStyle" -> editor.putString(
            COLOR_STYLE_PREF,
            value.lowercase().takeIf { requested -> ColorStyleController.STYLES.any { it.id == requested } }
                ?: ColorStyleController.CLASSIC
        )
        "enableSound" -> editor.putBoolean(ENABLE_SOUND_PREF, value.toBoolean())
        "frameSkip" -> editor.putInt(FRAME_SKIP_PREF, value.toIntOrNull()?.coerceIn(0, 10) ?: 0)
        "volume" -> editor.putInt(VOLUME_PREF, value.toIntOrNull()?.coerceIn(0, 100) ?: 100)
        "soundFrequency" -> editor.putInt(SOUND_FREQUENCY_PREF, value.toIntOrNull()?.coerceIn(8000, 96000) ?: 44100)
        "cpuCore" -> editor.putString(CPU_CORE_PREF, value)
        "cartridgeSaveType" -> editor.putString(CARTRIDGE_SAVE_TYPE_PREF, value)
        "useBios" -> editor.putBoolean(USE_BIOS_PREF, value.toBoolean())
        "bootBios" -> editor.putBoolean(BOOT_BIOS_PREF, value.toBoolean())
        "smcCheck" -> editor.putInt(SMC_CHECK_PREF, value.toIntOrNull()?.coerceIn(0, 10) ?: 4)
        "speedOptimization" -> editor.putBoolean(SPEED_OPTIMIZATION_PREF, value.toBoolean())
        "mosaicEffect" -> editor.putBoolean(MOSAIC_EFFECT_PREF, value.toBoolean())
        "fastForwardButtonMode" -> editor.putString(FAST_FORWARD_BUTTON_MODE_PREF, value)
        else -> return false
    }
    editor.apply()
    if (key == "automaticArtwork" || key == "artworkWifiOnly") {
        artworkRepository.resumeDeferred()
    }
    runOnUiThread {
        applyRuntimeSettingsToNative()
        if (key == "enableSound" || key == "soundFrequency") {
            audioController.configure(romLoaded)
            if (romLoaded && emulatorRunning) audioController.play()
        } else if (key == "volume") {
            val volume = prefs.getInt(VOLUME_PREF, 100).coerceIn(0, 100) / 100f
            audioController.setVolume(volume)
        }

        if (key == "enableCheats" && romLoaded && !localLinkActive) {
            val synchronized = applyStoredCheatsToCore()
            if (!synchronized) {
                RetraNotice.makeText(
                    this,
                    "Could not update cheats in the running game",
                    RetraNotice.LENGTH_SHORT
                ).show()
            }
        }
        if (binding.emulatorOverlay.visibility == View.VISIBLE) enterEmulatorPresentation()
    }
    return true
}

internal fun MainActivity.resetAdvancedSettingsInternal() {
    prefs.edit()
        .putString(CPU_CORE_PREF, "Automatic")
        .putString(CARTRIDGE_SAVE_TYPE_PREF, "Automatic")
        .putBoolean(USE_BIOS_PREF, false)
        .putBoolean(BOOT_BIOS_PREF, false)
        .putInt(SMC_CHECK_PREF, 4)
        .putBoolean(SPEED_OPTIMIZATION_PREF, true)
        .putBoolean(MOSAIC_EFFECT_PREF, true)
        .apply()
    applyRuntimeSettingsToNative()
    notifyWebSettingsState()
}

internal fun MainActivity.openImportPicker() {
    importPicker.launch(arrayOf("*/*"))
}

internal fun MainActivity.cloudRootUri(): Uri? = prefs.getString(CLOUD_SYNC_URI_PREF, null)?.let {
    try { Uri.parse(it) } catch (_: Exception) { null }
}

internal fun MainActivity.requestCloudSyncAccount() {
    pendingCloudEnable = true
    pendingCloudAccount = null
    cloudSync.launchAccountChooser({ cloudAccountPicker.launch(it) }) {
        if (cloudRootUri() == null) prefs.edit().putBoolean(CLOUD_SYNC_ENABLED_PREF, false).apply()
        pendingCloudEnable = false
        notifyWebSettingsState()
        RetraNotice.makeText(this, "Google account picker is unavailable on this device", RetraNotice.LENGTH_LONG).show()
    }
}

internal fun MainActivity.requestCloudSyncFolder() {
    pendingCloudEnable = true
    val account = pendingCloudAccount ?: prefs.getString(CLOUD_SYNC_ACCOUNT_PREF, null)
    RetraNotice.makeText(this, cloudSync.folderPrompt(account), RetraNotice.LENGTH_LONG).show()
    cloudFolderPicker.launch(cloudRootUri())
}

internal fun MainActivity.openAppFolderInternal() {
    val existing = prefs.getString(APP_FOLDER_URI_PREF, null)?.let {
        try { Uri.parse(it) } catch (_: Exception) { null }
    }
    pendingAppFolderSelection = true
    if (existing == null) {
        RetraNotice.makeText(this, "Choose or create your Retra data folder", RetraNotice.LENGTH_LONG).show()
    }

    // Always use the registered picker so "Use this folder" returns the selected
    // tree to Retra. The picker callback persists the permission/path and only
    // then exports into the existing folder structure. This avoids exporting to
    // an old tree before the user's selection is confirmed.
    appFolderPicker.launch(existing)
}

internal fun MainActivity.deleteCloudPathsAsync(paths: List<String>) { cloudSync.deletePaths(paths) }

internal fun MainActivity.syncCloudAsync(showResult: Boolean) = cloudSync.sync(showResult)

internal fun MainActivity.exportSaveDataToTreeAsync(uri: Uri, showResult: Boolean) {
    ioExecutor.execute {
        val exported = runCatching { saveTransfer.export(uri) }.getOrDefault(0)
        if (showResult) runOnUiThread {
            RetraNotice.makeText(this, "$exported Retra data file${if (exported == 1) "" else "s"} exported", RetraNotice.LENGTH_LONG).show()
        }
    }
}
