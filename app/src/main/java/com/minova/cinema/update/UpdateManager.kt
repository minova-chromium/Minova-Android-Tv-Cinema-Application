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

internal enum class DownloadState {
    Pending,
    Running,
    Paused,
    Successful,
    Failed,
    Missing,
}

internal data class DownloadSnapshot(
    val state: DownloadState,
    val progressPercent: Int? = null,
    val reason: Int? = null,
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
        UpdatePreferences(context).saveDownload(downloadId, apkFile, update.versionName)
        return downloadId
    }

    /**
     * Reads DownloadManager instead of assuming that an enqueue succeeded.
     * This lets the foreground UI launch Android's installer and lets a later
     * app launch recover a download that completed while Minova Cinema was not
     * on screen.
     */
    internal fun queryDownload(downloadId: Long): DownloadSnapshot {
        if (downloadId == UpdatePreferences.NO_DOWNLOAD) {
            return DownloadSnapshot(DownloadState.Missing)
        }
        val downloadManager = context.getSystemService(DownloadManager::class.java)
        return runCatching {
            downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (!cursor.moveToFirst()) return@use DownloadSnapshot(DownloadState.Missing)

                val status = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS),
                )
                val downloaded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                )
                val total = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                )
                val progress = if (total > 0L) {
                    ((downloaded.coerceIn(0L, total) * 100L) / total).toInt()
                } else {
                    null
                }
                val state = when (status) {
                    DownloadManager.STATUS_PENDING -> DownloadState.Pending
                    DownloadManager.STATUS_RUNNING -> DownloadState.Running
                    DownloadManager.STATUS_PAUSED -> DownloadState.Paused
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadState.Successful
                    DownloadManager.STATUS_FAILED -> DownloadState.Failed
                    else -> DownloadState.Missing
                }
                val reason = if (
                    status == DownloadManager.STATUS_PAUSED ||
                    status == DownloadManager.STATUS_FAILED
                ) {
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                } else {
                    null
                }
                DownloadSnapshot(state = state, progressPercent = progress, reason = reason)
            }
        }.getOrElse { DownloadSnapshot(DownloadState.Missing) }
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
