package com.retra.emulator

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.text.InputType
import android.view.View
import android.widget.EditText
import androidx.core.content.ContextCompat
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Locale
import com.retra.emulator.MainActivity.Companion.BIOS_GBA_PATH_PREF
import com.retra.emulator.MainActivity.Companion.BLUETOOTH_CONNECT_REQUEST
import com.retra.emulator.MainActivity.Companion.MAX_ROM_BYTES
import com.retra.emulator.MainActivity.Companion.PLATFORM_GBA
import com.retra.emulator.MainActivity.Companion.REMOTE_BLUETOOTH_UUID
import com.retra.emulator.MainActivity.Companion.REMOTE_HOST_PREF
import com.retra.emulator.MainActivity.Companion.REMOTE_LINK_PORT
import com.retra.emulator.MainActivity.Companion.REMOTE_LINK_PROTOCOL
import com.retra.emulator.MainActivity.Companion.REMOTE_MAX_SAVE_BYTES

/**
 * GBA multiplayer orchestration extracted from MainActivity.
 * Remote transport packet/timing policy remains in RemoteLinkTransport; this
 * module owns the activity-facing Local/Wi-Fi/Bluetooth session flow.
 */
internal fun MainActivity.renderLinkRemoteScreen(dialog: Dialog) {
    gameplayMenuSubscreen = true
    gameplayMenuBackHandler = { renderGameplayMainMenu(dialog) }

    val (panel, content) = buildMenuShell("Link remote", showBack = true)
    val compatibility = multiplayerCompatibility.detect(currentRomPath, currentPlatform == PLATFORM_GBA, currentPatchPath != null)
    if (!compatibility.linkCableEligible) {
        addMenuAction(content, "Link unavailable", compatibility.reason) {
            RetraNotice.makeText(this, compatibility.reason, RetraNotice.LENGTH_LONG).show()
        }
    } else {
        addMenuAction(content, "Wi-Fi (server)", "Host on this phone • TCP $REMOTE_LINK_PORT") {
            beginWifiRemoteServer(dialog)
        }
        addMenuAction(content, "Wi-Fi (client)", "Connect to another Retra phone") {
            promptWifiRemoteClient(dialog)
        }
        addMenuAction(content, "Bluetooth (server)", "Wait for a previously paired Android device") {
            beginBluetoothRemoteServer(dialog)
        }
        addMenuAction(content, "Bluetooth (client)", "Connect to a previously paired Android device") {
            beginBluetoothRemoteClient(dialog)
        }
        addMenuAction(
            content,
            "Compatibility",
            compatibility.availabilityLabel
        ) {
            AlertDialog.Builder(this)
                .setTitle("Remote Link compatibility")
                .setMessage(
                    "${compatibility.availabilityLabel}. Retra supports normal GBA Multi-Pak/Link Cable only: Local, Wi-Fi Remote, and Bluetooth Remote. " +
                        "Wi-Fi/Bluetooth use state-hash desync detection with automatic host-authoritative recovery. " +
                        "Different GBA games can link when both ROMs exist on both phones and match by SHA-256. " +
                        "Single-Pak/Multiboot is available through Local Link; Wireless Adapter/RFU is not supported."
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }

    dialog.setContentView(panel)
    if (dialog.isShowing) sizeGameplayDialog(dialog)
}

internal fun MainActivity.currentRemoteRomPath(): String? {
    if (!romLoaded || currentPlatform != PLATFORM_GBA || currentPatchPath != null) return null
    return currentRomPath?.takeIf { it.isNotBlank() && File(it).exists() }
}

internal fun MainActivity.remoteRomHash(path: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(path).use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count <= 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun MainActivity.findLocalGbaRomByHash(hash: String, currentPath: String): String? {
    if (hash.isBlank()) return null
    if (runCatching { remoteRomHash(currentPath).equals(hash, ignoreCase = true) }.getOrDefault(false)) {
        return currentPath
    }
    val contentDir = File(filesDir, "library_content")
    val candidates = contentDir.listFiles()?.filter { file ->
        file.isFile && file.length() in 1..MAX_ROM_BYTES &&
            file.extension.lowercase(Locale.US) in setOf("gba", "mgba")
    }.orEmpty()
    for (candidate in candidates) {
        if (candidate.absolutePath == currentPath) continue
        val matches = runCatching { remoteRomHash(candidate.absolutePath).equals(hash, ignoreCase = true) }
            .getOrDefault(false)
        if (matches) return candidate.absolutePath
    }
    return null
}

internal fun MainActivity.stageRemoteMirrorSave(file: File, bytes: ByteArray) {
    remoteMirrorSaveFile = file
    remoteMirrorSaveExisted = file.exists()
    remoteMirrorSaveBackup = if (file.exists() && file.length() <= REMOTE_MAX_SAVE_BYTES) {
        runCatching { file.readBytes() }.getOrNull()
    } else null
    writeRemoteSave(file, bytes)
}

internal fun MainActivity.restoreRemoteMirrorSave() {
    val file = remoteMirrorSaveFile ?: return
    try {
        if (remoteMirrorSaveExisted) {
            val backup = remoteMirrorSaveBackup
            if (backup != null) writeRemoteSave(file, backup)
        } else {
            file.delete()
        }
    } catch (_: Exception) {
    }
    remoteMirrorSaveFile = null
    remoteMirrorSaveBackup = null
    remoteMirrorSaveExisted = false
}

internal fun MainActivity.remoteSaveFile(path: String, player: Int = 0): File {
    val rom = File(path)
    val romId = romIdForLaunchPath(path)
    if (!romId.isNullOrBlank()) {
        saveData.migrateLegacyBatteryData(romId, rom)
        return saveData.batterySaveFile(romId, player)
    }
    // Legacy fallback for a path that predates the identity migration.
    val suffix = if (player == 0) "sav" else "sa${player + 1}"
    return File(rom.parentFile, "${rom.nameWithoutExtension}.$suffix")
}

internal fun MainActivity.readRemoteSave(path: String): ByteArray {
    val file = remoteSaveFile(path, 0)
    return if (file.exists() && file.length() in 1..REMOTE_MAX_SAVE_BYTES.toLong()) file.readBytes() else ByteArray(0)
}

internal fun MainActivity.writeRemoteSave(file: File, bytes: ByteArray) {
    file.parentFile?.mkdirs()
    if (bytes.isEmpty()) {
        if (file.exists()) file.delete()
    } else {
        fileOps.atomicWriteBytes(file, bytes)
    }
}

internal fun MainActivity.writeRemoteHello(out: DataOutputStream, romHash: String, save: ByteArray) {
    out.writeUTF(REMOTE_LINK_PROTOCOL)
    out.writeUTF(romHash)
    out.writeInt(save.size)
    if (save.isNotEmpty()) out.write(save)
    out.flush()
}

internal data class RemoteHello(val hash: String, val save: ByteArray)

internal fun MainActivity.readRemoteHello(input: DataInputStream): RemoteHello {
    val protocol = input.readUTF()
    if (protocol != REMOTE_LINK_PROTOCOL) throw IOException("Incompatible Retra Remote Link version")
    val hash = input.readUTF()
    val size = input.readInt()
    if (size !in 0..REMOTE_MAX_SAVE_BYTES) throw IOException("Remote save data is too large")
    val save = ByteArray(size)
    if (size > 0) input.readFully(save)
    return RemoteHello(hash, save)
}

internal fun MainActivity.localIpv4Address(): String {
    return try {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress ?: "Unavailable"
    } catch (_: Exception) {
        "Unavailable"
    }
}

internal fun MainActivity.showRemoteProgress(title: String, message: String, cancel: () -> Unit) {
    runOnUiThread {
        remoteProgressDialog?.dismiss()
        remoteProgressDialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ -> cancel() }
            .create()
            .also { it.show() }
    }
}

internal fun MainActivity.beginRemoteAttempt(dialog: Dialog) {
    gameplayModalPauseActive = true
    dialog.dismiss()
    stopEmulation()
    releaseAllKeys()
}

internal fun MainActivity.cancelRemoteAttempt(message: String? = null, resumeGame: Boolean = true) {
    try { remoteAttemptCloser?.invoke() } catch (_: Exception) {}
    remoteAttemptCloser = null
    remoteProgressDialog?.dismiss()
    remoteProgressDialog = null
    gameplayModalPauseActive = false
    if (!message.isNullOrBlank()) RetraNotice.makeText(this, message, RetraNotice.LENGTH_LONG).show()
    if (resumeGame && romLoaded && binding.emulatorOverlay.visibility == View.VISIBLE) startEmulation()
}

internal fun MainActivity.beginWifiRemoteServer(dialog: Dialog) {
    val romPath = currentRemoteRomPath() ?: run {
        RetraNotice.makeText(this, "Remote Link requires an unpatched GBA ROM", RetraNotice.LENGTH_LONG).show()
        return
    }
    beginRemoteAttempt(dialog)
    showRemoteProgress(
        "Wi-Fi Remote Link • Host",
        "Waiting for Player 2…\n\nIP: ${localIpv4Address()}\nPort: $REMOTE_LINK_PORT\n\nSame or different GBA games are supported when both ROMs are imported on both phones.",
    ) { cancelRemoteAttempt() }

    remoteExecutor.execute {
        var server: ServerSocket? = null
        try {
            val serverSocket = ServerSocket(REMOTE_LINK_PORT).apply { reuseAddress = true }
            server = serverSocket
            remoteAttemptCloser = { try { serverSocket.close() } catch (_: Exception) {} }
            val socket = serverSocket.accept().apply { tcpNoDelay = true; keepAlive = true }
            try { serverSocket.close() } catch (_: Exception) {}
            remoteAttemptCloser = { try { socket.close() } catch (_: Exception) {} }
            establishRemoteLink(
                role = 0,
                romPath = romPath,
                transport = "Wi-Fi",
                input = DataInputStream(socket.getInputStream().buffered()),
                output = DataOutputStream(socket.getOutputStream().buffered()),
                closer = { try { socket.close() } catch (_: Exception) {} }
            )
        } catch (e: Exception) {
            try { server?.close() } catch (_: Exception) {}
            if (!remoteTransport.isActive) runOnUiThread { cancelRemoteAttempt("Wi-Fi host failed: ${e.message ?: "connection closed"}") }
        }
    }
}

internal fun MainActivity.promptWifiRemoteClient(dialog: Dialog) {
    val romPath = currentRemoteRomPath() ?: run {
        RetraNotice.makeText(this, "Remote Link requires an unpatched GBA ROM", RetraNotice.LENGTH_LONG).show()
        return
    }
    val input = EditText(this).apply {
        hint = "Host IP address"
        setText(prefs.getString(REMOTE_HOST_PREF, "") ?: "")
        inputType = InputType.TYPE_CLASS_TEXT
        setSingleLine(true)
        setPadding(dpInt(18), dpInt(8), dpInt(18), dpInt(8))
    }
    AlertDialog.Builder(this)
        .setTitle("Wi-Fi Remote Link")
        .setMessage("Enter the IP shown on Player 1's Retra screen. Both phones should be on the same Wi-Fi or hotspot.")
        .setView(input)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Connect") { _, _ ->
            val host = input.text.toString().trim()
            if (host.isBlank()) {
                RetraNotice.makeText(this, "Enter the host IP address", RetraNotice.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            prefs.edit().putString(REMOTE_HOST_PREF, host).apply()
            beginRemoteAttempt(dialog)
            showRemoteProgress("Wi-Fi Remote Link • Client", "Connecting to $host:$REMOTE_LINK_PORT…") { cancelRemoteAttempt() }
            remoteExecutor.execute {
                try {
                    val socket = Socket().apply {
                        tcpNoDelay = true
                        keepAlive = true
                        connect(java.net.InetSocketAddress(host, REMOTE_LINK_PORT), 8000)
                    }
                    remoteAttemptCloser = { try { socket.close() } catch (_: Exception) {} }
                    establishRemoteLink(
                        role = 1,
                        romPath = romPath,
                        transport = "Wi-Fi",
                        input = DataInputStream(socket.getInputStream().buffered()),
                        output = DataOutputStream(socket.getOutputStream().buffered()),
                        closer = { try { socket.close() } catch (_: Exception) {} }
                    )
                } catch (e: Exception) {
                    if (!remoteTransport.isActive) runOnUiThread { cancelRemoteAttempt("Wi-Fi connection failed: ${e.message ?: "host unavailable"}") }
                }
            }
        }
        .show()
}

internal fun MainActivity.hasBluetoothConnectPermission(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}

internal fun MainActivity.requestBluetoothConnectPermission(): Boolean {
    if (hasBluetoothConnectPermission()) return true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), BLUETOOTH_CONNECT_REQUEST)
        RetraNotice.makeText(this, "Allow Nearby devices, then choose Bluetooth Link again", RetraNotice.LENGTH_LONG).show()
    }
    return false
}

