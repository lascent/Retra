package com.retra.emulator

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Room-backed persistent ROM identity/library index.
 *
 * The database keeps the original retra_rom_identity.db filename and rom_records
 * table so v4.24 SQLiteOpenHelper users are migrated in-place without data loss.
 * romId is immutable; content hashes reconnect replaceable ROM files to user data.
 */
class RomIdentityStore(context: Context) {
    data class Record(
        val romId: String,
        val contentHash: String,
        val hashAlgorithm: String = "SHA-256",
        val platform: String,
        val displayName: String,
        val fileName: String,
        val sourceUri: String?,
        val currentFileUri: String? = sourceUri,
        val launchPath: String?,
        val patchPath: String?,
        val fileSize: Long,
        val lastModified: Long = 0L,
        val archived: Boolean,
        val fileAvailable: Boolean,
        val favorite: Boolean = false,
        val categoriesJson: String = "[]",
        val playtimeMs: Long = 0L,
        val finalContentHash: String? = null,
        val legacyIdentityHash: String? = null,
        val createdAt: Long,
        val updatedAt: Long
    )

    private val dao = RetraDatabase.get(context.applicationContext).romDao()

    fun getById(romId: String): Record? {
        if (romId.isBlank()) return null
        return io { dao.getById(romId)?.toRecord() }
    }

    fun findByHash(platform: String, contentHash: String): Record? {
        if (contentHash.isBlank()) return null
        val normalizedHash = contentHash.lowercase()
        return io {
            val direct = dao.findByPlatformAndAnyHash(platform, normalizedHash)
                ?: dao.findByAnyHash(normalizedHash)
            if (direct != null) return@io direct.toRecord()
            val alias = dao.findAlias(normalizedHash) ?: return@io null
            dao.getById(alias.romId)?.toRecord()
        }
    }

    fun findByFingerprint(currentFileUri: String?, fileSize: Long, lastModified: Long): Record? {
        if (currentFileUri.isNullOrBlank() || fileSize <= 0L || lastModified <= 0L) return null
        return io { dao.findByFingerprint(currentFileUri, fileSize, lastModified)?.toRecord() }
    }

    fun all(): List<Record> = io { dao.all().map { it.toRecord() } }

    fun upsert(record: Record) {
        io {
            val existing = dao.getById(record.romId)
            dao.upsert(
                record.toEntity(
                    createdAt = existing?.createdAt ?: record.createdAt,
                    favorite = if (existing != null && !record.favorite) existing.favorite else record.favorite,
                    categoriesJson = if (existing != null && record.categoriesJson == "[]") existing.categoriesJson else record.categoriesJson,
                    playtimeMs = if (existing != null && record.playtimeMs == 0L) existing.playtimeMs else record.playtimeMs,
                    finalContentHash = record.finalContentHash ?: existing?.finalContentHash,
                    legacyIdentityHash = record.legacyIdentityHash ?: existing?.legacyIdentityHash
                )
            )
        }
    }

    fun setArchived(romId: String, archived: Boolean) {
        if (romId.isBlank()) return
        io { dao.setArchived(romId, archived, System.currentTimeMillis()) }
    }

    fun setFileAvailable(romId: String, available: Boolean) {
        if (romId.isBlank()) return
        io { dao.setFileAvailable(romId, available, System.currentTimeMillis()) }
    }

    fun updateLibraryMetadata(romId: String, favorite: Boolean, categoriesJson: String) {
        if (romId.isBlank()) return
        val safeCategories = categoriesJson.takeIf { it.isNotBlank() } ?: "[]"
        io { dao.updateLibraryMetadata(romId, favorite, safeCategories, System.currentTimeMillis()) }
    }

    fun updatePlaytime(romId: String, playtimeMs: Long) {
        if (romId.isBlank()) return
        io { dao.updatePlaytime(romId, playtimeMs.coerceAtLeast(0L), System.currentTimeMillis()) }
    }

    fun updatePatchedIdentity(romId: String, finalContentHash: String, legacyIdentityHash: String?) {
        if (romId.isBlank() || finalContentHash.isBlank()) return
        io {
            dao.updatePatchedIdentity(
                romId,
                finalContentHash.lowercase(),
                finalContentHash.lowercase(),
                legacyIdentityHash?.lowercase(),
                System.currentTimeMillis()
            )
        }
    }

