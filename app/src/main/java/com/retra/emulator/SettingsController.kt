package com.retra.emulator

import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.provider.DocumentsContract
import android.view.View
import android.widget.ImageView
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.io.File
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
import com.retra.emulator.MainActivity.Companion.CONTROLLER_SOUND_PREF
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
internal fun MainActivity.persistTreePermission(uri: Uri): Boolean {
    val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    val persisted = runCatching {
        contentResolver.takePersistableUriPermission(uri, readWrite)
        true
    }.getOrDefault(false)

    // Some document providers are picky about the exact mode passed to
    // takePersistableUriPermission. Retra requires durable write access; retry
    // with write-only before rejecting the folder.
    if (!persisted) {
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
    }
    return hasPersistedTreeWriteAccess(uri)
}

internal fun MainActivity.hasPersistedTreeWriteAccess(uri: Uri): Boolean {
    val hasPersistedWrite = runCatching {
        contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isWritePermission
        }
    }.getOrDefault(false)
    if (!hasPersistedWrite) return false
    return runCatching {
        DocumentFile.fromTreeUri(this, uri)?.let { it.exists() && it.isDirectory && it.canWrite() } == true
    }.getOrDefault(false)
}

internal fun MainActivity.settingsStateJson(): String = JSONObject().apply {
    put("cloudSync", prefs.getBoolean(CLOUD_SYNC_ENABLED_PREF, false))
    put("automaticArtwork", prefs.getBoolean(AUTO_ARTWORK_PREF, true))
    put("artworkWifiOnly", prefs.getBoolean(ARTWORK_WIFI_ONLY_PREF, false))
    val cloudMode = cloudSync.mode()
    val cloudAccount = prefs.getString(CLOUD_SYNC_ACCOUNT_PREF, "") ?: ""
    put("cloudSyncMode", cloudMode)
    put("cloudFolderConnected", cloudAccount.isNotBlank())
    put("cloudAccount", cloudAccount)
    put("cloudLastBackupAt", cloudSync.lastSuccessfulSyncAt())
    put("cloudLastRestoreAt", cloudSync.lastRestoreAt())
    put("cloudLastSyncError", cloudSync.lastSyncError())
    put("cloudLastUploaded", cloudSync.lastUploadedCount())
    put("cloudLastDownloaded", cloudSync.lastDownloadedCount())
    put("cloudLastConflicts", cloudSync.lastConflictCount())
    put("cloudTransferActive", cloudSync.transferActive())
    put("cloudTransferLabel", cloudSync.transferLabel())
    put("cloudTransferProgress", cloudSync.transferProgress())
    put("enableCheats", prefs.getBoolean(ENABLE_CHEATS_PREF, true))
    put("romPatching", prefs.getBoolean(ROM_PATCHING_PREF, true))
    put("autoSaveLoad", prefs.getBoolean(AUTO_SAVE_LOAD_PREF, true))
    put("confirmCloseReset", prefs.getBoolean(CONFIRM_CLOSE_RESET_PREF, true))
    put("controllerSound", prefs.getBoolean(CONTROLLER_SOUND_PREF, true))
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

internal fun MainActivity.storageSummaryJson(): String {
    val stat = StatFs(filesDir.absolutePath)
    fun decimalGb(bytes: Long): Long = kotlin.math.round(bytes / 1_000_000_000.0).toLong()
    return JSONObject()
        .put("availableGb", decimalGb(stat.availableBytes))
        .put("totalGb", decimalGb(stat.totalBytes))
        .toString()
}

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
        "controllerSound" -> editor.putBoolean(CONTROLLER_SOUND_PREF, value.toBoolean())
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
    // The preference cache is updated synchronously by RetraPreferences.apply(),
    // so the portable settings snapshot can be queued immediately.
    syncCloudAsync(showResult = false)
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

/**
 * Fast path for settings changed continuously by range controls.
 *
 * The generic updateSetting() path intentionally reapplies the complete runtime
 * settings surface and refreshes portable metadata. That is correct for discrete
 * toggles, but wasteful for a slider that may emit dozens of events per second.
 * This path updates only the affected subsystem while a drag is active and does
 * one portable-metadata refresh when the UI reports the final value.
 */
internal fun MainActivity.updateRangeSetting(key: String, rawValue: Int, commit: Boolean): Boolean {
    val value = when (key) {
        "buttonsOpacity" -> rawValue.coerceIn(25, 100)
        "frameSkip" -> rawValue.coerceIn(0, 10)
        "volume" -> rawValue.coerceIn(0, 100)
        "smcCheck" -> rawValue.coerceIn(0, 10)
        else -> return false
    }

    // Preview events are intentionally transient. Persisting DataStore/portable
    // preference state for every pointer move creates avoidable allocations and
    // storage work while the user drags a slider. The native subsystem still
    // receives the preview immediately; only the final committed value is saved.
    if (commit) {
        when (key) {
            "buttonsOpacity" -> prefs.edit().putInt(BUTTON_OPACITY_PREF, value).apply()
            "frameSkip" -> prefs.edit().putInt(FRAME_SKIP_PREF, value).apply()
            "volume" -> prefs.edit().putInt(VOLUME_PREF, value).apply()
            "smcCheck" -> prefs.edit().putInt(SMC_CHECK_PREF, value).apply()
        }
    }

    runOnUiThread {
        when (key) {
            "buttonsOpacity" -> {
                preferredButtonsOpacity = value / 100f
                applyNativeButtonsOpacity()
            }
            "frameSkip" -> {
                try { setCoreConfigOption("frameskip", value.toString()) } catch (_: Throwable) {}
            }
            "volume" -> audioController.setVolume(value / 100f)
            "smcCheck" -> {
                try { setCoreConfigOption("retra.syncCheckLevel", value.toString()) } catch (_: Throwable) {}
            }
        }
    }

    // Keep the expensive portable settings.json rewrite/export off the hot drag
    // path. The final change/pointer release always commits the latest value.
    if (commit) syncAppFolderAsync(showResult = false)
    if (commit) cloudSync.requestAutoSync(urgent = false)
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
    syncCloudAsync(showResult = false)
}

internal fun MainActivity.openImportPicker() {
    importPicker.launch(arrayOf("*/*"))
}

internal fun MainActivity.requestCloudSyncAccount() {
    pendingCloudEnable = true
    pendingCloudRestore = false
    pendingCloudAccount = null
    cloudSync.launchAccountChooser({ cloudAccountPicker.launch(it) }) {
        prefs.edit().putBoolean(CLOUD_SYNC_ENABLED_PREF, false).apply()
        pendingCloudEnable = false
        pendingCloudRestore = false
        notifyWebSettingsState()
        RetraNotice.makeText(this, "Google account picker is unavailable on this device", RetraNotice.LENGTH_LONG).show()
    }
}

internal fun MainActivity.requestCloudRecoveryAccount() {
    pendingCloudEnable = false
    pendingCloudRestore = true
    pendingCloudAccount = null
    RetraNotice.makeText(
        this,
        "Choose the Google account that contains your Retra backup",
        RetraNotice.LENGTH_LONG
    ).show()
    cloudSync.launchAccountChooser({ cloudAccountPicker.launch(it) }) {
        pendingCloudRestore = false
        notifyWebSettingsState()
        RetraNotice.makeText(this, "Google account picker is unavailable on this device", RetraNotice.LENGTH_LONG).show()
    }
}

internal fun MainActivity.openAppFolderInternal() {
    // Keep the provider-backed Retra root current before handing it to Android Files.
    syncAppFolderAsync(showResult = false)

    val grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    // Do not preflight these intents with PackageManager.resolveActivity().
    // On some Android/OEM builds package visibility can hide the handler from
    // resolveActivity() even though startActivity() can route it correctly.
    // Try both standardized DocumentsProvider deep-link forms instead.
    val browseIntents = listOf(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(RetraDocumentsProvider.rootUri(), DocumentsContract.Root.MIME_TYPE_ITEM)
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(grantFlags)
        },
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                RetraDocumentsProvider.rootDocumentUri(),
                DocumentsContract.Document.MIME_TYPE_DIR
            )
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(grantFlags)
        }
    )

    val opened = browseIntents.any { intent ->
        runCatching {
            startActivity(intent)
            true
        }.getOrDefault(false)
    }

    if (!opened) {
        // The Retra provider is still available in Android's document navigator,
        // but this device's file manager does not support direct folder deep links.
        RetraNotice.makeText(
            this,
            "Open Android Files and choose Retra from the storage locations",
            RetraNotice.LENGTH_LONG
        ).show()
    }
}