@SuppressLint("MissingPermission")
internal fun MainActivity.beginBluetoothRemoteServer(dialog: Dialog) {
    if (!requestBluetoothConnectPermission()) return
    val romPath = currentRemoteRomPath() ?: run {
        RetraNotice.makeText(this, "Remote Link requires an unpatched GBA ROM", RetraNotice.LENGTH_LONG).show()
        return
    }
    val adapter = getSystemService(BluetoothManager::class.java)?.adapter
    if (adapter == null || !adapter.isEnabled) {
        RetraNotice.makeText(this, "Turn Bluetooth on first", RetraNotice.LENGTH_LONG).show()
        return
    }
    beginRemoteAttempt(dialog)
    showRemoteProgress(
        "Bluetooth Remote Link • Host",
        "Waiting for a paired Retra device…\n\nPair the two phones in Android Bluetooth settings first.",
    ) { cancelRemoteAttempt() }
    remoteExecutor.execute {
        var server: BluetoothServerSocket? = null
        try {
            val serverSocket = adapter.listenUsingRfcommWithServiceRecord("Retra Remote Link", REMOTE_BLUETOOTH_UUID)
            server = serverSocket
            remoteAttemptCloser = { try { serverSocket.close() } catch (_: Exception) {} }
            val socket: BluetoothSocket = serverSocket.accept()
            try { serverSocket.close() } catch (_: Exception) {}
            remoteAttemptCloser = { try { socket.close() } catch (_: Exception) {} }
            establishRemoteLink(
                role = 0,
                romPath = romPath,
                transport = "Bluetooth",
                input = DataInputStream(socket.inputStream.buffered()),
                output = DataOutputStream(socket.outputStream.buffered()),
                closer = { try { socket.close() } catch (_: Exception) {} }
            )
        } catch (e: Exception) {
            try { server?.close() } catch (_: Exception) {}
            if (!remoteTransport.isActive) runOnUiThread { cancelRemoteAttempt("Bluetooth host failed: ${e.message ?: "connection closed"}") }
        }
    }
}

