package com.minova.cinema.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.minova.cinema.ui.theme.MinovaCyan
import com.minova.cinema.ui.theme.MinovaMuted
import com.minova.cinema.ui.theme.MinovaNightDeep
import com.minova.cinema.ui.theme.MinovaSurfaceRaised
import com.minova.cinema.update.AppUpdate

/** TV-first alert dialog with deterministic left/right/up D-pad navigation. */
@Composable
fun UpdateAvailableDialog(
    update: AppUpdate,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit,
) {
    val notesFocusRequester = remember { FocusRequester() }
    val updateFocusRequester = remember { FocusRequester() }
    val laterFocusRequester = remember { FocusRequester() }

    LaunchedEffect(update.versionName) {
        updateFocusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 560.dp, max = 760.dp)
                    .background(MinovaNightDeep, RoundedCornerShape(18.dp))
                    .border(1.dp, MinovaCyan.copy(alpha = 0.72f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 38.dp, vertical = 32.dp),
            ) {
                Text(
                    text = "New Version Available",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Minova Cinema ${update.versionName}",
                    color = MinovaCyan,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "WHAT'S NEW",
                    color = MinovaMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 26.dp, bottom = 8.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 210.dp)
                        .background(MinovaSurfaceRaised.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .focusRequester(notesFocusRequester)
                        .focusProperties { down = updateFocusRequester }
                        .focusable()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                ) {
                    Text(
                        text = update.releaseNotes,
                        color = MinovaMuted,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onUpdateNow,
                        modifier = Modifier
                            .focusRequester(updateFocusRequester)
                            .focusProperties {
                                up = notesFocusRequester
                                right = laterFocusRequester
                            },
                    ) {
                        Text("Update Now")
                    }
                    OutlinedButton(
                        onClick = onLater,
                        modifier = Modifier
                            .focusRequester(laterFocusRequester)
                            .focusProperties {
                                up = notesFocusRequester
                                left = updateFocusRequester
                            },
                    ) {
                        Text("Later")
                    }
                }
            }
        }
    }
}

/** Progress surface while DownloadManager owns the APK; Back hides it safely. */
@Composable
fun UpdateDownloadDialog(
    versionName: String,
    progressPercent: Int?,
    paused: Boolean,
    onHide: () -> Unit,
) {
    Dialog(
        onDismissRequest = onHide,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 520.dp, max = 680.dp)
                    .background(MinovaNightDeep, RoundedCornerShape(18.dp))
                    .border(1.dp, MinovaCyan.copy(alpha = 0.72f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 38.dp, vertical = 32.dp),
            ) {
                Text(
                    text = if (paused) "Download Paused" else "Downloading Update",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Minova Cinema $versionName",
                    color = MinovaCyan,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp)
                        .height(8.dp)
                        .background(MinovaSurfaceRaised, RoundedCornerShape(4.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((progressPercent ?: 4).coerceIn(1, 100) / 100f)
                            .height(8.dp)
                            .background(MinovaCyan, RoundedCornerShape(4.dp)),
                    )
                }
                Text(
                    text = when {
                        paused -> "Waiting for Android Download Manager to continue"
                        progressPercent != null -> "$progressPercent% complete"
                        else -> "Preparing download..."
                    },
                    color = MinovaMuted,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    text = "Press Back to hide this window. The download will continue.",
                    color = MinovaMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
