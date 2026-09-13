package com.retra.emulator

import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentValues
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import com.retra.emulator.MainActivity.Companion.CONFIRM_CLOSE_RESET_PREF
import com.retra.emulator.MainActivity.Companion.ENABLE_CHEATS_PREF

/**
 * Gameplay menu, save-state, cheat, reset, screenshot and in-game settings
 * orchestration extracted from MainActivity. JNI/core behavior is unchanged.
 */
internal fun MainActivity.showGameplayMenu() {
    if (!romLoaded || gameplayMenuDialog?.isShowing == true) return

    stopEmulation()
    releaseAllKeys()

    val dialog = Dialog(this)
    gameplayMenuDialog = dialog
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    dialog.window?.let { dialogWindow ->
        val attrs = dialogWindow.attributes
        attrs.dimAmount = 0.48f
        dialogWindow.attributes = attrs
    }
    // Tapping the dimmed game area outside the rounded menu frame should
    // behave like My Boy!: close the menu and immediately resume gameplay.
    dialog.setCancelable(true)
    dialog.setCanceledOnTouchOutside(true)
    dialog.setOnDismissListener {
        gameplayMenuDialog = null
        gameplayMenuSubscreen = false
        gameplayMenuBackHandler = null
        if (!inGameSettingsActive && !gameplayModalPauseActive && romLoaded && binding.emulatorOverlay.visibility == View.VISIBLE) {
            startEmulation()
        }
    }
    dialog.setOnKeyListener { _, keyCode, event ->
        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            if (gameplayMenuSubscreen) {
                gameplayMenuBackHandler?.invoke() ?: renderGameplayMainMenu(dialog)
            } else {
                dialog.dismiss()
            }
            true
        } else {
            false
        }
    }

    renderGameplayMainMenu(dialog)
    dialog.show()
    sizeGameplayDialog(dialog)
}

internal fun MainActivity.sizeGameplayDialog(dialog: Dialog) {
    val portrait = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
    val metrics = resources.displayMetrics
    val width = if (portrait) (metrics.widthPixels * 0.92f).roundToInt()
    else (metrics.widthPixels * 0.64f).roundToInt()
    val maxHeight = (metrics.heightPixels * 0.90f).roundToInt()
    dialog.window?.setLayout(width, maxHeight)
    dialog.window?.setGravity(Gravity.CENTER)
}

internal fun MainActivity.menuPanelBackground(radiusDp: Float = 28f): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp)
        setColor(Color.argb(246, 29, 27, 22))
        setStroke(dp(1f).roundToInt().coerceAtLeast(1), Color.argb(90, 255, 255, 255))
    }
}

internal fun MainActivity.menuRowBackground(radiusDp: Float = 16f): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp)
        setColor(Color.argb(72, 255, 255, 255))
    }
}

internal fun MainActivity.buildMenuShell(title: String, showBack: Boolean, trailingText: String? = null): Pair<LinearLayout, LinearLayout> {
    val panel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = menuPanelBackground()
        setPadding(dpInt(22), dpInt(18), dpInt(22), dpInt(18))
    }

    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    if (showBack) {
        val back = TextView(this).apply {
            text = "‹"
            textSize = 36f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = "Back"
            setPadding(0, 0, dpInt(12), 0)
            setOnClickListener {
                gameplayMenuBackHandler?.invoke() ?: gameplayMenuDialog?.let(::renderGameplayMainMenu)
            }
        }
        header.addView(back, LinearLayout.LayoutParams(dpInt(44), dpInt(48)))
    }

    val heading = TextView(this).apply {
        text = title
        textSize = 23f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER_VERTICAL
    }
    header.addView(heading, LinearLayout.LayoutParams(0, dpInt(52), 1f))

    if (!trailingText.isNullOrBlank()) {
        val trailing = TextView(this).apply {
            text = trailingText
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dpInt(10), 0, dpInt(2), 0)
        }
        header.addView(trailing, LinearLayout.LayoutParams(dpInt(48), dpInt(48)))
    }

    panel.addView(header)

    val divider = View(this).apply { setBackgroundColor(Color.argb(42, 255, 255, 255)) }
    panel.addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpInt(1)).apply {
        bottomMargin = dpInt(8)
    })

    val scroll = ScrollView(this).apply {
        isFillViewport = true
        overScrollMode = View.OVER_SCROLL_NEVER
    }
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dpInt(2), 0, dpInt(4))
    }
    scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    panel.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    return panel to content
}

