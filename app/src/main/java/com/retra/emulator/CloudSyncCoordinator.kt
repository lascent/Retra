package com.retra.emulator

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns Google Drive OAuth/API vs SAF fallback policy, token lifetime,
 * conflict-safe synchronization and Retra's automatic cloud-protection status.
 *
 * Automatic requests are debounced/coalesced so saving a state or changing
 * several settings never starts overlapping network work. If data changes while
 * a sync is already running, one final pass is guaranteed after that sync ends.
 */
class CloudSyncCoordinator(
    private val activity: Activity,
    private val prefs: RetraPreferences,
    private val saveTransfer: SaveTransferRepository,
    private val driveApi: GoogleDriveApiRepository,
    private val ioExecutor: Executor,
    private val cloudRootUri: () -> Uri?,
    private val onSettingsChanged: () -> Unit,
    private val onPortableDataDownloaded: (Boolean) -> Unit,
    private val onFolderFallbackRequested: () -> Unit,
    private val onAuthRecoveryRequired: (String, Intent, Boolean) -> Unit
) {
    @Volatile private var accessToken: String? = null
    @Volatile private var syncInFlight = false
    @Volatile private var pendingAutoSync = false
    private val autoSyncScheduled = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val autoSyncRunnable = Runnable {
        autoSyncScheduled.set(false)
        sync(showResult = false)
    }

    fun mode(): String = prefs.getString(MODE_PREF, if (cloudRootUri() != null) MODE_SAF else MODE_API) ?: MODE_API

    fun isReady(): Boolean {
        if (!prefs.getBoolean(ENABLED_PREF, false)) return false
        return if (mode() == MODE_API) !prefs.getString(ACCOUNT_PREF, null).isNullOrBlank() else cloudRootUri() != null
    }

    fun lastSuccessfulSyncAt(): Long = prefs.getLong(LAST_SUCCESS_PREF, 0L).coerceAtLeast(0L)
    fun lastSyncError(): String = prefs.getString(LAST_ERROR_PREF, "").orEmpty()
    fun lastUploadedCount(): Int = prefs.getInt(LAST_UPLOADED_PREF, 0).coerceAtLeast(0)
    fun lastDownloadedCount(): Int = prefs.getInt(LAST_DOWNLOADED_PREF, 0).coerceAtLeast(0)
    fun lastConflictCount(): Int = prefs.getInt(LAST_CONFLICTS_PREF, 0).coerceAtLeast(0)

    fun launchAccountChooser(launch: (Intent) -> Unit, onUnavailable: () -> Unit) {
        try {
            launch(AccountManager.newChooseAccountIntent(
                null, null, arrayOf("com.google"),
                "Choose the Google account Retra should use for Drive sync",
                null, null, null
            ))
        } catch (_: Exception) {
            onUnavailable()
        }
    }

    fun folderPrompt(accountName: String?): String = if (accountName.isNullOrBlank()) {
        "Choose a folder in Google Drive for Retra saves"
    } else {
        "Choose a Retra folder inside $accountName in Google Drive"
    }

    fun connectAccount(
        accountName: String,
        interactive: Boolean,
        fallbackToFolder: Boolean,
        showSyncResult: Boolean = true,
        restoreRemoteFirst: Boolean = false
    ) {
        val account = Account(accountName, "com.google")
        val manager = AccountManager.get(activity)
        val callback = android.accounts.AccountManagerCallback<Bundle> { future ->
            val bundle = runCatching { future.result }.getOrNull()
            if (bundle == null) {
                activity.runOnUiThread {
                    if (fallbackToFolder) onFolderFallbackRequested()
                    else if (showSyncResult) toast("Google Drive authorization is unavailable")
                }
            } else {
                val recovery = if (Build.VERSION.SDK_INT >= 33) {
                    bundle.getParcelable(AccountManager.KEY_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") bundle.getParcelable<Intent>(AccountManager.KEY_INTENT)
                }
                val token = bundle.getString(AccountManager.KEY_AUTHTOKEN)
                when {
                    recovery != null -> activity.runOnUiThread {
                        if (interactive) onAuthRecoveryRequired(accountName, recovery, restoreRemoteFirst)
                        else if (fallbackToFolder) onFolderFallbackRequested()
                    }
                    token.isNullOrBlank() -> activity.runOnUiThread {
                        if (fallbackToFolder) onFolderFallbackRequested()
                        else if (showSyncResult) toast("Google Drive API token was not granted")
                    }
                    else -> {
                        accessToken = token
                        prefs.edit()
                            .putBoolean(ENABLED_PREF, true)
                            .putString(ACCOUNT_PREF, accountName)
                            .putString(MODE_PREF, MODE_API)
                            .remove(URI_PREF)
                            .apply()
                        onSettingsChanged()
                        syncApiWithToken(token, showSyncResult, restoreRemoteFirst)
                    }
                }
            }
        }
        try {
            if (interactive) manager.getAuthToken(account, DRIVE_AUTH_SCOPE, null, activity, callback, null)
            else manager.getAuthToken(account, DRIVE_AUTH_SCOPE, null, false, callback, null)
        } catch (_: Exception) {
            if (fallbackToFolder) onFolderFallbackRequested()
            else recordFailure("Google Drive authorization failed")
        }
    }

    fun markSafConnected(uri: Uri, accountName: String?) {
        val editor = prefs.edit()
            .putString(URI_PREF, uri.toString())
            .putString(MODE_PREF, MODE_SAF)
            .putBoolean(ENABLED_PREF, true)
        accountName?.let { editor.putString(ACCOUNT_PREF, it) }
        editor.apply()
        accessToken = null
        onSettingsChanged()
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(ENABLED_PREF, enabled).apply()
        if (!enabled) {
            mainHandler.removeCallbacks(autoSyncRunnable)
            autoSyncScheduled.set(false)
            pendingAutoSync = false
        }
        onSettingsChanged()
    }

    /**
     * Queue an automatic backup after a short quiet period. Repeated save/settings
     * events collapse into one network operation, protecting gameplay performance.
     */
    fun requestAutoSync(urgent: Boolean = false) {
        if (!prefs.getBoolean(ENABLED_PREF, false) || !isReady()) return
        if (syncInFlight) {
            pendingAutoSync = true
            return
        }
        mainHandler.removeCallbacks(autoSyncRunnable)
        autoSyncScheduled.set(true)
        mainHandler.postDelayed(autoSyncRunnable, if (urgent) 0L else AUTO_SYNC_DEBOUNCE_MS)
    }

    fun showSettings(requestAccount: () -> Unit, requestFolder: () -> Unit) {
        val account = prefs.getString(ACCOUNT_PREF, null)
        val mode = mode()
        val modeLabel = if (mode == MODE_API) "Drive API" else "Drive folder"
        AlertDialog.Builder(activity)
            .setTitle(if (account.isNullOrBlank()) "Google Drive sync • $modeLabel" else "Google Drive sync • $account • $modeLabel")
            .setItems(arrayOf("Sync now", "Change Google account", "Use Drive folder instead", "Disconnect")) { _, which ->
                when (which) {
                    0 -> sync(showResult = true)
                    1 -> requestAccount()
                    2 -> requestFolder()
                    3 -> {
                        disconnect()
                        RetraNotice.makeText(activity, "Google Drive sync disconnected", RetraNotice.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun disconnect() {
        mainHandler.removeCallbacks(autoSyncRunnable)
        autoSyncScheduled.set(false)
        pendingAutoSync = false
        accessToken = null
        prefs.edit()
            .putBoolean(ENABLED_PREF, false)
            .remove(URI_PREF)
            .remove(ACCOUNT_PREF)
            .remove(MODE_PREF)
            .remove(LAST_SUCCESS_PREF)
            .remove(LAST_ERROR_PREF)
            .remove(LAST_UPLOADED_PREF)
            .remove(LAST_DOWNLOADED_PREF)
            .remove(LAST_CONFLICTS_PREF)
            .apply()
        onSettingsChanged()
    }

    fun deletePaths(paths: List<String>) {
        if (!prefs.getBoolean(ENABLED_PREF, false) || paths.isEmpty()) return
        if (mode() == MODE_API) {
            driveApi.queueDelete(paths)
            val token = accessToken ?: return
            ioExecutor.execute {
                runCatching { driveApi.deletePaths(token, paths) }.onFailure { error ->
                    if (error is GoogleDriveApiRepository.AuthExpiredException) invalidateToken(token)
                }
            }
            return
        }
        val uri = cloudRootUri() ?: return
        ioExecutor.execute { saveTransfer.deleteRemotePaths(uri, paths) }
    }

    /**
     * Bidirectional sync. restoreRemoteFirst is used only by the explicit
     * "Restore from Google Drive" action on a reinstall/new device. It prevents
     * freshly generated empty metadata from winning over the existing cloud copy.
     */
    fun sync(showResult: Boolean, restoreRemoteFirst: Boolean = false) {
        if (!prefs.getBoolean(ENABLED_PREF, false)) return
        mainHandler.removeCallbacks(autoSyncRunnable)
        autoSyncScheduled.set(false)
        if (syncInFlight) {
            pendingAutoSync = true
            return
        }
        if (mode() == MODE_API) {
            val account = prefs.getString(ACCOUNT_PREF, null) ?: return
            val token = accessToken
            if (!token.isNullOrBlank()) syncApiWithToken(token, showResult, restoreRemoteFirst)
            else connectAccount(
                account,
                interactive = false,
                fallbackToFolder = false,
                showSyncResult = showResult,
                restoreRemoteFirst = restoreRemoteFirst
            )
            return
        }
        val uri = cloudRootUri() ?: return
        syncInFlight = true
        ioExecutor.execute {
            val result = runCatching { saveTransfer.syncDetailed(uri, restoreRemoteFirst) }
                .getOrElse {
                    recordFailure(it.message ?: "Drive folder sync failed")
                    SaveTransferRepository.SyncResult(errors = 1)
                }
            if (result.downloaded > 0) runCatching { onPortableDataDownloaded(restoreRemoteFirst) }
            if (result.errors == 0) recordSuccess(result.uploaded, result.downloaded, result.conflicts)
            else recordFailure("Drive folder sync completed with ${result.errors} error${if (result.errors == 1) "" else "s"}")
            finishSync()
            if (showResult) activity.runOnUiThread {
                toast(formatResult("Drive folder sync complete", result.uploaded, result.downloaded, result.conflicts, result.errors))
            }
        }
    }

    private fun syncApiWithToken(token: String, showResult: Boolean, restoreRemoteFirst: Boolean) {
        if (syncInFlight) {
            pendingAutoSync = true
            return
        }
        syncInFlight = true
        ioExecutor.execute {
            try {
                val result = driveApi.sync(token, restoreRemoteFirst)
                if (result.downloaded > 0) runCatching { onPortableDataDownloaded(restoreRemoteFirst) }
                if (result.errors == 0) recordSuccess(result.uploaded, result.downloaded, result.conflicts)
                else recordFailure("Drive API sync completed with ${result.errors} error${if (result.errors == 1) "" else "s"}")
                if (showResult) activity.runOnUiThread {
                    toast(formatResult("Drive API sync complete", result.uploaded, result.downloaded, result.conflicts, result.errors))
                }
            } catch (_: GoogleDriveApiRepository.AuthExpiredException) {
                invalidateToken(token)
                recordFailure("Drive authorization expired")
                if (showResult) activity.runOnUiThread { toast("Drive authorization expired • reconnect sync") }
            } catch (error: Exception) {
                recordFailure(error.message ?: "Drive API network error")
                if (showResult) activity.runOnUiThread { toast("Drive API sync failed: ${error.message ?: "network error"}") }
            } finally {
                finishSync()
            }
        }
    }

    private fun finishSync() {
        syncInFlight = false
        onSettingsChanged()
        if (pendingAutoSync) {
            pendingAutoSync = false
            requestAutoSync(urgent = true)
        }
    }

    private fun recordSuccess(uploaded: Int, downloaded: Int, conflicts: Int) {
        prefs.edit()
            .putLong(LAST_SUCCESS_PREF, System.currentTimeMillis())
            .putString(LAST_ERROR_PREF, "")
            .putInt(LAST_UPLOADED_PREF, uploaded.coerceAtLeast(0))
            .putInt(LAST_DOWNLOADED_PREF, downloaded.coerceAtLeast(0))
            .putInt(LAST_CONFLICTS_PREF, conflicts.coerceAtLeast(0))
            .apply()
    }

    private fun recordFailure(message: String) {
        prefs.edit().putString(LAST_ERROR_PREF, message.take(240)).apply()
        onSettingsChanged()
    }

    private fun invalidateToken(token: String) {
        accessToken = null
        AccountManager.get(activity).invalidateAuthToken("com.google", token)
    }

    private fun formatResult(title: String, uploaded: Int, downloaded: Int, conflicts: Int, errors: Int): String {
        val conflictCopy = if (conflicts > 0) " • $conflicts conflict${if (conflicts == 1) "" else "s"} preserved" else ""
        val errorCopy = if (errors > 0) " • $errors error${if (errors == 1) "" else "s"}" else ""
        return "$title • $uploaded uploaded • $downloaded downloaded$conflictCopy$errorCopy"
    }

    private fun toast(message: String) = RetraNotice.makeText(activity, message, RetraNotice.LENGTH_LONG).show()

    companion object {
        const val ENABLED_PREF = "cloud_sync_enabled_v1"
        const val URI_PREF = "cloud_sync_uri_v1"
        const val ACCOUNT_PREF = "cloud_sync_account_v1"
        const val MODE_PREF = "cloud_sync_mode_v2"
        const val MODE_API = "api"
        const val MODE_SAF = "saf"
        const val LAST_SUCCESS_PREF = "cloud_sync_last_success_v1"
        const val LAST_ERROR_PREF = "cloud_sync_last_error_v1"
        const val LAST_UPLOADED_PREF = "cloud_sync_last_uploaded_v1"
        const val LAST_DOWNLOADED_PREF = "cloud_sync_last_downloaded_v1"
        const val LAST_CONFLICTS_PREF = "cloud_sync_last_conflicts_v1"
        private const val AUTO_SYNC_DEBOUNCE_MS = 1_500L
        private const val DRIVE_AUTH_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file"
    }
}
