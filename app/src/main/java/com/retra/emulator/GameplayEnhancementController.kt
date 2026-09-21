package com.retra.emulator

import android.app.Dialog
import android.widget.LinearLayout

/** Gameplay-menu features that are intentionally kept out of the core menu module. */
internal fun MainActivity.addRewindGameplayAction(parent: LinearLayout, dialog: Dialog) {
    val linked = localLinkActive || remoteTransport.isActive
    val available = if (linked) 0 else runCatching { getRewindAvailableSeconds() }.getOrDefault(0)
    addMenuAction(
        parent,
        "Rewind",
        if (linked) "Unavailable while linked" else if (available > 0) "Up to ${available.coerceAtMost(15)} sec available" else "5 sec • 10 sec • 15 sec"
    ) {
        if (linked) {
            RetraNotice.makeText(this, "Rewind is disabled while linked", RetraNotice.LENGTH_SHORT).show()
        } else {
            renderRewindScreen(dialog)
        }
    }
}

internal fun MainActivity.addLayoutAndImportGameplayActions(parent: LinearLayout, dialog: Dialog) {
    addMenuAction(parent, "Edit layout", "Adjust the current controller layout") {
        openInGameLayoutEditor(dialog)
    }
    addMenuAction(
        parent,
        "Import save",
        if (localLinkActive || remoteTransport.isActive) "Unavailable while linked" else ".sav / .srm for $currentRomTitle"
    ) {
        beginGameplaySaveImport(dialog)
    }
}

internal fun MainActivity.renderRewindScreen(dialog: Dialog) {
    gameplayMenuSubscreen = true
    gameplayMenuBackHandler = { renderGameplayMainMenu(dialog) }
    val available = runCatching { getRewindAvailableSeconds() }.getOrDefault(0)
    val (panel, content) = buildMenuShell("Rewind", showBack = true, trailingText = if (available > 0) "${available.coerceAtMost(15)}s" else null)

    listOf(5, 10, 15).forEach { seconds ->
        addMenuAction(
            content,
            "$seconds seconds",
            if (available >= seconds) "Rewind gameplay by $seconds seconds" else "Not enough history yet"
        ) {
            if (localLinkActive || remoteTransport.isActive) {
                RetraNotice.makeText(this, "Rewind is disabled while linked", RetraNotice.LENGTH_SHORT).show()
                return@addMenuAction
            }
            val rewound = runCatching { rewindSeconds(seconds) }.getOrDefault(false)
            if (rewound) {
                audioController.pauseAndFlush()
                RetraNotice.makeText(this, "Rewound $seconds seconds", RetraNotice.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                RetraNotice.makeText(this, "Not enough rewind history yet", RetraNotice.LENGTH_SHORT).show()
                renderRewindScreen(dialog)
            }
        }
    }

    dialog.setContentView(panel)
    if (dialog.isShowing) sizeGameplayDialog(dialog)
}

internal fun MainActivity.openInGameLayoutEditor(dialog: Dialog) {
    inGameSettingsActive = true
    dialog.dismiss()
    leaveEmulatorPresentation()
    showWebUiWithoutBlankFrame()
    binding.webView.evaluateJavascript(
        "window.retraOpenInGameLayoutEditor && window.retraOpenInGameLayoutEditor()",
        null
    )
}
