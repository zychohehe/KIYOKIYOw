package com.kiyo.music.core.updater

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.kiyo.music.BuildConfig
import com.kiyo.music.MainActivity
import com.kiyo.music.R
import com.kiyo.music.ui.screens.update.ActionOpenUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

const val GithubOwner = "zychohehe"
const val GithubRepo = "Kiyo-Music"

const val GithubProfileUrl = "https://github.com/$GithubOwner"
const val GithubRepoUrl = "https://github.com/$GithubOwner/$GithubRepo"
// community channel (replaces the old Discord server)
const val TikTokHandle = "a2unknowwn"
const val TikTokUrl = "https://www.tiktok.com/@$TikTokHandle"
const val WhatsAppUrl = "https://whatsapp.com/channel/0029Vb8gHJ7545uuF9Ox1I2z"

private const val ReleasesApi =
    "https://api.github.com/repos/$GithubOwner/$GithubRepo/releases?per_page=50"

// the newest non-prerelease, asked for on its own: with a nightly on every push, the list above can fill up with
// nightlies and push the latest stable out of view, and the Release channel would then see nothing
private const val LatestReleaseApi =
    "https://api.github.com/repos/$GithubOwner/$GithubRepo/releases/latest"

data class GithubRelease(
    val title: String,
    val tag: String,
    val version: String,
    val body: String,
    val htmlUrl: String,
    val apkUrl: String?,
    val apkName: String?,
    val apkSizeBytes: Long,
    val publishedAt: String,
    // marked as a prerelease on github, which is what makes it a nightly
    val isNightly: Boolean = false,
    // the sha256sums.txt attached to the release, used to check the download before it is installed
    val sha256Url: String? = null,
)

sealed interface UpdateState {
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: GithubRelease) : UpdateState
    data class Failed(val message: String) : UpdateState
}

// a release is x.y.z, a nightly adds a fourth number counting up from the release it was built on (8.3.2.1, 8.3.2.2, then 8.3.3)
data class FullVersion(
    val major: Int = 0,
    val minor: Int = 0,
    val patch: Int = 0,
    val buildNumber: Int = 0,
    val nightly: Int = 0,
) : Comparable<FullVersion> {
    override fun compareTo(other: FullVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        if (nightly != other.nightly) return nightly.compareTo(other.nightly)
        return buildNumber.compareTo(other.buildNumber)
    }

    // what people read: 8.3.2, 8.3.2.4 for a nightly
    val name: String get() = if (nightly > 0) "$major.$minor.$patch.$nightly" else "$major.$minor.$patch"
}

private val VersionRegex = Regex("""(\d+)\.(\d+)(?:\.(\d+))?(?:\.(\d+))?""")
private val BuildNumberRegex = Regex("""(?:build#|\(#|#|\()(\d+)\)?""", RegexOption.IGNORE_CASE)

fun parseFullVersion(text: String): FullVersion {
    val version = VersionRegex.find(text)?.groupValues
    val buildNumber = BuildNumberRegex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    return FullVersion(
        major = version?.get(1)?.toIntOrNull() ?: 0,
        minor = version?.get(2)?.toIntOrNull() ?: 0,
        patch = version?.getOrNull(3)?.toIntOrNull() ?: 0,
        buildNumber = buildNumber,
        nightly = version?.getOrNull(4)?.toIntOrNull() ?: 0,
    )
}

private const val CacheTtlMillis = 15L * 60L * 1000L

// the first Kiyo release; anything below it is not this app. (This used to be 8.2.3, the number Mochy was on, which
// is higher than every Kiyo version 5.x, so the filter below threw out every release and no update was ever offered)
private val FirstReleaseWithThisId = FullVersion(major = 5, minor = 0, patch = 0)

/** A release with the version it was parsed to, so it can be compared without parsing again. */
private class ParsedRelease(val version: FullVersion, val release: GithubRelease)

private val cacheLock = Any()
private var cachedAt = 0L
private var cachedResult: Result<List<ParsedRelease>>? = null
private var inFlight: kotlinx.coroutines.Deferred<Result<List<ParsedRelease>>>? = null

private val updateScope = kotlinx.coroutines.CoroutineScope(
    kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
)

/** Every published release, asked for once and shared for [CacheTtlMillis] unless [force]d. */
private suspend fun getReleases(force: Boolean): Result<List<ParsedRelease>> {
    val deferred = synchronized(cacheLock) {
        if (!force) {
            val cached = cachedResult
            if (cached != null && System.currentTimeMillis() - cachedAt < CacheTtlMillis) {
                return cached
            }
        }
        inFlight ?: updateScope.async { fetchReleases() }.also { inFlight = it }
    }

    val result = deferred.await()
    synchronized(cacheLock) {
        if (inFlight === deferred) inFlight = null
        if (result.isSuccess) {
            cachedResult = result
            cachedAt = System.currentTimeMillis()
        }
    }
    return result
}

