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
import com.retra.emulator.MainActivity.Companion.FRAME_SKIP_PREF
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
    localLinkSinglePakActive = false
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
    // Built-in shaders are selected in Settings but compiled only after the
    // native game overlay has a real Surface/EGL context. This avoids false
    // compile failures on OEMs that tear down hidden GLSurfaceViews.
    shaderController.applySelection(showToast = false)
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

    // Pick one cadence-compatible gameplay mode for the whole session. Speed-mode
    // changes never switch the physical panel mode; that avoids a compositor hitch
    // every time fast-forward is toggled.
    if (hasDisplayPerformanceManager()) {
        displayPerformanceManager.applyGameplayMode(extremeTurbo = false)
    }
    val gameplayPresentationHz = if (hasDisplayPerformanceManager()) {
        displayPerformanceManager.preferredGameplayRefreshRateHz(extremeTurbo = false)
            .coerceAtMost(120f)
    } else {
        60f
    }
    // Retra presents turbo at a stable 60 or 120 Hz cadence. 90 Hz is deliberately
    // treated as 60 here because ~59.73 FPS GBA timing does not map evenly to 90 Hz.
    val turboPresentationHz = if (gameplayPresentationHz >= 100f) 120.0 else 60.0
    binding.shaderGameScreen.setPresentationFrameRate(turboPresentationHz.toFloat())

    if (hasGameplayFramePresenter()) {
        gameplayFramePresenter.start()
        gameplayFramePresenter.setContinuousVsync(false)
    }
    emulatorRunning = true
    emulatorThread = Thread {
        val cpuProfile = prefs.getString(CPU_CORE_PREF, "Automatic")
        val userFrameSkip = prefs.getInt(FRAME_SKIP_PREF, 0).coerceIn(0, 10)
        val framePacer = EmulationFramePacer()
        val turboGovernor = TurboThroughputGovernor(FRAME_TIME_NS)
        val turboSlicePlanner = TurboSlicePlanner()
        val performanceHints = EmulationPerformanceHints(this)
        val framePublishResult = GameplayFrameMailbox.PublishResult()
        val normalPrecisionWindow = EmulationFramePacer.precisionWindowForProfile(cpuProfile)
        framePacer.reset()
        turboGovernor.reset()
        performanceHints.start(FRAME_TIME_NS)

        // Automatic turbo renderer frameskip made the core advance correctly while
        // leaving an old framebuffer visible. That looked like dragging/low FPS.
        // Keep the user's configured value authoritative for the whole session.
        var activeCoreFrameSkip = -1
        var lastSpeed = -1.0

        while (emulatorRunning) {
            val speed = EmulationSpeedPolicy.sanitize(activeEmulationSpeed)
            val turbo = speed > 1.0

            if (kotlin.math.abs(speed - lastSpeed) > 0.0001) {
                framePacer.reset()
                turboGovernor.reset()
                turboSlicePlanner.reset(speed, FRAME_TIME_NS, turboPresentationHz)
                audioController.onSpeedChanged(speed)
                if (hasGameplayFramePresenter()) {
                    // During Speed Mode, keep one Choreographer callback armed per
                    // display VSync. The callback samples the mailbox generation and
                    // draws only when a newer frame exists, decoupling presentation
                    // cadence from producer-thread wake-up jitter.
                    gameplayFramePresenter.setContinuousVsync(turbo)
                }

                if (activeCoreFrameSkip != userFrameSkip) {
                    runCatching { setCoreConfigOption("frameskip", userFrameSkip.toString()) }
                    activeCoreFrameSkip = userFrameSkip
                }

                // Leave Android's RenderThread above fast-forward work. The emulator
                // still runs flat-out when needed, but it no longer starves the GL
                // consumer that makes the game screen look smooth.
                val priority = when {
                    turbo -> Process.THREAD_PRIORITY_MORE_FAVORABLE
                    cpuProfile.equals("Performance", ignoreCase = true) -> Process.THREAD_PRIORITY_DISPLAY
                    cpuProfile.equals("Compatibility", ignoreCase = true) -> Process.THREAD_PRIORITY_DEFAULT
                    else -> Process.THREAD_PRIORITY_MORE_FAVORABLE
                }
                runCatching { Process.setThreadPriority(priority) }
                lastSpeed = speed
            }

            val framesThisSlice = if (turbo) {
                // Fractional batching removes the slow cadence beat caused by
                // rounding every 60/120 Hz slice to one fixed integer size. The
                // cumulative governor still owns the exact 2x/4x/8x/16x speed.
                turboSlicePlanner.nextFrames()
            } else {
                1
            }
            val sliceCadenceNs = TurboFramePolicy.sliceCadenceNs(
                speed = speed,
                frameTimeNs = FRAME_TIME_NS,
                framesPerSlice = framesThisSlice
            )

            val workStartedNs = System.nanoTime()
            val successful = if (turbo) {
                // Hidden frames stay entirely native. Only the final state of this
                // display-sized slice crosses JNI, matching mature emulator designs.
                runTurboSlice(
                    framePixels,
                    framesThisSlice,
                    captureVideo = true,
                    discardAudio = false
                )
            } else {
                runFrame(framePixels)
            }
            val coreWorkNs = (System.nanoTime() - workStartedNs).coerceAtLeast(1L)

            if (successful) {
                // Producer -> latest-frame mailbox. Stale pending video is recycled;
                // the renderer never receives a queue of old fast-forward frames.
                gameplayFrameMailbox.publishLatest(framePixels, framePublishResult)
                framePixels = framePublishResult.producerBuffer
                if (hasGameplayFramePresenter()) {
                    gameplayFramePresenter.requestPresent(framePublishResult.generation)
                }

                // Audio is independent from presentation. Turbo time-compression is
                // performed natively so only playable wall-clock PCM crosses JNI;
                // AudioTrack writes remain isolated on the dedicated audio thread,
                // so audio cannot hold a completed video frame on the UI thread.
                audioController.pump(speed = speed, flushOutput = true)
            } else {
                audioController.flushPendingOutput()
            }

            val totalWorkNs = (System.nanoTime() - workStartedNs).coerceAtLeast(coreWorkNs)
            performanceHints.report(
                actualWorkNs = totalWorkNs,
                desiredTargetNs = sliceCadenceNs
            )

            if (turbo && successful) {
                // Exact cumulative wall-clock timing remains separate from rendering.
                // If a device cannot sustain the selected multiplier, waiting reaches
                // zero and the core runs flat-out; presentation still stays ordered.
                turboGovernor.onBatchComplete(speed, framesThisSlice)
            } else {
                framePacer.waitForNext(
                    sliceCadenceNs,
                    TurboFramePolicy.precisionWindowNs(speed, normalPrecisionWindow)
                )
            }
        }

        performanceHints.close()
        if (hasGameplayFramePresenter()) gameplayFramePresenter.setContinuousVsync(false)
        if (hasDisplayPerformanceManager()) displayPerformanceManager.applyGameplayMode(extremeTurbo = false)
        if (activeCoreFrameSkip != userFrameSkip) {
            runCatching { setCoreConfigOption("frameskip", userFrameSkip.toString()) }
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
    localLinkSinglePakActive = false
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