internal fun MainActivity.addMenuAction(
    parent: LinearLayout,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        isClickable = true
        isFocusable = true
        setPadding(dpInt(14), dpInt(12), dpInt(14), dpInt(12))
        setOnClickListener { onClick() }
    }

    val label = TextView(this).apply {
        text = title
        textSize = 18f
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD.takeIf { title == "Save" || title == "Load" } ?: Typeface.NORMAL)
    }
    row.addView(label)

    if (!subtitle.isNullOrBlank()) {
        val sub = TextView(this).apply {
            text = subtitle
            textSize = 13f
            setTextColor(Color.argb(175, 255, 255, 255))
            setPadding(0, dpInt(2), 0, 0)
        }
        row.addView(sub)
    }

    parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    val divider = View(this).apply { setBackgroundColor(Color.argb(28, 255, 255, 255)) }
    parent.addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpInt(1)))
}

internal fun MainActivity.renderGameplayMainMenu(dialog: Dialog) {
    gameplayMenuSubscreen = false
    gameplayMenuBackHandler = null
    val (panel, content) = buildMenuShell("Menu", showBack = false)

    addMenuAction(
        content,
        "Load",
        if (localLinkActive) "Unavailable during Local Link" else "Quick + Slot 1–10"
    ) {
        if (localLinkActive) showLocalLinkStateRestriction()
        else renderStateScreen(dialog, saveMode = false)
    }
    addMenuAction(
        content,
        "Save",
        if (localLinkActive) "Unavailable during Local Link" else "Quick + Slot 1–10"
    ) {
        if (localLinkActive) showLocalLinkStateRestriction()
        else renderStateScreen(dialog, saveMode = true)
    }
    addMenuAction(
        content,
        if (EmulationSpeedPolicy.isNormal(activeEmulationSpeed)) "Speed mode" else "Normal speed",
        if (localLinkActive) "Disabled while linked"
        else if (EmulationSpeedPolicy.isNormal(activeEmulationSpeed)) "Uses ${EmulationSpeedPolicy.format(preferredEmulationSpeed)} from Settings"
        else "Currently ${EmulationSpeedPolicy.format(activeEmulationSpeed)}"
    ) {
        toggleFastForward()
        if (!localLinkActive) dialog.dismiss()
    }
    addMenuAction(content, "Cheats", if (localLinkActive) "Unavailable during Local Link" else null) {
        if (localLinkActive) {
            RetraNotice.makeText(this, "Cheats are disabled during Local Link", RetraNotice.LENGTH_SHORT).show()
        } else {
            renderCheatsScreen(dialog)
        }
    }
    addMenuAction(content, "Settings") { openInGameSettings(dialog) }

    if (remoteTransport.isActive) {
        val roleName = if (remoteTransport.role == 0) "Host" else "Client"
        val health = remoteTransport.healthSnapshot()
        addMenuAction(
            content,
            "Remote link connected",
            "${health.transport} • $roleName • P${remoteTransport.role + 1} • ${health.quality} • ${health.rttMs}ms RTT • ${health.jitterMs}ms jitter • ${health.inputDelayFrames}f delay"
        ) {
            RetraNotice.makeText(
                this,
                "Remote Link ${health.quality.lowercase()} • ${health.successfulResyncs} recovered desync${if (health.successfulResyncs == 1) "" else "s"}",
                RetraNotice.LENGTH_SHORT
            ).show()
        }
        addMenuAction(content, "Disconnect remote link", "End the phone-to-phone session") {
            dialog.dismiss()
            disconnectRemoteLinkAndRestore()
        }
    } else if (localLinkActive) {
        val activeTitle = if (localLinkPlayer == 0) localLinkPlayer1Title else localLinkPlayer2Title
        addMenuAction(
            content,
            "Switch player",
            "Player ${localLinkPlayer + 1} • $activeTitle"
        ) {
            switchLocalLinkPlayer()
            dialog.dismiss()
        }
        addMenuAction(content, "Disconnect local link", "Return to Player 1") {
            disconnectLocalLinkAndRestore(dialog)
        }
    } else {
        addMenuAction(content, "Link remote") { renderLinkRemoteScreen(dialog) }
        addMenuAction(content, "Link local") { renderLinkLocalScreen(dialog) }
    }

    addMenuAction(content, "Screenshot") {
        val saved = saveGameplayScreenshot()
        RetraNotice.makeText(this, if (saved) "Screenshot saved" else "Could not save screenshot", RetraNotice.LENGTH_SHORT).show()
        if (saved) dialog.dismiss()
    }
    addMenuAction(content, "Reset") { showResetConfirmation(dialog) }
    // Closing from the gameplay menu must use the same durable exit path as
    // Android Back: write the dedicated auto-resume state, stop mGBA, commit
    // battery/RTC working saves atomically, then return to the warm WebView.
    addMenuAction(content, "Close") { closeEmulator() }

    dialog.setContentView(panel)
    if (dialog.isShowing) sizeGameplayDialog(dialog)
}

