package com.retra.emulator

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Persistent import transaction journal.
 *
 * An import advances monotonically through:
 * PREPARING -> ROM_READY -> DATABASE_COMMITTED -> COMPLETE.
 * Each transition is synchronously fsynced before the importer continues, so
 * a process/device crash can be recovered deterministically on next launch.
 */
class ImportJournal(context: Context) {
    enum class State { PREPARING, ROM_READY, DATABASE_COMMITTED, COMPLETE }

    data class Entry(
        val transactionId: String,
        val state: State,
        val sourceUri: String,
        val displayName: String,
        val platform: String = "",
        val romId: String? = null,
        val contentHash: String? = null,
        val legacyIdentityHash: String? = null,
        val launchPath: String? = null,
        val patchPath: String? = null,
        val fileSize: Long = 0L,
        val lastModified: Long = 0L,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
    )

    private val directory = File(context.filesDir, "import_journal").apply { mkdirs() }

    @Synchronized
    fun begin(sourceUri: String, displayName: String): Entry {
        val entry = Entry(
            transactionId = UUID.randomUUID().toString(),
            state = State.PREPARING,
            sourceUri = sourceUri,
            displayName = displayName
        )
        write(entry)
        return entry
    }

    @Synchronized
    fun transition(
        previous: Entry,
        state: State,
        platform: String = previous.platform,
        romId: String? = previous.romId,
        contentHash: String? = previous.contentHash,
        legacyIdentityHash: String? = previous.legacyIdentityHash,
        launchPath: String? = previous.launchPath,
        patchPath: String? = previous.patchPath,
        fileSize: Long = previous.fileSize,
        lastModified: Long = previous.lastModified
    ): Entry {
        require(state.ordinal >= previous.state.ordinal) { "Import journal cannot move backwards" }
        val updated = previous.copy(
            state = state,
            platform = platform,
            romId = romId,
            contentHash = contentHash,
            legacyIdentityHash = legacyIdentityHash,
            launchPath = launchPath,
            patchPath = patchPath,
            fileSize = fileSize,
            lastModified = lastModified,
            updatedAt = System.currentTimeMillis()
        )
        write(updated)
        return updated
    }

    @Synchronized
    fun finish(entry: Entry) {
        val complete = transition(entry, State.COMPLETE)
        fileFor(complete.transactionId).delete()
    }

    @Synchronized
    fun find(transactionId: String): Entry? = fileFor(transactionId)
        .takeIf { it.exists() }
        ?.let { runCatching { parse(it.readText()) }.getOrNull() }

    @Synchronized
    fun pending(): List<Entry> = directory.listFiles()
        ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
        ?.mapNotNull { runCatching { parse(it.readText()) }.getOrNull() }
        ?.filter { it.state != State.COMPLETE }
        ?.sortedBy { it.createdAt }
        ?: emptyList()

    @Synchronized
    fun discard(entry: Entry) {
        fileFor(entry.transactionId).delete()
    }

    private fun fileFor(id: String): File = File(directory, "${id.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")

    private fun write(entry: Entry) {
        val target = fileFor(entry.transactionId)
        val tmp = File(directory, ".${target.name}.${System.nanoTime()}.tmp")
        val bytes = toJson(entry).toString(2).toByteArray(Charsets.UTF_8)
        FileOutputStream(tmp).use { output ->
            output.write(bytes)
            output.flush()
            runCatching { output.fd.sync() }
        }
        try {
            java.nio.file.Files.move(
                tmp.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            java.nio.file.Files.move(
                tmp.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun toJson(entry: Entry) = JSONObject().apply {
        put("transactionId", entry.transactionId)
        put("state", entry.state.name)
        put("sourceUri", entry.sourceUri)
        put("displayName", entry.displayName)
        put("platform", entry.platform)
        put("romId", entry.romId ?: JSONObject.NULL)
        put("contentHash", entry.contentHash ?: JSONObject.NULL)
        put("legacyIdentityHash", entry.legacyIdentityHash ?: JSONObject.NULL)
        put("launchPath", entry.launchPath ?: JSONObject.NULL)
        put("patchPath", entry.patchPath ?: JSONObject.NULL)
        put("fileSize", entry.fileSize)
        put("lastModified", entry.lastModified)
        put("createdAt", entry.createdAt)
        put("updatedAt", entry.updatedAt)
    }

    private fun parse(raw: String): Entry {
        val json = JSONObject(raw)
        fun nullable(key: String): String? = if (json.isNull(key)) null else json.optString(key, null)
        return Entry(
            transactionId = json.getString("transactionId"),
            state = State.valueOf(json.getString("state")),
            sourceUri = json.optString("sourceUri", ""),
            displayName = json.optString("displayName", "ROM"),
            platform = json.optString("platform", ""),
            romId = nullable("romId"),
            contentHash = nullable("contentHash"),
            legacyIdentityHash = nullable("legacyIdentityHash"),
            launchPath = nullable("launchPath"),
            patchPath = nullable("patchPath"),
            fileSize = json.optLong("fileSize", 0L),
            lastModified = json.optLong("lastModified", 0L),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
        )
    }
}
