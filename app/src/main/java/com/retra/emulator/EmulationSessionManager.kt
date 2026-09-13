package com.retra.emulator

import android.graphics.Color
import android.os.Process
import android.view.View
import java.io.File
import java.util.Locale
import com.retra.emulator.MainActivity.Companion.AUTO_SAVE_LOAD_PREF
import com.retra.emulator.MainActivity.Companion.CPU_CORE_PREF
import com.retra.emulator.MainActivity.Companion.ENABLE_CHEATS_PREF
import com.retra.emulator.MainActivity.Companion.FRAME_TIME_NS
import com.retra.emulator.MainActivity.Companion.PLATFORM_GB
import com.retra.emulator.MainActivity.Companion.PLATFORM_GBA

/**
 * ROM-session lifecycle, auto-resume, frame loop and clean shutdown.
 * Working JNI calls remain on MainActivity; this module only orchestrates them.
 * Successful ROM-load banners are intentionally suppressed for an unobtrusive UI.
 */
internal fun MainActivity.normalizedSaveBase(value: String): String = value.substringBeforeLast('.', value)
    .lowercase(Locale.US)
    .replace(Regex("[^a-z0-9]+"), "")

internal fun MainActivity.saveAutoState(force: Boolean = false): Boolean {
    if (!romLoaded || (!force && !prefs.getBoolean(AUTO_SAVE_LOAD_PREF, true))) return false
    val file = saveStates.autoFile(currentRomId)
    file.parentFile?.mkdirs()
    val tempState = File(file.parentFile, ".${file.name}.${System.nanoTime()}.tmp")
    val nativeSaved = try { quickSaveState(tempState.absolutePath) } catch (_: Throwable) { false }
    val ok = nativeSaved && tempState.exists() && tempState.length() > 0L &&
        runCatching { fileOps.atomicCopyVerified(tempState, file); true }.getOrDefault(false)
    tempState.delete()
    if (ok && file.exists() && file.length() > 0L) {
        file.setLastModified(System.currentTimeMillis())
        saveStates.writeAutoMetadata(currentRomId)
        syncCloudAsync(showResult = false)
        return true
    }
    return false
}

internal fun MainActivity.saveAutoStateIfEnabled(): Boolean = saveAutoState(force = false)

internal fun MainActivity.loadAutoState(force: Boolean = false): Boolean {
    if (!romLoaded || (!force && !prefs.getBoolean(AUTO_SAVE_LOAD_PREF, true))) return false
    val file = saveStates.autoFile(currentRomId)
    if (!file.exists() || file.length() <= 0L || !saveStates.isAutoCompatible(currentRomId)) return false
    return try { quickLoadState(file.absolutePath) } catch (_: Throwable) { false }
}

internal fun MainActivity.loadAutoStateIfEnabled(): Boolean = loadAutoState(force = false)