internal fun MainActivity.renderStateScreen(dialog: Dialog, saveMode: Boolean) {
    gameplayMenuSubscreen = true
    gameplayMenuBackHandler = { renderGameplayMainMenu(dialog) }
    val (panel, content) = buildMenuShell(if (saveMode) "Save state" else "Load state", showBack = true)

    for (slot in 0..10) {
        val state = saveStates.stateFile(slot, currentRomId)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpInt(10), dpInt(10), dpInt(10), dpInt(10))
            isClickable = true
            isFocusable = true
        }

        val thumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(12, 12, 12))
            val imageFile = saveStates.thumbnailFile(slot, currentRomId)
            if (imageFile.exists()) {
                BitmapFactory.decodeFile(imageFile.absolutePath)?.let(::setImageBitmap)
            }
        }
        row.addView(thumb, LinearLayout.LayoutParams(dpInt(118), dpInt(76)).apply { rightMargin = dpInt(16) })

        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val name = TextView(this).apply {
            text = saveStates.slotLabel(slot)
            textSize = 19f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }
        copy.addView(name)

        val meta = TextView(this).apply {
            text = if (state.exists()) formatStateTimestamp(state.lastModified()) else "<empty>"
            textSize = 14f
            setTextColor(Color.argb(190, 255, 255, 255))
            setPadding(0, dpInt(3), 0, 0)
        }
        copy.addView(meta)
        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        row.setOnClickListener {
            if (saveMode) {
                if (state.exists()) {
                    AlertDialog.Builder(this)
                        .setTitle("Overwrite ${saveStates.slotLabel(slot)}?")
                        .setMessage("The existing save state in this slot will be replaced.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Overwrite") { _, _ ->
                            saveStateToSlot(slot)
                            renderStateScreen(dialog, true)
                        }
                        .show()
                } else {
                    saveStateToSlot(slot)
                    renderStateScreen(dialog, true)
                }
            } else {
                if (!state.exists()) {
                    RetraNotice.makeText(this, "${saveStates.slotLabel(slot)} is empty", RetraNotice.LENGTH_SHORT).show()
                } else if (loadStateFromSlot(slot)) {
                    dialog.dismiss()
                }
            }
        }

        row.setOnLongClickListener {
            if (!state.exists()) {
                RetraNotice.makeText(this, "${saveStates.slotLabel(slot)} is empty", RetraNotice.LENGTH_SHORT).show()
                return@setOnLongClickListener true
            }

            AlertDialog.Builder(this)
                .setTitle("Delete ${saveStates.slotLabel(slot)}?")
                .setMessage("This save state will be permanently deleted.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->
                    if (deleteRomSaveStateInternal(currentRomId, slot)) {
                        RetraNotice.makeText(this, "${saveStates.slotLabel(slot)} deleted", RetraNotice.LENGTH_SHORT).show()
                        renderStateScreen(dialog, saveMode)
                    } else {
                        RetraNotice.makeText(this, "Could not delete ${saveStates.slotLabel(slot)}", RetraNotice.LENGTH_SHORT).show()
                    }
                }
                .show()
            true
        }

        content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val divider = View(this).apply { setBackgroundColor(Color.argb(30, 255, 255, 255)) }
        content.addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpInt(1)))
    }

    dialog.setContentView(panel)
    if (dialog.isShowing) sizeGameplayDialog(dialog)
}

internal fun MainActivity.notifySaveStatesChanged(romId: String = currentRomId) {
    if (romId.isBlank() || !hasBinding()) return
    runOnUiThread {
        binding.webView.evaluateJavascript(
            "window.retraNativeSaveStatesChanged && window.retraNativeSaveStatesChanged(${jsQuote(romId)})",
            null
        )
    }
}

internal fun MainActivity.deleteRomSaveStateInternal(romId: String, slot: Int): Boolean {
    val deleted = saveStates.delete(romId, slot)
    if (deleted) notifySaveStatesChanged(romId)
    return deleted
}

internal fun MainActivity.formatStateTimestamp(time: Long): String {
    val date = Date(time)
    val dateText = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
    val timeText = SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
    return "$dateText\n$timeText"
}

