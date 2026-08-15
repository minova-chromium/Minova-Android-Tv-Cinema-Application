package com.minova.cinema.update

import android.app.Activity
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
            // A BroadcastReceiver is a background component. Android TV may
            // silently block it from opening Settings or Package Installer.
            // Persist readiness instead; the foreground activity performs the
            // visible hand-off immediately or the next time the app is opened.
            preferences.markInstallationPending()
            Toast.makeText(
                context,
                "Update downloaded. Return to Minova Cinema to install it.",
                Toast.LENGTH_LONG,
            ).show()
        } else {
            preferences.clear()
            Toast.makeText(context, "Minova Cinema update download failed.", Toast.LENGTH_LONG).show()
        }
    }
}

object UpdateInstaller {
    fun installDownloadedApk(context: Context): Boolean {
        val preferences = UpdatePreferences(context)
        val apkFile = preferences.downloadedApk
        if (apkFile == null || !apkFile.isFile) {
            preferences.clear()
            Toast.makeText(context, "The downloaded update could not be found.", Toast.LENGTH_LONG).show()
            return false
        }

        // Never launch package-management screens from a receiver or an
        // application Context. Modern Android blocks background activity
        // starts; requiring Activity here makes that failure impossible.
        val activity = context as? Activity ?: return false

        preferences.markInstallationPending()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            )
            if (permissionIntent.resolveActivity(context.packageManager) != null) {
                return runCatching { activity.startActivity(permissionIntent) }
                    .onSuccess {
                        Toast.makeText(
                            context,
                            "Allow Minova Cinema to install this update, then return to the app.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    .isSuccess
            } else {
                Toast.makeText(
                    context,
                    "Enable installs from Minova Cinema in Android TV settings.",
                    Toast.LENGTH_LONG,
                ).show()
                return false
            }
        }

        return launchPackageInstaller(activity, apkFile)
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

    private fun launchPackageInstaller(activity: Activity, apkFile: java.io.File): Boolean {
        val context: Context = activity
        val preferences = UpdatePreferences(context)
        val apkUri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile,
        )
        val installIntent = listOf(Intent.ACTION_INSTALL_PACKAGE, Intent.ACTION_VIEW)
            .map { action ->
                Intent(action).apply {
                    setDataAndType(apkUri, UpdateManager.APK_MIME_TYPE)
                    clipData = ClipData.newRawUri("Minova Cinema update", apkUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            .firstOrNull { intent -> intent.resolveActivity(context.packageManager) != null }

        if (installIntent == null) {
            Toast.makeText(context, "No Android package installer is available.", Toast.LENGTH_LONG).show()
            return false
        }

        context.packageManager.queryIntentActivities(installIntent, 0).forEach { resolved ->
            context.grantUriPermission(
                resolved.activityInfo.packageName,
                apkUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        return runCatching {
            activity.startActivity(installIntent)
        }.fold(
            onSuccess = {
                // Clearing only after a foreground launch succeeds preserves a
                // recoverable pending install if Android rejects the hand-off.
                preferences.clear()
                true
            },
            onFailure = {
                Toast.makeText(
                    context,
                    "The Android package installer could not be opened.",
                    Toast.LENGTH_LONG,
                ).show()
                false
            },
        )
    }
}
