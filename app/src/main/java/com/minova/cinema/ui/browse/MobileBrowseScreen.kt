package com.minova.cinema.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.google.gson.Gson
import com.minova.cinema.R
import com.minova.cinema.data.local.HomeLayoutPreferences
import com.minova.cinema.data.local.orderedShelfKeys
import com.minova.cinema.domain.CinemaCatalog
import com.minova.cinema.domain.LibraryFilter
import com.minova.cinema.domain.MediaCollection
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.collectionMembers
import com.minova.cinema.domain.filterLibrary
import com.minova.cinema.ui.theme.MinovaBlack
import com.minova.cinema.ui.theme.MinovaCyan
import com.minova.cinema.ui.theme.MinovaMuted
import com.minova.cinema.ui.theme.MinovaNightDeep
import com.minova.cinema.ui.theme.MinovaSurface
import com.minova.cinema.ui.theme.MinovaSurfaceRaised
import com.minova.cinema.ui.theme.MinovaTeal
import com.minova.cinema.ui.theme.MinovaWhite
import com.minova.cinema.ui.cast.MinovaCastButton
import kotlinx.coroutines.CancellationException

private enum class MobileBrowseTab(val label: String) {
    Home("Home"),
    Movies("Movies"),
    Series("Series"),
    Collections("Collections"),
    MyList("Watchlist"),
    Search("Search"),
}

/** Touch-first phone presentation backed by the exact same Plex state and actions as TV. */
@Composable
internal fun MobileBrowseScreen(
    catalog: CinemaCatalog,
    onOpen: (MediaContent) -> Unit,
    onPlay: (MediaContent) -> Unit,
    onToggleMyList: (MediaContent) -> Unit,
    onSettings: () -> Unit,
    onWatchlistRefresh: () -> Unit,
    homePreferences: HomeLayoutPreferences,
    onPlayFromBeginning: (MediaContent) -> Unit,
    onSetWatched: (MediaContent, Boolean) -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean,
    onHighlighted: (MediaContent?) -> Unit,
    onResetPlaybackPreferences: (MediaContent) -> Unit,
    loadCollectionMembers: (suspend (String) -> List<MediaContent>)?,
) {
    var tab by rememberSaveable {
        mutableStateOf(
            MobileBrowseTab.entries.firstOrNull { it.name == homePreferences.openingTab }
                ?: MobileBrowseTab.Home,
        )
    }
    var quickContent by remember { mutableStateOf<MediaContent?>(null) }
    var filterOpen by remember { mutableStateOf(false) }
    var filtersByTab by rememberSaveable { mutableStateOf(hashMapOf<String, String>()) }
    val activeFilterJson = filtersByTab[tab.name].orEmpty()
    val filter = remember(activeFilterJson) {
        runCatching { Gson().fromJson(activeFilterJson, LibraryFilter::class.java) }.getOrNull()
            ?: LibraryFilter()
    }
    val source = remember(catalog, tab) {
        when (tab) {
            MobileBrowseTab.Movies -> catalog.movies
            MobileBrowseTab.Series -> catalog.shows
            MobileBrowseTab.MyList -> catalog.myList
            else -> emptyList()
        }
    }
    val filtered = remember(source, filter) { filterLibrary(source, filter) }

    LaunchedEffect(tab) {
        if (tab == MobileBrowseTab.MyList) onWatchlistRefresh()
        onHighlighted(null)
    }

    CompositionLocalProvider(LocalQuickActions provides { quickContent = it }) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MinovaNightDeep)
                .statusBarsPadding(),
        ) {
            MobileTopBar(
                onSearch = { tab = MobileBrowseTab.Search },
                onRefresh = onRefresh,
                refreshing = refreshing,
                onSettings = onSettings,
            )
            Box(Modifier.weight(1f)) {
                when (tab) {
                    MobileBrowseTab.Home -> MobileHome(
                        catalog = catalog,
                        preferences = homePreferences,
                        onOpen = onOpen,
                        onPlay = onPlay,
                        onQuickActions = { quickContent = it },
                        onHighlighted = onHighlighted,
                    )
                    MobileBrowseTab.Movies,
                    MobileBrowseTab.Series,
                    MobileBrowseTab.MyList,
                    -> MobileLibrary(
                        title = tab.label,
                        items = filtered,
                        filterActive = filter != LibraryFilter(),
                        onFilter = { filterOpen = true },
                        onOpen = onOpen,
                        onQuickActions = { quickContent = it },
                    )
                    MobileBrowseTab.Collections -> MobileCollections(
                        catalog = catalog,
                        loadMembers = loadCollectionMembers,
                        onOpen = onOpen,
                        onQuickActions = { quickContent = it },
                    )
                    MobileBrowseTab.Search -> MobileSearch(
                        items = (catalog.movies + catalog.shows).distinctBy(MediaContent::ratingKey),
                        onOpen = onOpen,
                        onQuickActions = { quickContent = it },
                    )
                }
            }
            MobileBottomBar(selected = tab, onSelect = { tab = it })
        }
    }

    quickContent?.let { content ->
        fun dismiss() { quickContent = null }
        TitleActionsDialog(
            content = content,
            saved = catalog.myList.any { it.ratingKey == content.ratingKey },
            onDismiss = ::dismiss,
            onOpen = { dismiss(); onOpen(content) },
            onPlay = { restart -> dismiss(); if (restart) onPlayFromBeginning(content) else onPlay(content) },
            onWatchlist = { onToggleMyList(content); dismiss() },
            onWatched = { onSetWatched(content, !content.isWatched); dismiss() },
            onResetPlaybackPreferences = { onResetPlaybackPreferences(content); dismiss() },
        )
    }
    if (filterOpen) {
        com.minova.cinema.ui.experience.LibraryFilterDialog(
            items = source,
            initial = filter,
            onApply = {
                filtersByTab = HashMap(filtersByTab).apply { put(tab.name, Gson().toJson(it)) }
                filterOpen = false
            },
        )
    }
}