@SuppressLint("MissingPermission")
internal fun MainActivity.beginBluetoothRemoteClient(dialog: Dialog) {
    if (!requestBluetoothConnectPermission()) return
    val romPath = currentRemoteRomPath() ?: run {
        RetraNotice.makeText(this, "Remote Link requires an unpatched GBA ROM", RetraNotice.LENGTH_LONG).show()
        return
    }
    val adapter = getSystemService(BluetoothManager::class.java)?.adapter
    if (adapter == null || !adapter.isEnabled) {
        RetraNotice.makeText(this, "Turn Bluetooth on first", RetraNotice.LENGTH_LONG).show()
        return
    }
    val devices = try { adapter.bondedDevices.toList().sortedBy { it.name ?: it.address } } catch (_: Exception) { emptyList() }
    if (devices.isEmpty()) {
        RetraNotice.makeText(this, "Pair the two phones in Android Bluetooth settings first", RetraNotice.LENGTH_LONG).show()
        return
    }
    val labels = devices.map { "${it.name ?: "Android device"}\n${it.address}" }.toTypedArray()
    AlertDialog.Builder(this)
        .setTitle("Bluetooth Remote Link")
        .setItems(labels) { _, which ->
            val device = devices[which]
            beginRemoteAttempt(dialog)
            showRemoteProgress("Bluetooth Remote Link • Client", "Connecting to ${device.name ?: device.address}…") { cancelRemoteAttempt() }
            remoteExecutor.execute {
                try {
                    val socket = device.createRfcommSocketToServiceRecord(REMOTE_BLUETOOTH_UUID)
                    remoteAttemptCloser = { try { socket.close() } catch (_: Exception) {} }
                    socket.connect()
                    establishRemoteLink(
                        role = 1,
                        romPath = romPath,
                        transport = "Bluetooth",
                        input = DataInputStream(socket.inputStream.buffered()),
                        output = DataOutputStream(socket.outputStream.buffered()),
                        closer = { try { socket.close() } catch (_: Exception) {} }
                    )
                } catch (e: Exception) {
                    if (!remoteTransport.isActive) runOnUiThread { cancelRemoteAttempt("Bluetooth connection failed: ${e.message ?: "device unavailable"}") }
                }
            }
        }
        .setNegativeButton("Cancel", null)
        .show()
}