internal fun MainActivity.saveStateToSlot(
    slot: Int,
    successMessage: String? = null,
    failureMessage: String? = null,
    showNotice: Boolean = true
): Boolean {
    if (!romLoaded) return false
    if (localLinkActive) {
        showLocalLinkStateRestriction()
        return false
    }
    val state = saveStates.stateFile(slot, currentRomId)
    state.parentFile?.mkdirs()
    val tempState = File(state.parentFile, ".${state.name}.${System.nanoTime()}.tmp")
    val nativeSaved = try { quickSaveState(tempState.absolutePath) } catch (_: Throwable) { false }
    val ok = nativeSaved && tempState.exists() && tempState.length() > 0L &&
        runCatching { fileOps.atomicCopyVerified(tempState, state); true }.getOrDefault(false)
    tempState.delete()
    if (ok) {
        state.setLastModified(System.currentTimeMillis())
        saveStateThumbnail(slot)
        saveStates.writeMetadata(slot, currentRomId)
        notifySaveStatesChanged(currentRomId)
        syncCloudAsync(showResult = false)
    } else {
        if (state.exists() && state.length() == 0L) state.delete()
    }
    if (showNotice) {
        RetraNotice.makeText(
            this,
            if (ok) (successMessage ?: "${saveStates.slotLabel(slot)} saved")
            else (failureMessage ?: "Could not save ${saveStates.slotLabel(slot)}"),
            RetraNotice.LENGTH_SHORT
        ).show()
    }
    return ok
}

internal fun MainActivity.loadStateFromSlot(
    slot: Int,
    successMessage: String? = null,
    failureMessage: String? = null,
    showNotice: Boolean = true
): Boolean {
    if (!romLoaded) return false
    if (localLinkActive) {
        showLocalLinkStateRestriction()
        return false
    }
    val state = saveStates.stateFile(slot, currentRomId)
    if (!state.exists()) return false
    if (!saveStates.isCompatible(slot, currentRomId)) {
        RetraNotice.makeText(this, "This save state belongs to a different ROM revision", RetraNotice.LENGTH_LONG).show()
        return false
    }
    val ok = try { quickLoadState(state.absolutePath) } catch (_: Throwable) { false }
    if (showNotice) {
        RetraNotice.makeText(
            this,
            if (ok) (successMessage ?: "${saveStates.slotLabel(slot)} loaded")
            else (failureMessage ?: "Could not load ${saveStates.slotLabel(slot)}"),
            RetraNotice.LENGTH_SHORT
        ).show()
    }
    return ok
}

