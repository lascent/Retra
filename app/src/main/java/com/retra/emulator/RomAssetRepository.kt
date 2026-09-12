package com.retra.emulator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

/** Owns per-ROM config and user-selected artwork persistence. */
class RomAssetRepository(
    private val fileOps: RetraFileOps,
    private val artworkRepository: ArtworkRepository
) {
    fun getConfig(romId: String): String {
        if (romId.isBlank()) return "{}"
        val file = configFile(romId)
        if (!file.exists() || file.length() <= 0L) return "{}"
        return runCatching { JSONObject(file.readText()).toString() }.getOrDefault("{}")
    }

    fun setConfig(romId: String, json: String): Boolean {
        if (romId.isBlank()) return false
        val normalized = runCatching { JSONObject(json).toString(2) }.getOrNull() ?: return false
        return runCatching {
            fileOps.atomicWriteText(configFile(romId), normalized)
            true
        }.getOrDefault(false)
    }

    fun saveMedia(romId: String, kind: String, dataUrl: String): Boolean {
        val target = mediaFile(romId, kind) ?: return false
        val base64 = dataUrl.substringAfter("base64,", "")
        if (base64.isBlank()) return false
        return runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IOException("Unsupported image")
            val temp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
            try {
                FileOutputStream(temp).use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.WEBP, 92, output)) {
                        throw IOException("Could not encode WebP")
                    }
                    output.flush()
                    runCatching { output.fd.sync() }
                }
                bitmap.recycle()
                if (temp.length() <= 0L) throw IOException("Empty image")

                // Mark the user's choice before the final file swap so the
                // automatic artwork worker cannot win a race and overwrite it.
                artworkRepository.beginManual(romId, kind)
                try {
                    fileOps.moveTempIntoPlace(temp, target, "Could not replace artwork")
                    artworkRepository.markManual(romId, kind)
                } catch (error: Throwable) {
                    artworkRepository.cancelManual(romId, kind)
                    throw error
                }
                true
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                temp.delete()
            }
        }.getOrDefault(false)
    }

    fun mediaUrl(romId: String, kind: String): String {
        val target = mediaFile(romId, kind) ?: return ""
        if (!target.exists() || target.length() <= 0L) return ""
        val safeId = fileOps.sanitizeFileName(romId)
        val root = if (kind.equals("cover", true)) "covers" else "backgrounds"
        return "https://appassets.androidplatform.net/$root/$safeId/${target.name}?v=${target.lastModified()}"
    }

    fun mediaFile(romId: String, kind: String): File? {
        if (romId.isBlank()) return null
        val safeId = fileOps.sanitizeFileName(romId)
        return when (kind.lowercase(Locale.US)) {
            "cover" -> File(File(fileOps.persistentCategoryDir("Covers"), safeId).apply { mkdirs() }, "cover.webp")
            "background" -> File(File(fileOps.persistentCategoryDir("Backgrounds"), safeId).apply { mkdirs() }, "background.webp")
            else -> null
        }
    }

    private fun configFile(romId: String): File =
        File(File(fileOps.persistentCategoryDir("Config"), fileOps.sanitizeFileName(romId)).apply { mkdirs() }, "config.json")
}