// release only sees releases; nightly sees the prereleases and the releases, and takes the newest of all
private fun ParsedRelease.inChannel(channel: UpdateChannel) = channel == UpdateChannel.Nightly || !release.isNightly

/** The newest release on [channel], whatever is installed. */
suspend fun getLatestRelease(channel: UpdateChannel, force: Boolean = false): Result<GithubRelease?> =
    getReleases(force).map { list -> list.filter { it.inChannel(channel) }.maxByOrNull { it.version }?.release }

/**
 * What "Downgrade to" offers, null when there's none: on release, the newest release before the
 * installed x.y.z (an older build of the same version doesn't count); on nightly, the nightly before
 * this one. A nightly never steps down to a release, that can break the library.
 */
suspend fun getPreviousRelease(channel: UpdateChannel, force: Boolean = false): Result<GithubRelease?> {
    val installed = parseFullVersion(BuildConfig.VERSION_NAME).copy(buildNumber = 0)
    return getReleases(force).map { list ->
        when {
            channel == UpdateChannel.Release && installedIsNightly -> null
            channel == UpdateChannel.Release -> list.filter { !it.release.isNightly && it.version.copy(buildNumber = 0) < installed }
            !installedIsNightly -> null
            else -> list.filter { it.release.isNightly && it.version.copy(buildNumber = 0) < installed }
        }?.maxByOrNull { it.version }?.release
    }
}

private fun githubGet(url: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 12_000
        readTimeout = 12_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "kiyo-android")
    }
    return try {
        val code = connection.responseCode
        if (code == 403 || code == 429) error("GitHub is limiting requests right now, please try again in a while")
        if (code !in 200..299) error("GitHub answered HTTP $code")
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private suspend fun fetchReleases(): Result<List<ParsedRelease>> = withContext(Dispatchers.IO) {
    runCatching {
        val releases = JSONArray(githubGet(ReleasesApi))
        // the newest stable on its own; if that one call fails the list still works
        runCatching { JSONObject(githubGet(LatestReleaseApi)) }.getOrNull()?.let { latest ->
            val seen = (0 until releases.length()).any { releases.getJSONObject(it).optString("tag_name") == latest.optString("tag_name") }
            if (!seen) releases.put(latest)
        }
        val appArch = runCatching { BuildConfig.ARCHITECTURE }.getOrDefault("universal")

        buildList {
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                if (release.optBoolean("draft", false)) continue

                val tag = release.optString("tag_name").orEmpty()
                val title = release.optString("name").orEmpty().ifBlank { tag }
                val fullVer = parseFullVersion("$title $tag")

                if (fullVer.major == 0 && fullVer.minor == 0 && fullVer.patch == 0) continue
                if (fullVer.copy(buildNumber = 0) < FirstReleaseWithThisId) continue

                val displayVersion = if (fullVer.buildNumber > 0) "${fullVer.name} (${fullVer.buildNumber})" else fullVer.name

                val assets = release.optJSONArray("assets")
                var selectedUrl: String? = null
                var selectedName: String? = null
                var selectedSize = 0L

                var universalUrl: String? = null
                var universalName: String? = null
                var universalSize = 0L

                var fallbackUrl: String? = null
                var fallbackName: String? = null
                var fallbackSize = 0L
                var shaUrl: String? = null

                if (assets != null) {
                    for (a in 0 until assets.length()) {
                        val asset = assets.getJSONObject(a)
                        val name = asset.optString("name").orEmpty()
                        if (name.equals("sha256sums.txt", ignoreCase = true)) {
                            shaUrl = asset.optString("browser_download_url").orEmpty().ifBlank { null }
                            continue
                        }
                        if (!name.endsWith(".apk", ignoreCase = true)) continue
                        val url = asset.optString("browser_download_url").orEmpty()
                        val size = asset.optLong("size", 0L)

                        if (appArch.isNotBlank() && appArch != "universal" && name.contains(appArch, ignoreCase = true)) {
                            selectedUrl = url
                            selectedName = name
                            selectedSize = size
                        } else if (name.contains("universal", ignoreCase = true)) {
                            universalUrl = url
                            universalName = name
                            universalSize = size
                        } else if (fallbackUrl == null) {
                            fallbackUrl = url
                            fallbackName = name
                            fallbackSize = size
                        }
                    }
                }

                val apkUrl = selectedUrl ?: universalUrl ?: fallbackUrl
                val apkName = selectedName ?: universalName ?: fallbackName
                val apkSize = when {
                    selectedUrl != null -> selectedSize
                    universalUrl != null -> universalSize
                    else -> fallbackSize
                }
                // a release with no APK yet (still uploading, or a notes-only release) is not an update, so it isn't announced
                if (apkUrl == null) continue

                add(
                    ParsedRelease(
                        version = fullVer,
                        release = GithubRelease(
                            title = title,
                            tag = tag,
                            version = displayVersion,
                            body = release.optString("body").orEmpty(),
                            htmlUrl = release.optString("html_url").orEmpty().ifBlank { "$GithubRepoUrl/releases" },
                            apkUrl = apkUrl,
                            apkName = apkName,
                            apkSizeBytes = apkSize,
                            publishedAt = release.optString("published_at").orEmpty(),
                            isNightly = release.optBoolean("prerelease", false),
                            sha256Url = shaUrl,
                        ),
                    ),
                )
            }
        }
    }
}