internal fun MainActivity.snapshotCurrentFrame(): Bitmap? {
    if (!romLoaded || videoWidth <= 0 || videoHeight <= 0) return null
    val pixels = synchronized(frameLock) { displayPixels.copyOf() }
    return try {
        Bitmap.createBitmap(pixels, videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
    } catch (_: Throwable) {
        null
    }
}

internal fun MainActivity.saveStateThumbnail(slot: Int) {
    val source = snapshotCurrentFrame() ?: return
    val targetWidth = 320
    val targetHeight = (targetWidth * source.height.toFloat() / source.width.toFloat()).roundToInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    try {
        saveStates.thumbnailFile(slot, currentRomId).parentFile?.mkdirs()
        FileOutputStream(saveStates.thumbnailFile(slot, currentRomId)).use { out -> scaled.compress(Bitmap.CompressFormat.PNG, 100, out) }
    } catch (_: Exception) {
    } finally {
        if (scaled !== source) scaled.recycle()
        source.recycle()
    }
}

internal fun MainActivity.quickSave() {
    saveStateToSlot(0, successMessage = "Saved", failureMessage = "Could not save")
}

internal fun MainActivity.quickLoad() {
    if (!saveStates.stateFile(0, currentRomId).exists()) {
        RetraNotice.makeText(this, "No quick save yet", RetraNotice.LENGTH_SHORT).show()
        return
    }
    loadStateFromSlot(0, successMessage = "Loaded", failureMessage = "Could not load")
}

internal fun MainActivity.toggleFastForward() {
    if (localLinkActive) {
        RetraNotice.makeText(this, "Speed changes are disabled during Local Link", RetraNotice.LENGTH_SHORT).show()
        return
    }
    activeEmulationSpeed = if (EmulationSpeedPolicy.isNormal(activeEmulationSpeed)) preferredEmulationSpeed else 1.0
    updateFastForwardUi()
}

internal fun MainActivity.updateFastForwardUi() {
    binding.speedButton.contentDescription = "Emulation speed ${EmulationSpeedPolicy.format(activeEmulationSpeed)}"
}

internal fun MainActivity.renderCheatsScreen(dialog: Dialog) {
    gameplayMenuSubscreen = true
    gameplayMenuBackHandler = { renderGameplayMainMenu(dialog) }
    val (panel, content) = buildMenuShell("Cheats", showBack = true)
    content.setPadding(0, dpInt(2), 0, dpInt(82))
    val cheats = loadCheats()

    if (cheats.isEmpty()) {
        val empty = TextView(this).apply {
            text = "No cheats yet\n\nTap + to add one"
            gravity = Gravity.CENTER
            textSize = 17f
            setTextColor(Color.argb(190, 255, 255, 255))
            setPadding(dpInt(12), dpInt(60), dpInt(12), dpInt(60))
        }
        content.addView(empty, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    } else {
        cheats.forEach { cheat ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpInt(12), dpInt(12), dpInt(8), dpInt(12))
                isClickable = true
                isFocusable = true
            }
            val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            copy.addView(TextView(this).apply {
                text = cheat.name.ifBlank { "Unnamed cheat" }
                textSize = 18f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
            })
            copy.addView(TextView(this).apply {
                text = cheatTypeLabel(cheat.type)
                textSize = 13f
                setTextColor(Color.argb(175, 255, 255, 255))
                setPadding(0, dpInt(3), 0, 0)
            })
            row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            var internalSwitchUpdate = false
            val enabledSwitch = Switch(this).apply {
                isChecked = cheat.enabled
                contentDescription = "Enable ${cheat.name}"
                setOnCheckedChangeListener { _, checked ->
                    if (internalSwitchUpdate) return@setOnCheckedChangeListener

                    val previous = cheat.enabled
                    cheat.enabled = checked
                    saveCheats(cheats)
                    val applied = applyStoredCheatsToCore()
                    if (!applied) {
                        // Never leave the UI claiming a cheat is disabled/enabled when
                        // the native mGBA cheat device could not be synchronized.
                        cheat.enabled = previous
                        saveCheats(cheats)
                        internalSwitchUpdate = true
                        isChecked = previous
                        internalSwitchUpdate = false
                        applyStoredCheatsToCore()
                        RetraNotice.makeText(
                            this@renderCheatsScreen,
                            if (checked) "Cheat code was not recognized" else "Could not disable cheat. Try again.",
                            RetraNotice.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            row.addView(enabledSwitch)
            row.setOnClickListener { renderCheatEditor(dialog, cheat) }
            content.addView(row)
            content.addView(View(this).apply { setBackgroundColor(Color.argb(30, 255, 255, 255)) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpInt(1)))
        }
    }

    val shell = FrameLayout(this).apply {
        addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
    val addButton = TextView(this).apply {
        text = "+"
        textSize = 30f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(15, 20, 20))
        contentDescription = "Add cheat"
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(132, 213, 207))
        }
        elevation = dp(8f)
        setOnClickListener { renderCheatEditor(dialog, null) }
    }
    shell.addView(addButton, FrameLayout.LayoutParams(dpInt(58), dpInt(58), Gravity.END or Gravity.BOTTOM).apply {
        rightMargin = dpInt(18)
        bottomMargin = dpInt(18)
    })

    dialog.setContentView(shell)
    if (dialog.isShowing) sizeGameplayDialog(dialog)
}

internal fun MainActivity.renderCheatEditor(dialog: Dialog, existing: CheatEntry?) {
    gameplayMenuSubscreen = true
    gameplayMenuBackHandler = { renderCheatsScreen(dialog) }
    val draft = existing?.copy() ?: CheatEntry(UUID.randomUUID().toString(), "", 0, "", true)
    val (panel, content) = buildMenuShell(if (existing == null) "New cheat" else "Edit cheat", showBack = true, trailingText = "⋮")

    val header = panel.getChildAt(0) as? LinearLayout
    val overflow = header?.getChildAt(header.childCount - 1) as? TextView
    overflow?.setOnClickListener {
        val choices = if (existing == null) arrayOf("Discard") else arrayOf("Delete cheat")
        AlertDialog.Builder(this)
            .setItems(choices) { _, which ->
                if (existing == null) {
                    renderCheatsScreen(dialog)
                } else if (which == 0) {
                    AlertDialog.Builder(this)
                        .setTitle("Delete cheat?")
                        .setMessage(existing.name.ifBlank { "This cheat" })
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ ->
                            val updated = loadCheats().filterNot { it.id == existing.id }
                            saveCheats(updated)
                            applyStoredCheatsToCore()
                            renderCheatsScreen(dialog)
                        }
                        .show()
                }
            }
            .show()
    }

    fun addField(title: String, value: () -> String, onClick: () -> Unit): TextView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpInt(12), dpInt(16), dpInt(12), dpInt(16))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        row.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        val sub = TextView(this).apply {
            text = value().ifBlank { "Not set" }
            textSize = 14f
            setTextColor(Color.rgb(214, 226, 226))
            setPadding(0, dpInt(4), 0, 0)
            maxLines = 2
        }
        row.addView(sub)
        content.addView(row)
        content.addView(View(this).apply { setBackgroundColor(Color.argb(30, 255, 255, 255)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpInt(1)))
        return sub
    }

    lateinit var nameValue: TextView
    lateinit var typeValue: TextView
    lateinit var codeValue: TextView

    nameValue = addField("Cheat name", { draft.name }) {
        showTextEntryDialog("Cheat name", draft.name, multiline = false) { value ->
            draft.name = value.trim()
            nameValue.text = draft.name.ifBlank { "Not set" }
        }
    }

    typeValue = addField("Cheat type", { cheatTypeLabel(draft.type) }) {
        showCheatTypeDialog(draft.type) { type ->
            draft.type = type
            typeValue.text = cheatTypeLabel(type)
        }
    }

    codeValue = addField("Cheat code", { draft.code }) {
        showTextEntryDialog("Cheat code", draft.code, multiline = true) { value ->
            draft.code = normalizeCheatCode(value)
            codeValue.text = if (draft.code.isBlank()) "Not set" else draft.code.lineSequence().firstOrNull().orEmpty().let {
                if (draft.code.lines().size > 1) "$it …" else it
            }
        }
    }

    val saveButton = Button(this).apply {
        text = "Save cheat"
        isAllCaps = false
        textSize = 16f
        setTextColor(Color.BLACK)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18f)
            setColor(Color.rgb(132, 213, 207))
        }
        setOnClickListener {
            if (draft.name.isBlank()) {
                RetraNotice.makeText(this@renderCheatEditor, "Enter a cheat name", RetraNotice.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (draft.code.isBlank()) {
                RetraNotice.makeText(this@renderCheatEditor, "Enter a cheat code", RetraNotice.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            draft.code = normalizeCheatCode(draft.code)
            val cheats = loadCheats().toMutableList()
            val index = cheats.indexOfFirst { it.id == draft.id }
            if (index >= 0) cheats[index] = draft else cheats.add(draft)
            saveCheats(cheats)
            val applied = applyStoredCheatsToCore()
            if (!applied && draft.enabled) {
                draft.enabled = false
                val retryIndex = cheats.indexOfFirst { it.id == draft.id }
                if (retryIndex >= 0) cheats[retryIndex] = draft
                saveCheats(cheats)
                applyStoredCheatsToCore()
                RetraNotice.makeText(this@renderCheatEditor, "Saved, but disabled because the code was not recognized", RetraNotice.LENGTH_LONG).show()
            } else {
                RetraNotice.makeText(this@renderCheatEditor, "Cheat saved", RetraNotice.LENGTH_SHORT).show()
            }
            renderCheatsScreen(dialog)
        }
    }
    content.addView(saveButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpInt(52)).apply {
        topMargin = dpInt(20)
        bottomMargin = dpInt(8)
    })

    dialog.setContentView(panel)
    if (dialog.isShowing) sizeGameplayDialog(dialog)
}

internal fun MainActivity.showTextEntryDialog(title: String, initial: String, multiline: Boolean, onConfirm: (String) -> Unit) {
    // Use a Retra-owned dialog instead of the platform AlertDialog input. The
    // platform dialog can be light even while the gameplay menu is dark,
    // which previously rendered our white EditText text on a white surface.
    val dialog = Dialog(this)
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    dialog.window?.let { window ->
        val attrs = window.attributes
        attrs.dimAmount = 0.58f
        window.attributes = attrs
    }

    val panel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpInt(20), dpInt(18), dpInt(20), dpInt(16))
        background = menuPanelBackground(24f)
    }
    panel.addView(TextView(this).apply {
        text = title
        textSize = 20f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE)
        setPadding(dpInt(2), 0, dpInt(2), dpInt(14))
    })

    val input = EditText(this).apply {
        setText(initial)
        setTextColor(Color.rgb(242, 247, 247))
        setHintTextColor(Color.rgb(142, 156, 156))
        textSize = if (multiline) 16f else 17f
        setPadding(dpInt(14), dpInt(12), dpInt(14), dpInt(12))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14f)
            setColor(Color.rgb(31, 37, 38))
            setStroke(dpInt(1), Color.rgb(75, 99, 100))
        }
        inputType = if (multiline) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        if (multiline) {
            minLines = 6
            maxLines = 10
            gravity = Gravity.TOP or Gravity.START
            setHorizontallyScrolling(false)
        } else {
            setSingleLine(true)
        }
        setSelection(text.length)
        highlightColor = Color.argb(110, 132, 213, 207)
    }
    panel.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

    val actions = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        setPadding(0, dpInt(14), 0, 0)
    }
    fun actionButton(label: String, primary: Boolean, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(if (primary) Color.rgb(15, 20, 20) else Color.rgb(206, 218, 218))
        setPadding(dpInt(16), dpInt(10), dpInt(16), dpInt(10))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14f)
            setColor(if (primary) Color.rgb(132, 213, 207) else Color.rgb(43, 50, 51))
        }
        setOnClickListener { action() }
    }
    actions.addView(actionButton("Cancel", false) { dialog.dismiss() })
    actions.addView(actionButton("OK", true) {
        val value = input.text?.toString().orEmpty()
        dialog.dismiss()
        onConfirm(value)
    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        marginStart = dpInt(10)
    })
    panel.addView(actions)

    dialog.setContentView(panel)
    dialog.setOnShowListener {
        val portrait = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val width = if (portrait) (resources.displayMetrics.widthPixels * 0.88f).roundToInt()
        else (resources.displayMetrics.widthPixels * 0.52f).roundToInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setGravity(Gravity.CENTER)
        input.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }
    dialog.show()
}