    fun addIdentityAlias(romId: String, hash: String?, type: String) {
        if (romId.isBlank() || hash.isNullOrBlank()) return
        io {
            dao.upsertAlias(
                RomIdentityAliasEntity(
                    aliasHash = hash.lowercase(),
                    romId = romId,
                    aliasType = type,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun close() {
        RetraDatabase.closeForTests()
    }

    private fun <T> io(block: () -> T): T = runBlocking(Dispatchers.IO) { block() }

    private fun Record.toEntity(
        createdAt: Long,
        favorite: Boolean,
        categoriesJson: String,
        playtimeMs: Long,
        finalContentHash: String?,
        legacyIdentityHash: String?
    ) = RomRecordEntity(
        romId = romId,
        contentHash = contentHash.lowercase(),
        hashAlgorithm = hashAlgorithm,
        platform = platform,
        displayName = displayName,
        fileName = fileName,
        sourceUri = sourceUri,
        currentFileUri = currentFileUri ?: sourceUri,
        launchPath = launchPath,
        patchPath = patchPath,
        fileSize = fileSize,
        lastModified = lastModified,
        archived = archived,
        fileAvailable = fileAvailable,
        favorite = favorite,
        categoriesJson = categoriesJson,
        playtimeMs = playtimeMs,
        finalContentHash = finalContentHash,
        legacyIdentityHash = legacyIdentityHash,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun RomRecordEntity.toRecord() = Record(
        romId = romId,
        contentHash = contentHash,
        hashAlgorithm = hashAlgorithm,
        platform = platform,
        displayName = displayName,
        fileName = fileName,
        sourceUri = sourceUri,
        currentFileUri = currentFileUri,
        launchPath = launchPath,
        patchPath = patchPath,
        fileSize = fileSize,
        lastModified = lastModified,
        archived = archived,
        fileAvailable = fileAvailable,
        favorite = favorite,
        categoriesJson = categoriesJson,
        playtimeMs = playtimeMs,
        finalContentHash = finalContentHash,
        legacyIdentityHash = legacyIdentityHash,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

@Entity(
    tableName = "rom_records",
    indices = [
        Index(value = ["platform", "content_hash"], name = "idx_rom_platform_hash"),
        Index(value = ["archived"], name = "idx_rom_archived"),
        Index(value = ["current_file_uri", "file_size", "last_modified"], name = "idx_rom_fingerprint")
    ]
)
data class RomRecordEntity(
    @PrimaryKey @ColumnInfo(name = "rom_id") val romId: String,
    @ColumnInfo(name = "content_hash", defaultValue = "''") val contentHash: String,
    @ColumnInfo(name = "hash_algorithm", defaultValue = "'SHA-256'") val hashAlgorithm: String,
    @ColumnInfo(name = "platform") val platform: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "source_uri") val sourceUri: String?,
    @ColumnInfo(name = "current_file_uri") val currentFileUri: String?,
    @ColumnInfo(name = "launch_path") val launchPath: String?,
    @ColumnInfo(name = "patch_path") val patchPath: String?,
    @ColumnInfo(name = "file_size", defaultValue = "0") val fileSize: Long,
    @ColumnInfo(name = "last_modified", defaultValue = "0") val lastModified: Long,
    @ColumnInfo(name = "archived", defaultValue = "0") val archived: Boolean,
    @ColumnInfo(name = "file_available", defaultValue = "1") val fileAvailable: Boolean,
    @ColumnInfo(name = "favorite", defaultValue = "0") val favorite: Boolean,
    @ColumnInfo(name = "categories_json", defaultValue = "'[]'") val categoriesJson: String,
    @ColumnInfo(name = "playtime_ms", defaultValue = "0") val playtimeMs: Long,
    @ColumnInfo(name = "final_content_hash") val finalContentHash: String?,
    @ColumnInfo(name = "legacy_identity_hash") val legacyIdentityHash: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "rom_identity_aliases",
    indices = [Index(value = ["rom_id"], name = "idx_identity_alias_rom_id")]
)
data class RomIdentityAliasEntity(
    @PrimaryKey @ColumnInfo(name = "alias_hash") val aliasHash: String,
    @ColumnInfo(name = "rom_id") val romId: String,
    @ColumnInfo(name = "alias_type") val aliasType: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Dao
interface RomDao {
    @Query("SELECT * FROM rom_records WHERE rom_id = :romId LIMIT 1")
    fun getById(romId: String): RomRecordEntity?

    @Query(
        "SELECT * FROM rom_records WHERE platform = :platform AND " +
            "(content_hash = :hash OR final_content_hash = :hash OR legacy_identity_hash = :hash) " +
            "ORDER BY archived ASC, created_at ASC LIMIT 1"
    )
    fun findByPlatformAndAnyHash(platform: String, hash: String): RomRecordEntity?

    @Query(
        "SELECT * FROM rom_records WHERE content_hash = :hash OR final_content_hash = :hash " +
            "OR legacy_identity_hash = :hash ORDER BY archived ASC, created_at ASC LIMIT 1"
    )
    fun findByAnyHash(hash: String): RomRecordEntity?

    @Query(
        "SELECT * FROM rom_records WHERE current_file_uri = :uri AND file_size = :fileSize " +
            "AND last_modified = :lastModified ORDER BY updated_at DESC LIMIT 1"
    )
    fun findByFingerprint(uri: String, fileSize: Long, lastModified: Long): RomRecordEntity?

    @Query("SELECT * FROM rom_records ORDER BY created_at ASC")
    fun all(): List<RomRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(record: RomRecordEntity)

    @Query("UPDATE rom_records SET archived = :archived, updated_at = :updatedAt WHERE rom_id = :romId")
    fun setArchived(romId: String, archived: Boolean, updatedAt: Long)

    @Query("UPDATE rom_records SET file_available = :available, updated_at = :updatedAt WHERE rom_id = :romId")
    fun setFileAvailable(romId: String, available: Boolean, updatedAt: Long)

    @Query(
        "UPDATE rom_records SET favorite = :favorite, categories_json = :categoriesJson, " +
            "updated_at = :updatedAt WHERE rom_id = :romId"
    )
    fun updateLibraryMetadata(romId: String, favorite: Boolean, categoriesJson: String, updatedAt: Long)

    @Query("UPDATE rom_records SET playtime_ms = :playtimeMs, updated_at = :updatedAt WHERE rom_id = :romId")
    fun updatePlaytime(romId: String, playtimeMs: Long, updatedAt: Long)

    @Query(
        "UPDATE rom_records SET content_hash = :contentHash, final_content_hash = :finalHash, " +
            "legacy_identity_hash = :legacyHash, updated_at = :updatedAt WHERE rom_id = :romId"
    )
    fun updatePatchedIdentity(
        romId: String,
        contentHash: String,
        finalHash: String,
        legacyHash: String?,
        updatedAt: Long
    )

    @Query("SELECT * FROM rom_identity_aliases WHERE alias_hash = :hash LIMIT 1")
    fun findAlias(hash: String): RomIdentityAliasEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAlias(alias: RomIdentityAliasEntity)
}


@Database(entities = [RomRecordEntity::class, RomIdentityAliasEntity::class], version = 2, exportSchema = true)
abstract class RetraDatabase : RoomDatabase() {
    abstract fun romDao(): RomDao

    companion object {
        private const val DATABASE_NAME = "retra_rom_identity.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rom_records ADD COLUMN current_file_uri TEXT")
                db.execSQL("ALTER TABLE rom_records ADD COLUMN last_modified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rom_records ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rom_records ADD COLUMN categories_json TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE rom_records ADD COLUMN playtime_ms INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rom_records ADD COLUMN final_content_hash TEXT")
                db.execSQL("ALTER TABLE rom_records ADD COLUMN legacy_identity_hash TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS rom_identity_aliases (" +
                        "alias_hash TEXT NOT NULL PRIMARY KEY, rom_id TEXT NOT NULL, " +
                        "alias_type TEXT NOT NULL, created_at INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_identity_alias_rom_id ON rom_identity_aliases(rom_id)")
                db.execSQL("UPDATE rom_records SET current_file_uri = source_uri WHERE current_file_uri IS NULL")
                db.execSQL("DROP INDEX IF EXISTS idx_rom_platform_hash")
                db.execSQL("DROP INDEX IF EXISTS idx_rom_archived")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_rom_platform_hash ON rom_records(platform, content_hash)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_rom_archived ON rom_records(archived)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_rom_fingerprint " +
                        "ON rom_records(current_file_uri, file_size, last_modified)"
                )
            }
        }

        @Volatile private var INSTANCE: RetraDatabase? = null

        fun get(context: Context): RetraDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context, RetraDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { INSTANCE = it }
        }

        fun closeForTests() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