fun isNewerThanInstalled(candidateVersionText: String): Boolean {
    val candidate = parseFullVersion(candidateVersionText)
    // the version name no longer carries the build counter, so it comes from BuildConfig
    val installed = parseFullVersion(BuildConfig.VERSION_NAME).copy(buildNumber = BuildConfig.BUILD_NUMBER)
    return candidate > installed
}

// the download runs in the app's own scope, so closing the sheet (or leaving the screen) doesn't cancel it; a second
// request for the same file just joins the one that is running, and a cut connection continues from the .part file
private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val downloadLock = Any()
private var activeDownload: Pair<String, Deferred<Result<File>>>? = null
private val downloadProgress = MutableStateFlow(0f)
private val downloadWatchers = AtomicInteger(0)

private const val UpdatesChannelId = "updates"
private const val DownloadNotificationId = 1002

suspend fun downloadApk(
    context: Context,
    release: GithubRelease,
    onProgress: (Float) -> Unit,
): Result<File> {
    val app = context.applicationContext
    val key = release.apkUrl ?: return Result.failure(IllegalStateException("This release has no APK attached"))
    if (isDownloaded(app, release)) {
        onProgress(1f)
        return Result.success(apkFileFor(app, release))
    }
    val deferred = synchronized(downloadLock) {
        val running = activeDownload
        if (running != null && running.first == key && running.second.isActive) {
            running.second
        } else {
            downloadProgress.value = 0f
            downloadScope.async { runDownload(app, release) }.also { activeDownload = key to it }
        }
    }
    downloadWatchers.incrementAndGet()
    return try {
        coroutineScope {
            val watcher = launch { downloadProgress.collect { onProgress(it) } }
            try {
                deferred.await()
            } finally {
                watcher.cancel()
            }
        }
    } finally {
        downloadWatchers.decrementAndGet()
    }
}

private fun runDownload(context: Context, release: GithubRelease): Result<File> = runCatching {
    val url = release.apkUrl ?: error("This release has no APK attached")
    val target = apkFileFor(context, release)
    val part = File(target.path + ".part")
    if (target.exists()) target.delete()

    val expected = release.apkSizeBytes.takeIf { it > 0 } ?: -1L
    var written = if (part.exists()) part.length() else 0L
    if (expected > 0 && written > expected) {
        part.delete()
        written = 0L
    }

    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "kiyo-android")
        if (written > 0) setRequestProperty("Range", "bytes=$written-")
    }
    val code = connection.responseCode
    val resuming = written > 0 && code == HttpURLConnection.HTTP_PARTIAL
    if (code != HttpURLConnection.HTTP_OK && !resuming) {
        if (code == 416) part.delete()
        error("Download failed (HTTP $code), please try again")
    }
    if (!resuming) written = 0L

    val total = if (expected > 0) expected else connection.contentLengthLong.takeIf { it > 0 }?.plus(written) ?: -1L
    var lastPercent = -1
    connection.inputStream.use { input ->
        java.io.FileOutputStream(part, resuming).use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                written += read
                if (total > 0) {
                    val p = (written.toFloat() / total).coerceIn(0f, 1f)
                    downloadProgress.value = p
                    val percent = (p * 100).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        showDownloadProgress(context, release, percent)
                    }
                }
            }
        }
    }
    // a short file keeps its .part, the next try picks up from there
    if (total > 0 && part.length() != total) error("The download was cut short, please try again")
    verifyChecksum(release, part)
    if (target.exists()) target.delete()
    if (!part.renameTo(target)) error("Couldn't save the update file")
    downloadProgress.value = 1f
    finishDownloadNotification(context, release, ready = true)
    target
}.onFailure { finishDownloadNotification(context, release, ready = false) }

