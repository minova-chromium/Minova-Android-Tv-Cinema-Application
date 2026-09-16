package com.minova.cinema.ui.experience

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.minova.cinema.domain.*
import com.minova.cinema.ui.settings.SettingsPrimaryButton
import com.minova.cinema.ui.settings.SettingsSecondaryButton
import com.minova.cinema.ui.theme.*

@Composable
internal fun CinemaPanel(title: String, onClose: () -> Unit, doneLabel: String = "Done", status: String? = null, content: LazyListScope.() -> Unit) {
    val first = remember { FocusRequester() }
    val body = remember { FocusRequester() }
    val activation = remember { com.minova.cinema.ui.browse.DialogActivationKeyGuard() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().testTag("cinema-panel").onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            if (key.keyCode in listOf(android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER, android.view.KeyEvent.KEYCODE_BUTTON_A)) {
                activation.consume(key.keyCode, key.action == android.view.KeyEvent.ACTION_DOWN, key.repeatCount, key.isCanceled)
            } else false
        }.background(MinovaNightDeep).padding(horizontal = 40.dp, vertical = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(title, color = MinovaWhite, style = androidx.tv.material3.MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f))
                SettingsPrimaryButton(onClick = onClose, modifier = Modifier.testTag("cinema-panel-done")
                    .focusRequester(first).focusProperties { down = body }) { Text(doneLabel) }
            }
            status?.let { Text(it, color = MinovaMuted, modifier = Modifier.padding(top = 10.dp)) }
            LazyColumn(Modifier.weight(1f).testTag("cinema-panel-list").focusRequester(body).focusGroup().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 20.dp), content = content)
        }
        LaunchedEffect(Unit) { first.requestFocus() }
    }
}

@Composable
internal fun ChoiceStrip(label: String, choices: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = MinovaMuted)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(3.dp)) {
            itemsIndexed(choices, key = { _, choice -> choice.first }) { index, (value, title) ->
                SettingsSecondaryButton(onClick = { onSelect(value) }, modifier = Modifier.focusProperties {
                    if (index == 0) left = FocusRequester.Cancel
                    if (index == choices.lastIndex) right = FocusRequester.Cancel
                }) {
                    Text((if (selected == value) "✓  " else "") + title)
                }
            }
        }
    }
}

@Composable
internal fun LibraryFilterDialog(items: List<MediaContent>, initial: LibraryFilter, onApply: (LibraryFilter) -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    val matches = remember(items, draft) { filterLibrary(items, draft).size }
    CinemaPanel("Library filters · $matches titles", onClose = { onApply(draft) }, doneLabel = "Apply") {
        item { ChoiceStrip("Watch status", WatchFilter.entries.map { it.name to it.label }, draft.watch.name) { draft = draft.copy(watch = WatchFilter.valueOf(it)) } }
        item { ChoiceStrip("Genre", listOf("" to "All genres") + items.flatMap { it.genres }.distinct().sorted().map { it to it }, draft.genre.orEmpty()) { draft = draft.copy(genre = it.ifBlank { null }) } }
        item { ChoiceStrip("Year", listOf("" to "Any year") + items.mapNotNull { it.year }.distinct().sortedDescending().map { "$it" to "$it" }, draft.year?.toString().orEmpty()) { draft = draft.copy(year = it.toIntOrNull()) } }
        item { ChoiceStrip("Resolution (reported by Plex)", listOf("" to "Any resolution") + items.mapNotNull { it.resolution }.distinct().sorted().map { it to it }, draft.resolution.orEmpty()) { draft = draft.copy(resolution = it.ifBlank { null }) } }
        item { ChoiceStrip("Audio language (reported by Plex)", listOf("" to "Any language") + items.flatMap { it.audioLanguages }.distinct().sorted().map { it to it }, draft.language.orEmpty()) { draft = draft.copy(language = it.ifBlank { null }) } }
        item { ChoiceStrip("Sort", LibrarySort.entries.map { it.name to it.label }, draft.sort.name) { draft = draft.copy(sort = LibrarySort.valueOf(it)) } }
        item { SettingsSecondaryButton(onClick = { draft = LibraryFilter() }) { Text("Reset all filters") } }
        item { Text("Unknown resolution or language won't match a specific filter. A–Z jumping uses the first matching title in the current sort order.", color = MinovaMuted) }
    }
}

