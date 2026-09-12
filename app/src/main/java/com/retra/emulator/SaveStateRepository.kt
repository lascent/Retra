package com.retra.emulator

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Per-ROM save-state file naming, metadata, compatibility and deletion. */
class SaveStateRepository(
    private val prefs: RetraPreferences,
    private val fileOps: RetraFileOps,
    private val saveData: SaveDataRepository,
    private val coreVersion: () -> String,
    private val onDeletedCloudPaths: (List<String>) -> Unit = {}
) {
    fun slotLabel(slot: Int): String = if (slot == 0) "Quick" else "Slot $slot"

    fun stateFile(slot: Int, romId: String): File {
        val name = if (slot == 0) "quick.ss" else "slot_${slot.toString().padStart(2, '0')}.ss"
        return File(directory(romId), name)
    }

    fun thumbnailFile(slot: Int, romId: String): File {
        val name = if (slot == 0) "quick.png" else "slot_${slot.toString().padStart(2, '0')}.png"
        return File(directory(romId), name)
    }

    fun metadataFile(slot: Int, romId: String): File {
        val name = if (slot == 0) "quick.json" else "slot_${slot.toString().padStart(2, '0')}.json"
        return File(directory(romId), name)
    }

    fun writeMetadata(slot: Int, romId: String) {
        if (romId.isBlank()) return
        val metadata = JSONObject()
            .put("schemaVersion", 1)
            .put("romId", romId)
            .put("contentHash", prefs.getString("content_hash_$romId", "") ?: "")
            .put("platform", prefs.getString("system_$romId", "") ?: "")
            .put("coreVersion", runCatching { coreVersion() }.getOrDefault("mGBA"))
            .put("slot", slot)
            .put("createdAt", System.currentTimeMillis())
        fileOps.atomicWriteText(metadataFile(slot, romId), metadata.toString())
    }

    fun isCompatible(slot: Int, romId: String): Boolean {
        val file = metadataFile(slot, romId)
        if (!file.exists() || file.length() <= 0L) return true // legacy state
        return try {
            val metadata = JSONObject(file.readText())
            val storedRomId = metadata.optString("romId", "")
            val storedHash = metadata.optString("contentHash", "")
            val currentHash = prefs.getString("content_hash_$romId", "") ?: ""
            storedRomId == romId &&
                (storedHash.isBlank() || currentHash.isBlank() || storedHash.equals(currentHash, true))
        } catch (_: Exception) {
            false
        }
    }

    fun directory(romId: String, fallbackTitle: String = romId): File {
        val safeId = fileOps.sanitizeFileName(romId).ifBlank { fileOps.sanitizeFileName(fallbackTitle) }
        saveData.migrateLegacyStateData(romId)
        return File(fileOps.persistentCategoryDir("SaveStates"), safeId).apply { mkdirs() }
    }

    fun listJson(romId: String): String {
        if (romId.isBlank()) return "[]"
        val saves = mutableListOf<Pair<Long, JSONObject>>()
        (0..10).forEach { slot ->
            val state = stateFile(slot, romId)
            if (!state.exists() || state.length() <= 0L) return@forEach
            val modifiedAt = state.lastModified()
            val item = JSONObject()
                .put("slot", slot)
                .put("label", slotLabel(slot))
                .put("modifiedAt", modifiedAt)
                .put("size", state.length())
                .put("hasThumbnail", thumbnailFile(slot, romId).exists())
            saves += modifiedAt to item
        }
        saves.sortByDescending { it.first }
        return JSONArray().apply { saves.forEach { put(it.second) } }.toString()
    }


    fun autoFile(romId: String): File = File(directory(romId), "auto.ss")

    fun autoMetadataFile(romId: String): File = File(directory(romId), "auto.json")

    fun writeAutoMetadata(romId: String) {
        if (romId.isBlank()) return
        val metadata = JSONObject()
            .put("schemaVersion", 1)
            .put("romId", romId)
            .put("contentHash", prefs.getString("content_hash_$romId", "") ?: "")
            .put("platform", prefs.getString("system_$romId", "") ?: "")
            .put("coreVersion", runCatching { coreVersion() }.getOrDefault("mGBA"))
            .put("slot", "auto")
            .put("createdAt", System.currentTimeMillis())
        fileOps.atomicWriteText(autoMetadataFile(romId), metadata.toString())
    }

    fun isAutoCompatible(romId: String): Boolean {
        val file = autoMetadataFile(romId)
        if (!file.exists() || file.length() <= 0L) return true
        return try {
            val metadata = JSONObject(file.readText())
            val storedRomId = metadata.optString("romId", "")
            val storedHash = metadata.optString("contentHash", "")
            val currentHash = prefs.getString("content_hash_$romId", "") ?: ""
            storedRomId == romId &&
                (storedHash.isBlank() || currentHash.isBlank() || storedHash.equals(currentHash, true))
        } catch (_: Exception) {
            false
        }
    }

    fun hasResumeState(romId: String): Boolean {
        if (romId.isBlank()) return false
        val file = autoFile(romId)
        return file.exists() && file.length() > 0L && isAutoCompatible(romId)
    }

    fun delete(romId: String, slot: Int): Boolean {
        if (romId.isBlank() || slot !in 0..10) return false
        val state = stateFile(slot, romId)
        val thumbnail = thumbnailFile(slot, romId)
        val metadata = metadataFile(slot, romId)
        val existed = state.exists() || thumbnail.exists() || metadata.exists()
        if (!existed) return false

        val ok = (!state.exists() || state.delete()) &&
            (!thumbnail.exists() || thumbnail.delete()) &&
            (!metadata.exists() || metadata.delete())
        if (!ok) return false

        val directory = directory(romId)
        directory.listFiles()?.takeIf { it.isEmpty() }?.let { directory.delete() }
        val safeId = fileOps.sanitizeFileName(romId)
        val base = if (slot == 0) "quick" else "slot_${slot.toString().padStart(2, '0')}"
        onDeletedCloudPaths(
            listOf(
                "SaveStates/$safeId/$base.ss",
                "SaveStates/$safeId/$base.png",
                "SaveStates/$safeId/$base.json"
            )
        )
        return true
    }
}
