package com.minova.cinema.data.local

import android.content.Context
import androidx.core.content.edit

internal data class PlaybackResumeSnapshot(
    val ratingKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val savedAtEpochMs: Long,
)

/**
 * Keeps one short-lived local playback checkpoint.
 *
 * Plex remains the long-term source of truth. This checkpoint only bridges a
 * brief Android interruption where the process can be stopped before the last
 * timeline request reaches the server.
 */
class PlaybackResumeStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun resumePosition(
        ratingKey: String,
        serverPositionMs: Long,
        durationMs: Long?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Long {
        val snapshot = readSnapshot() ?: return serverPositionMs.coerceAtLeast(0L)
        return selectPlaybackResumePosition(
            ratingKey = ratingKey,
            serverPositionMs = serverPositionMs,
            expectedDurationMs = durationMs,
            snapshot = snapshot,
            nowEpochMs = nowEpochMs,
        )
    }

    fun save(
        ratingKey: String,
        positionMs: Long,
        durationMs: Long,
        durable: Boolean,
    ) {
        val safePosition = positionMs.coerceAtLeast(0L)
        val safeDuration = durationMs.coerceAtLeast(0L)
        if (safePosition <= 0L || playbackIsComplete(safePosition, safeDuration)) {
            clear(ratingKey, durable)
            return
        }
        preferences.edit(commit = durable) {
            putString(KEY_RATING_KEY, ratingKey)
            putLong(KEY_POSITION_MS, safePosition)
            putLong(KEY_DURATION_MS, safeDuration)
            putLong(KEY_SAVED_AT_MS, System.currentTimeMillis())
        }
    }

    fun clear(ratingKey: String, durable: Boolean = false) {
        if (preferences.getString(KEY_RATING_KEY, null) != ratingKey) return
        preferences.edit(commit = durable) {
            remove(KEY_RATING_KEY)
            remove(KEY_POSITION_MS)
            remove(KEY_DURATION_MS)
            remove(KEY_SAVED_AT_MS)
        }
    }

    private fun readSnapshot(): PlaybackResumeSnapshot? {
        val ratingKey = preferences.getString(KEY_RATING_KEY, null)
            ?.takeIf(String::isNotBlank)
            ?: return null
        return PlaybackResumeSnapshot(
            ratingKey = ratingKey,
            positionMs = preferences.getLong(KEY_POSITION_MS, 0L),
            durationMs = preferences.getLong(KEY_DURATION_MS, 0L),
            savedAtEpochMs = preferences.getLong(KEY_SAVED_AT_MS, 0L),
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "minova_cinema_resume"
        const val KEY_RATING_KEY = "rating_key"
        const val KEY_POSITION_MS = "position_ms"
        const val KEY_DURATION_MS = "duration_ms"
        const val KEY_SAVED_AT_MS = "saved_at_ms"
    }
}

internal fun selectPlaybackResumePosition(
    ratingKey: String,
    serverPositionMs: Long,
    expectedDurationMs: Long?,
    snapshot: PlaybackResumeSnapshot?,
    nowEpochMs: Long,
): Long {
    val serverPosition = serverPositionMs.coerceAtLeast(0L)
    val local = snapshot ?: return serverPosition
    if (local.ratingKey != ratingKey) return serverPosition
    val ageMs = (nowEpochMs - local.savedAtEpochMs).coerceAtLeast(0L)
    if (ageMs > LOCAL_RESUME_MAX_AGE_MS) return serverPosition

    val expectedDuration = expectedDurationMs?.takeIf { it > 0L }
    if (
        expectedDuration != null &&
        local.durationMs > 0L &&
        kotlin.math.abs(expectedDuration - local.durationMs) > DURATION_TOLERANCE_MS
    ) return serverPosition

    if (playbackIsComplete(local.positionMs, expectedDuration ?: local.durationMs)) {
        return serverPosition
    }
    // A fresh device checkpoint is newer than the Plex catalog snapshot. It
    // must also preserve an intentional rewind, so do not simply take max().
    return local.positionMs.coerceAtLeast(0L)
}

private fun playbackIsComplete(positionMs: Long, durationMs: Long): Boolean =
    durationMs > 0L && positionMs >= (durationMs - COMPLETION_TOLERANCE_MS).coerceAtLeast(0L)

private const val LOCAL_RESUME_MAX_AGE_MS = 30L * 60L * 1_000L
private const val DURATION_TOLERANCE_MS = 60L * 1_000L
private const val COMPLETION_TOLERANCE_MS = 30L * 1_000L