@Composable
private fun MobileTopBar(
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean,
    onSettings: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    Row(
        Modifier.fillMaxWidth().height(if (landscape) 56.dp else 74.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = null,
            modifier = Modifier.size(if (landscape) 34.dp else 40.dp),
        )
        Column(Modifier.padding(start = 9.dp).weight(1f)) {
            Text("MINOVA", color = MinovaWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Text(
                "CINEMA",
                color = MinovaCyan,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.7.sp,
            )
        }
        MobileIconButton(Icons.Rounded.Search, "Search", onSearch, background = false)
        if (refreshing) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MinovaCyan,
                    strokeWidth = 2.dp,
                )
            }
        } else {
            MobileIconButton(Icons.Rounded.Refresh, "Refresh library", onRefresh, background = false)
        }
        MinovaCastButton()
        MobileIconButton(Icons.Rounded.Settings, "Settings", onSettings, background = false)
    }
}

@Composable
private fun MobileIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    background: Boolean = true,
) {
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .background(if (background) MinovaSurface else Color.Transparent)
            .clickable(
            role = Role.Button,
            onClickLabel = description,
            onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icon, description, tint = MinovaWhite, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun MobileBottomBar(selected: MobileBrowseTab, onSelect: (MobileBrowseTab) -> Unit) {
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val destinations = listOf(
        Triple(MobileBrowseTab.Home, Icons.Rounded.Home, "Home"),
        Triple(MobileBrowseTab.Movies, Icons.Rounded.Movie, "Movies"),
        Triple(MobileBrowseTab.Series, Icons.Rounded.Tv, "Series"),
        Triple(MobileBrowseTab.Collections, Icons.Rounded.CollectionsBookmark, "Collections"),
        Triple(MobileBrowseTab.MyList, Icons.Rounded.Bookmark, "Watchlist"),
    )
    Row(
        Modifier.fillMaxWidth().background(MinovaBlack)
            .border(width = 0.5.dp, color = MinovaSurfaceRaised)
            .navigationBarsPadding().height(if (landscape) 48.dp else 67.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        destinations.forEach { (tab, icon, label) ->
            val active = selected == tab
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .padding(top = if (landscape) 4.dp else 8.dp, bottom = if (landscape) 4.dp else 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (landscape) Arrangement.Center else Arrangement.SpaceBetween,
            ) {
                androidx.compose.material3.Icon(
                    icon,
                    label,
                    tint = if (active) MinovaCyan else MinovaMuted,
                    modifier = Modifier.size(25.dp),
                )
                if (!landscape) {
                    Text(
                        label,
                        color = if (active) MinovaCyan else MinovaMuted,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileHome(
    catalog: CinemaCatalog,
    preferences: HomeLayoutPreferences,
    onOpen: (MediaContent) -> Unit,
    onPlay: (MediaContent) -> Unit,
    onQuickActions: (MediaContent) -> Unit,
    onHighlighted: (MediaContent?) -> Unit,
) {
    val shelves = remember(catalog, preferences) {
        val available = buildHomeDiscoveryShelves(catalog)
        orderedShelfKeys(available.map(DiscoveryShelf::key), preferences).map { key -> available.first { it.key == key } }
    }
    LaunchedEffect(Unit) { onHighlighted(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
        if (catalog.continueWatching.isNotEmpty()) {
            item {
                MobileShelf(
                    title = "Continue Watching",
                    items = catalog.continueWatching,
                    onTap = onPlay,
                    onQuickActions = onQuickActions,
                    showPlayBadge = true,
                )
            }
        }
        items(shelves, key = DiscoveryShelf::key) { shelf ->
            MobileShelf(shelf.title, shelf.media, onOpen, onQuickActions)
        }
        if (catalog.movies.isEmpty() && catalog.shows.isEmpty()) {
            item { MobileEmpty("Your Plex library is empty.") }
        }
    }
}

@Composable
private fun MobileAction(label: String, icon: ImageVector?, primary: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.height(43.dp).clip(RoundedCornerShape(10.dp))
            .background(if (primary) MinovaCyan else MinovaSurface.copy(alpha = 0.94f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        icon?.let { androidx.compose.material3.Icon(it, null, tint = if (primary) MinovaBlack else MinovaWhite, modifier = Modifier.size(20.dp)) }
        Text(label, color = if (primary) MinovaBlack else MinovaWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun MobileShelf(
    title: String,
    items: List<MediaContent>,
    onTap: (MediaContent) -> Unit,
    onQuickActions: (MediaContent) -> Unit,
    showPlayBadge: Boolean = false,
) {
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    Column(Modifier.padding(top = 18.dp)) {
        Text(title, color = MinovaWhite, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            items(items, key = { "$title-${it.ratingKey}" }) { content ->
                MobileMediaCard(
                    content = content,
                    landscape = landscape,
                    modifier = Modifier.width(if (landscape) 160.dp else 100.dp),
                    onOpen = onTap,
                    onQuickActions = onQuickActions,
                    showPlayBadge = showPlayBadge,
                )
            }
        }
    }
}

@Composable
private fun MobileLibrary(
    title: String,
    items: List<MediaContent>,
    filterActive: Boolean,
    onFilter: () -> Unit,
    onOpen: (MediaContent) -> Unit,
    onQuickActions: (MediaContent) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = MinovaWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("${items.size} titles", color = MinovaMuted)
            }
            MobileIconButton(Icons.Rounded.FilterList, if (filterActive) "Filters active" else "Filter", onFilter)
        }
        if (items.isEmpty()) {
            MobileEmpty("No matching titles were found.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items, key = MediaContent::ratingKey) { content ->
                    MobileMediaCard(content, false, Modifier.fillMaxWidth(), onOpen, onQuickActions)
                }
            }
        }
    }
}

@Composable
private fun MobileSearch(
    items: List<MediaContent>,
    onOpen: (MediaContent) -> Unit,
    onQuickActions: (MediaContent) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    val results = remember(query, items) {
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
        if (tokens.isEmpty()) emptyList() else items.filter { item ->
            val searchable = listOf(item.title, item.secondaryTitle.orEmpty(), item.year?.toString().orEmpty(), item.genres.joinToString(" ")).joinToString(" ").lowercase()
            tokens.all(searchable::contains)
        }.sortedBy(MediaContent::title)
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp).height(54.dp)
                .background(MinovaSurface, RoundedCornerShape(14.dp))
                .border(1.dp, MinovaSurfaceRaised, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(Icons.Rounded.Search, null, tint = MinovaMuted)
            BasicTextField(
                value = query,
                onValueChange = { query = it.take(100) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MinovaWhite),
                cursorBrush = SolidColor(MinovaCyan),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp).focusRequester(focusRequester),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isBlank()) Text("Search titles, genres or years", color = MinovaMuted)
                        field()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable { query = "" },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(Icons.Rounded.Close, "Clear search", tint = MinovaWhite)
                }
            }
        }
        when {
            query.isBlank() -> MobileEmpty("Search your complete Plex library.")
            results.isEmpty() -> MobileEmpty("No matching titles were found.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(results, key = MediaContent::ratingKey) { content ->
                    MobileMediaCard(content, false, Modifier.fillMaxWidth(), onOpen, onQuickActions)
                }
            }
        }
    }
}

@Composable
private fun MobileCollections(
    catalog: CinemaCatalog,
    loadMembers: (suspend (String) -> List<MediaContent>)?,
    onOpen: (MediaContent) -> Unit,
    onQuickActions: (MediaContent) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val collection = catalog.collections.firstOrNull { it.ratingKey == selected }
    var members by remember(selected) { mutableStateOf(emptyList<MediaContent>()) }
    var loading by remember(selected) { mutableStateOf(selected != null) }
    var failed by remember(selected) { mutableStateOf(false) }
    val library = remember(catalog) { (catalog.movies + catalog.shows).distinctBy(MediaContent::ratingKey) }
    BackHandler(enabled = collection != null) { selected = null }
    LaunchedEffect(collection?.ratingKey, library) {
        val active = collection ?: return@LaunchedEffect
        loading = true
        failed = false
        try {
            members = loadMembers?.invoke(active.ratingKey) ?: collectionMembers(library, active.title)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (collection != null) {
                MobileAction("Back", null, false) { selected = null }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(collection?.title ?: "Collections", color = MinovaWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        collection == null -> "${catalog.collections.size} collections"
                        loading -> "Loading titles…"
                        failed -> "Could not load collection titles"
                        else -> "${members.size} titles · release order"
                    },
                    color = MinovaMuted,
                )
            }
        }
        if (collection == null) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp), modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(catalog.collections, key = MediaCollection::ratingKey) { item ->
                    MobileCollectionCard(item) { selected = item.ratingKey }
                }
            }
        } else if (!loading && !failed) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp), modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(members, key = MediaContent::ratingKey) { content ->
                    MobileMediaCard(content, false, Modifier.fillMaxWidth(), onOpen, onQuickActions)
                }
            }
        }
    }
}