@Composable
internal fun DiscoveryDialog(catalog: CinemaCatalog, onOpen: (MediaContent) -> Unit, onWatched: (MediaContent, Boolean) -> Unit, onClose: () -> Unit) {
    var tab by remember { mutableStateOf("Movie Night") }
    var minutes by remember { mutableIntStateOf(120) }
    var genres by remember { mutableStateOf(emptySet<String>()) }
    var seed by remember { mutableIntStateOf(0) }
    var collection by remember { mutableStateOf<String?>(null) }
    val all = remember(catalog) { (catalog.movies + catalog.shows + catalog.continueWatching).distinctBy { it.ratingKey } }
    val collections = remember(all) { all.flatMap { it.collections }.distinct().sorted() }
    val choices = remember(catalog.movies, minutes, genres, seed) { movieNightChoices(catalog.movies, minutes, genres, seed) }
    val members = remember(all, collection) { collection?.let { collectionMembers(all, it) }.orEmpty() }
    val history = remember(all) { all.filter { (it.lastViewedAtEpochSeconds ?: 0) > 0 }.sortedByDescending { it.lastViewedAtEpochSeconds }.take(100) }
    CinemaPanel("Discover your library", onClose) {
        item { ChoiceStrip("Choose a section", listOf("Movie Night", "Collections", "History").map { it to it }, tab) { tab = it } }
        when (tab) {
            "Movie Night" -> {
                item { ChoiceStrip("How much time do you have?", listOf(60, 90, 120, 150, 180, 240).map { "$it" to "$it min" }, "$minutes") { minutes = it.toInt() } }
                item {
                    Text("Pick any genres (optional)", color = MinovaMuted)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(catalog.movies.flatMap { it.genres }.distinct().sorted()) { genre ->
                            SettingsSecondaryButton(onClick = { genres = if (genre in genres) genres - genre else genres + genre }) {
                                Text((if (genre in genres) "✓ " else "") + genre)
                            }
                        }
                    }
                }
                item { SettingsSecondaryButton(onClick = { seed++ }) { Text("Pick another selection") } }
                if (choices.isEmpty()) item { Text("No unwatched movies fit. Try more time or fewer genre restrictions. Titles with unknown runtime are excluded.", color = MinovaMuted) }
                items(choices, key = { it.ratingKey }) { MediaChoice(it, onOpen) }
            }
            "Collections" -> {
                if (collections.isEmpty()) item { Text("No collections were reported. Add titles to a collection in your library, then refresh.", color = MinovaMuted) }
                item { ChoiceStrip("Collections", listOf("" to "Choose collection") + collections.map { it to it }, collection.orEmpty()) { collection = it.ifBlank { null } } }
                if (collection != null) {
                    item { Text("$collection · release order · ${members.size} titles", color = MinovaCyan) }
                    members.firstOrNull { !it.isWatched }?.let { next -> item {
                        SettingsPrimaryButton(onClick = { onOpen(next) }) { Text("Next unwatched: ${next.title}") }
                    } }
                    items(members, key = { it.ratingKey }) { MediaChoice(it, onOpen) }
                }
            }
            else -> {
                item { Text("Latest watched titles reported by Plex for this profile. One entry per title, not a log of every viewing.", color = MinovaMuted) }
                if (history.isEmpty()) item { Text("No viewing history is available in this library yet.", color = MinovaMuted) }
                items(history, key = { it.ratingKey }) { title ->
                    MediaChoice(title, onOpen)
                    SettingsSecondaryButton(onClick = { onWatched(title, !title.isWatched) }) { Text(if (title.isWatched) "Mark unwatched" else "Mark watched") }
                }
            }
        }
    }
}

@Composable
internal fun MediaChoice(content: MediaContent, onOpen: (MediaContent) -> Unit) {
    SettingsSecondaryButton(onClick = { onOpen(content) }, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            com.minova.cinema.ui.browse.BrowseArtwork(content.posterUrl, null, Modifier.width(66.dp).height(99.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(content.title, maxLines = 3)
                Text(content.metadataLine)
                Text(if (content.isWatched) "Watched" else if (content.viewOffsetMs > 0) "In progress" else "Unwatched")
            }
        }
    }
}
