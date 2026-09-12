package com.retra.emulator

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RomIdentityStoreInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: RomIdentityStore

    @Before
    fun setUp() {
        context.deleteDatabase("retra_rom_identity.db")
        store = RomIdentityStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase("retra_rom_identity.db")
    }

    @Test
    fun archivedRomReconnectsByHashAndKeepsPermanentId() {
        val hash = "a".repeat(64)
        val createdAt = 1_700_000_000_000L
        store.upsert(
            RomIdentityStore.Record(
                romId = "gba_permanent_test",
                contentHash = hash,
                platform = "GBA",
                displayName = "Test Game",
                fileName = "original.gba",
                sourceUri = "content://old/original.gba",
                launchPath = "/data/test/original.gba",
                patchPath = null,
                fileSize = 1024,
                archived = false,
                fileAvailable = true,
                createdAt = createdAt,
                updatedAt = createdAt
            )
        )

        store.setArchived("gba_permanent_test", true)
        val archived = store.findByHash("GBA", hash)
        assertNotNull(archived)
        assertEquals("gba_permanent_test", archived!!.romId)
        assertTrue(archived.archived)

        store.upsert(
            archived.copy(
                fileName = "renamed-game.gba",
                sourceUri = "content://new/renamed-game.gba",
                launchPath = "/data/test/renamed-game.gba",
                archived = false,
                fileAvailable = true,
                updatedAt = createdAt + 10_000
            )
        )

        val restored = store.findByHash("GBA", hash)
        assertNotNull(restored)
        assertEquals("gba_permanent_test", restored!!.romId)
        assertEquals(createdAt, restored.createdAt)
        assertEquals("renamed-game.gba", restored.fileName)
        assertFalse(restored.archived)
    }

    @Test
    fun exactHashCanReconnectEvenWhenExtensionPlatformLabelChanges() {
        val hash = "b".repeat(64)
        store.upsert(
            RomIdentityStore.Record(
                romId = "rom_hash_fallback",
                contentHash = hash,
                platform = "GBA",
                displayName = "Hash Test",
                fileName = "hash-test.gba",
                sourceUri = null,
                launchPath = "/data/test/hash-test.gba",
                patchPath = null,
                fileSize = 2048,
                archived = true,
                fileAvailable = false,
                createdAt = 100,
                updatedAt = 100
            )
        )

        val found = store.findByHash("ROM", hash.uppercase())
        assertNotNull(found)
        assertEquals("rom_hash_fallback", found!!.romId)
    }
    @Test
    fun legacySqliteV1MigratesToRoomV2WithoutLosingIdentity() {
        store.close()
        context.deleteDatabase("retra_rom_identity.db")

        val dbFile = context.getDatabasePath("retra_rom_identity.db")
        dbFile.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        legacy.execSQL(
            """
            CREATE TABLE rom_records (
                rom_id TEXT PRIMARY KEY NOT NULL,
                content_hash TEXT NOT NULL DEFAULT '',
                hash_algorithm TEXT NOT NULL DEFAULT 'SHA-256',
                platform TEXT NOT NULL,
                display_name TEXT NOT NULL,
                file_name TEXT NOT NULL,
                source_uri TEXT,
                launch_path TEXT,
                patch_path TEXT,
                file_size INTEGER NOT NULL DEFAULT 0,
                archived INTEGER NOT NULL DEFAULT 0,
                file_available INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        legacy.execSQL(
            "CREATE INDEX idx_rom_platform_hash ON rom_records(platform, content_hash) WHERE content_hash <> ''"
        )
        legacy.execSQL("CREATE INDEX idx_rom_archived ON rom_records(archived)")
        legacy.execSQL(
            "INSERT INTO rom_records " +
                "(rom_id, content_hash, hash_algorithm, platform, display_name, file_name, source_uri, " +
                "launch_path, patch_path, file_size, archived, file_available, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(
                "gba_legacy_keep", "c".repeat(64), "SHA-256", "GBA", "Legacy Game", "legacy.gba",
                "content://legacy/game", "/data/legacy/game.gba", null, 4096L, 1, 0, 100L, 200L
            )
        )
        legacy.version = 1
        legacy.close()

        store = RomIdentityStore(context)
        val migrated = store.getById("gba_legacy_keep")
        assertNotNull(migrated)
        assertEquals("c".repeat(64), migrated!!.contentHash)
        assertEquals("content://legacy/game", migrated.currentFileUri)
        assertEquals("/data/legacy/game.gba", migrated.launchPath)
        assertEquals(4096L, migrated.fileSize)
        assertEquals(0L, migrated.lastModified)
        assertTrue(migrated.archived)
        assertFalse(migrated.fileAvailable)
        assertFalse(migrated.favorite)
        assertEquals("[]", migrated.categoriesJson)
        assertEquals(0L, migrated.playtimeMs)
    }

}