internal fun MainActivity.establishRemoteLink(
    role: Int,
    romPath: String,
    transport: String,
    input: DataInputStream,
    output: DataOutputStream,
    closer: () -> Unit
) {
    var stagedClientSave = false
    try {
        val hash = remoteRomHash(romPath)
        // The normal core runs against a working save. Commit it before the
        // Remote Link handshake so the peer receives the latest battery data.
        runCatching { saveData.commitWorkingSave(currentRomId, 0) }
        val localSave = readRemoteSave(romPath)
        val peer: RemoteHello
        if (role == 0) {
            peer = readRemoteHello(input)
            writeRemoteHello(output, hash, localSave)
        } else {
            writeRemoteHello(output, hash, localSave)
            peer = readRemoteHello(input)
        }

        val sameRom = peer.hash.equals(hash, ignoreCase = true)
        val peerRomPath = if (sameRom) romPath else findLocalGbaRomByHash(peer.hash, romPath)
            ?: throw IOException(
                "Different game detected. Import the other player's GBA ROM on this phone too, then retry. Retra does not transfer ROM files."
            )

        // Every phone must build the same ordered pair: host cartridge is
        // logical P1, client cartridge is logical P2. For different games,
        // each phone resolves the peer ROM from its own local library.
        val pairFirstPath = if (role == 0) romPath else peerRomPath
        val pairSecondPath = if (role == 0) peerRomPath else romPath
        remoteSameRomSession = sameRom

        // Close the normal single-core session before touching battery saves.
        shutdownCore()
        remoteLocalSaveBefore = localSave

        if (sameRom) {
            // Same cartridge: mGBA's P1/P2 convention is .sav + .sa2.
            if (role == 0) {
                writeRemoteSave(remoteSaveFile(romPath, 1), peer.save)
            } else {
                writeRemoteSave(remoteSaveFile(romPath, 1), localSave)
                writeRemoteSave(remoteSaveFile(romPath, 0), peer.save)
                stagedClientSave = true
            }
        } else {
            // Different cartridges use their own normal .sav files. Only
            // the mirrored peer cartridge is temporary on each phone, so
            // preserve its local user's save and restore it after linking.
            val mirrorPath = if (role == 0) pairSecondPath else pairFirstPath
            stageRemoteMirrorSave(remoteSaveFile(mirrorPath, 0), peer.save)
        }

        val pairFirstId = romIdForLaunchPath(pairFirstPath)
            ?: throw IOException("Could not resolve Player 1 ROM identity")
        val pairSecondId = romIdForLaunchPath(pairSecondPath)
            ?: throw IOException("Could not resolve Player 2 ROM identity")
        val secondSavePlayer = if (sameRom) 1 else 0
        val firstSavePath = saveData.prepareWorkingSave(pairFirstId, 0, File(pairFirstPath)).absolutePath
        val secondSavePath = saveData.prepareWorkingSave(pairSecondId, secondSavePlayer, File(pairSecondPath)).absolutePath
        val started = startLocalLink(pairFirstPath, pairSecondPath, firstSavePath, secondSavePath)
        if (!started) throw IOException("mGBA could not create the two-player link session")
        localLinkActive = true
        localLinkSinglePakActive = false
        activeLinkFirstRomId = pairFirstId
        activeLinkSecondRomId = pairSecondId
        activeLinkSecondSavePlayer = secondSavePlayer
        setLocalLinkPlayer(role)
        setLocalLinkPaused(true)
        resetCore()
        clearLocalLinkInputSchedule()
        setLocalLinkKeyMask(role, 0)
        setLocalLinkKeyMask(1 - role, 0)

        // READY/START keeps both replicated lockstep pairs paused until both
        // phones have loaded and reset the same ordered cartridge pair.
        if (role == 1) {
            output.writeUTF("READY")
            output.flush()
            if (input.readUTF() != "START") throw IOException("Remote host cancelled startup")
        } else {
            if (input.readUTF() != "READY") throw IOException("Remote client cancelled startup")
            output.writeUTF("START")
            output.flush()
        }

        remoteAttemptCloser = null
        remoteTransport.attach(
            role = role,
            transportName = transport,
            input = input,
            output = output,
            closer = closer
        )

        runOnUiThread {
            remoteProgressDialog?.dismiss()
            remoteProgressDialog = null
            gameplayModalPauseActive = false
            configureVideoSurfaceFromNative()
            applyGameplayVisualSettings()
            audioController.configure(romLoaded)
            val roleName = if (role == 0) "Host" else "Client"
            val pairLabel = if (sameRom) "same game" else "two-game pair"
            binding.gameTitle.text = currentRomTitle
            binding.systemLabel.text = "Game Boy Advance • Remote Link v2 • $roleName • P${role + 1}"
            binding.buttonL.visibility = View.VISIBLE
            binding.buttonR.visibility = View.VISIBLE
            RetraNotice.makeText(this, "$transport Remote Link connected • $pairLabel • Player ${role + 1}", RetraNotice.LENGTH_LONG).show()
            startEmulation()
        }
    } catch (e: Exception) {
        try { closer() } catch (_: Exception) {}
        if (localLinkActive) {
            try { stopLocalLink() } catch (_: Throwable) {}
            commitActiveWorkingSaves()
            clearActiveLinkSaveTracking()
            localLinkActive = false
        }
        remoteTransport.close(sendDisconnect = false)
        if (remoteSameRomSession) {
            if (stagedClientSave && role == 1) {
                remoteLocalSaveBefore?.let { writeRemoteSave(remoteSaveFile(romPath, 0), it) }
            }
            try { remoteSaveFile(romPath, 1).delete() } catch (_: Exception) {}
        } else {
            restoreRemoteMirrorSave()
        }
        remoteLocalSaveBefore = null
        runOnUiThread {
            cancelRemoteAttempt("Remote Link failed: ${e.message ?: "connection error"}", resumeGame = false)
            romLoaded = false
            if (File(romPath).exists()) {
                loadRomFile(File(romPath), currentRomTitle, null, currentRomId)
            }
        }
    }
}

