package com.retra.emulator

import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

/**
 * Conservative compatibility gate for Retra's supported GBA multiplayer scope.
 *
 * Retra supports normal GBA Multi-Pak/link-cable sessions plus Local
 * Single-Pak/Multiboot through a BIOS-only receiver core. There is no
 * reliable universal bit in a GBA ROM header saying that a game exposes a link
 * menu, so this detector validates the cartridge/platform/patch requirements
 * and surfaces the ROM header identity. The game itself still decides whether
 * a multiplayer mode is available from its own menus.
 */
class GbaMultiplayerCompatibility {
    enum class Confidence { BLOCKED, ELIGIBLE, VERIFIED_HEADER }

    data class Profile(
        val linkCableEligible: Boolean,
        val confidence: Confidence,
        val internalTitle: String,
        val gameCode: String,
        val reason: String,
        val localSupported: Boolean,
        val wifiSupported: Boolean,
        val bluetoothSupported: Boolean,
        val singlePakSupported: Boolean = false,
        val wirelessRfuSupported: Boolean = false
    ) {
        val identityLabel: String
            get() = listOf(internalTitle, gameCode.takeIf { it.isNotBlank() })
                .filterNotNull()
                .filter { it.isNotBlank() }
                .joinToString(" • ")
                .ifBlank { "GBA ROM" }

        val availabilityLabel: String
            get() = if (linkCableEligible) {
                when (confidence) {
                    Confidence.VERIFIED_HEADER -> "Link Cable ready • $identityLabel"
                    else -> "Link Cable eligible • $identityLabel"
                }
            } else reason
    }

    fun detect(path: String?, platformIsGba: Boolean, patched: Boolean): Profile {
        if (!platformIsGba) return blocked("Game Boy Advance games only")
        if (patched) return blocked("Patched ROMs are not supported for GBA Link")
        val file = path?.takeIf { it.isNotBlank() }?.let(::File)
            ?: return blocked("ROM path is unavailable")
        if (!file.isFile || file.length() <= 0L) return blocked("ROM file is unavailable")
        if (file.extension.lowercase(Locale.US) !in setOf("gba", "mgba")) {
            return blocked("Normal GBA ROMs are required for Link Cable")
        }

        val header = readHeader(file)
        return Profile(
            linkCableEligible = true,
            confidence = if (header?.checksumValid == true) Confidence.VERIFIED_HEADER else Confidence.ELIGIBLE,
            internalTitle = header?.title.orEmpty(),
            gameCode = header?.gameCode.orEmpty(),
            reason = if (header?.checksumValid == true) "Verified GBA cartridge header" else "GBA ROM detected; header could not be fully verified",
            localSupported = true,
            wifiSupported = true,
            bluetoothSupported = true,
            singlePakSupported = true
        )
    }

    /** Used for a second Local Link cartridge before mGBA opens it. */
    fun detectCandidate(path: String?): Profile {
        val file = path?.takeIf { it.isNotBlank() }?.let(::File)
        val looksGba = file?.isFile == true &&
            file.length() > 0L && file.extension.lowercase(Locale.US) in setOf("gba", "mgba")
        return detect(path, platformIsGba = looksGba, patched = false)
    }

    private fun blocked(reason: String) = Profile(
        linkCableEligible = false,
        confidence = Confidence.BLOCKED,
        internalTitle = "",
        gameCode = "",
        reason = reason,
        localSupported = false,
        wifiSupported = false,
        bluetoothSupported = false
    )

    private data class Header(val title: String, val gameCode: String, val checksumValid: Boolean)

    private fun readHeader(file: File): Header? = runCatching {
        if (file.length() < 0xC0) return@runCatching null
        val bytes = ByteArray(0xC0)
        RandomAccessFile(file, "r").use { raf -> raf.readFully(bytes) }
        fun ascii(start: Int, length: Int): String = bytes.copyOfRange(start, start + length)
            .takeWhile { it.toInt() != 0 }
            .map { (it.toInt() and 0xFF).toChar() }
            .joinToString("")
            .trim()
            .filter { it.code in 32..126 }
        var sum = 0
        for (i in 0xA0..0xBC) sum = (sum + (bytes[i].toInt() and 0xFF)) and 0xFF
        val expected = (-(sum + 0x19)) and 0xFF
        Header(
            title = ascii(0xA0, 12),
            gameCode = ascii(0xAC, 4).uppercase(Locale.US),
            checksumValid = expected == (bytes[0xBD].toInt() and 0xFF)
        )
    }.getOrNull()
}
