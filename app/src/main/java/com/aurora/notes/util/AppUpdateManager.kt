package com.aurora.notes.util

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.aurora.notes.BuildConfig
import com.aurora.notes.MainActivity
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.exitProcess

sealed class UpdateScanResult {
    data class UpToDate(
        val currentVersion: String,
        val latestVersion: String,
        val releaseTitle: String
    ) : UpdateScanResult()

    data class UpdateAvailable(
        val currentVersion: String,
        val latestVersion: String,
        val releaseTitle: String,
        val releaseNotes: String,
        val apkDownloadUrl: String,
        val apkFileName: String,
        val apkSizeBytes: Long
    ) : UpdateScanResult()

    data class Error(
        val message: String
    ) : UpdateScanResult()
}

object AppUpdateManager {

    private const val PREFS_NAME = "aurora_notes_preferences"
    private const val KEY_GITHUB_REPO_OVERRIDE = "key_github_repo_slug_v1"

    val currentVersionName: String
        get() = BuildConfig.VERSION_NAME

    val currentVersionCode: Int
        get() = BuildConfig.VERSION_CODE

    fun getConfiguredRepoSlug(context: Context): String {
        val bakedRepo = BuildConfig.GITHUB_REPO.trim()
        if (bakedRepo.isNotBlank() && bakedRepo.contains("/")) {
            return bakedRepo
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedOverride = prefs.getString(KEY_GITHUB_REPO_OVERRIDE, null)?.trim().orEmpty()
        if (savedOverride.isNotBlank() && savedOverride.contains("/")) {
            return savedOverride
        }
        return "DefinitelynotRylix/Aurora-Notes"
    }

    /**
     * Compares two semantic/numeric version strings (e.g. "v1.3.30" vs "1.3.29", or "v1.2.29" vs "1.3.28"
     * where the 3rd segment is the monotonic GitHub Actions run_number).
     * Returns > 0 if [remoteVersion] > [localVersion], 0 if equal, < 0 if [remoteVersion] < [localVersion].
     */
    fun compareVersions(
        remoteVersion: String,
        localVersion: String,
        remoteRunNumber: Int? = null,
        localVersionCode: Int? = null
    ): Int {
        // 1. If both CI run_number and localVersionCode (100 + run_number) are known, compare build code first
        if (remoteRunNumber != null && remoteRunNumber > 0 && localVersionCode != null && localVersionCode > 100) {
            val remoteCode = 100 + remoteRunNumber
            if (remoteCode != localVersionCode) {
                return remoteCode.compareTo(localVersionCode)
            }
        }

        val cleanRemote = remoteVersion.trim().removePrefix("v").removePrefix("V")
        val cleanLocal = localVersion.trim().removePrefix("v").removePrefix("V")

        val remoteParts = cleanRemote.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        val localParts = cleanLocal.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }

        if (remoteParts.isEmpty() && localParts.isEmpty()) {
            return cleanRemote.compareTo(cleanLocal, ignoreCase = true)
        }

        // 2. In Aurora Notes CI builds, the 3rd segment (X.Y.<run_number>) is the monotonic GitHub Actions run_number.
        // If both versions have a positive CI run_number in index 2 and share the same major version,
        // compare the monotonic CI run_number directly so tag prefix changes (e.g. 1.2.29 vs 1.3.28) still detect updates.
        if (remoteParts.size >= 3 && localParts.size >= 3 &&
            remoteParts[0] == localParts[0] &&
            remoteParts[2] > 0 && localParts[2] > 0 &&
            remoteParts[2] != localParts[2]
        ) {
            return remoteParts[2].compareTo(localParts[2])
        }

        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) {
                return r.compareTo(l)
            }
        }
        return 0
    }

    private fun extractCiRunNumber(tag: String, releaseBody: String): Int? {
        val bodyMatch = Regex("""\(#(\d+)\)""").find(releaseBody)
        if (bodyMatch != null) {
            val parsed = bodyMatch.groupValues.getOrNull(1)?.toIntOrNull()
            if (parsed != null && parsed > 0) return parsed
        }
        val cleanTag = tag.trim().removePrefix("v").removePrefix("V")
        val parts = cleanTag.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        if (parts.size >= 3 && parts[2] > 0) {
            return parts[2]
        }
        return null
    }

    suspend fun scanForUpdate(context: Context, repoSlugOverride: String? = null): UpdateScanResult =
        withContext(Dispatchers.IO) {
            val repoSlug = (repoSlugOverride ?: getConfiguredRepoSlug(context))
                .trim()
                .removePrefix("https://github.com/")
                .removePrefix("http://github.com/")
                .trim('/')

            if (repoSlug.isBlank() || !repoSlug.contains("/")) {
                return@withContext UpdateScanResult.Error(
                    "Release update source is not available right now."
                )
            }

            LocalLogger.security("UPDATE", "Scanning GitHub Releases for updates (current v$currentVersionName • build $currentVersionCode)")

            try {
                val releaseObj = fetchLatestReleaseJson(repoSlug)
                    ?: return@withContext UpdateScanResult.Error(
                        "No published releases found on GitHub Releases."
                    )

                val rawTag = releaseObj.get("tag_name")?.asString?.trim().orEmpty()
                val releaseTitle = releaseObj.get("name")?.asString?.trim().takeUnless { it.isNullOrBlank() } ?: rawTag
                val releaseBody = releaseObj.get("body")?.asString?.trim().orEmpty()

                val remoteRunNum = extractCiRunNumber(rawTag, releaseBody)
                val rawCleanVersion = rawTag.removePrefix("v").removePrefix("V").ifBlank { "0.0.0" }
                val latestCleanVersion = if (remoteRunNum != null && rawCleanVersion.startsWith("1.2.")) {
                    "1.3.$remoteRunNum"
                } else {
                    rawCleanVersion
                }

                val cmp = compareVersions(
                    remoteVersion = latestCleanVersion,
                    localVersion = currentVersionName,
                    remoteRunNumber = remoteRunNum,
                    localVersionCode = currentVersionCode
                )

                // Find APK asset in release assets
                val assetsArray: JsonArray? = releaseObj.getAsJsonArray("assets")
                var apkUrl = ""
                var apkName = "Aurora Notes.apk"
                var apkSize = 0L

                if (assetsArray != null) {
                    for (element: JsonElement in assetsArray) {
                        val assetObj = element.asJsonObject
                        val assetName = assetObj.get("name")?.asString.orEmpty()
                        val downloadUrl = assetObj.get("browser_download_url")?.asString.orEmpty()
                        if (assetName.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                            apkUrl = downloadUrl
                            apkName = assetName
                            apkSize = assetObj.get("size")?.asLong ?: 0L
                            break
                        }
                    }
                }

                if (cmp <= 0) {
                    LocalLogger.security(
                        "UPDATE",
                        "App is already at the latest version (installed v$currentVersionName, latest release v$latestCleanVersion)"
                    )
                    return@withContext UpdateScanResult.UpToDate(
                        currentVersion = currentVersionName,
                        latestVersion = latestCleanVersion,
                        releaseTitle = releaseTitle
                    )
                }

                if (apkUrl.isBlank()) {
                    return@withContext UpdateScanResult.Error(
                        "Release v$latestCleanVersion was found on GitHub, but no .apk file is attached to its Assets yet."
                    )
                }

                LocalLogger.security(
                    "UPDATE",
                    "New update found: v$latestCleanVersion (current v$currentVersionName) — APK '$apkName'"
                )

                UpdateScanResult.UpdateAvailable(
                    currentVersion = currentVersionName,
                    latestVersion = latestCleanVersion,
                    releaseTitle = releaseTitle,
                    releaseNotes = releaseBody,
                    apkDownloadUrl = apkUrl,
                    apkFileName = apkName,
                    apkSizeBytes = apkSize
                )
            } catch (e: Exception) {
                LocalLogger.error("UPDATE", "Update scan failed: ${e.localizedMessage}")
                UpdateScanResult.Error(
                    "Could not reach GitHub Releases: ${e.localizedMessage ?: "Network error"}"
                )
            }
        }

    private fun hasApkAsset(releaseObj: JsonObject): Boolean {
        val assets = releaseObj.getAsJsonArray("assets") ?: return false
        for (el in assets) {
            if (!el.isJsonObject) continue
            val obj = el.asJsonObject
            val name = obj.get("name")?.asString.orEmpty()
            val url = obj.get("browser_download_url")?.asString.orEmpty()
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                return true
            }
        }
        return false
    }

    private fun fetchLatestReleaseJson(repoSlug: String): JsonObject? {
        val candidates = mutableListOf<JsonObject>()

        // 1. Fetch recent releases list first so we can inspect the newest releases with APK assets
        val listUrl = URL("https://api.github.com/repos/$repoSlug/releases?per_page=10")
        val listConn = (listUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "AuroraNotes-Android-Updater/${BuildConfig.VERSION_NAME}")
        }

        try {
            if (listConn.responseCode in 200..299) {
                val body = listConn.inputStream.bufferedReader().use { it.readText() }
                val parsed = JsonParser.parseString(body)
                if (parsed.isJsonArray) {
                    for (item in parsed.asJsonArray) {
                        if (item.isJsonObject) {
                            val obj = item.asJsonObject
                            val isDraft = obj.get("draft")?.asBoolean ?: false
                            if (!isDraft) {
                                candidates.add(obj)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fall through to /releases/latest fallback
        } finally {
            listConn.disconnect()
        }

        // 2. Also check /releases/latest if candidates list is empty
        if (candidates.isEmpty()) {
            val latestUrl = URL("https://api.github.com/repos/$repoSlug/releases/latest")
            val latestConn = (latestUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "AuroraNotes-Android-Updater/${BuildConfig.VERSION_NAME}")
            }

            try {
                if (latestConn.responseCode in 200..299) {
                    val body = latestConn.inputStream.bufferedReader().use { it.readText() }
                    val parsed = JsonParser.parseString(body)
                    if (parsed.isJsonObject) {
                        candidates.add(parsed.asJsonObject)
                    }
                }
            } finally {
                latestConn.disconnect()
            }
        }

        if (candidates.isEmpty()) return null

        // Prefer releases that have an attached .apk asset, sorted by highest version / CI run number
        val withApk = candidates.filter { hasApkAsset(it) }
        val pool = if (withApk.isNotEmpty()) withApk else candidates

        return pool.maxWithOrNull { a, b ->
            val tagA = a.get("tag_name")?.asString.orEmpty()
            val bodyA = a.get("body")?.asString.orEmpty()
            val runA = extractCiRunNumber(tagA, bodyA)

            val tagB = b.get("tag_name")?.asString.orEmpty()
            val bodyB = b.get("body")?.asString.orEmpty()
            val runB = extractCiRunNumber(tagB, bodyB)

            compareVersions(
                remoteVersion = tagA,
                localVersion = tagB,
                remoteRunNumber = runA,
                localVersionCode = if (runB != null) 100 + runB else null
            )
        }
    }

    suspend fun downloadUpdateApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            LocalLogger.security("UPDATE", "Downloading update APK from $downloadUrl")
            val updatesDir = File(context.cacheDir, "apk_updates").apply {
                if (!exists()) mkdirs()
            }
            val targetFile = File(updatesDir, "Aurora Notes.apk")
            if (targetFile.exists()) {
                targetFile.delete()
            }

            var currentUrl = downloadUrl
            var redirectCount = 0
            var connection: HttpURLConnection? = null

            while (redirectCount < 6) {
                val url = URL(currentUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "AuroraNotes-Android-Updater/${BuildConfig.VERSION_NAME}")
                }
                conn.connect()
                val code = conn.responseCode
                if (code in 301..308) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrBlank()) {
                        return@withContext Result.failure(
                            IllegalStateException("Redirect missing Location header while downloading APK")
                        )
                    }
                    currentUrl = URL(url, location).toString()
                    redirectCount++
                } else {
                    connection = conn
                    break
                }
            }

            val activeConn = connection
                ?: return@withContext Result.failure(IllegalStateException("Too many redirects while downloading APK"))

            if (activeConn.responseCode !in 200..299) {
                val errCode = activeConn.responseCode
                activeConn.disconnect()
                return@withContext Result.failure(
                    IllegalStateException("HTTP $errCode while downloading APK")
                )
            }

            val totalBytes = activeConn.contentLengthLong
            BufferedInputStream(activeConn.inputStream).use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var downloadedBytes = 0L
                    var bytesRead: Int
                    var lastReportedPercent = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            val percent = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                    output.flush()
                }
            }
            activeConn.disconnect()
            onProgress(100)
            LocalLogger.security("UPDATE", "Downloaded update APK (${targetFile.length()} bytes)")
            Result.success(targetFile)
        } catch (e: Exception) {
            LocalLogger.error("UPDATE", "Failed downloading update APK: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openUnknownSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Installs the downloaded APK using Android's PackageInstaller session API (with UpdateRestartReceiver
     * listening for MY_PACKAGE_REPLACED & STATUS_SUCCESS to force-restart the app), and falls back to
     * ACTION_VIEW FileProvider installer if needed.
     */
    fun installApkAndRestart(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() == 0L) return false

        if (!canRequestPackageInstalls(context)) {
            openUnknownSourcesSettings(context)
            return false
        }

        return try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
            }
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            session.use { activeSession ->
                FileInputStream(apkFile).use { fileInput ->
                    activeSession.openWrite("Aurora_Notes_Update.apk", 0, apkFile.length()).use { out ->
                        fileInput.copyTo(out)
                        activeSession.fsync(out)
                    }
                }

                val callbackIntent = Intent(context, UpdateRestartReceiver::class.java).apply {
                    action = UpdateRestartReceiver.ACTION_INSTALL_COMMIT
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, callbackIntent, flags)
                activeSession.commit(pendingIntent.intentSender)
            }

            LocalLogger.security("UPDATE", "Committed self-update session ($sessionId); awaiting OS package replacement & forced restart")
            true
        } catch (e: Exception) {
            LocalLogger.warn("UPDATE", "PackageInstaller session fallback to ACTION_VIEW installer: ${e.localizedMessage}")
            try {
                val apkUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(viewIntent)
                true
            } catch (fallbackErr: Exception) {
                LocalLogger.error("UPDATE", "Failed launching APK installer: ${fallbackErr.localizedMessage}")
                false
            }
        }
    }

    /**
     * Forces a clean restart of Aurora Notes by launching MainActivity with a cleared task stack
     * and terminating the old process.
     */
    fun forceRestartApp(context: Context) {
        LocalLogger.security("UPDATE", "Executing forced app restart")
        val restartIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra("extra_updated_restarted", true)
        }
        context.startActivity(restartIntent)
        if (context is Activity) {
            context.finishAffinity()
        }
        Runtime.getRuntime().exit(0)
        exitProcess(0)
    }
}
