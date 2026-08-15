package com.minova.cinema.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.minova.cinema.BuildConfig

/** Receives only system DownloadManager completion broadcasts. */
class ApkDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val preferences = UpdatePreferences(context)
        if (completedId == -1L || completedId != preferences.downloadId) return

        val downloadManager = context.getSystemService(DownloadManager::class.java)
        val query = DownloadManager.Query().setFilterById(completedId)
        val status = downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return@use DownloadManager.STATUS_FAILED
            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        }

        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            UpdateInstaller.installDownloadedApk(context)
        } else {
            preferences.clear()
            Toast.makeText(context, "Minova Cinema update download failed.", Toast.LENGTH_LONG).show()
        }
    }
}

object UpdateInstaller {
    fun installDownloadedApk(context: Context) {
        val preferences = UpdatePreferences(context)
        val apkFile = preferences.downloadedApk
        if (apkFile == null || !apkFile.isFile) {
            preferences.clear()
            Toast.makeText(context, "The downloaded update could not be found.", Toast.LENGTH_LONG).show()
            return
        }

        preferences.markInstallationPending()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (permissionIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(permissionIntent)
                Toast.makeText(
                    context,
                    "Allow Minova Cinema to install this update, then return to the app.",
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "Enable installs from Minova Cinema in Android TV settings.",
                    Toast.LENGTH_LONG,
                ).show()
            }
            return
        }

        launchPackageInstaller(context, apkFile)
    }

    /** Called by MainActivity after the unknown-source settings screen closes. */
    fun resumePendingInstall(context: Context) {
        val preferences = UpdatePreferences(context)
        if (!preferences.installationPending) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) return
        installDownloadedApk(context)
    }

    private fun launchPackageInstaller(context: Context, apkFile: java.io.File) {
        val preferences = UpdatePreferences(context)
        val apkUri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile,
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, UpdateManager.APK_MIME_TYPE)
            clipData = ClipData.newRawUri("Minova Cinema update", apkUri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (installIntent.resolveActivity(context.packageManager) == null) {
            Toast.makeText(context, "No Android package installer is available.", Toast.LENGTH_LONG).show()
            return
        }

        // Clear before launching to prevent onResume from opening the installer
        // twice. A failed/cancelled install can be downloaded again later.
        preferences.clear()
        context.startActivity(installIntent)
    }
}