internal fun MainActivity.loadRomFile(file: File, requestedTitle: String, patchFile: File? = null, romId: String? = null) {
    stopEmulation()
    // Persist the previous session before mGBA replaces its native core.
    // This also makes switching directly from one ROM to another safe.
    if (romLoaded) commitActiveWorkingSaves()
    applyRuntimeSettingsToNative()

    val resolvedRomId = romId?.ifBlank { fileOps.sanitizeFileName(requestedTitle) }
        ?: fileOps.sanitizeFileName(requestedTitle.ifBlank { file.nameWithoutExtension })
    clearActiveLinkSaveTracking()
    val saveFile = try {
        saveData.prepareWorkingSave(resolvedRomId, 0, file)
    } catch (e: Exception) {
        RetraNotice.makeText(this, "Could not prepare save data: ${e.message ?: "storage error"}", RetraNotice.LENGTH_LONG).show()
        return
    }

    val loaded = try {
        if (patchFile != null) {
            loadRomWithPatch(file.absolutePath, patchFile.absolutePath, saveFile.absolutePath)
        } else {
            loadRom(file.absolutePath, saveFile.absolutePath)
        }
    } catch (_: Throwable) {
        false
    }

    if (!loaded) {
        romLoaded = false
        pendingStateLoadRomId = null
        pendingStateLoadSlot = null
        RetraNotice.makeText(
            this,
            if (patchFile != null) "mGBA could not load this ROM + patch" else "mGBA could not load this ROM",
            RetraNotice.LENGTH_LONG
        ).show()
        return
    }

    configureVideoSurfaceFromNative()
    applyGameplayVisualSettings()

    localLinkActive = false
    localLinkPlayer = 0
    currentRomPath = file.absolutePath
    currentPatchPath = patchFile?.absolutePath
    currentRomExtension = file.extension.lowercase(Locale.US)
    currentRomId = resolvedRomId
    currentRomTitle = requestedTitle.ifBlank { file.nameWithoutExtension }
    romLoaded = true
    audioController.configure(romLoaded)
    if (prefs.getBoolean(ENABLE_CHEATS_PREF, true)) {
        applyStoredCheatsToCore()
    } else {
        try { clearNativeCheats() } catch (_: Throwable) {}
    }

    val requestedStateSlot = pendingStateLoadSlot
    val shouldLoadPendingState =
        requestedStateSlot != null && pendingStateLoadRomId == currentRomId
    val shouldForceAutoResume = pendingForceAutoResumeRomId == currentRomId
    pendingStateLoadRomId = null
    pendingStateLoadSlot = null
    pendingForceAutoResumeRomId = null
    if (shouldLoadPendingState) {
        loadStateFromSlot(requestedStateSlot!!)
    } else if (shouldForceAutoResume) {
        // Resume is an explicit user action, so restore the last close/back
        // state even when the general Auto save/load preference is off.
        loadAutoState(force = true)
    } else {
        loadAutoStateIfEnabled()
    }

    activeEmulationSpeed = 1.0
    updateFastForwardUi()

    val platform = getPlatform()
    currentPlatform = platform
    val system = when (platform) {
        PLATFORM_GBA -> "Game Boy Advance"
        PLATFORM_GB -> when (currentRomExtension) {
            "gbc" -> "Game Boy Color"
            "gb" -> "Game Boy"
            else -> "Game Boy / Color"
        }
        else -> "mGBA"
    }

    binding.gameTitle.text = currentRomTitle
    binding.systemLabel.text = if (patchFile != null) "$system • Patched" else system

    val gbaShoulders = platform == PLATFORM_GBA
    binding.buttonL.visibility = if (gbaShoulders) View.VISIBLE else View.INVISIBLE
    binding.buttonR.visibility = if (gbaShoulders) View.VISIBLE else View.INVISIBLE

    showEmulatorUiKeepingWebWarm()
    enterEmulatorPresentation()
    scheduleNativeEmulatorLayout(120L)

    binding.webView.evaluateJavascript(
        "window.retraNativeRomStarted && window.retraNativeRomStarted(${jsQuote(currentRomTitle)}, ${jsQuote(system)})",
        null
    )
    startEmulation()
}