internal fun MainActivity.showCheatTypeDialog(current: Int, onSelected: (Int) -> Unit) {
    // Fully custom rows keep cheat types readable in both Retra light and dark
    // appearance modes instead of inheriting an incompatible platform dialog.
    val dialog = Dialog(this)
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    dialog.window?.let { window ->
        val attrs = window.attributes
        attrs.dimAmount = 0.58f
        window.attributes = attrs
    }

    val panel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpInt(10), dpInt(16), dpInt(10), dpInt(10))
        background = menuPanelBackground(24f)
    }
    panel.addView(TextView(this).apply {
        text = "Cheat type"
        textSize = 20f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE)
        setPadding(dpInt(12), 0, dpInt(12), dpInt(12))
    })

    val labels = cheatTypeLabels()
    labels.forEachIndexed { index, label ->
        val selected = index == current.coerceIn(labels.indices)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpInt(12), dpInt(13), dpInt(12), dpInt(13))
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12f)
                setColor(if (selected) Color.rgb(42, 64, 64) else Color.TRANSPARENT)
            }
            setOnClickListener {
                dialog.dismiss()
                onSelected(index)
            }
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.rgb(238, 244, 244))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = if (selected) "✓" else ""
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(132, 213, 207))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dpInt(34), ViewGroup.LayoutParams.WRAP_CONTENT))
        panel.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (index > 0) topMargin = dpInt(3)
        })
    }

    dialog.setContentView(panel)
    dialog.setOnShowListener {
        val portrait = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val width = if (portrait) (resources.displayMetrics.widthPixels * 0.88f).roundToInt()
        else (resources.displayMetrics.widthPixels * 0.48f).roundToInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setGravity(Gravity.CENTER)
    }
    dialog.show()
}