internal fun MainActivity.setGameplayKeyHeld(key: Int, pressed: Boolean) {
    if (key !in 0..9) return

    val previousCount = gameplayInputState.keyHoldCounts[key]
    val nextCount = if (pressed) {
        (previousCount + 1).coerceAtMost(32)
    } else {
        (previousCount - 1).coerceAtLeast(0)
    }
    if (previousCount == nextCount) return

    gameplayInputState.keyHoldCounts[key] = nextCount
    // Only cross JNI / Remote Link on the effective 0 <-> 1 transition.
    if (previousCount == 0 || nextCount == 0) {
        setGameplayKey(key, nextCount > 0)
    }
}

internal fun MainActivity.setGameplayKey(key: Int, pressed: Boolean) {
    if (key !in 0..9) return

    val bit = 1 shl key
    val wasPressed = gameplayInputState.activeGameplayKeyMask and bit != 0
    if (wasPressed == pressed) return

    gameplayInputState.activeGameplayKeyMask = if (pressed) {
        gameplayInputState.activeGameplayKeyMask or bit
    } else {
        gameplayInputState.activeGameplayKeyMask and bit.inv()
    }

    if (remoteTransport.handleGameplayKey(key, pressed)) return
    setKey(key, pressed)
}

internal fun MainActivity.finalizeRemoteRoleSave(romPath: String, role: Int) {
    if (remoteSameRomSession) {
        if (role == 1) {
            val p2 = remoteSaveFile(romPath, 1)
            if (p2.exists() && p2.length() > 0L) {
                try { fileOps.atomicCopyVerified(p2, remoteSaveFile(romPath, 0)) } catch (_: Exception) {}
            } else {
                remoteLocalSaveBefore?.let { try { writeRemoteSave(remoteSaveFile(romPath, 0), it) } catch (_: Exception) {} }
            }
        }
        try { remoteSaveFile(romPath, 1).delete() } catch (_: Exception) {}
    } else {
        restoreRemoteMirrorSave()
    }
    remoteLocalSaveBefore = null
    remoteSameRomSession = true
}