internal fun MainActivity.startEmulation() {
    if (emulatorRunning || !romLoaded) return

    if (localLinkActive) {
        try { setLocalLinkPaused(false) } catch (_: Throwable) {}
    }
    statistics.begin(romLoaded)
    audioController.play()
    if (hasDisplayPerformanceManager()) displayPerformanceManager.applyGameplayMode()
    if (hasGameplayFramePresenter()) gameplayFramePresenter.start()
    emulatorRunning = true
    emulatorThread = Thread {
        val cpuProfile = prefs.getString(CPU_CORE_PREF, "Automatic")
        runCatching {
            Process.setThreadPriority(
                when {
                    cpuProfile.equals("Performance", ignoreCase = true) -> Process.THREAD_PRIORITY_URGENT_DISPLAY
                    cpuProfile.equals("Compatibility", ignoreCase = true) -> Process.THREAD_PRIORITY_DEFAULT
                    else -> Process.THREAD_PRIORITY_DISPLAY
                }
            )
        }
        val framePacer = EmulationFramePacer()
        val precisionWindow = EmulationFramePacer.precisionWindowForProfile(cpuProfile)
        framePacer.reset()

        while (emulatorRunning) {
            var successful = false
            val speed = EmulationSpeedPolicy.sanitize(activeEmulationSpeed)
            val loops = if (speed >= 1.0) speed.toInt().coerceIn(1, 16) else 1

            for (i in 0 until loops) {
                if (!emulatorRunning) break
                successful = runFrame(framePixels)
                if (!successful) break

                // mGBA produces PCM into its native ring buffer. Drain it after
                // every core frame so the ring never overflows, but defer the
                // Android write until the final turbo sub-frame. AudioController
                // time-compresses/expands PCM to the same 0.2x..16x game speed,
                // so music and SFX stay synchronized instead of muting in turbo.
                audioController.pump(
                    speed = speed,
                    flushOutput = (i == loops - 1)
                )
            }

            // If a core frame failed or gameplay was stopped in the middle of a
            // turbo batch, do not strand already transformed PCM in the queue.
            if (!successful || !emulatorRunning) {
                audioController.flushPendingOutput()
            }

            if (successful) {
                // mGBA owns the configured frameskip policy. Do not apply a
                // second Kotlin-side skip here: frameskip=1 already means the
                // core targets roughly 30 unique rendered FPS. Skipping again
                // in the presenter makes motion look substantially choppier.
                // Publish every completed core frame and let the VSync presenter
                // coalesce to the newest frame when the display is busy.
                synchronized(frameLock) {
                    val completedFrame = framePixels
                    framePixels = displayPixels
                    displayPixels = completedFrame
                }

                if (hasGameplayFramePresenter()) {
                    gameplayFramePresenter.requestPresent()
                }
            }

            val cadence = if (speed < 1.0) (FRAME_TIME_NS / speed).toLong() else FRAME_TIME_NS
            framePacer.waitForNext(cadence, precisionWindow)
        }
    }.apply {
        name = "Retra-mGBA"
        priority = when (prefs.getString(CPU_CORE_PREF, "Automatic")) {
            "Compatibility" -> Thread.NORM_PRIORITY
            "Performance" -> Thread.MAX_PRIORITY
            else -> Thread.MAX_PRIORITY - 1
        }
        start()
    }
}

internal fun MainActivity.stopEmulation() {
    if (hasGameplayFramePresenter()) gameplayFramePresenter.stop()
    statistics.commit(romLoaded, currentRomId)
    if (localLinkActive) {
        try { setLocalLinkPaused(true) } catch (_: Throwable) {}
    }
    emulatorRunning = false
    emulatorThread?.let {
        try {
            it.join(700)
        } catch (_: InterruptedException) {
        }
    }
    emulatorThread = null
    audioController.pauseAndFlush()
}

internal fun MainActivity.closeEmulator() {
    val closingRomId = currentRomId
    gameplayMenuDialog?.setOnDismissListener(null)
    gameplayMenuDialog?.dismiss()
    gameplayMenuDialog = null
    gameplayMenuBackHandler = null
    gameplayMenuSubscreen = false
    gameplayModalPauseActive = false
    inGameSettingsActive = false
    // Explicitly backing out of a game always creates a dedicated resume
    // state. Manual Quick Save (slot 0) stays untouched.
    if (romLoaded) saveAutoState(force = true)
    stopEmulation()

    releaseAllKeys()

    val closingRemote = remoteTransport.isActive
    val closingRemoteRole = remoteTransport.role
    val closingRemotePath = currentRomPath
    if (closingRemote) {
        remoteTransport.close(sendDisconnect = true)
    }

    if (romLoaded) {
        shutdownCore()
        commitActiveWorkingSaves()
        clearActiveLinkSaveTracking()
        if (closingRemote && !closingRemotePath.isNullOrBlank()) {
            finalizeRemoteRoleSave(closingRemotePath, closingRemoteRole)
        }
        // Battery-backed working saves are committed atomically after mGBA
        // closes, then synchronized to the user-selected Retra folder/cloud.
        syncCloudAsync(showResult = false)
    }

    audioController.release()
    localLinkActive = false
    localLinkPlayer = 0
    currentRomPath = null
    currentPatchPath = null
    romLoaded = false
    leaveEmulatorPresentation()
    showWebUiWithoutBlankFrame()

    if (closingRomId.isNotBlank()) {
        binding.webView.evaluateJavascript(
            "window.retraNativeGameClosed && window.retraNativeGameClosed(${jsQuote(closingRomId)})",
            null
        )
    }
    artworkRepository.resumeDeferred()
}