@Composable
private fun MobileCollectionCard(collection: MediaCollection, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MinovaSurface)
            .clickable(onClick = onClick).padding(bottom = 9.dp),
    ) {
        BrowseArtwork(collection.posterUrl, collection.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f), ContentScale.Fit)
        Text(collection.title, color = MinovaWhite, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
        Text(collection.childCount?.let { "$it titles" } ?: collection.libraryTitle, color = MinovaMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 9.dp))
    }
}

@Composable
private fun MobileMediaCard(
    content: MediaContent,
    landscape: Boolean,
    modifier: Modifier,
    onOpen: (MediaContent) -> Unit,
    onQuickActions: (MediaContent) -> Unit,
    showPlayBadge: Boolean = false,
) {
    val ratio = if (landscape) 16f / 9f else 2f / 3f
    val haptics = LocalHapticFeedback.current
    Column(
        modifier.combinedClickable(
                role = Role.Button,
                onClick = { onOpen(content) },
                onLongClickLabel = "Title actions",
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onQuickActions(content)
                },
            ).padding(bottom = 2.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(ratio)
                .clip(RoundedCornerShape(10.dp)).background(MinovaBlack),
        ) {
            BrowseArtwork(
                if (landscape) content.backdropUrl ?: content.posterUrl else content.posterUrl,
                content.title,
                Modifier.fillMaxSize(),
                if (landscape) ContentScale.Crop else ContentScale.Fit,
            )
            if (content.progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(content.progress).height(4.dp).background(MinovaTeal))
            }
            if (content.isWatched) {
                Box(Modifier.align(Alignment.TopStart).padding(7.dp).size(25.dp).background(MinovaCyan, CircleShape), contentAlignment = Alignment.Center) {
                    Text("✓", color = MinovaBlack, fontWeight = FontWeight.Bold)
                }
            }
            if (showPlayBadge) {
                Box(
                    Modifier.align(Alignment.Center).size(36.dp)
                        .background(MinovaBlack.copy(alpha = 0.76f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Rounded.PlayArrow,
                        "Resume",
                        tint = MinovaWhite,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
        }
        Column(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(top = 7.dp)) {
            Text(content.title, color = MinovaWhite, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Text(
                content.remainingTimeLabel ?: content.secondaryTitle ?: content.year?.toString().orEmpty(),
                color = if (content.remainingTimeLabel != null) MinovaTeal else MinovaMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun MobileEmpty(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MinovaMuted, style = MaterialTheme.typography.bodyLarge)
    }
}
