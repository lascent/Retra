package com.retra.emulator

import android.app.Dialog
import android.net.Uri
import android.view.View
import java.io.File

/**
 * Strict per-game battery-save import flow exposed from the gameplay menu.
 * Raw GBA .sav files have no universal ROM identifier, so Retra combines exact
 * normalized ROM-name matching with save-size checks and always backs up the
 * previous canonical save before replacing it.
 */
internal data class PendingSaveImportSession(
    val romId: String,
    val title: String,
    val romPath: String,
    val patchPath: String?
)

internal fun MainActivity.beginGameplaySaveImport(dialog: Dialog) {
    if (!romLoaded) return
    if (localLinkActive || remoteTransport.isActive || isNativeLocalLinkActive()) {
        RetraNotice.makeText(this, "Save import is disabled while linked", RetraNotice.LENGTH_SHORT).show()
        return
    }

    val romPath = currentRomPath?.takeIf { it.isNotBlank() } ?: run {
        RetraNotice.makeText(this, "Current ROM file is unavailable", RetraNotice.LENGTH_SHORT).show()
        return
    }

    pendingSaveImportRomId = currentRomId
    pendingSaveImportGameplay = true
    pendingSaveImportSession = PendingSaveImportSession(
        romId = currentRomId,
        title = currentRomTitle,
        romPath = romPath,
        patchPath = currentPatchPath
    )

    // Keep gameplay paused while Android's document picker is on top. The
    // menu's dismiss listener sees gameplayModalPauseActive and will not resume
    // the core behind the picker.
    gameplayModalPauseActive = true
    dialog.dismiss()
    saveImportPicker.launch(arrayOf("application/octet-stream", "*/*"))
}

internal fun MainActivity.handleSaveImportPickerResult(uri: Uri?) {
    val targetRomId = pendingSaveImportRomId
    val gameplayImport = pendingSaveImportGameplay
    val session = pendingSaveImportSession

    pendingSaveImportRomId = null
    pendingSaveImportGameplay = false
    pendingSaveImportSession = null

    if (uri == null) {
        gameplayModalPauseActive = false
        if (gameplayImport && romLoaded && binding.emulatorOverlay.visibility == View.VISIBLE) {
            startEmulation()
        }
        return
    }

    if (!gameplayImport) {
        ioExecutor.execute {
            val result = saveTransfer.importBatterySave(uri, targetRomId = null)
            runOnUiThread {
                RetraNotice.makeText(this, result.message, RetraNotice.LENGTH_LONG).show()
                if (result.success) syncCloudAsync(showResult = false)
            }
        }
        return
    }

    if (session == null || targetRomId.isNullOrBlank()) {
        gameplayModalPauseActive = false
        if (romLoaded && binding.emulatorOverlay.visibility == View.VISIBLE) startEmulation()
        return
    }

    // Shut down first, then commit the old working save. This guarantees the
    // backup made by replaceBatterySaveFromFile() contains the user's latest
    // progress and prevents the old mGBA RAM save from winning after import.
    stopEmulation()
    releaseAllKeys()
    runCatching { shutdownCore() }
    commitActiveWorkingSaves()
    clearActiveLinkSaveTracking()
    romLoaded = false

    ioExecutor.execute {
        val result = saveTransfer.importBatterySave(uri, targetRomId)
        if (result.success) {
            // An auto state captures the entire old emulation state, not just
            // SRAM. Loading it immediately after a new .sav would effectively
            // undo the import, so retire only that automatic resume snapshot.
            runCatching { saveStates.autoFile(session.romId).delete() }
            runCatching { saveStates.autoMetadataFile(session.romId).delete() }
        }

        runOnUiThread {
            gameplayModalPauseActive = false
            val rom = File(session.romPath)
            val patch = session.patchPath?.let(::File)?.takeIf { it.isFile }
            if (rom.isFile) {
                loadRomFile(rom, session.title, patch, session.romId)
            } else {
                RetraNotice.makeText(this, "ROM file is no longer available", RetraNotice.LENGTH_LONG).show()
                showWebUiWithoutBlankFrame()
            }
            RetraNotice.makeText(this, result.message, RetraNotice.LENGTH_LONG).show()
            if (result.success) syncCloudAsync(showResult = false)
        }
    }
}