/** Compares the file with the release's sha256sums.txt. No list, or no line for this file, means nothing to compare and it passes. */
private fun verifyChecksum(release: GithubRelease, file: File) {
    val sumsUrl = release.sha256Url ?: return
    val name = release.apkName ?: return
    val expected = runCatching {
        val connection = (URL(sumsUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "kiyo-android")
        }
        connection.inputStream.bufferedReader().use { it.readLines() }.firstNotNullOfOrNull { line ->
            val parts = line.trim().split(Regex("\\s+"), 2)
            if (parts.size == 2 && parts[1].trim().trimStart('*') == name) parts[0].lowercase() else null
        }
    }.getOrNull() ?: return

    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    val actual = digest.digest().joinToString("") { "%02x".format(it) }
    if (actual != expected) {
        file.delete()
        error("The downloaded file didn't match its checksum, so it was not installed. Please try again.")
    }
}

private fun canNotify(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun showDownloadProgress(context: Context, release: GithubRelease, percent: Int) {
    if (!canNotify(context)) return
    val notification = NotificationCompat.Builder(context, UpdatesChannelId)
        .setSmallIcon(R.drawable.kiyo_notification)
        .setContentTitle(context.getString(R.string.update_downloading_title, release.version))
        .setContentText("$percent%")
        .setProgress(100, percent, false)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .build()
    runCatching { NotificationManagerCompat.from(context).notify(DownloadNotificationId, notification) }
}

/** Clears the progress notification; when nobody is watching the download, a finished one becomes a "tap to install" notification. */
private fun finishDownloadNotification(context: Context, release: GithubRelease, ready: Boolean) {
    runCatching { NotificationManagerCompat.from(context).cancel(DownloadNotificationId) }
    if (!ready || downloadWatchers.get() > 0 || !canNotify(context)) return
    val intent = Intent(context, MainActivity::class.java)
        .setAction(ActionOpenUpdate)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val pending = PendingIntent.getActivity(
        context,
        DownloadNotificationId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, UpdatesChannelId)
        .setSmallIcon(R.drawable.kiyo_notification)
        .setContentTitle(context.getString(R.string.update_ready_title, release.version))
        .setContentText(context.getString(R.string.update_ready_text))
        .setContentIntent(pending)
        .setAutoCancel(true)
        .build()
    runCatching { NotificationManagerCompat.from(context).notify(DownloadNotificationId, notification) }
}

/**
 * Removes update files nobody needs any more: the one for the build that is installed, ones for older versions,
 * and anything a day old (half-finished .part files included). Called once when the app starts.
 */
fun clearStaleUpdateApks(context: Context) {
    val dayAgo = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
    val installed = parseFullVersion(BuildConfig.VERSION_NAME).copy(buildNumber = 0)
    val ownBuild = Regex("build${BuildConfig.BUILD_NUMBER}(?!\\d)", RegexOption.IGNORE_CASE)
    context.cacheDir.listFiles()?.forEach { file ->
        if (!file.isFile) return@forEach
        val isApk = file.name.endsWith(".apk", ignoreCase = true)
        val isPart = file.name.endsWith(".apk.part", ignoreCase = true)
        if (!isApk && !isPart) return@forEach
        val installedOne = isApk && ownBuild.containsMatchIn(file.name)
        val older = isApk && parseFullVersion(file.name).copy(buildNumber = 0) < installed
        if (installedOne || older || file.lastModified() < dayAgo) file.delete()
    }
}

fun apkFileFor(context: Context, release: GithubRelease): File =
    File(context.cacheDir, release.apkName ?: "kiyo-update.apk")

fun isDownloaded(context: Context, release: GithubRelease): Boolean {
    val f = apkFileFor(context, release)
    if (!f.exists() || f.length() <= 0L) return false
    return release.apkSizeBytes <= 0L || f.length() == release.apkSizeBytes
}

fun canInstallPackages(context: Context): Boolean =
    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
        context.packageManager.canRequestPackageInstalls()

fun requestInstallPermission(context: Context) {
    // many tv boxes have no page for one app, so it falls back to the whole list, then security, then settings
    val pages = listOf(
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + context.packageName)),
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
        Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS),
        Intent(android.provider.Settings.ACTION_SETTINGS),
    )
    pages.firstOrNull { page ->
        runCatching { context.startActivity(page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
}

fun installApk(context: Context, apk: File): Boolean {
    if (!canInstallPackages(context)) {
        requestInstallPermission(context)
        return false
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", apk)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(intent); true }.getOrDefault(false)
}

fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "unknown size"
    bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000f)
    else -> String.format("%.0f KB", bytes / 1000f)
}
