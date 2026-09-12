package com.retra.emulator

import android.accounts.AccountManager
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.retra.emulator.databinding.ActivityMainBinding
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {

    internal lateinit var binding: ActivityMainBinding
    internal lateinit var artworkRepository: ArtworkRepository
    internal lateinit var displayPerformanceManager: DisplayPerformanceManager
    internal lateinit var gameplayFramePresenter: GameplayFramePresenter
    internal lateinit var webUiInsetsManager: WebUiInsetsManager
    internal lateinit var webUiController: WebUiController
    internal lateinit var remoteTransport: RemoteLinkTransport
    internal val multiplayerCompatibility by lazy { GbaMultiplayerCompatibility() }
    internal val fileOps by lazy { RetraFileOps(this) }

    companion object {
        internal const val FRAME_TIME_NS = 16_742_706L
        internal const val PLATFORM_GBA = 0
        internal const val PLATFORM_GB = 1

        internal const val MAX_ZIP_ENTRIES = 256
        internal const val MAX_ROM_BYTES = 128L * 1024L * 1024L
        internal const val MAX_PATCH_BYTES = 32L * 1024L * 1024L

        // Remote Link v2: replicated mGBA pair + adaptive timing and recovery.
        internal const val REMOTE_LINK_PORT = 5738
        internal const val REMOTE_LINK_PROTOCOL = "RETRA_REMOTE_LINK_V2"
        internal const val REMOTE_MAX_SAVE_BYTES = 1024 * 1024
        internal const val REMOTE_HOST_PREF = "remote_link_host_v1"
        internal const val BLUETOOTH_CONNECT_REQUEST = 7301
        internal val REMOTE_BLUETOOTH_UUID: UUID = UUID.fromString("9d6f0c5b-7f6a-4f28-9f62-524554524131")

        internal const val CONTROLLER_LAYOUT_PREF = "controller_layout_v390"
        internal const val SCREEN_LAYOUT_PREF = "screen_layout_v388"
        internal const val ORIENTATION_PREF = "screen_orientation"
        internal const val BUTTON_OPACITY_PREF = "buttons_opacity"
        internal const val FAST_FORWARD_SPEED_PREF = "fast_forward_speed_v1" // legacy migration source
        internal const val EMULATION_SPEED_PREF = "emulation_speed_v2"
        internal const val LANDSCAPE_100_MIGRATION_PREF = "controller_layout_landscape_100_v394"
        internal const val CLOUD_SYNC_ENABLED_PREF = "cloud_sync_enabled_v1"
        internal const val CLOUD_SYNC_URI_PREF = "cloud_sync_uri_v1"
        internal const val CLOUD_SYNC_ACCOUNT_PREF = "cloud_sync_account_v1"
        internal const val CLOUD_SYNC_MODE_PREF = "cloud_sync_mode_v2"
        internal const val APP_FOLDER_URI_PREF = "app_folder_uri_v1"
        internal const val ROM_IDENTITY_MIGRATION_PREF = "rom_identity_migration_v2"
        internal const val ROM_IDENTITY_SCHEMA_VERSION = 2
        internal const val AUTO_SAVE_LOAD_PREF = "auto_save_load_v1"
        internal const val ROM_PATCHING_PREF = "rom_patching_v1"
        internal const val ENABLE_CHEATS_PREF = "enable_cheats_v1"
        internal const val CONFIRM_CLOSE_RESET_PREF = "confirm_close_reset_v1"
        internal const val FULLSCREEN_PREF = "fullscreen_mode_v1"
        internal const val IMMERSIVE_PREF = "immersive_mode_v1"
        internal const val STRETCH_TO_FIT_PREF = "stretch_to_fit_v1"
        internal const val HARDWARE_RENDERING_PREF = "hardware_rendering_v1"
        internal const val LINEAR_FILTERING_PREF = "linear_filtering_v1"
        internal const val ENABLE_SOUND_PREF = "enable_sound_v1"
        internal const val FRAME_SKIP_PREF = "frame_skip_v1"
        internal const val VOLUME_PREF = "volume_v1"
        internal const val SOUND_FREQUENCY_PREF = "sound_frequency_v1"
        internal const val CPU_CORE_PREF = "cpu_core_v1"
        internal const val CARTRIDGE_SAVE_TYPE_PREF = "cartridge_save_type_v1"
        internal const val USE_BIOS_PREF = "use_bios_v1"
        internal const val BOOT_BIOS_PREF = "boot_bios_v1"
        internal const val SMC_CHECK_PREF = "smc_check_v1"
        internal const val SPEED_OPTIMIZATION_PREF = "speed_optimization_v1"
        internal const val MOSAIC_EFFECT_PREF = "mosaic_effect_v1"
        internal const val FAST_FORWARD_BUTTON_MODE_PREF = "fast_forward_button_mode_v1"
        internal const val BIOS_GBA_PATH_PREF = "bios_gba_path_v1"
        internal const val BIOS_GB_PATH_PREF = "bios_gb_path_v1"
        internal const val BIOS_GBC_PATH_PREF = "bios_gbc_path_v1"
        internal const val BIOS_LAST_LABEL_PREF = "bios_last_label_v1"
        internal const val AUTO_ARTWORK_PREF = "automatic_artwork_v1"
        internal const val ARTWORK_WIFI_ONLY_PREF = "artwork_wifi_only_v1"
        internal const val COLOR_STYLE_PREF = ColorStyleController.PREF_KEY

        internal val PLAYABLE_EXTENSIONS = setOf("gba", "gbc", "gb", "mgba")
        internal val PATCH_EXTENSIONS = setOf("ips", "ups", "bps")
        internal val IMPORT_EXTENSIONS = PLAYABLE_EXTENSIONS + PATCH_EXTENSIONS + "zip"

        const val KEY_A = 0
        const val KEY_B = 1
        const val KEY_SELECT = 2
        const val KEY_START = 3
        const val KEY_RIGHT = 4
        const val KEY_LEFT = 5
        const val KEY_UP = 6
        const val KEY_DOWN = 7
        const val KEY_R = 8
        const val KEY_L = 9

        init {
            System.loadLibrary("emulator")
        }
    }

    internal data class NativeLibraryItem(
        val id: String,
        val title: String,
        val fileName: String,
        val size: Long,
        val lastModified: Long,
        val system: String,
        val sourceExtension: String,
        val launchPath: String?,
        val patchPath: String?,
        val contentHash: String,
        val sourceUri: String?,
        val legacyIdentityHash: String? = null,
        val importTransactionId: String? = null,
        val reusedExisting: Boolean = false,
        val restoredArchived: Boolean = false
    )

    internal data class PendingPatchLaunch(
        val id: String,
        val title: String,
        val patchFile: File
    )

    internal data class PendingLocate(
        val id: String,
        val title: String,
        val fileName: String,
        val system: String
    )

    internal var videoWidth = 240
    internal var videoHeight = 160
    internal var framePixels = IntArray(videoWidth * videoHeight)
    internal var displayPixels = IntArray(videoWidth * videoHeight)
    internal var bitmap: Bitmap? = null

    internal val frameLock = Any()

    @Volatile
    internal var emulatorRunning = false

    internal var emulatorThread: Thread? = null
    internal var romLoaded = false
    internal var currentRomId = "rom"
    internal var currentRomTitle = "ROM"
    internal var currentRomExtension = "gba"
    internal var gameplayMenuDialog: Dialog? = null
    internal var gameplayMenuSubscreen = false
    internal var gameplayMenuBackHandler: (() -> Unit)? = null
    internal var gameplayModalPauseActive = false
    internal var inGameSettingsActive = false
    internal var currentPlatform = PLATFORM_GBA
    internal var preferredOrientationValue = "Auto rotate"
    internal var preferredButtonsOpacity = 1f
    internal val activeDpadKeys = mutableSetOf<Int>()
    internal var screenEditorPresentationActive = false

    // Controller coordinates are persisted independently for portrait and landscape.
    // A generation counter debounces transient layout passes during rotation so an
    // old-orientation measurement can never overwrite/reposition the new layout.
    internal var nativeLayoutGeneration = 0

    @Volatile
    internal var activeEmulationSpeed = 1.0

    @Volatile
    internal var preferredEmulationSpeed = 4.0

    internal var pendingPatchLaunch: PendingPatchLaunch? = null
    internal var pendingLocate: PendingLocate? = null
    internal var pendingStateLoadRomId: String? = null
    internal var pendingStateLoadSlot: Int? = null
    // Set only when the Library's explicit Resume action is used. This lets
    // Resume restore the last close/back state even if automatic loading is
    // disabled, while a first-time Play still starts the ROM normally.
    internal var pendingForceAutoResumeRomId: String? = null
    internal var pendingLocalLinkPicker = false

    // Local Link keeps two synchronized native mGBA cores alive. Retra shows
    // and controls one player at a time, like My Boy!'s same-device link flow.
    internal var currentRomPath: String? = null
    internal var currentPatchPath: String? = null
    internal var localLinkActive = false
    internal var localLinkPlayer = 0
    internal var localLinkPlayer1Title = "Player 1"
    internal var localLinkPlayer2Title = "Player 2"
    internal var activeLinkFirstRomId: String? = null
    internal var activeLinkSecondRomId: String? = null
    internal var activeLinkSecondSavePlayer = 0

    // Remote Link network/timing state is owned by RemoteLinkTransport; this
    // Activity keeps only connection-attempt UI and temporary save-pair state.
    internal var remoteAttemptCloser: (() -> Unit)? = null
    internal var remoteProgressDialog: AlertDialog? = null
    internal var remoteLocalSaveBefore: ByteArray? = null
    internal var remoteSameRomSession = true
    internal var remoteMirrorSaveFile: File? = null
    internal var remoteMirrorSaveBackup: ByteArray? = null
    internal var remoteMirrorSaveExisted = false
    internal val taskExecutors = RetraTaskExecutors()
    internal val remoteExecutor = taskExecutors.network

    internal var pendingCloudEnable = false
    internal var pendingCloudAccount: String? = null
    internal var pendingDriveAuthAccount: String? = null
    internal var pendingAppFolderSelection = false

    internal val ioExecutor = taskExecutors.serialIo

    internal val prefs: RetraPreferences by lazy {
        RetraPreferences(this)
    }

    internal val audioController: AudioController by lazy {
        AudioController(
            isEnabled = { prefs.getBoolean(ENABLE_SOUND_PREF, true) },
            sampleRate = { prefs.getInt(SOUND_FREQUENCY_PREF, 44100) },
            volume = { prefs.getInt(VOLUME_PREF, 100).coerceIn(0, 100) / 100f },
            readSamples = { buffer -> readAudioSamples(buffer) }
        )
    }

    internal val gameplayLayouts by lazy {
        GameplayLayoutRepository(prefs, fileOps, CONTROLLER_LAYOUT_PREF, SCREEN_LAYOUT_PREF)
    }

    internal val saveData: SaveDataRepository by lazy {
        SaveDataRepository(this, prefs, fileOps)
    }

    internal val saveStates: SaveStateRepository by lazy {
        SaveStateRepository(
            prefs = prefs,
            fileOps = fileOps,
            saveData = saveData,
            coreVersion = { stringFromJNI() },
            onDeletedCloudPaths = { paths -> deleteCloudPathsAsync(paths) }
        )
    }

    internal val cheatRepository by lazy {
        CheatRepository(prefs, fileOps)
    }

    internal val romAssets by lazy {
        RomAssetRepository(fileOps, artworkRepository)
    }

    internal val importJournal by lazy {
        ImportJournal(this)
    }

    internal val romIdentityStore by lazy {
        RomIdentityStore(this)
    }

    internal val statistics by lazy {
        StatisticsRepository(prefs, romIdentityStore, fileOps)
    }

    internal val shaderRepository by lazy {
        ShaderRepository(this, prefs)
    }

    internal val shaderController by lazy {
        ShaderController(
            activity = this,
            repository = shaderRepository,
            normalView = binding.gameScreen,
            shaderView = binding.shaderGameScreen,
            onSettingsChanged = { notifyWebSettingsState() },
            latestFrame = {
                synchronized(frameLock) {
                    ShaderController.FrameSnapshot(displayPixels.copyOf(), videoWidth, videoHeight)
                }
            }
        )
    }

    internal val colorStyleController by lazy {
        ColorStyleController(
            prefs = prefs,
            normalView = binding.gameScreen,
            shaderView = binding.shaderGameScreen
        )
    }

    internal val saveTransfer: SaveTransferRepository by lazy {
        SaveTransferRepository(
            context = this,
            prefs = prefs,
            fileOps = fileOps,
            romIdentityStore = romIdentityStore,
            saveData = saveData,
            saveStates = saveStates,
            preparePortableMetadata = { writePortableMetadataFiles() }
        )
    }

    internal val driveApi: GoogleDriveApiRepository by lazy {
        GoogleDriveApiRepository(
            filesDir = filesDir,
            fileOps = fileOps,
            preparePortableMetadata = { writePortableMetadataFiles() }
        )
    }

    internal val cloudSync: CloudSyncCoordinator by lazy {
        CloudSyncCoordinator(
            activity = this,
            prefs = prefs,
            saveTransfer = saveTransfer,
            driveApi = driveApi,
            ioExecutor = ioExecutor,
            cloudRootUri = { cloudRootUri() },
            onSettingsChanged = { notifyWebSettingsState() },
            onFolderFallbackRequested = { runOnUiThread { requestCloudSyncFolder() } },
            onAuthRecoveryRequired = { account, intent ->
                pendingDriveAuthAccount = account
                driveAuthRecoveryLauncher.launch(intent)
            }
        )
    }

    internal val importPicker =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNullOrEmpty()) return@registerForActivityResult

            ioExecutor.execute {
                var imported = 0
                var failed = 0

                uris.forEach { uri ->
                    try {
                        val item = importUri(uri)
                        persistNativeItem(item)
                        runOnUiThread { notifyWebImported(item) }
                        imported++
                    } catch (e: Exception) {
                        failed++
                        runOnUiThread {
                            RetraNotice.makeText(
                                this,
                                "Could not import ${fileOps.queryDisplayName(uri) ?: "file"}: ${e.message ?: "unsupported file"}",
                                RetraNotice.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                // Successful imports are intentionally silent. The Library updates immediately.
                // Individual import failures above still surface a Retra-styled error notice.
            }
        }

    internal val locatePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val pending = pendingLocate
            pendingLocate = null
            if (uri == null || pending == null) {
                if (uri == null) {
                    pendingStateLoadRomId = null
                    pendingStateLoadSlot = null
                }
                return@registerForActivityResult
            }

            ioExecutor.execute {
                try {
                    val item = importUri(uri, forcedId = pending.id, forcedTitle = pending.title)
                    persistNativeItem(item)
                    runOnUiThread {
                        notifyWebImported(item, replaceExisting = true)
                        launchNativeItem(item.id, item.title, item.fileName, item.system)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        RetraNotice.makeText(
                            this,
                            "Could not use this file: ${e.message ?: "unsupported file"}",
                            RetraNotice.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

    internal val patchBasePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val pending = pendingPatchLaunch
            pendingPatchLaunch = null
            if (uri == null || pending == null) {
                if (uri == null) {
                    pendingStateLoadRomId = null
                    pendingStateLoadSlot = null
                }
                return@registerForActivityResult
            }

            ioExecutor.execute {
                try {
                    val baseFile = preparePatchBaseRom(uri, pending.id)
                    prefs.edit().putString(basePathKey(pending.id), baseFile.absolutePath).apply()
                    reconcilePatchedIdentity(pending.id, baseFile, pending.patchFile)
                    runOnUiThread {
                        loadRomFile(baseFile, pending.title, pending.patchFile, pending.id)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        RetraNotice.makeText(
                            this,
                            "Base ROM error: ${e.message ?: "unsupported ROM"}",
                            RetraNotice.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

    internal val localLinkGamePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (!pendingLocalLinkPicker) return@registerForActivityResult
            pendingLocalLinkPicker = false
            if (uri == null) return@registerForActivityResult

            val displayName = fileOps.queryDisplayName(uri) ?: "Another game"
            val extension = displayName.substringAfterLast('.', "").lowercase(Locale.US)
            if (extension !in PLAYABLE_EXTENSIONS && extension != "zip") {
                RetraNotice.makeText(
                    this,
                    "Choose a Game Boy Advance ROM",
                    RetraNotice.LENGTH_SHORT
                ).show()
                return@registerForActivityResult
            }

            ioExecutor.execute {
                try {
                    val item = importUri(uri)
                    val secondPath = item.launchPath
                        ?: throw IllegalStateException("This file is not a playable ROM")
                    persistNativeItem(item)
                    runOnUiThread {
                        notifyWebImported(item)
                        startLocalLinkSession(secondPath, item.title, item.id)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        RetraNotice.makeText(
                            this,
                            "Could not use this ROM for Local Link: ${e.message ?: "unsupported ROM"}",
                            RetraNotice.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

    internal val webFilePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (::webUiController.isInitialized) {
                webUiController.deliverFileChooserResult(result.resultCode, result.data)
            }
        }

    internal val biosFilePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val displayName = fileOps.queryDisplayName(uri) ?: "BIOS file"
            val temp = File(cacheDir, "bios-import-${System.nanoTime()}.bin")
            try {
                copyUriToFile(uri, temp, 2L * 1024L * 1024L)
                val size = temp.length()
                val lower = displayName.lowercase(Locale.US)
                val kind = when {
                    size == 16_384L || lower.contains("gba") -> "gba"
                    size == 2_304L || lower.contains("gbc") || lower.contains("cgb") -> "gbc"
                    size == 256L || lower.contains("dmg") || lower.contains("gb_bios") || lower.contains("gb boot") -> "gb"
                    else -> null
                }
                if (kind == null) {
                    RetraNotice.makeText(this, "Unsupported BIOS file. Choose a GBA, GB, or GBC BIOS.", RetraNotice.LENGTH_LONG).show()
                    return@registerForActivityResult
                }
                val dir = File(filesDir, "bios").apply { mkdirs() }
                val dest = File(dir, "${kind}_bios.bin")
                temp.copyTo(dest, overwrite = true)
                val key = when (kind) {
                    "gba" -> BIOS_GBA_PATH_PREF
                    "gbc" -> BIOS_GBC_PATH_PREF
                    else -> BIOS_GB_PATH_PREF
                }
                val label = "${kind.uppercase(Locale.US)} • $displayName"
                prefs.edit().putString(key, dest.absolutePath).putString(BIOS_LAST_LABEL_PREF, label).apply()
                applyRuntimeSettingsToNative()
                notifyWebSettingsState()
                RetraNotice.makeText(this, "${kind.uppercase(Locale.US)} BIOS selected", RetraNotice.LENGTH_SHORT).show()
            } catch (e: Exception) {
                RetraNotice.makeText(this, "Could not import BIOS: ${e.message ?: "invalid file"}", RetraNotice.LENGTH_LONG).show()
            } finally {
                temp.delete()
            }
        }

    internal val cloudAccountPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) {
                if (pendingCloudEnable && cloudRootUri() == null) {
                    prefs.edit().putBoolean(CLOUD_SYNC_ENABLED_PREF, false).apply()
                }
                pendingCloudEnable = false
                pendingCloudAccount = null
                notifyWebSettingsState()
                return@registerForActivityResult
            }

            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME).orEmpty()
            val accountType = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE).orEmpty()
            if (accountName.isBlank() || accountType != "com.google") {
                if (pendingCloudEnable && cloudRootUri() == null) {
                    prefs.edit().putBoolean(CLOUD_SYNC_ENABLED_PREF, false).apply()
                }
                pendingCloudEnable = false
                pendingCloudAccount = null
                notifyWebSettingsState()
                RetraNotice.makeText(this, "Choose a Google account to use Drive sync", RetraNotice.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            pendingCloudAccount = accountName
            RetraNotice.makeText(this, "Selected $accountName • connecting Google Drive API", RetraNotice.LENGTH_SHORT).show()
            cloudSync.connectAccount(accountName, interactive = true, fallbackToFolder = true)
            notifyWebSettingsState()
        }

    internal val cloudFolderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) {
                if (pendingCloudEnable && cloudRootUri() == null) {
                    prefs.edit().putBoolean(CLOUD_SYNC_ENABLED_PREF, false).apply()
                }
                pendingCloudEnable = false
                pendingCloudAccount = null
                notifyWebSettingsState()
                return@registerForActivityResult
            }
            persistTreePermission(uri)
            val account = pendingCloudAccount ?: prefs.getString(CLOUD_SYNC_ACCOUNT_PREF, null)
            cloudSync.markSafConnected(uri, account)
            pendingCloudEnable = false
            pendingCloudAccount = null
            syncCloudAsync(showResult = true)
        }

    internal val driveAuthRecoveryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val account = pendingDriveAuthAccount
            pendingDriveAuthAccount = null
            if (result.resultCode == RESULT_OK && !account.isNullOrBlank()) {
                cloudSync.connectAccount(account, interactive = false, fallbackToFolder = true)
            } else if (!account.isNullOrBlank()) {
                pendingCloudAccount = account
                RetraNotice.makeText(this, "Drive API authorization was not granted • choose a Drive folder instead", RetraNotice.LENGTH_LONG).show()
                requestCloudSyncFolder()
            }
        }

    internal val importSavesFolderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@registerForActivityResult
            persistTreePermission(uri)
            ioExecutor.execute {
                val imported = saveTransfer.importSaves(uri)
                runOnUiThread {
                    RetraNotice.makeText(
                        this,
                        if (imported > 0) "$imported save file${if (imported == 1) "" else "s"} imported" else "No compatible saves found",
                        RetraNotice.LENGTH_LONG
                    ).show()
                    syncCloudAsync(showResult = false)
                }
            }
        }

    internal val appFolderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            pendingAppFolderSelection = false
            if (uri == null) return@registerForActivityResult
            persistTreePermission(uri)
            prefs.edit().putString(APP_FOLDER_URI_PREF, uri.toString()).apply()
            exportSaveDataToTreeAsync(uri, showResult = true)
        }

    internal val shaderFilePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            ioExecutor.execute { shaderController.installAndSelect(uri) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        remoteTransport = RemoteLinkTransport(
            activity = this,
            hooks = RemoteLinkTransport.NativeHooks(
                getFrame = { getLocalLinkFrame() },
                getFrameSkew = { getLocalLinkFrameSkew() },
                getLatestCheckpoint = { getLatestLocalLinkCheckpoint() },
                getCheckpointHash = { frame -> getLocalLinkCheckpointHash(frame) },
                exportSnapshot = { exportLocalLinkSnapshot() },
                importSnapshot = { payload -> importLocalLinkSnapshot(payload) },
                setPaused = { paused -> setLocalLinkPaused(paused) },
                setKeyMask = { player, mask -> setLocalLinkKeyMask(player, mask) },
                scheduleKeyMask = { player, frame, mask -> scheduleLocalLinkKeyMask(player, frame, mask) },
                clearInputSchedule = { clearLocalLinkInputSchedule() },
                onDisconnect = { message -> disconnectRemoteLinkAndRestore(message) },
                onStopEmulation = { if (emulatorRunning) stopEmulation() },
                onResumeEmulation = {
                    if (romLoaded && binding.emulatorOverlay.visibility == View.VISIBLE && !emulatorRunning) {
                        startEmulation()
                    }
                }
            )
        )

        displayPerformanceManager = DisplayPerformanceManager(this)
        displayPerformanceManager.applyPreferredMode()
        gameplayFramePresenter = GameplayFramePresenter(binding.gameScreen) {
            presentLatestGameplayFrame()
        }

        webUiInsetsManager = WebUiInsetsManager(binding.root, binding.webView)
        webUiInsetsManager.install()

        artworkRepository = ArtworkRepository(
            context = this,
            romStore = romIdentityStore,
            isGameplayActive = { romLoaded || emulatorRunning },
            isEnabled = { prefs.getBoolean(AUTO_ARTWORK_PREF, true) },
            wifiOnly = { prefs.getBoolean(ARTWORK_WIFI_ONLY_PREF, false) },
            onArtworkChanged = { romId, state ->
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && ::binding.isInitialized) {
                        binding.webView.evaluateJavascript(
                            "window.retraNativeArtworkChanged && window.retraNativeArtworkChanged(${jsQuote(romId)}, ${jsQuote(state)})",
                            null
                        )
                    }
                }
            }
        )

        migrateLandscapeControllerScaleTo100()

        // Queue the non-destructive v4.23 -> persistent identity migration before
        // any import or cloud operation. ioExecutor is single-threaded, so later
        // jobs cannot race the migration.
        ioExecutor.execute {
            recoverInterruptedImports()
            migrateLegacyRomIdentityIfNeeded()
            gameplayLayouts.migrateLegacyGlobalLayouts()
            statistics.migrateLegacyToRoom()
            writePortableMetadataFiles()
        }

        preferredButtonsOpacity = prefs.getInt(BUTTON_OPACITY_PREF, 70)
            .coerceIn(25, 100) / 100f
        preferredEmulationSpeed = EmulationSpeedPolicy.loadAndMigrate(prefs, EMULATION_SPEED_PREF, FAST_FORWARD_SPEED_PREF)
        preferredOrientationValue = prefs.getString(ORIENTATION_PREF, "Auto rotate") ?: "Auto rotate"
        applyPreferredOrientation(preferredOrientationValue)

        webUiController = WebUiController(
            activity = this,
            webView = binding.webView,
            fileOps = fileOps,
            javascriptBridge = RetraBridge(),
            onPageReady = { webUiInsetsManager.onPageReady() },
            launchFileChooser = { intent -> webFilePicker.launch(intent) },
            onRendererGone = { recreate() }
        ).also { it.configure() }

        bindControls()
        bindEmulatorChrome()
        applyNativeButtonsOpacity()
        configureNativeLayoutRotationHandling()
        configureBackHandling()
        applyRuntimeSettingsToNative()
        shaderController.applySelection(showToast = false)
        if (prefs.getBoolean(CLOUD_SYNC_ENABLED_PREF, false)) {
            syncCloudAsync(showResult = false)
        }
    }

    // Keep the WebView attached and laid out while native gameplay is visible.
    // View.GONE forces a full layout/raster catch-up when returning to Home and
    // can expose the dark root background for a frame on slower devices.
    internal var webUiSwitchGeneration = 0L

    // Extension-based controllers cannot inspect lateinit backing fields directly.
    // Keep these tiny lifecycle guards on the Activity host instead of duplicating state.
    internal fun hasBinding(): Boolean = ::binding.isInitialized
    internal fun hasDisplayPerformanceManager(): Boolean = ::displayPerformanceManager.isInitialized
    internal fun hasGameplayFramePresenter(): Boolean = ::gameplayFramePresenter.isInitialized

    internal fun showWebUiWithoutBlankFrame() {
        if (!::binding.isInitialized) return
        if (::displayPerformanceManager.isInitialized) displayPerformanceManager.applyPreferredMode()
        val generation = ++webUiSwitchGeneration
        binding.webView.onResume()
        binding.webView.visibility = View.VISIBLE
        webUiInsetsManager.refresh()
        binding.webView.postOnAnimation {
            if (!::binding.isInitialized || generation != webUiSwitchGeneration) return@postOnAnimation
            binding.webView.invalidate()
            binding.emulatorOverlay.visibility = View.GONE
        }
    }

    internal fun showEmulatorUiKeepingWebWarm() {
        if (!::binding.isInitialized) return
        if (::displayPerformanceManager.isInitialized) displayPerformanceManager.applyGameplayMode()
        webUiSwitchGeneration++
        binding.emulatorOverlay.visibility = View.VISIBLE
        binding.webView.visibility = View.INVISIBLE
        // Keep the page attached so returning Home is instant, but suspend its
        // renderer/timers while mGBA is active so WebView cannot compete with
        // the emulator thread and game-screen compositor for CPU/GPU time.
        binding.webView.onPause()
    }

    internal fun jsQuote(value: String): String = JSONObject.quote(value)

    override fun onPause() {
        if (::binding.isInitialized) shaderController.onPause()
        if (remoteTransport.isActive && !isChangingConfigurations) {
            // RemoteLinkTransport owns the pause packet and paired-core suspension.
            remoteTransport.pauseForLifecycle()
        }
        if (binding.emulatorOverlay.visibility == View.VISIBLE) {
            if (romLoaded && gameplayMenuDialog?.isShowing != true && !inGameSettingsActive && !remoteTransport.isActive) {
                saveAutoStateIfEnabled()
            }
            stopEmulation()
            if (romLoaded) commitActiveWorkingSaves()
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) shaderController.onResume()
        if (::displayPerformanceManager.isInitialized) displayPerformanceManager.applyPreferredMode()
        if (!romLoaded) artworkRepository.resumeDeferred()
        if (remoteTransport.resumeFromLifecycle()) {
            // The peer receiving RESUME initiates a host-authoritative resync.
            // Keep the local pair paused until RESYNC_RESUME arrives.
            return
        }
        if (romLoaded &&
            binding.emulatorOverlay.visibility == View.VISIBLE &&
            gameplayMenuDialog?.isShowing != true &&
            !gameplayModalPauseActive &&
            !inGameSettingsActive &&
            remoteTransport.canRunEmulation
        ) {
            startEmulation()
        }
    }

    override fun onDestroy() {
        try { remoteAttemptCloser?.invoke() } catch (_: Exception) {}
        if (::remoteTransport.isInitialized) remoteTransport.close(sendDisconnect = false)
        gameplayMenuDialog?.setOnDismissListener(null)
        gameplayMenuDialog?.dismiss()
        gameplayMenuDialog = null
        stopEmulation()
        audioController.release()
        if (romLoaded) {
            shutdownCore()
            commitActiveWorkingSaves()
            clearActiveLinkSaveTracking()
            romLoaded = false
        }

        artworkRepository.close()
        taskExecutors.close()

        if (::displayPerformanceManager.isInitialized) displayPerformanceManager.restoreSystemDefault()
        if (::webUiController.isInitialized) webUiController.destroy()

        bitmap?.recycle()
        bitmap = null

        super.onDestroy()
    }

    inner class RetraBridge {
        @JavascriptInterface
        fun launchRom(id: String, title: String, fileName: String, system: String) {
            pendingStateLoadRomId = null
            pendingStateLoadSlot = null
            pendingForceAutoResumeRomId = null
            runOnUiThread {
                launchNativeItem(
                    id.ifBlank { fileOps.sanitizeFileName(title) },
                    title.ifBlank { "ROM" },
                    fileName.ifBlank { "$title.gba" },
                    system.ifBlank { "ROM" }
                )
            }
        }

        @JavascriptInterface
        fun resumeRom(id: String, title: String, fileName: String, system: String) {
            val resolvedId = id.ifBlank { fileOps.sanitizeFileName(title) }
            if (!saveStates.hasResumeState(resolvedId)) {
                // If the resume state disappeared, fall back to a normal launch
                // instead of failing or showing a broken Resume action.
                launchRom(resolvedId, title, fileName, system)
                return
            }
            pendingStateLoadRomId = null
            pendingStateLoadSlot = null
            pendingForceAutoResumeRomId = resolvedId
            runOnUiThread {
                launchNativeItem(
                    resolvedId,
                    title.ifBlank { "ROM" },
                    fileName.ifBlank { "$title.gba" },
                    system.ifBlank { "ROM" }
                )
            }
        }

        @JavascriptInterface
        fun hasResumeState(romId: String): Boolean = saveStates.hasResumeState(romId)

        @JavascriptInterface
        fun launchRomState(id: String, title: String, fileName: String, system: String, slot: Int) {
            val resolvedId = id.ifBlank { fileOps.sanitizeFileName(title) }
            if (slot !in 0..10 || !saveStates.stateFile(slot, resolvedId).exists()) {
                runOnUiThread {
                    RetraNotice.makeText(this@MainActivity, "Save state is no longer available", RetraNotice.LENGTH_SHORT).show()
                }
                return
            }

            pendingStateLoadRomId = resolvedId
            pendingStateLoadSlot = slot
            runOnUiThread {
                launchNativeItem(
                    resolvedId,
                    title.ifBlank { "ROM" },
                    fileName.ifBlank { "$title.gba" },
                    system.ifBlank { "ROM" }
                )
            }
        }

        @JavascriptInterface
        fun getNativeLibraryRecords(): String = JSONArray().apply {
            romIdentityStore.all().forEach { record ->
                put(JSONObject().apply {
                    put("id", record.romId)
                    put("romId", record.romId)
                    put("contentHash", record.contentHash)
                    put("system", record.platform)
                    put("title", record.displayName)
                    put("fileName", record.fileName)
                    put("sourceUri", record.currentFileUri ?: record.sourceUri ?: "")
                    put("size", record.fileSize)
                    put("lastModified", record.lastModified)
                    put("favorite", record.favorite)
                    put("archived", record.archived)
                    put("fileAvailable", record.fileAvailable)
                    put("playtime", record.playtimeMs)
                    put("categories", runCatching { JSONArray(record.categoriesJson) }.getOrElse { JSONArray() })
                })
            }
        }.toString()

        @JavascriptInterface
        fun isLibraryMetadataRoomMigrationComplete(): Boolean =
            prefs.getBoolean("library_metadata_room_migration_v1", false)

        @JavascriptInterface
        fun markLibraryMetadataRoomMigrationComplete(): Boolean =
            prefs.edit().putBoolean("library_metadata_room_migration_v1", true).commit()

        @JavascriptInterface
        fun updateRomLibraryMetadata(romId: String, favorite: Boolean, categoriesJson: String): Boolean {
            if (romId.isBlank() || romIdentityStore.getById(romId) == null) return false
            val normalized = runCatching { JSONArray(categoriesJson).toString() }.getOrDefault("[]")
            romIdentityStore.updateLibraryMetadata(romId, favorite, normalized)
            return true
        }

        @JavascriptInterface
        fun saveRomMedia(romId: String, kind: String, dataUrl: String): Boolean =
            romAssets.saveMedia(romId, kind, dataUrl)

        @JavascriptInterface
        fun getRomMediaUrl(romId: String, kind: String): String = romAssets.mediaUrl(romId, kind)

        @JavascriptInterface
        fun queueRomArtwork(romId: String, force: Boolean): Boolean =
            artworkRepository.queue(romId, force)

        @JavascriptInterface
        fun getRomConfig(romId: String): String = romAssets.getConfig(romId)

        @JavascriptInterface
        fun setRomConfig(romId: String, json: String): Boolean = romAssets.setConfig(romId, json)

        @JavascriptInterface
        fun getUiPreference(key: String): String =
            prefs.getString("ui_${fileOps.sanitizeFileName(key)}", "") ?: ""

        @JavascriptInterface
        fun setUiPreference(key: String, value: String): Boolean =
            prefs.edit().putString("ui_${fileOps.sanitizeFileName(key)}", value).commit()

        @JavascriptInterface
        fun getRomSaveStates(romId: String): String = saveStates.listJson(romId)

        @JavascriptInterface
        fun archiveRom(romId: String): Boolean = archiveRomRecord(romId)

        @JavascriptInterface
        fun deleteGameData(romId: String): Boolean = deleteGameDataInternal(romId)

        @JavascriptInterface
        fun deleteRomSaveState(romId: String, slot: Int): Boolean =
            deleteRomSaveStateInternal(romId, slot)

        @JavascriptInterface
        fun importRom() {
            runOnUiThread { openImportPicker() }
        }

        @JavascriptInterface
        fun getSettingsState(): String = settingsStateJson()

        @JavascriptInterface
        fun setSetting(key: String, value: String): Boolean = updateSetting(key, value)

        @JavascriptInterface
        fun setCloudSyncEnabled(enabled: Boolean) {
            runOnUiThread {
                if (enabled) {
                    if (!cloudSync.isReady()) requestCloudSyncAccount()
                    else {
                        cloudSync.setEnabled(true)
                        syncCloudAsync(showResult = true)
                    }
                } else {
                    cloudSync.setEnabled(false)
                }
            }
        }

        @JavascriptInterface
        fun openCloudSyncSettings() {
            runOnUiThread { cloudSync.showSettings({ requestCloudSyncAccount() }, { requestCloudSyncFolder() }) }
        }

        @JavascriptInterface
        fun importSavesFromFolder() {
            runOnUiThread { importSavesFolderPicker.launch(null) }
        }

        @JavascriptInterface
        fun openAppFolder() {
            runOnUiThread { openAppFolderInternal() }
        }

        @JavascriptInterface
        fun selectBiosFile() {
            runOnUiThread { biosFilePicker.launch(arrayOf("application/octet-stream", "*/*")) }
        }

        @JavascriptInterface
        fun resetAdvancedSettings() {
            runOnUiThread { resetAdvancedSettingsInternal() }
        }

        @JavascriptInterface
        fun setFastForwardSpeed(value: String) {
            val parsed = value.lowercase(Locale.US).removeSuffix("x").toDoubleOrNull() ?: 4.0
            preferredEmulationSpeed = EmulationSpeedPolicy.sanitize(parsed)
            prefs.edit().putString(EMULATION_SPEED_PREF, preferredEmulationSpeed.toString()).apply()
            if (!EmulationSpeedPolicy.isNormal(activeEmulationSpeed)) {
                activeEmulationSpeed = preferredEmulationSpeed
                runOnUiThread { updateFastForwardUi() }
            }
            notifyWebSettingsState()
        }

        @JavascriptInterface
        fun setGlslShader(value: String) {
            runOnUiThread {
                shaderController.select(value, showToast = true)
            }
        }

        @JavascriptInterface
        fun installGlslShader() {
            runOnUiThread {
                shaderFilePicker.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
            }
        }

        @JavascriptInterface
        fun setButtonsOpacity(value: Int) {
            val percent = value.coerceIn(25, 100)
            preferredButtonsOpacity = percent / 100f
            prefs.edit().putInt(BUTTON_OPACITY_PREF, percent).apply()
            runOnUiThread {
                applyNativeButtonsOpacity()
            }
        }

        @JavascriptInterface
        fun setScreenOrientation(value: String) {
            preferredOrientationValue = value
            prefs.edit().putString(ORIENTATION_PREF, value).apply()
            // Apply the selection immediately. Rotation-safe gameplay/editor
            // layout handling already keeps portrait and landscape data separate.
            runOnUiThread { applyPreferredOrientation(value) }
        }

        @JavascriptInterface
        fun setScreenEditorActive(active: Boolean) {
            runOnUiThread { setScreenEditorPresentation(active) }
        }

        @JavascriptInterface
        fun closeInGameSettings() {
            runOnUiThread { this@MainActivity.closeInGameSettings() }
        }

        @JavascriptInterface
        fun getGameplayLayout(orientation: String): String = getRomGameplayLayout("_default", orientation)

        @JavascriptInterface
        fun getRomGameplayLayout(romId: String, orientation: String): String {
            val normalized = orientation.lowercase(Locale.US)
            if (normalized != "portrait" && normalized != "landscape") return "{}"
            val portrait = normalized == "portrait"
            val bundle = gameplayLayouts.readBundle(romId.ifBlank { "_default" }, portrait)
                ?: return JSONObject().put("orientation", normalized).toString()
            return JSONObject(bundle.toString()).apply {
                put("orientation", normalized)
                put("romId", romId.ifBlank { "_default" })
            }.toString()
        }

        @JavascriptInterface
        fun commitGameplayLayout(controllerJson: String, screenJson: String): Boolean =
            commitRomGameplayLayout("_default", controllerJson, screenJson)

        @JavascriptInterface
        fun commitRomGameplayLayout(romId: String, controllerJson: String, screenJson: String): Boolean {
            val controller = try { JSONObject(controllerJson) } catch (_: Exception) { return false }
            val screen = try { JSONObject(screenJson) } catch (_: Exception) { return false }
            val controllerOrientation = controller.optString("orientation", "").lowercase(Locale.US)
            val screenOrientation = screen.optString("orientation", "").lowercase(Locale.US)
            if (controllerOrientation != screenOrientation ||
                (controllerOrientation != "portrait" && controllerOrientation != "landscape")
            ) return false

            val portrait = controllerOrientation == "portrait"
            val saved = gameplayLayouts.writeBundle(romId.ifBlank { "_default" }, portrait, controller, screen)
            if (saved) {
                runOnUiThread {
                    if (binding.emulatorOverlay.visibility == View.VISIBLE &&
                        currentRomId == romId && currentNativePortrait() == portrait
                    ) scheduleNativeEmulatorLayout(0L)
                }
            }
            return saved
        }

        @JavascriptInterface
        fun setControllerLayout(json: String) = setRomControllerLayout("_default", json)

        @JavascriptInterface
        fun setRomControllerLayout(romId: String, json: String) {
            val controller = try { JSONObject(json) } catch (_: Exception) { return }
            val orientation = controller.optString("orientation", "").lowercase(Locale.US)
            if (orientation != "portrait" && orientation != "landscape") return
            val portrait = orientation == "portrait"
            if (!gameplayLayouts.writeBundle(romId.ifBlank { "_default" }, portrait, controller, null)) return
            runOnUiThread {
                if (binding.emulatorOverlay.visibility == View.VISIBLE &&
                    currentRomId == romId && currentNativePortrait() == portrait
                ) scheduleNativeEmulatorLayout(40L)
            }
        }

        @JavascriptInterface
        fun setEmulatorScreenLayout(json: String) = setRomEmulatorScreenLayout("_default", json)

        @JavascriptInterface
        fun setRomEmulatorScreenLayout(romId: String, json: String) {
            val screen = try { JSONObject(json) } catch (_: Exception) { return }
            val orientation = screen.optString("orientation", "").lowercase(Locale.US)
            if (orientation != "portrait" && orientation != "landscape") return
            val portrait = orientation == "portrait"
            if (!gameplayLayouts.writeBundle(romId.ifBlank { "_default" }, portrait, null, screen)) return
            runOnUiThread {
                if (binding.emulatorOverlay.visibility == View.VISIBLE &&
                    currentRomId == romId && currentNativePortrait() == portrait
                ) scheduleNativeEmulatorLayout(40L)
            }
        }

        @JavascriptInterface
        fun clearGameplayLayout(orientation: String): Boolean = clearRomGameplayLayout("_default", orientation)

        @JavascriptInterface
        fun clearRomGameplayLayout(romId: String, orientation: String): Boolean {
            val normalized = orientation.lowercase(Locale.US)
            if (normalized != "portrait" && normalized != "landscape") return false
            val portrait = normalized == "portrait"
            val cleared = gameplayLayouts.clear(romId.ifBlank { "_default" }, portrait)
            if (cleared) {
                runOnUiThread {
                    if (binding.emulatorOverlay.visibility == View.VISIBLE &&
                        currentRomId == romId && currentNativePortrait() == portrait
                    ) scheduleNativeEmulatorLayout(0L)
                }
            }
            return cleared
        }

        @JavascriptInterface
        fun getNativeStatistics(): String = statistics.json(romLoaded, currentRomId)

        @JavascriptInterface
        fun coreVersion(): String = stringFromJNI()
    }

    external fun loadRom(path: String, savePath: String): Boolean
    external fun loadRomWithPatch(path: String, patchPath: String, savePath: String): Boolean
    external fun materializePatchedRom(path: String, patchPath: String, outputPath: String): Boolean
    external fun startLocalLink(firstRomPath: String, secondRomPath: String, firstSavePath: String, secondSavePath: String): Boolean
    external fun stopLocalLink(): Boolean
    external fun isNativeLocalLinkActive(): Boolean
    external fun getLocalLinkPlayer(): Int
    external fun setLocalLinkPlayer(player: Int): Boolean
    external fun setLocalLinkPaused(paused: Boolean): Boolean
    external fun getLocalLinkFrame(): Long
    external fun getLocalLinkFrameSkew(): Long
    external fun getLatestLocalLinkCheckpoint(): LongArray
    external fun getLocalLinkCheckpointHash(frame: Long): Long
    external fun exportLocalLinkSnapshot(): ByteArray?
    external fun importLocalLinkSnapshot(payload: ByteArray): Boolean
    external fun setLocalLinkKeyMask(player: Int, mask: Int): Boolean
    external fun scheduleLocalLinkKeyMask(player: Int, frame: Long, mask: Int): Boolean
    external fun clearLocalLinkInputSchedule()
    external fun runFrame(pixels: IntArray): Boolean
    external fun setKey(key: Int, pressed: Boolean)
    external fun shutdownCore()
    external fun stringFromJNI(): String
    external fun getVideoWidth(): Int
    external fun getVideoHeight(): Int
    external fun getPlatform(): Int
    external fun quickSaveState(path: String): Boolean
    external fun quickLoadState(path: String): Boolean
    external fun setCoreConfigOption(key: String, value: String)
    external fun readAudioSamples(buffer: ShortArray): Int
    external fun resetCore(): Boolean
    external fun clearNativeCheats(): Boolean
    external fun addNativeCheat(name: String, code: String, type: Int, enabled: Boolean): Boolean

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::displayPerformanceManager.isInitialized) displayPerformanceManager.reapplyAfterConfigurationChange()
        gameplayMenuDialog?.takeIf { it.isShowing }?.let { dialog ->
            dialog.window?.decorView?.post { sizeGameplayDialog(dialog) }
        }
        if (::binding.isInitialized && binding.emulatorOverlay.visibility == View.VISIBLE) {
            // Wait for the viewport to finish its real portrait/landscape measure.
            // The viewport layout listener will schedule another pass if needed.
            scheduleNativeEmulatorLayout(120L)
        }
    }
}
