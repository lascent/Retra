package com.retra.emulator

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import java.util.concurrent.Executor

/**
 * Owns Google Drive OAuth/API vs SAF fallback policy, token lifetime and
 * conflict-safe synchronization so MainActivity remains navigation/UI glue.
 */
class CloudSyncCoordinator(
    private val activity: Activity,
    private val prefs: RetraPreferences,
    private val saveTransfer: SaveTransferRepository,
    private val driveApi: GoogleDriveApiRepository,
    private val ioExecutor: Executor,
    private val cloudRootUri: () -> Uri?,
    private val onSettingsChanged: () -> Unit,
    private val onFolderFallbackRequested: () -> Unit,
    private val onAuthRecoveryRequired: (String, Intent) -> Unit
) {
    @Volatile private var accessToken: String? = null
    @Volatile private var syncInFlight = false

    fun mode(): String = prefs.getString(MODE_PREF, if (cloudRootUri() != null) MODE_SAF else MODE_API) ?: MODE_API

    fun isReady(): Boolean {
        if (!prefs.getBoolean(ENABLED_PREF, false)) return false
        return if (mode() == MODE_API) !prefs.getString(ACCOUNT_PREF, null).isNullOrBlank() else cloudRootUri() != null
    }

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

    fun connectAccount(accountName: String, interactive: Boolean, fallbackToFolder: Boolean, showSyncResult: Boolean = true) {
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
                        if (interactive) onAuthRecoveryRequired(accountName, recovery)
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
                        syncApiWithToken(token, showSyncResult)
                    }
                }
            }
        }
        try {
            if (interactive) manager.getAuthToken(account, DRIVE_AUTH_SCOPE, null, activity, callback, null)
            else manager.getAuthToken(account, DRIVE_AUTH_SCOPE, null, false, callback, null)
        } catch (_: Exception) {
            if (fallbackToFolder) onFolderFallbackRequested()
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
        onSettingsChanged()
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
        accessToken = null
        prefs.edit()
            .putBoolean(ENABLED_PREF, false)
            .remove(URI_PREF)
            .remove(ACCOUNT_PREF)
            .remove(MODE_PREF)
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

    fun sync(showResult: Boolean) {
        if (!prefs.getBoolean(ENABLED_PREF, false) || syncInFlight) return
        if (mode() == MODE_API) {
            val account = prefs.getString(ACCOUNT_PREF, null) ?: return
            val token = accessToken
            if (!token.isNullOrBlank()) syncApiWithToken(token, showResult)
            else connectAccount(account, interactive = false, fallbackToFolder = false, showSyncResult = showResult)
            return
        }
        val uri = cloudRootUri() ?: return
        syncInFlight = true
        ioExecutor.execute {
            val result = runCatching { saveTransfer.syncDetailed(uri) }
                .getOrDefault(SaveTransferRepository.SyncResult(errors = 1))
            syncInFlight = false
            if (showResult) activity.runOnUiThread {
                toast(formatResult("Drive folder sync complete", result.uploaded, result.downloaded, result.conflicts, result.errors))
            }
        }
    }

    private fun syncApiWithToken(token: String, showResult: Boolean) {
        if (syncInFlight) return
        syncInFlight = true
        ioExecutor.execute {
            try {
                val result = driveApi.sync(token)
                if (showResult) activity.runOnUiThread {
                    toast(formatResult("Drive API sync complete", result.uploaded, result.downloaded, result.conflicts, result.errors))
                }
            } catch (_: GoogleDriveApiRepository.AuthExpiredException) {
                invalidateToken(token)
                if (showResult) activity.runOnUiThread { toast("Drive authorization expired • reconnect sync") }
            } catch (error: Exception) {
                if (showResult) activity.runOnUiThread { toast("Drive API sync failed: ${error.message ?: "network error"}") }
            } finally {
                syncInFlight = false
            }
        }
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
        private const val DRIVE_AUTH_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file"
    }
}
