package com.minova.cinema.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.minova.cinema.domain.CinemaCatalog
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.collectionMembers
import com.minova.cinema.ui.settings.SettingsSecondaryButton
import com.minova.cinema.ui.theme.*
import kotlinx.coroutines.CancellationException

/** Show Plex's collection records and their assigned posters, keyed by server identity. */
@Composable
internal fun CollectionsScreen(
    catalog: CinemaCatalog,
    entryFocus: FocusRequester,
    onHeaderFocus: () -> Unit,
    onOpen: (MediaContent) -> Unit,
    loadMembers: (suspend (String) -> List<MediaContent>)? = null,
) {
    val library = remember(catalog.movies, catalog.shows) {
        (catalog.movies + catalog.shows).distinctBy(MediaContent::ratingKey)
    }
    val collections = remember(catalog.collections) {
        catalog.collections.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var lastCollection by rememberSaveable { mutableStateOf<String?>(null) }
    var transferFocus by remember { mutableStateOf(false) }
    val collectionState = rememberLazyGridState()
    val memberStates = rememberSaveableStateHolder()
    val activeCollection = collections.firstOrNull { it.ratingKey == selected }
    LaunchedEffect(activeCollection) { if (activeCollection == null) selected = null }
    fun returnToCollections() { selected = null; transferFocus = true }
    BackHandler(enabled = activeCollection != null) { returnToCollections() }
    var members by remember(activeCollection?.ratingKey) { mutableStateOf(emptyList<MediaContent>()) }
    var loading by remember(activeCollection?.ratingKey) { mutableStateOf(activeCollection != null) }
    var loadFailed by remember(activeCollection?.ratingKey) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val currentLoader by rememberUpdatedState(loadMembers)
    LaunchedEffect(activeCollection, library, retry) {
        val collection = activeCollection ?: return@LaunchedEffect
        loading = true
        loadFailed = false
        try {
            members = currentLoader?.invoke(collection.ratingKey) ?: collectionMembers(library, collection.title)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loadFailed = true
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(MinovaNightDeep).padding(top = 72.dp)) {
        if (activeCollection == null) {
            Text("Collections", color = MinovaWhite, style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 44.dp))
            Text(if (collections.isEmpty()) "No collections were found. Add titles to a collection in your library, then refresh."
                else "${collections.size} collections · Movies & series",
                color = MinovaMuted, modifier = Modifier.padding(start = 44.dp, top = 6.dp, bottom = 8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(5), state = collectionState,
                modifier = Modifier.weight(1f).focusRequester(entryFocus).focusGroup().testTag("collections-grid"),
                contentPadding = PaddingValues(horizontal = 44.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                itemsIndexed(collections, key = { _, collection -> collection.ratingKey }) { index, collection ->
                    val focus = remember { FocusRequester() }
                    var focused by remember { mutableStateOf(false) }
                    Column(Modifier.fillMaxWidth().testTag("collection-${collection.ratingKey}").focusRequester(focus)
                        .onFocusChanged { focused = it.isFocused; if (it.isFocused) lastCollection = collection.ratingKey }
                        .onGloballyPositioned {
                            if (transferFocus && collection.ratingKey == lastCollection) { transferFocus = false; focus.requestFocus() }
                        }
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp && index < 5) {
                                onHeaderFocus(); true
                            } else false
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .border(if (focused) 3.dp else 1.dp, if (focused) MinovaCyan else MinovaSurfaceRaised, RoundedCornerShape(12.dp))
                        .background(if (focused) MinovaSurfaceRaised else MinovaSurface)
                        .clickable(role = Role.Button) { selected = collection.ratingKey; transferFocus = true }
                    ) {
                        BrowseArtwork(collection.posterUrl, "${collection.title} collection poster",
                            Modifier.fillMaxWidth().aspectRatio(2f / 3f), contentScale = ContentScale.Fit)
                        Column(Modifier.padding(10.dp)) {
                            Text(collection.title, color = MinovaWhite, style = MaterialTheme.typography.titleMedium,
                                maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(collection.childCount?.let { "$it ${if (it == 1) "title" else "titles"}" }
                                ?: collection.libraryTitle, color = MinovaMuted,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        } else {
            Row(Modifier.padding(horizontal = 44.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                SettingsSecondaryButton(onClick = ::returnToCollections,
                    modifier = Modifier.testTag("collections-back").focusRequester(entryFocus)
                        .onGloballyPositioned { if (transferFocus) { transferFocus = false; entryFocus.requestFocus() } }
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp) { onHeaderFocus(); true } else false
                        }) { Text("All collections") }
                Column(Modifier.weight(1f)) {
                    Text(activeCollection.title, color = MinovaWhite, style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (loading) "Loading titles…" else if (loadFailed) "Could not load collection titles."
                        else "${members.size} ${if (members.size == 1) "title" else "titles"} · Release order", color = MinovaMuted)
                }
            }
            if (loadFailed) {
                SettingsSecondaryButton(onClick = { retry++ }, modifier = Modifier.padding(44.dp)) { Text("Retry") }
            } else if (!loading && members.isEmpty()) {
                Text("This collection has no titles.", color = MinovaMuted, modifier = Modifier.padding(44.dp))
            }
            memberStates.SaveableStateProvider(activeCollection.ratingKey) {
                val grid = rememberLazyGridState()
                val memory = LocalBrowseFocus.current
                val identity = "collection:${activeCollection.ratingKey}"
                LaunchedEffect(members) {
                    if (memory.restore) {
                        val index = members.indexOfFirst { memory.lastKey == "$identity:grid-${it.ratingKey}" }
                        if (index >= 0) grid.scrollToItem(index)
                    }
                }
                CompositionLocalProvider(LocalShelfIdentity provides identity, LocalArtworkSequence provides members) {
                    LazyVerticalGrid(columns = GridCells.Fixed(5), state = grid,
                        modifier = Modifier.weight(1f).focusGroup().testTag("collection-members"),
                        contentPadding = PaddingValues(horizontal = 44.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        itemsIndexed(if (loadFailed) emptyList() else members, key = { _, item -> item.ratingKey }) { index, content ->
                            PosterCard(content, Modifier.fillMaxWidth().testTag("collection-member-${content.ratingKey}")
                                .onPreviewKeyEvent {
                                    if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp && index < 5) {
                                        entryFocus.requestFocus(); true
                                    } else false
                                }, onOpen)
                        }
                    }
                }
            }
        }
    }
}