internal fun MainActivity.deleteCloudPathsAsync(paths: List<String>) { cloudSync.deletePaths(paths) }

internal fun MainActivity.syncCloudAsync(showResult: Boolean) {
    // Keep Retra's provider-backed local metadata current independently of cloud backup.
    syncAppFolderAsync(showResult = false)
    if (showResult) cloudSync.backupNow(showResult = true)
    else cloudSync.requestAutoSync(urgent = false)
}

internal fun MainActivity.flushCloudBackupAsync() {
    // Lifecycle exit is the last reliable point before Android may kill the
    // process. Queue metadata first, then place an urgent Drive pass behind it
    // on the same serialized I/O executor.
    syncAppFolderAsync(showResult = false)
    cloudSync.requestAutoSync(urgent = true)
}

internal fun MainActivity.syncAppFolderAsync(showResult: Boolean) {
    // The DocumentsProvider exposes the exact persistent_data directory Retra
    // already writes to, so saves never need to be copied to a selected tree.
    // Keep only the coalesced metadata refresh so settings.json stays current
    // without making slider changes or rapid save-state writes expensive.
    appFolderSyncDirty.set(true)
    if (!appFolderSyncQueued.compareAndSet(false, true)) return

    runCatching {
        ioExecutor.execute {
            try {
                do {
                    appFolderSyncDirty.set(false)
                    runCatching { writePortableMetadataFiles() }
                    RetraDocumentsProvider.notifyDataChanged(this)
                } while (appFolderSyncDirty.get())
            } finally {
                appFolderSyncQueued.set(false)
                if (appFolderSyncDirty.get()) syncAppFolderAsync(showResult = false)
            }
            if (showResult) runOnUiThread {
                RetraNotice.makeText(
                    this,
                    "Retra app folder is always active • no folder permission needed",
                    RetraNotice.LENGTH_LONG
                ).show()
            }
        }
    }.onFailure {
        appFolderSyncQueued.set(false)
    }
}

internal fun MainActivity.exportSaveDataToTreeAsync(uri: Uri, showResult: Boolean) {
    // Kept for explicit one-off export callers; the built-in Retra app folder
    // itself no longer depends on a user-selected SAF tree.
    ioExecutor.execute {
        val exported = runCatching { saveTransfer.export(uri) }.getOrDefault(0)
        if (showResult) runOnUiThread {
            RetraNotice.makeText(this, "$exported Retra data file${if (exported == 1) "" else "s"} exported", RetraNotice.LENGTH_LONG).show()
        }
    }
}