internal fun MainActivity.disconnectRemoteLinkAndRestore(message: String = "Remote Link disconnected") {
    if (!remoteTransport.isActive && !localLinkActive) return
    val firstPath = currentRomPath
    val firstTitle = currentRomTitle
    val firstId = currentRomId
    val role = remoteTransport.role

    remoteTransport.close(sendDisconnect = true)
    gameplayModalPauseActive = true
    gameplayMenuDialog?.dismiss()
    stopEmulation()
    releaseAllKeys()
    try { stopLocalLink() } catch (_: Throwable) {}
    commitActiveWorkingSaves()
    clearActiveLinkSaveTracking()
    localLinkActive = false
    localLinkSinglePakActive = false
    localLinkPlayer = 0
    if (!firstPath.isNullOrBlank()) finalizeRemoteRoleSave(firstPath, role)

    romLoaded = false
    if (!firstPath.isNullOrBlank() && File(firstPath).exists()) {
        loadRomFile(File(firstPath), firstTitle, null, firstId)
    } else {
        leaveEmulatorPresentation()
        showWebUiWithoutBlankFrame()
    }
    gameplayModalPauseActive = false
    RetraNotice.makeText(this, message, RetraNotice.LENGTH_LONG).show()
}

internal fun MainActivity.renderLinkLocalScreen(dialog: Dialog) {
    gameplayMenuSubscreen = true
    gameplayMenuBackHandler = { renderGameplayMainMenu(dialog) }

    val (panel, content) = buildMenuShell("Link local", showBack = true)
    val compatibility = multiplayerCompatibility.detect(currentRomPath, currentPlatform == PLATFORM_GBA, currentPatchPath != null)

    if (!compatibility.linkCableEligible) {
        addMenuAction(content, "Link unavailable", compatibility.reason) {
            RetraNotice.makeText(this, compatibility.reason, RetraNotice.LENGTH_LONG).show()
        }
    } else {
        addMenuAction(content, currentRomTitle, "Use the current game for Player 2") {
            val secondPath = currentRomPath
            if (secondPath.isNullOrBlank()) {
                RetraNotice.makeText(this, "Current ROM path is unavailable", RetraNotice.LENGTH_SHORT).show()
            } else {
                startLocalLinkSession(secondPath, currentRomTitle, currentRomId)
            }
        }
        addMenuAction(content, "Another game…", "Choose a second GBA ROM") {
            pendingLocalLinkPicker = true
            localLinkGamePicker.launch(arrayOf("*/*"))
        }
        val singlePakBios = selectedGbaBiosForSinglePak()
        addMenuAction(
            content,
            "Single-Pak / Multiboot",
            if (singlePakBios != null) "One cartridge • BIOS receiver for Player 2" else "Requires a selected 16 KiB GBA BIOS"
        ) {
            if (singlePakBios == null) {
                AlertDialog.Builder(this)
                    .setTitle("GBA BIOS required")
                    .setMessage(
                        "Single-Pak/Multiboot needs Player 2 to boot the real GBA BIOS with no cartridge attached. " +
                            "Select your legally obtained GBA BIOS in Settings → BIOS, then try again."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                startLocalSinglePakSession(singlePakBios.absolutePath)
            }
        }
        addMenuAction(content, "Detected compatibility", compatibility.availabilityLabel) {
            RetraNotice.makeText(this, "Normal Multi-Pak and Local Single-Pak/Multiboot are supported • RFU is not supported", RetraNotice.LENGTH_LONG).show()
        }
    }
    addMenuAction(content, "Cancel") { renderGameplayMainMenu(dialog) }

    dialog.setContentView(panel)
    if (dialog.isShowing) sizeGameplayDialog(dialog)
}

internal fun MainActivity.showLocalLinkStateRestriction() {
    RetraNotice.makeText(
        this,
        "Save states are disabled during Local Link to keep both players synchronized",
        RetraNotice.LENGTH_SHORT
    ).show()
}

internal fun MainActivity.configureVideoSurfaceFromNative() {
    videoWidth = getVideoWidth().coerceAtLeast(1)
    videoHeight = getVideoHeight().coerceAtLeast(1)
    framePixels = gameplayFrameMailbox.configure(videoWidth, videoHeight)
    // Keep legacy buffers/Bitmap available only for the rare ImageView fallback.
    displayPixels = IntArray(videoWidth * videoHeight)
    presentationPixels = IntArray(videoWidth * videoHeight)
    bitmap?.recycle()
    bitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888).apply {
        density = Bitmap.DENSITY_NONE
    }
    binding.gameScreen.setImageBitmap(bitmap)
    binding.shaderGameScreen.attachFrameMailbox(gameplayFrameMailbox)
}

/** Called by GameplayFramePresenter on Android's display VSync. */
internal fun MainActivity.presentLatestGameplayFrame() {
    // Choreographer only schedules the GL draw. The GL thread consumes the
    // newest pending mailbox frame, so the UI thread never copies hot video.
    if (shaderController.requestPresentLatest()) return

    // OEM compatibility fallback if the GLSurfaceView could not be created.
    val frame = gameplayFrameMailbox.acquireLatestForRender() ?: return
    val targetBitmap = bitmap ?: return
    if (targetBitmap.isRecycled) return
    targetBitmap.setPixels(
        frame.pixels,
        0,
        frame.width,
        0,
        0,
        frame.width,
        frame.height
    )
    presentationPixels = frame.pixels.copyOf()
    binding.gameScreen.invalidate()
}

