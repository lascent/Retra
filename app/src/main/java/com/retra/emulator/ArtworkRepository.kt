package com.retra.emulator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Low-priority, disk-cached artwork lookup for Retra's ROM library.
 *
 * Design goals:
 * - never block the WebView/main thread;
 * - one network/decode job at a time;
 * - pause/defer while gameplay is active;
 * - exact-title matching only (no guessed art from a similar game);
 * - optimize downloaded images before writing them to persistent storage;
 * - keep retry metadata per permanent romId.
 */
class ArtworkRepository(
    context: Context,
    private val romStore: RomIdentityStore,
    private val isGameplayActive: () -> Boolean,
    private val isEnabled: () -> Boolean,
    private val wifiOnly: () -> Boolean,
    private val onArtworkChanged: (romId: String, state: String) -> Unit
) {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Retra-Artwork").apply { priority = Thread.MIN_PRIORITY }
    }
    private val queued = ConcurrentHashMap.newKeySet<String>()
    private val deferred = ConcurrentHashMap.newKeySet<String>()
    private val manualCovers = ConcurrentHashMap.newKeySet<String>()
    private val manualBackgrounds = ConcurrentHashMap.newKeySet<String>()

    fun queue(romId: String, force: Boolean = false): Boolean {
        val id = romId.trim()
        if (id.isEmpty() || !isEnabled()) return false

        if (isGameplayActive()) {
            deferred += id
            return true
        }

        if (!queued.add(id)) return true
        executor.execute {
            try {
                if (!isEnabled()) return@execute
                if (isGameplayActive()) {
                    deferred += id
                    return@execute
                }
                lookup(id, force)
            } finally {
                queued.remove(id)
            }
        }
        return true
    }

    fun resumeDeferred() {
        if (!isEnabled() || isGameplayActive()) return
        val ids = deferred.toList()
        deferred.clear()
        ids.forEach { queue(it, false) }
    }

    fun beginManual(romId: String, kind: String) {
        if (romId.isBlank()) return
        when (kind.lowercase(Locale.US)) {
            "cover" -> manualCovers += romId
            "background" -> manualBackgrounds += romId
        }
    }

    fun cancelManual(romId: String, kind: String) {
        if (romId.isBlank()) return
        val meta = readMetadata(romId)
        when (kind.lowercase(Locale.US)) {
            "cover" -> if (meta.optString("coverSource") != "manual") manualCovers.remove(romId)
            "background" -> if (meta.optString("backgroundSource") != "manual") manualBackgrounds.remove(romId)
        }
    }

    fun markManual(romId: String, kind: String) {
        if (romId.isBlank()) return
        val meta = readMetadata(romId)
        when (kind.lowercase(Locale.US)) {
            "cover" -> {
                manualCovers += romId
                meta.put("coverSource", "manual")
            }
            "background" -> {
                manualBackgrounds += romId
                meta.put("backgroundSource", "manual")
            }
            else -> return
        }
        meta.put("state", "MANUAL")
        meta.put("updatedAt", System.currentTimeMillis())
        writeMetadata(romId, meta)
    }

    fun clearMetadata(romId: String) {
        metadataDir(romId).deleteRecursively()
        queued.remove(romId)
        deferred.remove(romId)
        manualCovers.remove(romId)
        manualBackgrounds.remove(romId)
    }

    fun close() {
        deferred.clear()
        queued.clear()
        manualCovers.clear()
        manualBackgrounds.clear()
        executor.shutdownNow()
    }

    private fun lookup(romId: String, force: Boolean) {
        val record = romStore.getById(romId) ?: return
        if (record.archived || !record.fileAvailable) return

        val coverFile = mediaFile(romId, "cover")
        val backgroundFile = mediaFile(romId, "background")
        val meta = readMetadata(romId)
        val manualCover = meta.optString("coverSource") == "manual" || manualCovers.contains(romId)
        val manualBackground = meta.optString("backgroundSource") == "manual" || manualBackgrounds.contains(romId)
        if (manualCover) manualCovers += romId
        if (manualBackground) manualBackgrounds += romId

        // Existing media is always authoritative. This protects user-selected
        // images and artwork downloaded by older Retra versions.
        if (coverFile.exists() && coverFile.length() > 0L) {
            if (isGameplayActive()) { deferred += romId; return }
            if (!manualBackground && (!backgroundFile.exists() || backgroundFile.length() <= 0L)) {
                runCatching { deriveBackgroundFromCover(coverFile, backgroundFile) }
                    .onSuccess {
                        meta.put("backgroundSource", "derived")
                        meta.put("updatedAt", System.currentTimeMillis())
                        writeMetadata(romId, meta)
                    }
            }
            onArtworkChanged(romId, "FOUND")
            return
        }
        if (manualCover) return

        val now = System.currentTimeMillis()
        val lastChecked = meta.optLong("checkedAt", 0L)
        val previousState = meta.optString("state", "")
        val previousLookupVersion = meta.optInt("lookupVersion", 0)
        if (
            !force &&
            previousState == "NOT_FOUND" &&
            previousLookupVersion >= ARTWORK_LOOKUP_VERSION &&
            lastChecked > 0L &&
            now - lastChecked < RETRY_INTERVAL_MS
        ) return
        if (!networkAllowed()) return

        val repo = repoFor(record.platform) ?: return
        val candidates = candidateTitles(record)
        if (candidates.isEmpty()) return

        meta.put("state", "SEARCHING")
        meta.put("lookupVersion", ARTWORK_LOOKUP_VERSION)
        meta.put("checkedAt", now)
        meta.put("provider", "libretro-thumbnails")
        writeMetadata(romId, meta)

        for (title in candidates) {
            if (!isEnabled() || isGameplayActive()) {
                deferred += romId
                return
            }
            val bytes = downloadImage(boxartUrl(repo, title), romId)
            if (bytes == null) {
                if (isGameplayActive()) { deferred += romId; return }
                continue
            }
            if (isGameplayActive()) { deferred += romId; return }
            if (manualCovers.contains(romId) || readMetadata(romId).optString("coverSource") == "manual") return
            val bitmap = decodeBounded(bytes, COVER_MAX_WIDTH, COVER_MAX_HEIGHT) ?: continue
            try {
                if (isGameplayActive()) { deferred += romId; return }
                if (manualCovers.contains(romId)) return
                writeWebp(bitmap, coverFile, COVER_QUALITY)
                val backgroundStillManual = manualBackground || manualBackgrounds.contains(romId) ||
                    readMetadata(romId).optString("backgroundSource") == "manual"
                if (!isGameplayActive() && !backgroundStillManual && (!backgroundFile.exists() || backgroundFile.length() <= 0L)) {
                    deriveBackground(bitmap, backgroundFile)
                }
            } finally {
                bitmap.recycle()
            }

            // A manual cover can be chosen while the background worker is
            // finishing. Never let stale automatic metadata win that race.
            if (manualCovers.contains(romId) || readMetadata(romId).optString("coverSource") == "manual") {
                onArtworkChanged(romId, "FOUND")
                return
            }

            meta.put("state", "FOUND")
            meta.put("lookupVersion", ARTWORK_LOOKUP_VERSION)
            meta.put("provider", "libretro-thumbnails")
            meta.put("matchTitle", title)
            meta.put("coverSource", "automatic")
            val backgroundStillManual = manualBackground || manualBackgrounds.contains(romId) ||
                readMetadata(romId).optString("backgroundSource") == "manual"
            if (!backgroundStillManual && backgroundFile.exists() && backgroundFile.length() > 0L) {
                meta.put("backgroundSource", "derived")
            } else if (!backgroundStillManual && isGameplayActive()) {
                deferred += romId
            }
            meta.put("checkedAt", System.currentTimeMillis())
            meta.put("updatedAt", System.currentTimeMillis())
            writeMetadata(romId, meta)
            onArtworkChanged(romId, "FOUND")
            return
        }

        meta.put("state", "NOT_FOUND")
        meta.put("lookupVersion", ARTWORK_LOOKUP_VERSION)
        meta.put("checkedAt", System.currentTimeMillis())
        meta.put("updatedAt", System.currentTimeMillis())
        writeMetadata(romId, meta)
        onArtworkChanged(romId, "NOT_FOUND")
    }

    private fun candidateTitles(record: RomIdentityStore.Record): List<String> {
        val out = LinkedHashSet<String>()

        fun add(value: String?) {
            val clean = value.orEmpty()
                .trim()
                .replace(Regex("\\s+"), " ")
            if (clean.length in 2..120) out += clean
        }

        fun addSeed(value: String?) {
            val raw = normalizeArtworkSeed(value) ?: return
            add(raw)

            // Old GoodTools/scene dumps frequently append a release group after
            // the region, e.g. "(E) (Menace)". The group is not part of the
            // canonical No-Intro/libretro title, so remove it before matching.
            val deScene = stripSceneGroupAfterRegion(raw)
            val region = canonicalRegionFor(deScene)
            val legacyRevision = legacyRevisionFor(deScene)
            val stripped = stripReleaseMetadata(deScene)

            val spellingVariants = canonicalTitleSpellingVariants(stripped)
            spellingVariants.forEach { add(it) }

            val punctuationVariants = LinkedHashSet<String>()
            spellingVariants.forEach { base ->
                punctuationVariants += base
                if (base.contains(":")) punctuationVariants += base.replaceFirst(":", " -")
                if (base.contains(" - ")) punctuationVariants += base.replaceFirst(" - ", ": ")

                // Some scene filenames omit the title/subtitle separator entirely.
                if (!base.contains(" - ") && !base.contains(": ")) {
                    val words = base.split(' ').filter { it.isNotBlank() }
                    if (words.size >= 4) {
                        listOf(2, 1, 3, 4).forEach { split ->
                            if (split in 1 until words.lastIndex) {
                                punctuationVariants += words.take(split).joinToString(" ") +
                                    " - " + words.drop(split).joinToString(" ")
                            }
                        }
                    }
                }
            }

            punctuationVariants.forEach { variant ->
                add(variant)
                if (region != null) add("$variant ($region)")
                addRegionalNoIntroAliases(variant, region, legacyRevision).forEach(::add)
                if (variant.contains("Pokémon")) add(variant.replace("Pokémon", "Pokemon"))
                if (variant.contains("Pokemon")) add(variant.replace("Pokemon", "Pokémon"))
            }

            // ROM hacks are not guaranteed to exist in Libretro's No-Intro art
            // set. If the filename explicitly names its base game, use that base
            // game's box art as a last-resort visual instead of a blank gradient.
            hackBaseArtworkCandidates(raw).forEach(::add)
        }

        // Direct .gba/.gbc/.gb imports use fileName; ZIP/.mgba imports also use
        // the inner playable entry whenever it is available. displayName covers
        // current imports, while archive recovery keeps older Retra libraries
        // working without forcing a re-import.
        addSeed(record.displayName)
        addSeed(record.fileName)
        addSeed(record.fileName.substringBeforeLast('.', record.fileName))
        addSeed(readArchiveRomEntryTitle(record))
        addSeed(readInternalRomTitle(record.launchPath, record.platform))

        // Keep lookup bounded so a missing cover never causes an unbounded run of
        // network requests. Exact/raw and canonical-region names are inserted first.
        return out.take(MAX_TITLE_CANDIDATES)
    }

    private fun normalizeArtworkSeed(value: String?): String? {
        var clean = value.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
        if (clean.length < 2) return null

        // Handle both direct names (game.gba) and archive-style names
        // (game.gba.zip) without making the extension part of the artwork title.
        while (true) {
            val next = clean.replace(
                Regex("(?i)\\.(?:gba|gbc|gb|mgba|zip)$"),
                ""
            ).trim()
            if (next == clean) break
            clean = next
        }

        // Common scene numbering is not part of the canonical game title.
        clean = clean.replace(Regex("^\\s*\\d{1,5}\\s*[-._]\\s*"), "").trim()
        return clean.takeIf { it.length >= 2 }
    }

    private fun canonicalRegionFor(value: String): String? {
        val raw = value.uppercase(Locale.US)
        return when {
            Regex("\\((?:U|USA)(?:[, ]+(?:E|EUROPE))?\\)").containsMatchIn(raw) -> "USA"
            Regex("\\((?:E|EUR|EUROPE)\\)").containsMatchIn(raw) -> "Europe"
            Regex("\\((?:J|JPN|JAPAN)\\)").containsMatchIn(raw) -> "Japan"
            Regex("\\((?:A|AU|AUS|AUSTRALIA)\\)").containsMatchIn(raw) -> "Australia"
            else -> null
        }
    }

    private fun stripSceneGroupAfterRegion(value: String): String {
        val match = Regex("\\s*\\(([^()]*)\\)\\s*$").find(value) ?: return value
        val prefix = value.substring(0, match.range.first).trimEnd()
        if (canonicalRegionFor(prefix) == null) return value

        val tag = match.groupValues[1].trim()
        val knownMetadata = Regex(
            "(?i)^(?:U|USA|E|EUR|EUROPE|J|JPN|JAPAN|A|AU|AUS|AUSTRALIA|WORLD|" +
                "REV(?:ISION)?\\s*\\d+|V?\\d+(?:\\.\\d+){0,4}|" +
                "EN(?:,[A-Z]{2})+|VIRTUAL CONSOLE|BETA|PROTO(?:TYPE)?|DEMO|SAMPLE)$"
        )
        if (knownMetadata.matches(tag)) return value
        if (!Regex("^[A-Za-z0-9._ -]{2,24}$").matches(tag)) return value
        return prefix
    }

    private fun legacyRevisionFor(value: String): Int? {
        val match = Regex("(?i)(?:\\(|\\b)(?:v|ver(?:sion)?\\s*)?(\\d+)\\.(\\d+)(?:\\)|\\b)").find(value)
            ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        return if (major == 1 && minor in 1..9) minor else null
    }

    private fun canonicalTitleSpellingVariants(value: String): List<String> {
        val out = LinkedHashSet<String>()
        fun add(candidate: String) {
            val clean = candidate.trim().replace(Regex("\\s+"), " ")
            if (clean.length >= 2) out += clean
        }

        add(value)
        add(value.replace(Regex("(?i)\\bLeaf Green\\b"), "LeafGreen"))
        add(value.replace(Regex("(?i)\\bFire Red\\b"), "FireRed"))
        add(value.replace(Regex("(?i)\\bBros\\s+(?=\\d)"), "Bros. "))

        out.toList().forEach { current ->
            add(current.replace(Regex("(?i)\\bLeaf Green\\b"), "LeafGreen"))
            add(current.replace(Regex("(?i)\\bFire Red\\b"), "FireRed"))
            add(current.replace(Regex("(?i)\\bBros\\s+(?=\\d)"), "Bros. "))

            // GoodTools Pokémon filenames often omit No-Intro's separator:
            // "Pokemon Leaf Green Version" -> "Pokemon - LeafGreen Version".
            if (!current.contains(" - ")) {
                Regex("(?i)^(Pok[eé]mon)\\s+(.+)$").matchEntire(current)?.let { match ->
                    add("${match.groupValues[1]} - ${match.groupValues[2]}")
                }
            }
        }
        return out.toList()
    }

    private fun addRegionalNoIntroAliases(title: String, region: String?, revision: Int?): List<String> {
        if (region == null) return emptyList()
        val out = LinkedHashSet<String>()
        val normalized = title.lowercase(Locale.US)

        // FireRed/LeafGreen GoodTools dumps use (U) plus v1.0/v1.1 while
        // No-Intro/libretro names use USA/Europe and Rev 1.
        if (region == "USA" &&
            (normalized.contains("leafgreen version") || normalized.contains("firered version"))
        ) {
            out += "$title (USA)"
            out += "$title (USA, Europe)"
            if (revision != null) out += "$title (USA, Europe) (Rev $revision)"
        }

        // The common European scene dump "Super Mario Bros 3 (E)(Menace)"
        // maps to the No-Intro multilingual Europe title.
        if (region == "Europe" && normalized.contains("super mario advance 4") && normalized.contains("super mario bros")) {
            out += "$title (Europe)"
            out += "$title (Europe) (En,Fr,De,Es,It)"
            out += "$title (Europe) (En,Fr,De,Es,It) (Rev 1)"
        }
        return out.toList()
    }

    private fun hackBaseArtworkCandidates(value: String): List<String> {
        val normalized = value.lowercase(Locale.US)
        if (!normalized.contains("hack")) return emptyList()
        return when {
            Regex("fire\\s*red").containsMatchIn(normalized) -> listOf(
                "Pokemon - FireRed Version (USA)",
                "Pokemon - FireRed Version (USA, Europe) (Rev 1)"
            )
            Regex("leaf\\s*green").containsMatchIn(normalized) -> listOf(
                "Pokemon - LeafGreen Version (USA)",
                "Pokemon - LeafGreen Version (USA, Europe) (Rev 1)"
            )
            normalized.contains("emerald") -> listOf("Pokemon - Emerald Version (USA, Europe)")
            normalized.contains("ruby") -> listOf("Pokemon - Ruby Version (USA, Europe)")
            normalized.contains("sapphire") -> listOf("Pokemon - Sapphire Version (USA, Europe)")
            else -> emptyList()
        }
    }

    private fun stripReleaseMetadata(value: String): String {
        var clean = value.trim()
        val squareTag = Regex("\\s*\\[[^\\]]+\\]\\s*$", RegexOption.IGNORE_CASE)
        val knownParenTag = Regex(
            "\\s*\\((?:" +
                "U|USA|E|EUR|EUROPE|J|JPN|JAPAN|A|AU|AUS|AUSTRALIA|WORLD|" +
                "USA\\s*,\\s*EUROPE|EUROPE\\s*,\\s*USA|" +
                "[A-Z]{2}(?:\\s*,\\s*[A-Z]{2})+|" +
                "REV(?:ISION)?[^)]*|BETA[^)]*|PROTO(?:TYPE)?[^)]*|DEMO[^)]*|" +
                "SAMPLE[^)]*|UNL|UNLICENSED|PIRATE|VIRTUAL CONSOLE|" +
                "V?\\d+(?:\\.\\d+){0,4}[^)]*" +
            ")\\)\\s*$",
            RegexOption.IGNORE_CASE
        )

        // Remove trailing dump/release tags repeatedly, because GoodTools names
        // commonly end in combinations such as "(U) [!]".
        repeat(8) {
            val before = clean
            clean = clean.replace(squareTag, "").trim()
            clean = clean.replace(knownParenTag, "").trim()
            if (clean == before) return@repeat
        }

        clean = clean
            .replace(Regex("\\s+(?:v|version\\s*)\\d+(?:\\.\\d+){0,4}(?:[-_a-z0-9.]*)?\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(?:final|patched|patch)\\s*$", RegexOption.IGNORE_CASE), "")
            .trim()
            .replace(Regex("\\s+"), " ")
        return clean
    }

    private fun readArchiveRomEntryTitle(record: RomIdentityStore.Record): String? {
        val storedExtension = record.fileName.substringAfterLast('.', "").lowercase(Locale.US)
        if (storedExtension != "zip" && storedExtension != "mgba") return null

        val source = record.currentFileUri?.takeIf { it.isNotBlank() }
            ?: record.sourceUri?.takeIf { it.isNotBlank() }
            ?: return null

        return runCatching {
            val uri = Uri.parse(source)
            val input = appContext.contentResolver.openInputStream(uri) ?: return@runCatching null
            var title: String? = null
            input.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    var inspected = 0
                    while (inspected < MAX_ARCHIVE_TITLE_ENTRIES) {
                        val entry = zip.nextEntry ?: break
                        inspected++
                        if (!entry.isDirectory) {
                            val entryName = entry.name
                                .substringAfterLast('/')
                                .substringAfterLast('\\')
                                .trim()
                            val ext = entryName.substringAfterLast('.', "").lowercase(Locale.US)
                            if (ext in ARCHIVE_PLAYABLE_EXTENSIONS) {
                                title = entryName.substringBeforeLast('.').trim().takeIf { it.length >= 2 }
                                zip.closeEntry()
                                break
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
            title
        }.getOrNull()
    }

    private fun readInternalRomTitle(path: String?, platform: String): String? {
        val file = path?.let(::File)?.takeIf { it.exists() && it.isFile } ?: return null
        return runCatching {
            val bytes = ByteArray(0x150)
            file.inputStream().use { input ->
                val read = input.read(bytes)
                if (read < 0x144) return@runCatching null
            }
            val upper = platform.uppercase(Locale.US)
            val range = if (upper == "GBA" || upper == "MGBA") 0xA0 until 0xAC else 0x134 until 0x144
            bytes.copyOfRange(range.first, range.last + 1)
                .takeWhile { it.toInt() != 0 && it.toInt() != 0xFF }
                .toByteArray()
                .toString(Charsets.US_ASCII)
                .trim()
                .takeIf { it.length >= 2 }
        }.getOrNull()
    }

    private fun repoFor(platform: String): String? = when (platform.uppercase(Locale.US)) {
        "GBA", "MGBA" -> "Nintendo_-_Game_Boy_Advance"
        "GBC" -> "Nintendo_-_Game_Boy_Color"
        "GB" -> "Nintendo_-_Game_Boy"
        else -> null
    }

    private fun boxartUrl(repo: String, title: String): String {
        val safeName = title.replace(Regex("[&*/:\"<>?\\\\|]"), "_")
        val encoded = Uri.encode(safeName)
        return "https://raw.githubusercontent.com/libretro-thumbnails/$repo/master/Named_Boxarts/$encoded.png"
    }

    private fun networkAllowed(): Boolean {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        if (wifiOnly()) {
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
        return true
    }

    private fun downloadImage(url: String, romId: String): ByteArray? {
        val connection = (URL(url).openConnection() as? HttpURLConnection) ?: return null
        return try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Retra/$USER_AGENT_VERSION Android")
            connection.setRequestProperty("Accept", "image/png,image/jpeg,image/webp,*/*;q=0.5")
            connection.connect()
            if (connection.responseCode !in 200..299) return null
            val declared = connection.contentLengthLong
            if (declared > MAX_DOWNLOAD_BYTES) return null
            val type = connection.contentType.orEmpty().lowercase(Locale.US)
            if (type.isNotBlank() && !type.startsWith("image/")) return null

            val output = ByteArrayOutputStream(if (declared > 0L && declared <= MAX_DOWNLOAD_BYTES) declared.toInt() else 128 * 1024)
            connection.inputStream.use { input ->
                val buffer = ByteArray(32 * 1024)
                var total = 0L
                while (true) {
                    if (isGameplayActive()) {
                        deferred += romId
                        return null
                    }
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_DOWNLOAD_BYTES) return null
                    output.write(buffer, 0, read)
                }
            }
            output.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }


    private fun decodeBounded(bytes: ByteArray, maxWidth: Int, maxHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        if (bounds.outWidth.toLong() * bounds.outHeight.toLong() > MAX_SOURCE_PIXELS) return null

        val scale = max(bounds.outWidth.toDouble() / maxWidth, bounds.outHeight.toDouble() / maxHeight)
        var sample = 1
        if (scale > 1.0) {
            val target = ceil(scale).toInt()
            while (sample * 2 <= target) sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val finalScale = minOf(maxWidth.toFloat() / decoded.width, maxHeight.toFloat() / decoded.height, 1f)
        if (finalScale >= 0.999f) return decoded
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * finalScale).roundToInt().coerceAtLeast(1),
            (decoded.height * finalScale).roundToInt().coerceAtLeast(1),
            true
        )
        decoded.recycle()
        return scaled
    }

    private fun deriveBackgroundFromCover(coverFile: File, target: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(coverFile.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return
        var sample = 1
        while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2
        val bitmap = BitmapFactory.decodeFile(
            coverFile.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        ) ?: return
        try { deriveBackground(bitmap, target) } finally { bitmap.recycle() }
    }

    private fun deriveBackground(source: Bitmap, target: File) {
        val out = Bitmap.createBitmap(BACKGROUND_WIDTH, BACKGROUND_HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(out)
            val palette = fallbackColors(target.parentFile?.name.orEmpty())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, BACKGROUND_WIDTH.toFloat(), BACKGROUND_HEIGHT.toFloat(),
                    palette.first, palette.second, Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, BACKGROUND_WIDTH.toFloat(), BACKGROUND_HEIGHT.toFloat(), paint)

            // Draw the same exact matched cover as a dimmed center-crop. This
            // avoids a second network request while still giving each ROM a
            // game-specific hero background.
            val cropScale = max(BACKGROUND_WIDTH.toFloat() / source.width, BACKGROUND_HEIGHT.toFloat() / source.height)
            val drawW = source.width * cropScale
            val drawH = source.height * cropScale
            val left = (BACKGROUND_WIDTH - drawW) / 2f
            val top = (BACKGROUND_HEIGHT - drawH) / 2f
            val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 115 }
            canvas.drawBitmap(source, null, RectF(left, top, left + drawW, top + drawH), imagePaint)
            canvas.drawColor(0x42000000)
            writeWebp(out, target, BACKGROUND_QUALITY)
        } finally {
            out.recycle()
        }
    }

    private fun writeWebp(bitmap: Bitmap, target: File, quality: Int) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temp).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.WEBP, quality, output)) {
                    throw IOException("Could not encode artwork")
                }
                output.flush()
                runCatching { output.fd.sync() }
            }
            if (temp.length() <= 0L) throw IOException("Empty artwork")
            if (target.exists() && !target.delete()) throw IOException("Could not replace artwork")
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } finally {
            temp.delete()
        }
    }

    private fun fallbackColors(seed: String): Pair<Int, Int> {
        val hash = seed.fold(0x45D9F3B) { acc, c -> (acc * 31) xor c.code }
        val hue = (hash and 0x7fffffff) % 360
        val first = android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.52f, 0.55f))
        val second = android.graphics.Color.HSVToColor(floatArrayOf(((hue + 34) % 360).toFloat(), 0.66f, 0.24f))
        return first to second
    }

    private fun mediaFile(romId: String, kind: String): File {
        val category = if (kind == "background") "Backgrounds" else "Covers"
        val name = if (kind == "background") "background.webp" else "cover.webp"
        return File(File(File(appContext.filesDir, "persistent_data/$category"), safeId(romId)).apply { mkdirs() }, name)
    }

    private fun metadataDir(romId: String): File =
        File(File(appContext.filesDir, "persistent_data/Metadata"), safeId(romId)).apply { mkdirs() }

    private fun metadataFile(romId: String): File = File(metadataDir(romId), "artwork.json")

    private fun readMetadata(romId: String): JSONObject {
        val file = metadataFile(romId)
        if (!file.exists() || file.length() <= 0L) return JSONObject()
        return runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }
    }

    private fun writeMetadata(romId: String, json: JSONObject) {
        val target = metadataFile(romId)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        runCatching {
            temp.writeText(json.toString(2))
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) temp.copyTo(target, overwrite = true)
        }
        temp.delete()
    }

    private fun safeId(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .ifBlank { "rom" }

    companion object {
        private val ARCHIVE_PLAYABLE_EXTENSIONS = setOf("gba", "gbc", "gb", "mgba")
        private const val MAX_ARCHIVE_TITLE_ENTRIES = 512
        private const val ARTWORK_LOOKUP_VERSION = 4
        private const val USER_AGENT_VERSION = "4.33"
        private const val RETRY_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L
        private const val CONNECT_TIMEOUT_MS = 2500
        private const val READ_TIMEOUT_MS = 5000
        private const val MAX_DOWNLOAD_BYTES = 6L * 1024L * 1024L
        private const val MAX_SOURCE_PIXELS = 24L * 1024L * 1024L
        private const val MAX_TITLE_CANDIDATES = 48
        private const val COVER_MAX_WIDTH = 640
        private const val COVER_MAX_HEIGHT = 960
        private const val COVER_QUALITY = 86
        private const val BACKGROUND_WIDTH = 960
        private const val BACKGROUND_HEIGHT = 540
        private const val BACKGROUND_QUALITY = 74
    }
}
