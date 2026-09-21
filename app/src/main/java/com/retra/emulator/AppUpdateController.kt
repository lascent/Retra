package com.retra.emulator

import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight GitHub Releases update checker for Retra's sideload/GitHub builds.
 *
 * Checks always run away from the UI/emulation thread. Retra performs one
 * automatic check per fresh app process so a newly published release is
 * discovered the next time the user opens the app. Manual checks always bypass
 * the per-process guard. Installation remains user-confirmed: Retra opens the
 * official GitHub APK/release URL and Android owns the install flow.
 */
class AppUpdateController(
    private val activity: MainActivity,
    private val networkExecutor: ExecutorService,
    private val onResult: (payloadJson: String, manual: Boolean) -> Unit
) {
    private val inFlight = AtomicBoolean(false)

    fun versionInfoJson(): String = JSONObject().apply {
        val installed = installedVersion()
        put("versionName", installed.name)
        put("versionCode", installed.code)
    }.toString()

    fun checkForUpdates(manual: Boolean) {
        // Automatic checks are once per fresh app process, not once per persisted
        // time window. This prevents the old failure mode where a successful
        // check on an older release could suppress discovery of a newly published update for several hours.
        // The guard resets naturally when Android starts a new Retra process.
        if (!manual && !automaticCheckStartedThisProcess.compareAndSet(false, true)) return

        if (!inFlight.compareAndSet(false, true)) {
            if (manual) {
                onResult(JSONObject().apply {
                    put("status", "checking")
                    put("message", "Already checking for updates")
                }.toString(), true)
            }
            return
        }

        networkExecutor.execute {
            try {
                val installed = installedVersion()
                val release = fetchLatestRelease(installed.name)

                val newer = compareVersions(release.versionName, installed.name) > 0
                val payload = JSONObject().apply {
                    put("status", if (newer) "update_available" else "up_to_date")
                    put("currentVersionName", installed.name)
                    put("currentVersionCode", installed.code)
                    put("latestVersionName", release.versionName)
                    put("latestTag", release.tag)
                    put("releaseUrl", release.releaseUrl)
                    put("downloadUrl", release.downloadUrl)
                    put("publishedAt", release.publishedAt)
                    put("notes", release.notes)
                }
                onResult(payload.toString(), manual)
            } catch (error: Throwable) {
                val payload = JSONObject().apply {
                    put("status", "error")
                    put("message", friendlyError(error))
                }
                onResult(payload.toString(), manual)
            } finally {
                inFlight.set(false)
            }
        }
    }

    fun openOfficialUpdateUrl(rawUrl: String): Boolean {
        val uri = runCatching { Uri.parse(rawUrl.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase(Locale.US) ?: return false
        if (host != "github.com" && !host.endsWith(".github.com")) return false

        activity.runOnUiThread {
            runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                .onFailure {
                    RetraNotice.makeText(
                        activity,
                        "Could not open the Retra update page",
                        RetraNotice.LENGTH_SHORT
                    ).show()
                }
        }
        return true
    }

    private fun fetchLatestRelease(currentVersionName: String): ReleaseInfo {
        val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Retra/$currentVersionName Android")
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("GitHub returned HTTP $responseCode")
            }

            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val builder = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    val count = reader.read(buffer)
                    if (count <= 0) break
                    builder.append(buffer, 0, count)
                    if (builder.length > MAX_RESPONSE_CHARS) {
                        throw IOException("Update response was unexpectedly large")
                    }
                }
                builder.toString()
            }

            val json = JSONObject(text)
            val tag = json.optString("tag_name").trim()
            if (tag.isBlank()) throw IOException("Latest release has no version tag")

            val versionName = normalizeVersionName(tag)
            if (!looksLikeVersion(versionName)) throw IOException("Latest release version is invalid")

            val releaseUrl = json.optString("html_url").takeIf(::isOfficialGitHubUrl)
                ?: OFFICIAL_RELEASES_URL
            var apkUrl = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val name = asset.optString("name").lowercase(Locale.US)
                    val url = asset.optString("browser_download_url")
                    if (name.endsWith(".apk") && isOfficialGitHubUrl(url)) {
                        apkUrl = url
                        break
                    }
                }
            }

            return ReleaseInfo(
                tag = tag,
                versionName = versionName,
                releaseUrl = releaseUrl,
                downloadUrl = apkUrl.ifBlank { releaseUrl },
                publishedAt = json.optString("published_at"),
                notes = sanitizeReleaseNotes(json.optString("body"))
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun installedVersion(): InstalledVersion {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return InstalledVersion(info.versionName ?: "0.0.0", code)
    }

    private fun sanitizeReleaseNotes(raw: String): String {
        if (raw.isBlank()) return "See the GitHub release page for details."
        return raw
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()
            .take(MAX_RELEASE_NOTES_CHARS)
    }

    private fun friendlyError(error: Throwable): String = when (error) {
        is java.net.SocketTimeoutException -> "Update check timed out"
        is java.net.UnknownHostException -> "No internet connection"
        else -> "Could not check for updates"
    }

    private data class InstalledVersion(val name: String, val code: Long)

    private data class ReleaseInfo(
        val tag: String,
        val versionName: String,
        val releaseUrl: String,
        val downloadUrl: String,
        val publishedAt: String,
        val notes: String
    )

    companion object {
        private const val LATEST_RELEASE_API = "https://api.github.com/repos/lascent/Retra/releases/latest"
        private const val OFFICIAL_RELEASES_URL = "https://github.com/lascent/Retra/releases/latest"
        private val automaticCheckStartedThisProcess = AtomicBoolean(false)
        private const val CONNECT_TIMEOUT_MS = 6000
        private const val READ_TIMEOUT_MS = 7000
        private const val MAX_RESPONSE_CHARS = 512 * 1024
        private const val MAX_RELEASE_NOTES_CHARS = 1200

        internal fun normalizeVersionName(raw: String): String =
            raw.trim().removePrefix("v").removePrefix("V").substringBefore('+')

        internal fun looksLikeVersion(value: String): Boolean =
            VERSION_REGEX.matches(value)

        internal fun compareVersions(remoteRaw: String, localRaw: String): Int {
            val remote = parseVersion(remoteRaw)
            val local = parseVersion(localRaw)
            val length = maxOf(remote.numbers.size, local.numbers.size)
            for (index in 0 until length) {
                val remotePart = remote.numbers.getOrElse(index) { 0 }
                val localPart = local.numbers.getOrElse(index) { 0 }
                if (remotePart != localPart) return remotePart.compareTo(localPart)
            }

            // A stable release is newer than a prerelease with the same numbers.
            if (remote.preRelease.isBlank() && local.preRelease.isNotBlank()) return 1
            if (remote.preRelease.isNotBlank() && local.preRelease.isBlank()) return -1
            return remote.preRelease.compareTo(local.preRelease)
        }

        private fun parseVersion(raw: String): ParsedVersion {
            val normalized = normalizeVersionName(raw)
            val core = normalized.substringBefore('-')
            val prerelease = normalized.substringAfter('-', "")
            val numbers = core.split('.').mapNotNull { it.toIntOrNull() }
            return ParsedVersion(if (numbers.isEmpty()) listOf(0) else numbers, prerelease)
        }

        private fun isOfficialGitHubUrl(raw: String): Boolean {
            val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return false
            if (!uri.scheme.equals("https", ignoreCase = true)) return false
            val host = uri.host?.lowercase(Locale.US) ?: return false
            return host == "github.com" || host.endsWith(".github.com")
        }

        private data class ParsedVersion(val numbers: List<Int>, val preRelease: String)
        private val VERSION_REGEX = Regex("^\\d+(?:\\.\\d+){1,3}(?:-[0-9A-Za-z.-]+)?$")
    }
}