internal fun MainActivity.selectedGbaBiosForSinglePak(): File? {
    val path = prefs.getString(BIOS_GBA_PATH_PREF, null)?.takeIf { it.isNotBlank() } ?: return null
    val file = File(path)
    // The official GBA BIOS is exactly 16 KiB. mGBA performs its own BIOS
    // validation again natively before the receiver core is started.
    return file.takeIf { it.isFile && it.length() == 16_384L }
}

internal fun MainActivity.startLocalSinglePakSession(gbaBiosPath: String) {
    if (!romLoaded || currentPlatform != PLATFORM_GBA) {
        RetraNotice.makeText(this, "Single-Pak requires a running GBA game", RetraNotice.LENGTH_SHORT).show()
        return
    }
    if (currentPatchPath != null) {
        RetraNotice.makeText(this, "Single-Pak currently supports unpatched GBA ROMs", RetraNotice.LENGTH_LONG).show()
        return
    }

    val bios = File(gbaBiosPath)
    if (!bios.isFile || bios.length() != 16_384L) {
        RetraNotice.makeText(this, "Select a valid 16 KiB GBA BIOS first", RetraNotice.LENGTH_LONG).show()
        return
    }

    val firstRomPath = currentRomPath
    if (firstRomPath.isNullOrBlank()) {
        RetraNotice.makeText(this, "Current ROM path is unavailable", RetraNotice.LENGTH_SHORT).show()
        return
    }

    val firstTitle = currentRomTitle
    val firstId = currentRomId
    stopEmulation()
    releaseAllKeys()

    val firstSavePath = try {
        saveData.commitWorkingSave(firstId, 0)
        saveData.prepareWorkingSave(firstId, 0, File(firstRomPath)).absolutePath
    } catch (e: Exception) {
        RetraNotice.makeText(this, "Could not prepare Single-Pak save data: ${e.message ?: "storage error"}", RetraNotice.LENGTH_LONG).show()
        startEmulation()
        return
    }

    val started = try {
        startLocalSinglePak(firstRomPath, firstSavePath, bios.absolutePath)
    } catch (_: Throwable) {
        false
    }

    if (!started) {
        localLinkActive = false
        localLinkSinglePakActive = false
        RetraNotice.makeText(
            this,
            "Could not start Single-Pak. Check the GBA BIOS and confirm this game supports Single-Pak/Multiboot.",
            RetraNotice.LENGTH_LONG
        ).show()
        loadRomFile(File(firstRomPath), firstTitle, null, firstId)
        return
    }

    localLinkActive = true
    localLinkSinglePakActive = true
    activeLinkFirstRomId = firstId
    activeLinkSecondRomId = null
    activeLinkSecondSavePlayer = 0
    localLinkPlayer = 0
    localLinkPlayer1Title = firstTitle
    localLinkPlayer2Title = "Single-Pak Client"
    activeEmulationSpeed = 1.0
    updateFastForwardUi()
    configureVideoSurfaceFromNative()
    applyGameplayVisualSettings()
    audioController.configure(romLoaded)

    binding.gameTitle.text = localLinkPlayer1Title
    binding.systemLabel.text = "Game Boy Advance • Single-Pak • P1"
    binding.buttonL.visibility = View.VISIBLE
    binding.buttonR.visibility = View.VISIBLE

    // The receiver core boots a real GBA BIOS with no cartridge. Native code
    // temporarily holds START+SELECT so the BIOS enters multiboot receive mode.
    try { setLocalLinkPaused(true) } catch (_: Throwable) {}
    RetraNotice.makeText(this, "Single-Pak ready • choose Single-Pak mode in the game", RetraNotice.LENGTH_LONG).show()

    val openMenu = gameplayMenuDialog
    if (openMenu?.isShowing == true) {
        openMenu.dismiss()
    } else {
        startEmulation()
    }
}

