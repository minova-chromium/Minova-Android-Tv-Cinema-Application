package com.minova.cinema.update

import android.content.Context
import java.io.File

/** Private hand-off state shared by DownloadManager's receiver and the activity. */
internal class UpdatePreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val downloadId: Long
        get() = preferences.getLong(KEY_DOWNLOAD_ID, NO_DOWNLOAD)

    val downloadedApk: File?
        get() = preferences.getString(KEY_APK_PATH, null)?.let(::File)

    val installationPending: Boolean
        get() = preferences.getBoolean(KEY_INSTALL_PENDING, false)

    fun saveDownload(id: Long, file: File) {
        preferences.edit()
            .putLong(KEY_DOWNLOAD_ID, id)
            .putString(KEY_APK_PATH, file.absolutePath)
            .putBoolean(KEY_INSTALL_PENDING, false)
            .apply()
    }

    fun markInstallationPending() {
        preferences.edit().putBoolean(KEY_INSTALL_PENDING, true).apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        const val NO_DOWNLOAD = -1L
        private const val PREFERENCES_NAME = "minova_cinema_updates"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_APK_PATH = "apk_path"
        private const val KEY_INSTALL_PENDING = "install_pending"
    }
}
