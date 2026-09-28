package com.minova.cinema.ui.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.minova.cinema.offline.OfflineDownload
import com.minova.cinema.offline.OfflineDownloadState
import com.minova.cinema.ui.theme.MinovaCyan
import com.minova.cinema.ui.theme.MinovaMuted
import com.minova.cinema.ui.theme.MinovaNightDeep
import com.minova.cinema.ui.theme.MinovaSurface
import com.minova.cinema.ui.theme.MinovaSurfaceRaised
import com.minova.cinema.ui.theme.MinovaWhite
import java.util.Locale

@Composable
fun OfflineDownloadsScreen(
    downloads: List<OfflineDownload>,
    onBack: () -> Unit,
    onPlay: (OfflineDownload) -> Unit,
    onRetry: (OfflineDownload) -> Unit,
    onRemove: (OfflineDownload) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(MinovaNightDeep)
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Text(
                "Downloads",
                color = MinovaWhite,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
            )
            val total = downloads.filter { it.state == OfflineDownloadState.Ready }.sumOf { it.totalBytes }
            if (total > 0L) Text(formatBytes(total), color = MinovaMuted, fontSize = 12.sp)
        }

        if (downloads.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.Download, null, tint = MinovaCyan, modifier = Modifier.size(48.dp))
                Text("No downloads yet", color = MinovaWhite, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 18.dp))
                Text(
                    "Open a movie or episode and tap Download. Plex Pass and server permission are required.",
                    color = MinovaMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            return
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(downloads, key = { "${it.sourceKey}:${it.ratingKey}" }) { download ->
                DownloadRow(download, onPlay, onRetry, onRemove)
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DownloadRow(
    download: OfflineDownload,
    onPlay: (OfflineDownload) -> Unit,
    onRetry: (OfflineDownload) -> Unit,
    onRemove: (OfflineDownload) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(14.dp)).background(MinovaSurface)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 66.dp, height = 82.dp).clip(RoundedCornerShape(10.dp))
                    .background(MinovaSurfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (download.state == OfflineDownloadState.Failed) Icons.Rounded.ErrorOutline else Icons.Rounded.Download,
                    null,
                    tint = if (download.state == OfflineDownloadState.Failed) Color(0xFFFF6B73) else MinovaCyan,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(Modifier.padding(start = 13.dp).weight(1f)) {
                Text(download.title, color = MinovaWhite, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                download.secondaryTitle?.let {
                    Text(it, color = MinovaMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(download.statusLabel(), color = if (download.state == OfflineDownloadState.Failed) Color(0xFFFF858C) else MinovaCyan,
                    fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp), maxLines = 2)
            }
            when (download.state) {
                OfflineDownloadState.Ready -> RoundIcon(Icons.Rounded.PlayArrow, "Play downloaded title") { onPlay(download) }
                OfflineDownloadState.Failed -> RoundIcon(Icons.Rounded.Download, "Retry download") { onRetry(download) }
                else -> Unit
            }
            Spacer(Modifier.width(4.dp))
            RoundIcon(Icons.Rounded.DeleteOutline, "Remove download") { onRemove(download) }
        }
        if (download.state == OfflineDownloadState.Downloading || download.state == OfflineDownloadState.Paused) {
            LinearProgressIndicator(
                progress = { download.progress },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(4.dp),
                color = MinovaCyan,
                trackColor = MinovaSurfaceRaised,
            )
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = MinovaWhite, modifier = Modifier.size(24.dp))
    }
}

private fun OfflineDownload.statusLabel(): String = when (state) {
    OfflineDownloadState.Preparing -> "Plex is preparing this download…"
    OfflineDownloadState.Downloading -> if (totalBytes > 0L) {
        "${(progress * 100).toInt()}%  •  ${formatBytes(bytesDownloaded)} of ${formatBytes(totalBytes)}  •  Wi-Fi only"
    } else "Downloading on Wi-Fi…"
    OfflineDownloadState.Paused -> "Waiting for Wi-Fi"
    OfflineDownloadState.Ready -> "Downloaded  •  ${formatBytes(totalBytes)}"
    OfflineDownloadState.Failed -> errorMessage ?: "Download failed"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> String.format(Locale.US, "%.0f MB", bytes / 1_048_576.0)
    bytes >= 1_024L -> String.format(Locale.US, "%.0f KB", bytes / 1_024.0)
    else -> "$bytes B"
}