internal fun MainActivity.cheatTypeLabels(): Array<String> = arrayOf(
    "Auto detect",
    "GameShark v1/v2",
    "GameShark v3 (Action Replay)",
    "Code Breaker",
    "Raw code"
)

internal fun MainActivity.cheatTypeLabel(type: Int): String = cheatTypeLabels().getOrElse(type) { "Auto detect" }

internal fun MainActivity.cheatFile(romId: String = currentRomId): File = cheatRepository.cheatFile(romId)

internal fun MainActivity.loadCheats(): MutableList<CheatEntry> = cheatRepository.load(currentRomId)

internal fun MainActivity.saveCheats(cheats: List<CheatEntry>) = cheatRepository.save(currentRomId, cheats)

/** Keep pasted cheat text parser-friendly without changing the actual code bytes. */
internal fun normalizeCheatCode(raw: String): String = raw
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .lineSequence()
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .joinToString("\n")

internal fun MainActivity.applyStoredCheatsToCore(): Boolean {
    if (!romLoaded) return true
    if (localLinkActive) return false
    return try {
        // Do not report success if the old native cheat set could not actually
        // be removed. Otherwise the UI may show OFF while mGBA still runs it.
        if (!clearNativeCheats()) return false

        // The global setting is authoritative. Editing/saving cheats while it
        // is off is allowed, but nothing is injected into the running core.
        if (!prefs.getBoolean(ENABLE_CHEATS_PREF, true)) return true

        var allValid = true
        loadCheats().forEach { cheat ->
            if (cheat.enabled && cheat.code.isNotBlank()) {
                val ok = addNativeCheat(
                    cheat.name.ifBlank { "Cheat" },
                    normalizeCheatCode(cheat.code),
                    cheat.type,
                    true
                )
                if (!ok) allValid = false
            }
        }
        allValid
    } catch (_: Throwable) {
        false
    }
}

