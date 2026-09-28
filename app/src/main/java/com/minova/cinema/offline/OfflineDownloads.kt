package com.minova.cinema.offline

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.minova.cinema.data.PlexDownloadTicket
import com.minova.cinema.data.remote.PlexConfig
import com.minova.cinema.data.remote.PlexConnection
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.domain.MediaTechnicalInfo
import com.minova.cinema.domain.PlaybackSource
import java.io.File
import java.security.MessageDigest

enum class OfflineDownloadState { Preparing, Downloading, Paused, Ready, Failed }

data class OfflineDownload(
    val sourceKey: String,
    val ratingKey: String,
    val title: String,
    val secondaryTitle: String? = null,
    val summary: String? = null,
    val year: Int? = null,
    val durationMs: Long? = null,
    val viewOffsetMs: Long = 0L,
    val contentRating: String? = null,
    val kind: MediaKind = MediaKind.Movie,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val metadataKey: String,
    val technicalInfo: MediaTechnicalInfo = MediaTechnicalInfo(),
    val fileName: String,
    val queueId: Long? = null,
    val queueItemId: Long? = null,
    val systemDownloadId: Long? = null,
    val state: OfflineDownloadState = OfflineDownloadState.Preparing,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null,
    val addedAtMs: Long = System.currentTimeMillis(),
) {
    val progress: Float
        get() = if (totalBytes > 0L) {
            (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val ticket: PlexDownloadTicket?
        get() = if (queueId != null && queueItemId != null) {
            PlexDownloadTicket(queueId, queueItemId)
        } else null
}

/**
 * App-private download index. It deliberately stores no Plex token and no
 * authenticated remote URL; Plex remains the authority for preparing media.
 */
class OfflineDownloadsStore(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(DownloadManager::class.java)
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val lock = Any()

    fun sourceKey(connection: PlexConnection): String = MessageDigest.getInstance("SHA-256")
        .digest((connection.baseUrl + "\u0000" + connection.token).toByteArray())
        .joinToString("") { "%02x".format(it) }

    fun list(connection: PlexConnection): List<OfflineDownload> = synchronized(lock) {
        refreshTransfers(readAll()).also(::writeAll)
            .filter { it.sourceKey == sourceKey(connection) }
            .sortedByDescending(OfflineDownload::addedAtMs)
    }

    fun stage(content: MediaContent, connection: PlexConnection): OfflineDownload = synchronized(lock) {
        val all = readAll().toMutableList()
        all.firstOrNull {
            it.sourceKey == sourceKey(connection) && it.ratingKey == content.ratingKey
        }?.let { return it }
        val extension = content.playback?.technicalInfo?.container
            ?.lowercase()?.filter(Char::isLetterOrDigit)?.takeIf(String::isNotBlank) ?: "mp4"
        val safeRatingKey = content.ratingKey.filter { it.isLetterOrDigit() || it in "-_" }
            .ifBlank { MessageDigest.getInstance("SHA-256").digest(content.ratingKey.toByteArray()).take(8).joinToString("") { "%02x".format(it) } }
        val source = sourceKey(connection)
        val record = OfflineDownload(
            sourceKey = source,
            ratingKey = content.ratingKey,
            title = content.title,
            secondaryTitle = content.secondaryTitle,
            summary = content.summary,
            year = content.year,
            durationMs = content.durationMs,
            viewOffsetMs = content.viewOffsetMs,
            contentRating = content.contentRating,
            kind = content.kind,
            seasonNumber = content.seasonNumber,
            episodeNumber = content.episodeNumber,
            metadataKey = content.playback?.metadataKey ?: "/library/metadata/${content.ratingKey}",
            technicalInfo = content.playback?.technicalInfo ?: MediaTechnicalInfo(),
            fileName = "${source.take(10)}-$safeRatingKey.$extension",
        )
        all += record
        writeAll(all)
        record
    }

    fun attachTicket(record: OfflineDownload, ticket: PlexDownloadTicket): OfflineDownload =
        update(record) { it.copy(queueId = ticket.queueId, queueItemId = ticket.itemId, errorMessage = null) }

    fun clearTicket(record: OfflineDownload): OfflineDownload =
        update(record) { it.copy(queueId = null, queueItemId = null) }

    fun fail(record: OfflineDownload, message: String): OfflineDownload =
        update(record) { it.copy(state = OfflineDownloadState.Failed, errorMessage = message) }

    fun beginTransfer(
        record: OfflineDownload,
        connection: PlexConnection,
        mediaUrl: String,
    ): OfflineDownload = synchronized(lock) {
        if (record.systemDownloadId != null) return record
        val root = requireNotNull(appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)) {
            "Android media storage is unavailable."
        }
        File(root, "$DIRECTORY/${record.fileName}").delete()
        val request = DownloadManager.Request(Uri.parse(mediaUrl))
            .setTitle(record.title)
            .setDescription("Downloading for offline playback")
            .setMimeType("video/*")
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_MOVIES,
                "$DIRECTORY/${record.fileName}",
            )
        PlexConfig.requestHeaders(connection)
            .filterKeys { it != PlexConfig.HEADER_ACCEPT }
            .forEach(request::addRequestHeader)
        request.addRequestHeader(PlexConfig.HEADER_PMS_API_VERSION, "1.0.0")
        val id = manager.enqueue(request)
        update(record) {
            it.copy(
                systemDownloadId = id,
                state = OfflineDownloadState.Downloading,
                errorMessage = null,
            )
        }
    }

    fun remove(record: OfflineDownload) = synchronized(lock) {
        record.systemDownloadId?.let(manager::remove)
        localFile(record)?.delete()
        writeAll(readAll().filterNot {
            it.sourceKey == record.sourceKey && it.ratingKey == record.ratingKey
        })
    }

    fun updatePlaybackPosition(record: OfflineDownload, positionMs: Long) {
        update(record) { it.copy(viewOffsetMs = positionMs.coerceAtLeast(0L)) }
    }

    fun playableContent(record: OfflineDownload): MediaContent? {
        val file = localFile(record)?.takeIf(File::isFile) ?: return null
        return MediaContent(
            ratingKey = record.ratingKey,
            title = record.title,
            secondaryTitle = record.secondaryTitle,
            summary = record.summary,
            tagline = null,
            year = record.year,
            durationMs = record.durationMs,
            viewOffsetMs = record.viewOffsetMs,
            posterUrl = null,
            backdropUrl = null,
            contentRating = record.contentRating,
            kind = record.kind,
            seasonNumber = record.seasonNumber,
            episodeNumber = record.episodeNumber,
            playback = PlaybackSource(
                partId = -1L,
                directUrl = file.toUri().toString(),
                metadataKey = record.metadataKey,
                audioStreams = emptyList(),
                subtitles = emptyList(),
                technicalInfo = record.technicalInfo,
            ),
        )
    }

    fun isOfflineContent(content: MediaContent): Boolean =
        content.playback?.directUrl?.toUri()?.scheme.equals("file", ignoreCase = true)

    private fun localFile(record: OfflineDownload): File? =
        appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?.let { File(it, "$DIRECTORY/${record.fileName}") }

    private fun update(
        target: OfflineDownload,
        transform: (OfflineDownload) -> OfflineDownload,
    ): OfflineDownload = synchronized(lock) {
        var updated = target
        val all = readAll().map {
            if (it.sourceKey == target.sourceKey && it.ratingKey == target.ratingKey) {
                transform(it).also { value -> updated = value }
            } else it
        }
        writeAll(all)
        updated
    }

    private fun refreshTransfers(records: List<OfflineDownload>): List<OfflineDownload> = records.map { record ->
        val downloadId = record.systemDownloadId ?: return@map record
        val snapshot = runCatching {
            manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                TransferSnapshot(status, downloaded, total, reason)
            }
        }.getOrNull()
        when {
            snapshot == null && localFile(record)?.isFile == true -> record.copy(state = OfflineDownloadState.Ready)
            snapshot == null -> record.copy(state = OfflineDownloadState.Failed, errorMessage = "Android could not find this download.")
            snapshot.status == DownloadManager.STATUS_SUCCESSFUL && localFile(record)?.isFile == true ->
                record.copy(state = OfflineDownloadState.Ready, bytesDownloaded = snapshot.downloaded, totalBytes = snapshot.total, errorMessage = null)
            snapshot.status == DownloadManager.STATUS_PAUSED ->
                record.copy(state = OfflineDownloadState.Paused, bytesDownloaded = snapshot.downloaded, totalBytes = snapshot.total)
            snapshot.status == DownloadManager.STATUS_FAILED ->
                record.copy(state = OfflineDownloadState.Failed, bytesDownloaded = snapshot.downloaded, totalBytes = snapshot.total,
                    errorMessage = "Android stopped the download (code ${snapshot.reason}).")
            else -> record.copy(state = OfflineDownloadState.Downloading, bytesDownloaded = snapshot.downloaded, totalBytes = snapshot.total)
        }
    }

    private fun readAll(): List<OfflineDownload> = runCatching {
        gson.fromJson<List<OfflineDownload>>(
            preferences.getString(KEY_RECORDS, "[]"),
            object : TypeToken<List<OfflineDownload>>() {}.type,
        ).orEmpty()
    }.getOrDefault(emptyList())

    private fun writeAll(records: List<OfflineDownload>) {
        preferences.edit { putString(KEY_RECORDS, gson.toJson(records)) }
    }

    private data class TransferSnapshot(
        val status: Int,
        val downloaded: Long,
        val total: Long,
        val reason: Int,
    )

    private companion object {
        const val PREFERENCES = "minova_offline_downloads"
        const val KEY_RECORDS = "records_v1"
        const val DIRECTORY = "offline"
    }
}
