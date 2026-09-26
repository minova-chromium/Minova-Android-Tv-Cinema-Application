package com.minova.cinema.ui.browse

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.minova.cinema.data.local.HomeLayoutPreferences
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.ui.settings.SettingsPrimaryButton
import com.minova.cinema.ui.settings.SettingsSecondaryButton
import com.minova.cinema.ui.platform.DeviceProfile
import com.minova.cinema.ui.platform.rememberDeviceProfile
import com.minova.cinema.ui.theme.*

@Composable
internal fun TitleActionsDialog(content: MediaContent, saved: Boolean, onDismiss: () -> Unit,
    onOpen: () -> Unit, onPlay: (Boolean) -> Unit, onWatchlist: () -> Unit, onWatched: () -> Unit,
    onResetPlaybackPreferences: () -> Unit = {}) {
    val handheld = rememberDeviceProfile() == DeviceProfile.Handheld
    val first = remember { FocusRequester() }
    val activationGuard = remember { DialogActivationKeyGuard() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LazyColumn(Modifier.onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            when (key.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A ->
                    activationGuard.consume(key.keyCode, key.action == KeyEvent.ACTION_DOWN,
                        key.repeatCount, key.isCanceled)
                else -> false
            }
        }.then(if (handheld) Modifier.fillMaxWidth(0.92f) else Modifier.width(500.dp))
            .heightIn(max = if (handheld) 620.dp else 450.dp)
            .background(MinovaNightDeep, RoundedCornerShape(18.dp))
            .padding(if (handheld) 20.dp else 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(content.title, color = MinovaWhite) }
            if (content.kind == MediaKind.Movie || content.kind == MediaKind.Episode) {
                item { SettingsPrimaryButton(onClick = { onPlay(false) }, modifier = Modifier.fillMaxWidth().focusRequester(first)) {
                    Text(if (content.viewOffsetMs > 0) "Resume" else "Play")
                } }
                if (content.viewOffsetMs > 0) item { SettingsSecondaryButton(onClick = { onPlay(true) }, modifier = Modifier.fillMaxWidth()) { Text("Play from beginning") } }
            }
            item { SettingsSecondaryButton(onClick = onWatchlist, modifier = Modifier.fillMaxWidth().then(
                if (content.kind != MediaKind.Movie && content.kind != MediaKind.Episode) Modifier.focusRequester(first) else Modifier
            )) { Text(if (saved) "Remove from Watchlist" else "Add to Watchlist") } }
            item { SettingsSecondaryButton(onClick = onWatched, modifier = Modifier.fillMaxWidth()) { Text(if (content.isWatched) "Mark unwatched" else "Mark watched") } }
            item { SettingsSecondaryButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("More information") } }
            item { SettingsSecondaryButton(onClick = onResetPlaybackPreferences, modifier = Modifier.fillMaxWidth()) { Text("Reset this title's playback preferences") } }
            item { SettingsSecondaryButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") } }
        }
        LaunchedEffect(handheld) {
            if (!handheld) first.requestFocus()
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun HomeCustomizationDialog(shelves: List<Pair<String, String>>, preferences: HomeLayoutPreferences,
    onSave: (HomeLayoutPreferences) -> Unit, onDismiss: () -> Unit) {
    val handheld = rememberDeviceProfile() == DeviceProfile.Handheld
    var draft by remember { mutableStateOf(preferences) }
    val order = remember(draft.shelfOrder, shelves) {
        (draft.shelfOrder + shelves.map { it.first }).distinct().filter { key -> shelves.any { it.first == key } }
    }
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(MinovaNightDeep)
                .then(if (handheld) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)
                .padding(if (handheld) 16.dp else 32.dp),
        ) {
            Text("Your home screen", color = MinovaWhite)
            Text("Saved for this Plex profile · Show/hide shelves and move them with the arrow buttons", color = MinovaMuted,
                modifier = Modifier.padding(vertical = 10.dp))
            Text("Open the app on", color = MinovaCyan, modifier = Modifier.padding(bottom = 8.dp))
            val openingTabs: @Composable () -> Unit = {
                listOf("Home", "Movies", "Series", "MyList").forEachIndexed { index, tab ->
                    SettingsSecondaryButton(onClick = { draft = draft.copy(openingTab = tab) },
                        modifier = (if (index == 0) Modifier.focusRequester(first) else Modifier)
                            .then(if (handheld) Modifier.fillMaxWidth(0.48f) else Modifier)) {
                        Text((if (draft.openingTab == tab) "✓ " else "") + if (tab == "MyList") "Watchlist" else tab)
                    }
                }
            }
            if (handheld) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = { openingTabs() },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { openingTabs() }
            }
            LazyColumn(Modifier.weight(1f).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(order, key = { it }) { key ->
                    val index = order.indexOf(key)
                    if (handheld) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsSecondaryButton(onClick = {
                            draft = draft.copy(hiddenShelves = if (key in draft.hiddenShelves) draft.hiddenShelves - key else draft.hiddenShelves + key)
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text((if (key in draft.hiddenShelves) "○  " else "✓  ") + shelves.first { it.first == key }.second)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsSecondaryButton(enabled = index > 0, onClick = {
                                val moved = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                                draft = draft.copy(shelfOrder = moved)
                            }, modifier = Modifier.weight(1f)) { Text("↑ Move up") }
                            SettingsSecondaryButton(enabled = index < order.lastIndex, onClick = {
                                val moved = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                                draft = draft.copy(shelfOrder = moved)
                            }, modifier = Modifier.weight(1f)) { Text("↓ Move down") }
                        }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingsSecondaryButton(onClick = {
                            draft = draft.copy(hiddenShelves = if (key in draft.hiddenShelves) draft.hiddenShelves - key else draft.hiddenShelves + key)
                        }, modifier = Modifier.weight(1f)) {
                            Text((if (key in draft.hiddenShelves) "○  " else "✓  ") + shelves.first { it.first == key }.second)
                        }
                        SettingsSecondaryButton(enabled = index > 0, onClick = {
                            val moved = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                            draft = draft.copy(shelfOrder = moved)
                        }) { Text("↑ Up") }
                        SettingsSecondaryButton(enabled = index < order.lastIndex, onClick = {
                            val moved = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                            draft = draft.copy(shelfOrder = moved)
                        }) { Text("↓ Down") }
                    }
                }
            }
            if (handheld) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsPrimaryButton(onClick = { onSave(draft); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Save & done") }
                    SettingsSecondaryButton(onClick = { draft = HomeLayoutPreferences() }, modifier = Modifier.fillMaxWidth()) { Text("Reset layout") }
                    SettingsSecondaryButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingsPrimaryButton(onClick = { onSave(draft); onDismiss() }) { Text("Save & done") }
                SettingsSecondaryButton(onClick = { draft = HomeLayoutPreferences() }) { Text("Reset layout") }
                SettingsSecondaryButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
        LaunchedEffect(handheld) {
            if (!handheld) first.requestFocus()
        }
    }
}
