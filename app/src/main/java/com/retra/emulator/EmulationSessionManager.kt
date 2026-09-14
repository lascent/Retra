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

    val initialSpeed = EmulationSpeedPolicy.sanitize(activeEmulationSpeed)
    if (hasDisplayPerformanceManager()) {
        displayPerformanceManager.applyGameplayMode(extremeTurbo = initialSpeed >= 4.0)
    }

    // Resolve both normal-gameplay and high-refresh turbo display cadences on the UI
    // thread. 4x/8x/16x may use 144/165 Hz when the panel exposes it; normal/2x
    // gameplay still prefers cadence-compatible 60/120 Hz modes.
    val gameplayPresentationHz = if (hasDisplayPerformanceManager()) {
        displayPerformanceManager.preferredGameplayRefreshRateHz(extremeTurbo = false)
    } else {
        60f
    }
    val extremePresentationHz = if (hasDisplayPerformanceManager()) {
        displayPerformanceManager.preferredGameplayRefreshRateHz(extremeTurbo = true)
    } else {
        gameplayPresentationHz
    }

    if (hasGameplayFramePresenter()) gameplayFramePresenter.start()
    emulatorRunning = true
    emulatorThread = Thread {
        val cpuProfile = prefs.getString(CPU_CORE_PREF, "Automatic")
        val userFrameSkip = prefs.getInt(FRAME_SKIP_PREF, 0).coerceIn(0, 10)
        val framePacer = EmulationFramePacer()
        val extremeGovernor = TurboThroughputGovernor(FRAME_TIME_NS)
        val throughputMonitor = TurboPerformanceMonitor(FRAME_TIME_NS)
        val turboBatchSequencer = TurboBatchSequencer(FRAME_TIME_NS)
        val performanceHints = EmulationPerformanceHints(this)
        val normalPrecisionWindow = EmulationFramePacer.precisionWindowForProfile(cpuProfile)
        framePacer.reset()
        extremeGovernor.reset()
        throughputMonitor.reset()
        performanceHints.start(FRAME_TIME_NS)

        var lastSpeed = -1.0
        var activeCoreFrameSkip = -1
        var adaptiveQualityFallbackEngaged = false
        var turboAudioMuted = false
        var governorAlignedToVsync = false
        var presentationIntervalNs = TurboFramePolicy.presentationIntervalNs(gameplayPresentationHz, 1.0)
        var nextVideoDeadlineNs = System.nanoTime()
        var forceNextVideoCapture = true

        while (emulatorRunning) {
            val speed = EmulationSpeedPolicy.sanitize(activeEmulationSpeed)
            val turbo = speed > 1.0
            val highRefreshTurbo = speed >= 4.0

            if (kotlin.math.abs(speed - lastSpeed) > 0.0001) {
                framePacer.reset()
                extremeGovernor.reset()
                throughputMonitor.reset()
                turboBatchSequencer.reset()
                adaptiveQualityFallbackEngaged = false
                governorAlignedToVsync = false
                forceNextVideoCapture = true
                val targetPresentationHz = if (highRefreshTurbo) extremePresentationHz else gameplayPresentationHz
                presentationIntervalNs = TurboFramePolicy.presentationIntervalNs(
                    targetPresentationHz,
                    speed
                )
                nextVideoDeadlineNs = System.nanoTime()

                if (hasGameplayFramePresenter()) {
                    gameplayFramePresenter.setContinuousVsync(speed >= 4.0)
                }
                if (hasDisplayPerformanceManager()) {
                    // applyGameplayMode posts the window-mode change onto the UI thread.
                    displayPerformanceManager.applyGameplayMode(extremeTurbo = highRefreshTurbo)
                }

                // Protect the selected multiplier immediately: mGBA may skip internal
                // renderer work that the display cannot use, while every CPU/game
                // frame still executes. Additional skipping is only added on a measured miss.
                val desiredCoreFrameSkip = TurboFramePolicy.speedProtectingCoreFrameSkip(
                    speed = speed,
                    userFrameSkip = userFrameSkip,
                    frameTimeNs = FRAME_TIME_NS,
                    presentationHz = targetPresentationHz
                )
                if (desiredCoreFrameSkip != activeCoreFrameSkip) {
                    runCatching { setCoreConfigOption("frameskip", desiredCoreFrameSkip.toString()) }
                    activeCoreFrameSkip = desiredCoreFrameSkip
                }

                // Keep v1.0.1-style turbo audio audible by default. The controller
                // now uses its lightweight integer averaging path instead of the newer
                // native FIR; only a severe measured throughput failure may mute it.
                if (turboAudioMuted) {
                    audioController.setTurboMuted(false)
                    turboAudioMuted = false
                }

                runCatching {
                    Process.setThreadPriority(TurboFramePolicy.processThreadPriority(speed, cpuProfile))
                }
                lastSpeed = speed
            }

            val presentationNowNs = System.nanoTime()
            val baselineFramesThisSlice = if (turbo) {
                // Quality fallback never enlarges the native batch. Keeping short
                // batches preserves fresh visual states and input cadence even if
                // the ROM temporarily misses its requested throughput.
                TurboFramePolicy.framesPerSlice(speed, FRAME_TIME_NS)
            } else {
                1
            }

            // At 4x/8x/16x, align useful native batches to the selected display mode
            // whenever the CPU has headroom. This unlocks fresh 144/165 Hz snapshots
            // instead of being capped near 119.455 short batches/s and removes the
            // slow beat against 120 Hz. Cumulative emulation time remains governed
            // by the exact selected speed.
            val syncExtremeCandidate = TurboFramePolicy.canSynchronizeExtremeTurboBatchToDisplay(
                speed = speed,
                frameTimeNs = FRAME_TIME_NS,
                refreshRateHz = extremePresentationHz
            )
            val lastVsyncNs = if (syncExtremeCandidate && hasGameplayFramePresenter()) {
                gameplayFramePresenter.latestVsyncNanos()
            } else {
                0L
            }
            val syncExtremeToDisplay = syncExtremeCandidate && lastVsyncNs > 0L

            if (syncExtremeToDisplay && !governorAlignedToVsync) {
                // Rebase the cumulative extreme-turbo clock once to a real Choreographer
                // timestamp. From here, the fractional batch sequence stays phase-
                // bounded around VSync without altering the requested game speed.
                extremeGovernor.reset(lastVsyncNs)
                turboBatchSequencer.reset()
                governorAlignedToVsync = true
                forceNextVideoCapture = true
            } else if (!syncExtremeToDisplay && governorAlignedToVsync) {
                extremeGovernor.reset()
                turboBatchSequencer.reset()
                governorAlignedToVsync = false
                nextVideoDeadlineNs = presentationNowNs
                forceNextVideoCapture = true
            }

            val framesThisSlice = if (syncExtremeToDisplay) {
                turboBatchSequencer.nextFrames(
                    speed = speed,
                    refreshRateHz = extremePresentationHz,
                    maxFrames = 8
                )
            } else {
                baselineFramesThisSlice
            }
            val sliceCadenceNs = TurboFramePolicy.sliceCadenceNs(
                speed = speed,
                frameTimeNs = FRAME_TIME_NS,
                framesPerSlice = framesThisSlice
            )

            // A display-synchronized extreme-turbo batch represents one panel
            // interval on average, so every completed batch is a useful unique
            // state. Other modes retain the wall-clock copy limiter.
            val captureVideo = when {
                !turbo -> true
                forceNextVideoCapture -> true
                syncExtremeToDisplay -> true
                else -> presentationNowNs >= nextVideoDeadlineNs
            }

            if (captureVideo && turbo) {
                val wasForced = forceNextVideoCapture
                forceNextVideoCapture = false
                if (!syncExtremeToDisplay) {
                    nextVideoDeadlineNs = if (wasForced ||
                        presentationNowNs - nextVideoDeadlineNs >= presentationIntervalNs) {
                        presentationNowNs + presentationIntervalNs
                    } else {
                        nextVideoDeadlineNs + presentationIntervalNs
                    }
                }
            }

            val workStartedNs = System.nanoTime()
            val successful = if (turbo) {
                // One native call advances the whole batch. Hidden frames stay native;
                // only a display-timed final state crosses JNI as pixels.
                runTurboSlice(
                    framePixels,
                    framesThisSlice,
                    captureVideo,
                    discardAudio = turboAudioMuted
                )
            } else {
                runFrame(framePixels)
            }
            val actualWorkNs = (System.nanoTime() - workStartedNs).coerceAtLeast(1L)

            // Publish the newest completed image immediately. Audio resampling,
            // adaptive monitoring and performance-hint bookkeeping happen after
            // this hand-off so none of them can make a ready turbo frame miss the
            // next Choreographer deadline. Game speed is unchanged.
            if (successful && captureVideo) {
                synchronized(frameLock) {
                    val completedFrame = framePixels
                    framePixels = displayPixels
                    displayPixels = completedFrame
                }

                if (hasGameplayFramePresenter()) {
                    gameplayFramePresenter.requestPresent()
                }
            }

            performanceHints.report(
                actualWorkNs = actualWorkNs,
                desiredTargetNs = sliceCadenceNs
            )

            if (successful) {
                audioController.pump(
                    speed = speed,
                    flushOutput = !turbo || captureVideo
                )

                throughputMonitor.onFramesCompleted(speed, framesThisSlice)?.let { sample ->
                    if (turbo && sample.constrained) {
                        val fallbackSkip = TurboFramePolicy.adaptiveCoreFrameSkip(
                            speed = speed,
                            userFrameSkip = userFrameSkip,
                            utilization = sample.utilization,
                            frameTimeNs = FRAME_TIME_NS,
                            presentationHz = if (highRefreshTurbo) extremePresentationHz else gameplayPresentationHz
                        )
                        if (fallbackSkip != activeCoreFrameSkip) {
                            runCatching { setCoreConfigOption("frameskip", fallbackSkip.toString()) }
                            activeCoreFrameSkip = fallbackSkip
                        }
                        adaptiveQualityFallbackEngaged = true
                        // Do not break display synchronization here. A throughput
                        // miss may reduce invisible renderer/audio work, but the
                        // high-refresh turbo presenter must keep sampling at panel cadence.
                        // Preserve the selected game-speed target above all else.
                        // If renderer adaptation still reports a sustained miss,
                        // temporarily remove turbo-audio work; hysteresis restores
                        // it only after measured throughput has genuinely recovered.
                        val shouldProtectSpeedByMutingAudio = when {
                            // Preserve the older v1.0.1 audible-turbo behavior whenever
                            // practical. Mute only on a severe sustained miss after
                            // renderer adaptation; exact emulation speed still wins.
                            speed >= 16.0 -> sample.utilization < 0.78
                            speed >= 8.0 -> sample.utilization < 0.70
                            speed >= 4.0 -> sample.utilization < 0.62
                            else -> false
                        }
                        if (!turboAudioMuted && shouldProtectSpeedByMutingAudio) {
                            audioController.setTurboMuted(true)
                            turboAudioMuted = true
                        }
                    } else if (turbo && sample.recovered && adaptiveQualityFallbackEngaged) {
                        // Restore full renderer quality after a sustained recovery.
                        val restoredSkip = TurboFramePolicy.speedProtectingCoreFrameSkip(
                            speed = speed,
                            userFrameSkip = userFrameSkip,
                            frameTimeNs = FRAME_TIME_NS,
                            presentationHz = if (highRefreshTurbo) extremePresentationHz else gameplayPresentationHz
                        )
                        if (restoredSkip != activeCoreFrameSkip) {
                            runCatching { setCoreConfigOption("frameskip", restoredSkip.toString()) }
                            activeCoreFrameSkip = restoredSkip
                        }
                        adaptiveQualityFallbackEngaged = false
                        // The high-refresh sequencer stayed active during fallback,
                        // so recovery does not need to throw away VSync phase.
                        forceNextVideoCapture = true
                        if (turboAudioMuted) {
                            audioController.setTurboMuted(false)
                            turboAudioMuted = false
                        }
                    }
                }
            } else {
                audioController.flushPendingOutput()
            }

            if (turbo && successful) {
                // One cumulative wall-clock governor is used for every fast-forward
                // multiplier and every ROM. Android oversleep is repaid by later
                // batches; if the hardware cannot sustain the request, waiting drops
                // to zero and mGBA runs flat-out while quality work is shed first.
                extremeGovernor.onBatchComplete(speed, framesThisSlice)
            } else {
                framePacer.waitForNext(
                    sliceCadenceNs,
                    TurboFramePolicy.precisionWindowNs(speed, normalPrecisionWindow)
                )
            }
        }

        performanceHints.close()
        if (hasGameplayFramePresenter()) gameplayFramePresenter.setContinuousVsync(false)
        audioController.setTurboMuted(false)
        if (hasDisplayPerformanceManager()) displayPerformanceManager.applyGameplayMode(extremeTurbo = false)

        // Never leave a turbo-only mGBA frameskip applied to the next session.
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