internal fun MainActivity.showResetConfirmation(dialog: Dialog) {
    if (!prefs.getBoolean(CONFIRM_CLOSE_RESET_PREF, true)) {
        gameplayModalPauseActive = true
        dialog.dismiss()
        val ok = try { resetCore() } catch (_: Throwable) { false }
        gameplayModalPauseActive = false
        RetraNotice.makeText(this, if (ok) "Game reset" else "Could not reset game", RetraNotice.LENGTH_SHORT).show()
        if (romLoaded && binding.emulatorOverlay.visibility == View.VISIBLE && !inGameSettingsActive) startEmulation()
        return
    }
    // Match the emulator-style reset flow: remove the menu panel first so the
    // confirmation sits directly over the paused game and controller layout.
    gameplayModalPauseActive = true
    dialog.dismiss()

    val alert = AlertDialog.Builder(this)
        .setTitle("⚠ Reset")
        .setMessage("Are you sure you want to reset the current game?")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Reset") { _, _ ->
            val ok = try { resetCore() } catch (_: Throwable) { false }
            RetraNotice.makeText(this, if (ok) "Game reset" else "Could not reset game", RetraNotice.LENGTH_SHORT).show()
        }
        .create()

    alert.setOnDismissListener {
        gameplayModalPauseActive = false
        if (romLoaded && binding.emulatorOverlay.visibility == View.VISIBLE && !inGameSettingsActive) {
            startEmulation()
        }
    }
    alert.show()
}

internal fun MainActivity.saveGameplayScreenshot(): Boolean {
    val frame = snapshotCurrentFrame() ?: return false
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val displayName = "Retra_${fileOps.sanitizeFileName(currentRomTitle)}_$stamp.png"

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Retra")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            val wrote = contentResolver.openOutputStream(uri)?.use { out ->
                frame.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: false
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            wrote
        } else {
            val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Retra")
            dir.mkdirs()
            FileOutputStream(File(dir, displayName)).use { out ->
                frame.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    } catch (_: Exception) {
        false
    } finally {
        frame.recycle()
    }
}

internal fun MainActivity.openInGameSettings(dialog: Dialog) {
    inGameSettingsActive = true
    dialog.dismiss()
    leaveEmulatorPresentation()
    showWebUiWithoutBlankFrame()
    binding.webView.evaluateJavascript(
        "window.retraOpenInGameSettings && window.retraOpenInGameSettings()",
        null
    )
}

internal fun MainActivity.closeInGameSettings() {
    if (!inGameSettingsActive || !romLoaded) return
    binding.webView.evaluateJavascript(
        "window.retraCloseInGameSettings && window.retraCloseInGameSettings()",
        null
    )
    showEmulatorUiKeepingWebWarm()
    inGameSettingsActive = false
    enterEmulatorPresentation()
    scheduleNativeEmulatorLayout(100L)
    startEmulation()
}

internal fun MainActivity.releaseAllKeys() {
    // Only emit releases for keys that Android currently considers held. This
    // avoids up to ten unnecessary JNI calls / Remote Link packets on every
    // menu open, pause or lifecycle transition.
    val heldMask = activeGameplayKeyMask
    for (key in 0..9) {
        if (heldMask and (1 shl key) == 0) continue
        try { setGameplayKey(key, false) } catch (_: Throwable) {}
    }
    activeGameplayKeyMask = 0
    activeDpadMask = 0
    activeDpadPointerId = MotionEvent.INVALID_POINTER_ID
    dpadTouchGeometryValid = false

    // A sub-frame tap is latched natively so mGBA cannot miss it. When controls
    // are intentionally cancelled (menu/background/close), discard any latch
    // that has not yet reached an emulated frame to prevent a ghost tap later.
    try { clearKeyPressLatches() } catch (_: Throwable) {}

    if (hasBinding()) {
        fun clearPressedState(view: View) {
            view.isPressed = false
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    clearPressedState(view.getChildAt(index))
                }
            }
        }
        clearPressedState(binding.emulatorViewport)
    }
}

internal fun MainActivity.dpInt(value: Int): Int = dp(value.toFloat()).roundToInt()
