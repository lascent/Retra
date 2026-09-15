package com.retra.emulator

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Google Drive backup coordinator backed exclusively by Google Identity Services
 * + Drive REST v3. Google Drive backup/restore never uses the Android document-tree picker.
 *
 * Local backup/export/import can still use Android's Storage Access Framework;
 * cloud backup always targets My Drive/Retra Backups directly through Drive API.
 */
class CloudSyncCoordinator(
    private val activity: Activity,
    private val prefs: RetraPreferences,
    private val backupRepository: BackupRepository,
    private val driveApi: GoogleDriveApiRepository,
    private val ioExecutor: Executor,
    private val onSettingsChanged: () -> Unit,
    private val onBackupRestored: () -> Unit,
    private val onAuthorizationResolutionRequired: (PendingIntent) -> Unit
) {
    private enum class Operation { BACKUP, RESTORE }
    private data class PendingAuthorization(
        val operation: Operation,
        val accountName: String,
        val interactive: Boolean,
        val showResult: Boolean
    )

    private val authorizationClient = Identity.getAuthorizationClient(activity)
    private val driveScopes = listOf(Scope(DRIVE_FILE_SCOPE))
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoSyncScheduled = AtomicBoolean(false)

    @Volatile private var operationInFlight = false
    @Volatile private var restoreInFlight = false
    @Volatile private var pendingAutoBackup = false
    @Volatile private var pendingAuthorization: PendingAuthorization? = null

    private val autoSyncRunnable = Runnable {
        autoSyncScheduled.set(false)
        backupNow(showResult = false, interactiveAuthorization = false)
    }

    fun mode(): String = MODE_API
    fun isReady(): Boolean = !prefs.getString(ACCOUNT_PREF, null).isNullOrBlank()
    fun isAutomaticBackupEnabled(): Boolean = prefs.getBoolean(ENABLED_PREF, false)
    fun connectedAccount(): String = prefs.getString(ACCOUNT_PREF, "").orEmpty()
    fun lastSuccessfulSyncAt(): Long = prefs.getLong(LAST_SUCCESS_PREF, 0L).coerceAtLeast(0L)
    fun lastRestoreAt(): Long = prefs.getLong(LAST_RESTORE_PREF, 0L).coerceAtLeast(0L)
    fun lastSyncError(): String = prefs.getString(LAST_ERROR_PREF, "").orEmpty()
    fun lastUploadedCount(): Int = prefs.getInt(LAST_UPLOADED_PREF, 0).coerceAtLeast(0)
    fun lastDownloadedCount(): Int = prefs.getInt(LAST_DOWNLOADED_PREF, 0).coerceAtLeast(0)
    fun lastConflictCount(): Int = 0
    fun transferActive(): Boolean = prefs.getBoolean(TRANSFER_ACTIVE_PREF, false)
    fun transferLabel(): String = prefs.getString(TRANSFER_LABEL_PREF, "").orEmpty()
    fun transferProgress(): Int = prefs.getInt(TRANSFER_PROGRESS_PREF, 0).coerceIn(0, 100)

    fun launchAccountChooser(launch: (Intent) -> Unit, onUnavailable: () -> Unit) {
        try {
            launch(
                AccountManager.newChooseAccountIntent(
                    null,
                    null,
                    arrayOf("com.google"),
                    "Choose the Google account Retra should use for Google Drive",
                    null,
                    null,
                    null
                )
            )
        } catch (_: Exception) {
            onUnavailable()
        }
    }

    /** Connect/authorize the selected Google account, then immediately run the requested action. */
    fun connectAccount(accountName: String, restore: Boolean, showResult: Boolean = true) {
        if (accountName.isBlank()) return
        if (!operationInFlight && !beginOperation()) return
        authorize(
            PendingAuthorization(
                operation = if (restore) Operation.RESTORE else Operation.BACKUP,
                accountName = accountName,
                interactive = true,
                showResult = showResult
            )
        )
    }

    /** Called by MainActivity after the GIS authorization PendingIntent completes. */
    fun onAuthorizationResolutionResult(resultCode: Int, data: Intent?) {
        val pending = pendingAuthorization
        pendingAuthorization = null
        if (pending == null) return
        if (resultCode != Activity.RESULT_OK || data == null) {
            recordFailure("Google Drive authorization was cancelled")
            if (pending.showResult) toast("Google Drive authorization was cancelled")
            finishOperation()
            return
        }
        try {
            val result = authorizationClient.getAuthorizationResultFromIntent(data)
            val token = result.accessToken
            if (token.isNullOrBlank()) throw IllegalStateException("Google Drive access token was not granted")
            markAuthorized(pending.accountName, pending.operation == Operation.BACKUP)
            executeAuthorized(pending, token)
        } catch (error: Exception) {
            recordFailure(error.message ?: "Google Drive authorization failed")
            if (pending.showResult) toast("Google Drive authorization failed: ${friendly(error)}")
            finishOperation()
        }
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(ENABLED_PREF, enabled).apply()
        if (!enabled) {
            mainHandler.removeCallbacks(autoSyncRunnable)
            autoSyncScheduled.set(false)
            pendingAutoBackup = false
        }
        onSettingsChanged()
    }

    /** Automatic backup is debounced and never launches interactive OAuth UI. */
    fun requestAutoSync(urgent: Boolean = false) {
        if (!isAutomaticBackupEnabled() || !isReady() || restoreInFlight) return
        if (operationInFlight) {
            pendingAutoBackup = true
            return
        }
        mainHandler.removeCallbacks(autoSyncRunnable)
        autoSyncScheduled.set(true)
        mainHandler.postDelayed(autoSyncRunnable, if (urgent) 0L else AUTO_BACKUP_DEBOUNCE_MS)
    }

    fun backupNow(showResult: Boolean = true, interactiveAuthorization: Boolean = true) {
        val account = connectedAccount()
        if (account.isBlank()) {
            if (showResult) toast("Connect a Google account first")
            return
        }
        if (!beginOperation()) return
        authorize(PendingAuthorization(Operation.BACKUP, account, interactiveAuthorization, showResult))
    }

    fun restoreFromDrive(showResult: Boolean = true) {
        val account = connectedAccount()
        if (account.isBlank()) {
            if (showResult) toast("Choose the Google account that contains your Retra backup")
            return
        }
        if (!beginOperation()) return
        authorize(PendingAuthorization(Operation.RESTORE, account, true, showResult))
    }

    fun showSettings(requestAccount: () -> Unit) {
        val account = connectedAccount()
        AlertDialog.Builder(activity)
            .setTitle(if (account.isBlank()) "Google Drive" else "Google Drive • $account")
            .setItems(arrayOf("Backup Now", "Restore from Google Drive", "Change Google account", "Disconnect")) { _, which ->
                when (which) {
                    0 -> backupNow(showResult = true)
                    1 -> restoreFromDrive(showResult = true)
                    2 -> requestAccount()
                    3 -> {
                        disconnect()
                        toast("Google Drive disconnected")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Snapshot backups are immutable; deletions are captured by the next snapshot. */
    fun deletePaths(paths: List<String>) {
        if (paths.isNotEmpty()) requestAutoSync(urgent = false)
    }

    fun disconnect() {
        mainHandler.removeCallbacks(autoSyncRunnable)
        autoSyncScheduled.set(false)
        pendingAutoBackup = false
        pendingAuthorization = null
        val accountName = connectedAccount()
        if (accountName.isNotBlank()) {
            val request = RevokeAccessRequest.builder()
                .setAccount(Account(accountName, "com.google"))
                .setScopes(driveScopes)
                .build()
            authorizationClient.revokeAccess(request)
        }
        prefs.edit()
            .putBoolean(ENABLED_PREF, false)
            .remove(ACCOUNT_PREF)
            .remove(MODE_PREF)
            .remove(URI_PREF) // remove legacy SAF cloud setting from earlier v1.0.3 builds
            .remove(LAST_ERROR_PREF)
            .remove(TRANSFER_ACTIVE_PREF)
            .remove(TRANSFER_LABEL_PREF)
            .remove(TRANSFER_PROGRESS_PREF)
            .apply()
        onSettingsChanged()
    }

    private fun authorize(pending: PendingAuthorization) {
        pendingAuthorization = pending
        setProgress("Authorizing Google Drive", 0)
        val request = AuthorizationRequest.builder()
            .setAccount(Account(pending.accountName, "com.google"))
            .setRequestedScopes(driveScopes)
            .build()

        authorizationClient.authorize(request)
            .addOnSuccessListener { result ->
                when {
                    result.hasResolution() -> {
                        val resolution = result.pendingIntent
                        if (pending.interactive && resolution != null) {
                            pendingAuthorization = pending
                            activity.runOnUiThread { onAuthorizationResolutionRequired(resolution) }
                        } else {
                            pendingAuthorization = null
                            recordFailure("Google Drive authorization needs your approval. Open Google Drive settings and try again.")
                            finishOperation()
                        }
                    }
                    result.accessToken.isNullOrBlank() -> {
                        pendingAuthorization = null
                        recordFailure("Google Drive access token was not granted")
                        if (pending.showResult) activity.runOnUiThread { toast("Google Drive access was not granted") }
                        finishOperation()
                    }
                    else -> {
                        pendingAuthorization = null
                        markAuthorized(pending.accountName, pending.operation == Operation.BACKUP)
                        executeAuthorized(pending, result.accessToken!!)
                    }
                }
            }
            .addOnFailureListener { error ->
                pendingAuthorization = null
                recordFailure("Google Drive authorization failed: ${friendly(error)}")
                if (pending.showResult) activity.runOnUiThread { toast("Google Drive authorization failed: ${friendly(error)}") }
                finishOperation()
            }
    }

    private fun markAuthorized(accountName: String, enableAutomatic: Boolean) {
        val editor = prefs.edit()
            .putString(ACCOUNT_PREF, accountName)
            .putString(MODE_PREF, MODE_API)
            .remove(URI_PREF)
            .putString(LAST_ERROR_PREF, "")
        if (enableAutomatic) editor.putBoolean(ENABLED_PREF, true)
        editor.apply()
        onSettingsChanged()
    }

    private fun executeAuthorized(pending: PendingAuthorization, token: String) {
        when (pending.operation) {
            Operation.BACKUP -> performBackup(token, pending.showResult)
            Operation.RESTORE -> loadRestoreChoices(token, pending.showResult)
        }
    }

    private fun performBackup(token: String, showResult: Boolean) {
        ioExecutor.execute {
            val tempDir = File(activity.cacheDir, "drive-backup").apply { mkdirs() }
            val file = File(tempDir, cloudBackupName())
            try {
                // Critical fresh-install rule: query Drive FIRST, before metadata
                // generation or any upload. A cloud recovery can never be replaced
                // by an empty new installation.
                setProgress("Checking My Drive/Retra Backups", 3)
                val cloudBackups = driveApi.listBackups(token)
                val hasLocalData = backupRepository.hasMeaningfulUserData()
                if (!hasLocalData) {
                    val message = if (cloudBackups.isNotEmpty()) {
                        "A Retra backup already exists in Google Drive. Restore it before creating a new backup on this fresh installation."
                    } else {
                        "Nothing to back up yet"
                    }
                    recordFailure(message)
                    if (showResult) activity.runOnUiThread { toast(message) }
                    return@execute
                }

                setProgress("Preparing Retra backup", 8)
                backupRepository.create(file, BackupRepository.Selection())
                backupRepository.validate(file)
                val sha256 = driveApi.sha256(file)

                setProgress("Uploading to My Drive/Retra Backups", 12)
                val uploaded = driveApi.uploadBackup(token, file, sha256) { percent ->
                    setProgress("Uploading to My Drive/Retra Backups", 12 + (percent * 86 / 100))
                }
                recordBackupSuccess(if (uploaded.deduplicated) 0 else 1)
                setProgress("Backup complete", 100)
                if (showResult) activity.runOnUiThread {
                    toast(if (uploaded.deduplicated) "Google Drive already has this backup" else "Backup uploaded to My Drive/Retra Backups")
                }
            } catch (auth: GoogleDriveApiRepository.AuthExpiredException) {
                clearExpiredToken(token)
                recordFailure("Google Drive authorization expired. Tap Backup Now to reconnect.")
                if (showResult) activity.runOnUiThread { toast("Google Drive authorization expired • reconnect and try again") }
            } catch (error: Exception) {
                recordFailure("Backup failed: ${friendly(error)}")
                if (showResult) activity.runOnUiThread { toast("Google Drive backup failed: ${friendly(error)}") }
            } finally {
                file.delete()
                finishOperation()
            }
        }
    }

    private fun loadRestoreChoices(token: String, showResult: Boolean) {
        restoreInFlight = true
        ioExecutor.execute {
            try {
                setProgress("Loading Google Drive backups", 5)
                val backups = driveApi.listBackups(token)
                if (backups.isEmpty()) {
                    recordFailure("No Retra backups found in My Drive/Retra Backups")
                    activity.runOnUiThread { toast("No Retra backups found in My Drive/Retra Backups") }
                    finishOperation()
                    return@execute
                }
                activity.runOnUiThread { showRestoreChooser(token, backups, showResult) }
            } catch (auth: GoogleDriveApiRepository.AuthExpiredException) {
                clearExpiredToken(token)
                recordFailure("Google Drive authorization expired. Try Restore again.")
                if (showResult) activity.runOnUiThread { toast("Google Drive authorization expired • try Restore again") }
                finishOperation()
            } catch (error: Exception) {
                recordFailure("Could not list Google Drive backups: ${friendly(error)}")
                if (showResult) activity.runOnUiThread { toast("Could not load Drive backups: ${friendly(error)}") }
                finishOperation()
            }
        }
    }

    private fun showRestoreChooser(token: String, backups: List<GoogleDriveApiRepository.CloudBackup>, showResult: Boolean) {
        val visible = backups.take(MAX_RESTORE_CHOICES)
        val labels = visible.map { backup ->
            val whenText = dateLabel(backup.createdAt.takeIf { it > 0L } ?: backup.modifiedAt)
            "$whenText • ${sizeLabel(backup.size)}"
        }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("Restore from My Drive/Retra Backups")
            .setItems(labels) { _, which -> performRestore(token, visible[which], showResult) }
            .setNegativeButton("Cancel") { _, _ ->
                recordFailure("")
                finishOperation()
            }
            .setOnCancelListener { finishOperation() }
            .show()
    }

    private fun performRestore(token: String, backup: GoogleDriveApiRepository.CloudBackup, showResult: Boolean) {
        ioExecutor.execute {
            val targetDir = File(activity.cacheDir, "drive-restore").apply { mkdirs() }
            val target = File(targetDir, "restore-${System.nanoTime()}.retra")
            try {
                setProgress("Downloading ${backup.name}", 8)
                driveApi.downloadBackup(token, backup, target) { percent ->
                    setProgress("Downloading Google Drive backup", 8 + (percent * 72 / 100))
                }
                setProgress("Validating backup", 82)
                backupRepository.validate(target)
                setProgress("Restoring Retra data", 90)
                backupRepository.restore(target)
                prefs.edit()
                    .putLong(LAST_RESTORE_PREF, System.currentTimeMillis())
                    .putString(LAST_ERROR_PREF, "")
                    .putInt(LAST_DOWNLOADED_PREF, 1)
                    .apply()
                setProgress("Restore complete", 100)
                activity.runOnUiThread {
                    onBackupRestored()
                    if (showResult) toast("Retra restored from Google Drive")
                }
            } catch (auth: GoogleDriveApiRepository.AuthExpiredException) {
                clearExpiredToken(token)
                recordFailure("Google Drive authorization expired during restore")
                if (showResult) activity.runOnUiThread { toast("Google Drive authorization expired • try Restore again") }
            } catch (error: Exception) {
                recordFailure("Restore failed: ${friendly(error)}")
                if (showResult) activity.runOnUiThread { toast("Google Drive restore failed: ${friendly(error)}") }
            } finally {
                target.delete()
                finishOperation()
            }
        }
    }

    private fun clearExpiredToken(token: String) {
        runCatching {
            val request = ClearTokenRequest.builder().setToken(token).build()
            authorizationClient.clearToken(request)
        }
    }

    private fun beginOperation(): Boolean {
        synchronized(this) {
            if (operationInFlight) {
                pendingAutoBackup = pendingAutoBackup || isAutomaticBackupEnabled()
                return false
            }
            operationInFlight = true
            return true
        }
    }

    private fun finishOperation() {
        restoreInFlight = false
        operationInFlight = false
        prefs.edit().putBoolean(TRANSFER_ACTIVE_PREF, false).apply()
        onSettingsChanged()
        if (pendingAutoBackup && isAutomaticBackupEnabled()) {
            pendingAutoBackup = false
            requestAutoSync(urgent = true)
        }
    }

    private fun recordBackupSuccess(uploaded: Int) {
        prefs.edit()
            .putLong(LAST_SUCCESS_PREF, System.currentTimeMillis())
            .putString(LAST_ERROR_PREF, "")
            .putInt(LAST_UPLOADED_PREF, uploaded.coerceAtLeast(0))
            .putInt(LAST_DOWNLOADED_PREF, 0)
            .putInt(LAST_CONFLICTS_PREF, 0)
            .apply()
        onSettingsChanged()
    }

    private fun recordFailure(message: String) {
        prefs.edit().putString(LAST_ERROR_PREF, message.take(320)).apply()
        onSettingsChanged()
    }

    private fun setProgress(label: String, percent: Int) {
        prefs.edit()
            .putBoolean(TRANSFER_ACTIVE_PREF, percent < 100)
            .putString(TRANSFER_LABEL_PREF, label)
            .putInt(TRANSFER_PROGRESS_PREF, percent.coerceIn(0, 100))
            .apply()
        onSettingsChanged()
    }

    private fun cloudBackupName(): String = SimpleDateFormat("'Retra_'yyyyMMdd_HHmmss'.retra'", Locale.US).format(Date())
    private fun dateLabel(timestamp: Long): String = if (timestamp > 0L) {
        SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(timestamp))
    } else "Unknown date"
    private fun sizeLabel(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000L -> String.format(Locale.US, "%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
    private fun friendly(error: Throwable): String = error.message?.take(180)?.ifBlank { null } ?: "unknown error"
    private fun toast(message: String) = RetraNotice.makeText(activity, message, RetraNotice.LENGTH_LONG).show()

    companion object {
        const val ENABLED_PREF = "cloud_sync_enabled_v1"
        const val URI_PREF = "cloud_sync_uri_v1" // legacy only; cloud code never reads it
        const val ACCOUNT_PREF = "cloud_sync_account_v1"
        const val MODE_PREF = "cloud_sync_mode_v2"
        const val MODE_API = "api"
        const val LAST_SUCCESS_PREF = "cloud_sync_last_success_v1"
        const val LAST_RESTORE_PREF = "cloud_sync_last_restore_v1"
        const val LAST_ERROR_PREF = "cloud_sync_last_error_v1"
        const val LAST_UPLOADED_PREF = "cloud_sync_last_uploaded_v1"
        const val LAST_DOWNLOADED_PREF = "cloud_sync_last_downloaded_v1"
        const val LAST_CONFLICTS_PREF = "cloud_sync_last_conflicts_v1"
        const val TRANSFER_ACTIVE_PREF = "cloud_transfer_active_v1"
        const val TRANSFER_LABEL_PREF = "cloud_transfer_label_v1"
        const val TRANSFER_PROGRESS_PREF = "cloud_transfer_progress_v1"
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val AUTO_BACKUP_DEBOUNCE_MS = 1_500L
        private const val MAX_RESTORE_CHOICES = 30
    }
}