internal fun MainActivity.startLocalLinkSession(secondRomPath: String, secondTitle: String, secondRomId: String) {
    if (!romLoaded || currentPlatform != PLATFORM_GBA) {
        RetraNotice.makeText(this, "Local Link requires a running GBA game", RetraNotice.LENGTH_SHORT).show()
        return
    }
    if (currentPatchPath != null) {
        RetraNotice.makeText(this, "Local Link currently supports unpatched GBA ROMs", RetraNotice.LENGTH_LONG).show()
        return
    }
    val secondCompatibility = multiplayerCompatibility.detectCandidate(secondRomPath)
    if (!secondCompatibility.linkCableEligible) {
        RetraNotice.makeText(this, secondCompatibility.reason, RetraNotice.LENGTH_LONG).show()
        return
    }

    val firstRomPath = currentRomPath
    if (firstRomPath.isNullOrBlank()) {
        RetraNotice.makeText(this, "Current ROM path is unavailable", RetraNotice.LENGTH_SHORT).show()
        return
    }

    val firstTitle = currentRomTitle
    val firstId = currentRomId
    stopEmulation()
    releaseAllKeys()

    // Snapshot the current single-core save before startLocalLink replaces
    // that core, then stage verified working files for both linked players.
    val sameRom = firstRomPath == secondRomPath || firstId == secondRomId
    val secondSavePlayer = if (sameRom) 1 else 0
    val preparedPaths = try {
        saveData.commitWorkingSave(firstId, 0)
        saveData.prepareWorkingSave(firstId, 0, File(firstRomPath)).absolutePath to
            saveData.prepareWorkingSave(secondRomId, secondSavePlayer, File(secondRomPath)).absolutePath
    } catch (e: Exception) {
        RetraNotice.makeText(this, "Could not prepare Link save data: ${e.message ?: "storage error"}", RetraNotice.LENGTH_LONG).show()
        startEmulation()
        return
    }
    val firstSavePath = preparedPaths.first
    val secondSavePath = preparedPaths.second

    val started = try {
        startLocalLink(firstRomPath, secondRomPath, firstSavePath, secondSavePath)
    } catch (_: Throwable) {
        false
    }

    if (!started) {
        localLinkActive = false
        RetraNotice.makeText(
            this,
            "Could not start Local Link. Both games must support normal GBA Link Cable.",
            RetraNotice.LENGTH_LONG
        ).show()
        // startLocalLink replaces the single native core while attempting to
        // build the pair. Restore Player 1 immediately if setup failed.
        loadRomFile(File(firstRomPath), firstTitle, null, firstId)
        return
    }

    localLinkActive = true
    localLinkSinglePakActive = false
    activeLinkFirstRomId = firstId
    activeLinkSecondRomId = secondRomId
    activeLinkSecondSavePlayer = secondSavePlayer
    localLinkPlayer = 0
    localLinkPlayer1Title = firstTitle
    localLinkPlayer2Title = secondTitle.ifBlank { "Player 2" }
    activeEmulationSpeed = 1.0
    updateFastForwardUi()
    configureVideoSurfaceFromNative()
    applyGameplayVisualSettings()
    audioController.configure(romLoaded)

    binding.gameTitle.text = localLinkPlayer1Title
    binding.systemLabel.text = "Game Boy Advance • Local Link • P1"
    binding.buttonL.visibility = View.VISIBLE
    binding.buttonR.visibility = View.VISIBLE

    // The link cores begin running natively. Keep them paused until the
    // gameplay menu closes, then startEmulation() resumes both together.
    try { setLocalLinkPaused(true) } catch (_: Throwable) {}
    RetraNotice.makeText(this, "Local Link connected • Player 1", RetraNotice.LENGTH_SHORT).show()

    val openMenu = gameplayMenuDialog
    if (openMenu?.isShowing == true) {
        openMenu.dismiss()
    } else {
        startEmulation()
    }
}

internal fun MainActivity.switchLocalLinkPlayer() {
    if (!localLinkActive) return
    stopEmulation()
    releaseAllKeys()
    val nextPlayer = if (localLinkPlayer == 0) 1 else 0
    val switched = try { setLocalLinkPlayer(nextPlayer) } catch (_: Throwable) { false }
    if (!switched) {
        RetraNotice.makeText(this, "Could not switch link player", RetraNotice.LENGTH_SHORT).show()
        return
    }

    localLinkPlayer = nextPlayer
    configureVideoSurfaceFromNative()
    applyGameplayVisualSettings()
    audioController.configure(romLoaded)
    val title = if (nextPlayer == 0) localLinkPlayer1Title else localLinkPlayer2Title
    binding.gameTitle.text = title
    binding.systemLabel.text = "Game Boy Advance • ${if (localLinkSinglePakActive) "Single-Pak" else "Local Link"} • P${nextPlayer + 1}"
    RetraNotice.makeText(this, "Player ${nextPlayer + 1}", RetraNotice.LENGTH_SHORT).show()
}

internal fun MainActivity.disconnectLocalLinkAndRestore(dialog: Dialog? = gameplayMenuDialog) {
    if (!localLinkActive) return
    val firstPath = currentRomPath
    val firstTitle = localLinkPlayer1Title.ifBlank { currentRomTitle }
    val firstId = currentRomId

    gameplayModalPauseActive = true
    dialog?.dismiss()
    stopEmulation()
    releaseAllKeys()
    try { stopLocalLink() } catch (_: Throwable) {}
    commitActiveWorkingSaves()
    clearActiveLinkSaveTracking()
    localLinkActive = false
    localLinkSinglePakActive = false
    localLinkPlayer = 0
    localLinkPlayer2Title = "Player 2"
    romLoaded = false

    if (!firstPath.isNullOrBlank() && File(firstPath).exists()) {
        loadRomFile(File(firstPath), firstTitle, null, firstId)
        RetraNotice.makeText(this, "Local Link disconnected", RetraNotice.LENGTH_SHORT).show()
    } else {
        leaveEmulatorPresentation()
        showWebUiWithoutBlankFrame()
        RetraNotice.makeText(this, "Local Link disconnected", RetraNotice.LENGTH_SHORT).show()
    }
    gameplayModalPauseActive = false
}
