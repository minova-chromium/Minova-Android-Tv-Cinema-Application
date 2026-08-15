package com.minova.cinema.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.minova.cinema.BuildConfig
import java.io.File

data class AppUpdate(
    val versionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
)

/**
 * Owns the two non-UI update operations: checking GitHub and enqueueing the APK.
 * The package installer still verifies that the APK package name and signing
 * certificate match the installed Minova Cinema application.
 */
class UpdateManager(
    private val context: Context,
    private val service: GitHubReleaseService = GitHubReleaseServiceFactory.create(),
) {
    suspend fun findAvailableUpdate(): AppUpdate? {
        // A debug APK uses Android Studio's debug certificate and therefore
        // cannot be upgraded by a production-signed GitHub APK.
        if (BuildConfig.DEBUG) return null

        val release = service.latestRelease(
            owner = BuildConfig.UPDATE_GITHUB_OWNER,
            repository = BuildConfig.UPDATE_GITHUB_REPOSITORY,
        )
        val remoteVersion = VersionName.parse(release.tagName) ?: return null
        val installedVersion = VersionName.parse(BuildConfig.VERSION_NAME) ?: return null
        if (remoteVersion <= installedVersion) return null

        val apk = release.assets.firstOrNull { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) &&
                asset.browserDownloadUrl.startsWith(
                    "https://github.com/${BuildConfig.UPDATE_GITHUB_OWNER}/" +
                        "${BuildConfig.UPDATE_GITHUB_REPOSITORY}/releases/download/",
                )
        } ?: return null

        return AppUpdate(
            versionName = release.tagName.removePrefix("v").removePrefix("V"),
            releaseNotes = release.body?.trim().orEmpty().ifBlank {
                "This release contains improvements and fixes for Minova Cinema."
            },
            apkDownloadUrl = apk.browserDownloadUrl,
        )
    }

    fun enqueueDownload(update: AppUpdate): Long {
        val updateDirectory = File(
            requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)) {
                "External app storage is unavailable on this device."
            },
            UPDATE_DIRECTORY,
        ).apply { mkdirs() }
        check(updateDirectory.isDirectory) { "Could not create the update directory." }

        val apkFile = File(updateDirectory, "Minova-Cinema-${update.versionName}.apk")
        // This is app-owned update storage. Removing an interrupted copy avoids
        // DownloadManager selecting a different destination filename.
        if (apkFile.exists()) check(apkFile.delete()) { "Could not replace the old update file." }

        val request = DownloadManager.Request(Uri.parse(update.apkDownloadUrl))
            .setTitle("Minova Cinema ${update.versionName}")
            .setDescription("Downloading application update")
            .setMimeType(APK_MIME_TYPE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            )
            .setDestinationUri(Uri.fromFile(apkFile))

        val downloadManager = context.getSystemService(DownloadManager::class.java)
        val downloadId = downloadManager.enqueue(request)
        UpdatePreferences(context).saveDownload(downloadId, apkFile)
        return downloadId
    }

    private data class VersionName(private val components: List<Int>) : Comparable<VersionName> {
        override fun compareTo(other: VersionName): Int {
            val componentCount = maxOf(components.size, other.components.size)
            for (index in 0 until componentCount) {
                val comparison = components.getOrElse(index) { 0 }
                    .compareTo(other.components.getOrElse(index) { 0 })
                if (comparison != 0) return comparison
            }
            return 0
        }

        companion object {
            /** Accepts v2.3.0, 2.3, or 2.3.0+build; GitHub latest excludes prereleases. */
            fun parse(raw: String): VersionName? {
                val normalized = raw.trim()
                    .removePrefix("v")
                    .removePrefix("V")
                    .substringBefore('+')
                if ('-' in normalized) return null
                val values = normalized.split('.').map { component ->
                    component.toIntOrNull() ?: return null
                }
                return values.takeIf { it.isNotEmpty() }?.let(::VersionName)
            }
        }
    }

    companion object {
        internal const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val UPDATE_DIRECTORY = "updates"
    }
}
