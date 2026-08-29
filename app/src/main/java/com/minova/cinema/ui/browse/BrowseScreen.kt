package com.minova.cinema.ui.browse

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.minova.cinema.R
import com.minova.cinema.domain.CinemaCatalog
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.ui.theme.MinovaBlack
import com.minova.cinema.ui.theme.MinovaCobalt
import com.minova.cinema.ui.theme.MinovaCyan
import com.minova.cinema.ui.theme.MinovaMuted
import com.minova.cinema.ui.theme.MinovaNightDeep
import com.minova.cinema.ui.theme.MinovaSurface
import com.minova.cinema.ui.theme.MinovaSurfaceRaised
import com.minova.cinema.ui.theme.MinovaTeal
import com.minova.cinema.ui.theme.MinovaWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private enum class BrowseTab(val label: String) {
    Home("Home"), Movies("Movies"), Series("Series"), MyList("Watchlist"), Search("Search"),
}

private enum class BrowseLayout { Rows, Grid }
private enum class ShelfStyle { Landscape, Poster }

private const val GridColumnCount = 6
private val AlphabetBuckets = listOf("#") + ('A'..'Z').map(Char::toString)

private data class BrowseShelf(
    val key: String,
    val title: String,
    val style: ShelfStyle,
    val items: List<MediaContent>,
)

@Composable
fun BrowseScreen(
    catalog: CinemaCatalog,
    onOpen: (MediaContent) -> Unit,
    onSettings: () -> Unit,
    onWatchlistRefresh: () -> Unit,
) {
    var tab by remember { mutableStateOf(BrowseTab.Home) }
    var layout by remember { mutableStateOf(BrowseLayout.Rows) }
    var selectedGenre by remember(tab) { mutableStateOf<String?>(null) }
    var filterOpen by remember { mutableStateOf(false) }
    val shelves = remember(catalog, tab) { buildShelves(catalog, tab) }
    val gridItems = remember(catalog, tab) {
        when (tab) {
            BrowseTab.Home -> emptyList()
            BrowseTab.Movies -> catalog.movies
            BrowseTab.Series -> catalog.shows
            BrowseTab.MyList -> catalog.myList
            BrowseTab.Search -> emptyList()
        }
    }
    val gridGenres = remember(gridItems) {
        gridItems
            .flatMap { it.genres }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    val filteredGridItems = remember(gridItems, selectedGenre) {
        selectedGenre?.let { genre ->
            gridItems.filter { item ->
                item.genres.any { it.equals(genre, ignoreCase = true) }
            }
        } ?: gridItems
    }
    val visibleShelves = remember(shelves, selectedGenre, tab) {
        filterShelvesByGenre(shelves, selectedGenre, tab)
    }
    val heroCandidates = remember(visibleShelves) {
        visibleShelves.flatMap(BrowseShelf::items).distinctBy(MediaContent::ratingKey)
    }
    var highlightedContent by remember(tab) {
        mutableStateOf(heroCandidates.firstOrNull())
    }

    LaunchedEffect(gridGenres, selectedGenre) {
        if (selectedGenre != null && gridGenres.none { it.equals(selectedGenre, ignoreCase = true) }) {
            selectedGenre = null
        }
    }

    LaunchedEffect(tab) {
        if (tab == BrowseTab.MyList) onWatchlistRefresh()
    }

    LaunchedEffect(heroCandidates) {
        if (highlightedContent?.ratingKey !in heroCandidates.map(MediaContent::ratingKey)) {
            highlightedContent = heroCandidates.firstOrNull()
        }
    }

    Box(Modifier.fillMaxSize().background(MinovaNightDeep)) {
        Header(
            selectedTab = tab,
            layout = layout,
            showLayout = tab != BrowseTab.Home && tab != BrowseTab.Search,
            showFilter = tab != BrowseTab.Home && gridGenres.isNotEmpty(),
            filterActive = selectedGenre != null,
            onTabSelected = { tab = it },
            onLayoutChanged = { layout = it },
            onFilter = { filterOpen = true },
            onSettings = onSettings,
        )

        val emptyMessage = when (tab) {
            BrowseTab.Home -> "Your Plex library is empty."
            BrowseTab.Movies -> "No movies were found."
            BrowseTab.Series -> "No series were found."
            BrowseTab.MyList -> "No titles from this Plex server are in your Watchlist."
            BrowseTab.Search -> "Search your Plex library."
        }
        if (tab == BrowseTab.Search) {
            SearchScreen(
                movies = catalog.movies,
                shows = catalog.shows,
                onOpen = onOpen,
            )
        } else if (tab != BrowseTab.Home && layout == BrowseLayout.Grid) {
            CatalogGrid(
                media = filteredGridItems,
                emptyMessage = emptyMessage,
                onOpen = onOpen,
            )
        } else {
            ShelfBrowser(
                shelves = visibleShelves,
                // The approved Movies/Series render is shelf-first. Keep the
                // featured backdrop on Home only so catalog browsing remains
                // spacious and immediately scannable.
                hero = highlightedContent.takeIf { tab == BrowseTab.Home },
                emptyMessage = emptyMessage,
                onOpen = onOpen,
                onHighlighted = { highlightedContent = it },
            )
        }

        if (filterOpen) {
            GenreFilterDialog(
                genres = gridGenres,
                selectedGenre = selectedGenre,
                onGenreSelected = { genre ->
                    selectedGenre = genre
                    filterOpen = false
                },
                onDismiss = { filterOpen = false },
            )
        }
    }
}

private fun filterShelvesByGenre(
    shelves: List<BrowseShelf>,
    selectedGenre: String?,
    tab: BrowseTab,
): List<BrowseShelf> {
    if (selectedGenre == null || tab == BrowseTab.Home) return shelves
    return shelves.mapNotNull { shelf ->
        val shouldKeep = when (tab) {
            BrowseTab.Movies, BrowseTab.Series ->
                shelf.style == ShelfStyle.Landscape || shelf.title.equals(selectedGenre, ignoreCase = true)
            BrowseTab.MyList -> true
            BrowseTab.Home, BrowseTab.Search -> false
        }
        if (!shouldKeep) return@mapNotNull null
        val matching = shelf.items.filter { item ->
            item.genres.any { it.equals(selectedGenre, ignoreCase = true) }
        }
        shelf.copy(items = matching).takeIf { matching.isNotEmpty() }
    }
}

private fun buildShelves(catalog: CinemaCatalog, tab: BrowseTab): List<BrowseShelf> = buildList {
    when (tab) {
        BrowseTab.Home -> {
            if (catalog.continueWatching.isNotEmpty()) {
                add(BrowseShelf("continue", "Continue Watching", ShelfStyle.Landscape, catalog.continueWatching))
            }
            if (catalog.myList.isNotEmpty()) {
                add(BrowseShelf("my-list", "Plex Watchlist", ShelfStyle.Poster, catalog.myList))
            }
            if (catalog.movies.isNotEmpty()) {
                add(BrowseShelf("home-movies", "Movies", ShelfStyle.Poster, catalog.movies))
            }
            if (catalog.shows.isNotEmpty()) {
                add(BrowseShelf("home-series", "Series", ShelfStyle.Poster, catalog.shows))
            }
        }
        BrowseTab.Movies -> {
            val continuingMovies = catalog.continueWatching.filter {
                it.kind == MediaKind.Movie
            }
            if (continuingMovies.isNotEmpty()) {
                add(
                    BrowseShelf(
                        key = "movie-continue",
                        title = "Continue Watching",
                        style = ShelfStyle.Landscape,
                        items = continuingMovies,
                    ),
                )
            }
            addAll(genreShelves("movie", "Movies", catalog.movies))
        }
        BrowseTab.Series -> {
            // Plex normally returns the next/in-progress episode here rather
            // than its parent show, so every episodic type belongs in Series.
            val continuingSeries = catalog.continueWatching.filter {
                it.kind == MediaKind.Show ||
                    it.kind == MediaKind.Season ||
                    it.kind == MediaKind.Episode
            }
            if (continuingSeries.isNotEmpty()) {
                add(
                    BrowseShelf(
                        key = "series-continue",
                        title = "Continue Watching",
                        style = ShelfStyle.Landscape,
                        items = continuingSeries,
                    ),
                )
            }
            addAll(genreShelves("series", "Series", catalog.shows))
        }
        BrowseTab.MyList -> if (catalog.myList.isNotEmpty()) {
            add(BrowseShelf("saved", "Saved for Later", ShelfStyle.Poster, catalog.myList))
        }
        BrowseTab.Search -> Unit
    }
}

private fun genreShelves(prefix: String, label: String, media: List<MediaContent>): List<BrowseShelf> {
    if (media.isEmpty()) return emptyList()
    val genres = sortedMapOf<String, MutableList<MediaContent>>(String.CASE_INSENSITIVE_ORDER)
    media.forEach { item ->
        item.genres.ifEmpty { listOf("Other") }.distinct().forEach { genre ->
            genres.getOrPut(genre) { mutableListOf() }.add(item)
        }
    }
    return buildList {
        add(BrowseShelf("$prefix-all", "All $label", ShelfStyle.Poster, media))
        genres.forEach { (genre, items) ->
            add(BrowseShelf("$prefix-$genre", genre, ShelfStyle.Poster, items))
        }
    }
}

@Composable
private fun Header(
    selectedTab: BrowseTab,
    layout: BrowseLayout,
    showLayout: Boolean,
    showFilter: Boolean,
    filterActive: Boolean,
    onTabSelected: (BrowseTab) -> Unit,
    onLayoutChanged: (BrowseLayout) -> Unit,
    onFilter: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .zIndex(10f)
            .fillMaxWidth()
            .height(76.dp)
            .background(MinovaBlack.copy(alpha = 0.97f))
            .border(0.5.dp, MinovaSurfaceRaised.copy(alpha = 0.7f))
            .padding(horizontal = 38.dp)
            .focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = "Minova Cinema",
            modifier = Modifier.size(40.dp).clip(CircleShape),
        )
        Image(
            painter = painterResource(R.drawable.minova_cinema_wordmark),
            contentDescription = "Minova Cinema",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(start = 12.dp, end = 30.dp)
                .width(140.dp)
                .height(52.dp),
        )
        BrowseTab.entries.forEach { item ->
            if (item == BrowseTab.Search) {
                HeaderIconItem(
                    icon = Icons.Default.Search,
                    contentDescription = "Search",
                    selected = item == selectedTab,
                    onClick = { onTabSelected(item) },
                )
            } else {
                HeaderItem(item.label, item == selectedTab) { onTabSelected(item) }
            }
        }
        Spacer(Modifier.weight(1f))
        if (showLayout) {
            HeaderIconItem(
                icon = if (layout == BrowseLayout.Rows) Icons.Default.ViewHeadline else Icons.Default.Apps,
                contentDescription = if (layout == BrowseLayout.Rows) {
                    "Row view. Switch to grid view"
                } else {
                    "Grid view. Switch to row view"
                },
                selected = false,
                onClick = {
                    onLayoutChanged(
                        if (layout == BrowseLayout.Rows) BrowseLayout.Grid else BrowseLayout.Rows,
                    )
                },
            )
        }
        if (showFilter) {
            HeaderIconItem(
                icon = Icons.Default.FilterList,
                contentDescription = "Filter by genre",
                selected = filterActive,
                onClick = onFilter,
            )
        }
        HeaderIconItem(
            icon = Icons.Default.Settings,
            contentDescription = "Settings",
            selected = false,
            onClick = onSettings,
        )
    }
}

@Composable
private fun HeaderIconItem(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .size(44.dp)
            .clip(RoundedCornerShape(7.dp))
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) MinovaSurfaceRaised else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            imageVector = icon,
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(
                when {
                    focused -> MinovaWhite
                    selected -> MinovaCyan
                    else -> MinovaMuted
                },
            ),
            modifier = Modifier.size(25.dp),
        )
    }
}

@Composable
private fun GenreFilterDialog(
    genres: List<String>,
    selectedGenre: String?,
    onGenreSelected: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstChoiceFocus = remember { FocusRequester() }
    var initialFocusRequested by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MinovaBlack.copy(alpha = 0.88f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(720.dp)
                    .height(440.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, MinovaSurfaceRaised, RoundedCornerShape(18.dp))
                    .background(MinovaNightDeep)
                    .padding(30.dp),
            ) {
                Text(
                    "Filter by genre",
                    color = MinovaWhite,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Choose a genre, or select All genres to clear the filter.",
                    color = MinovaMuted,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize().focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(3.dp, 3.dp, 3.dp, 18.dp),
                ) {
                    item(key = "all-genres") {
                        GenreFilterChoice(
                            label = "All genres",
                            selected = selectedGenre == null,
                            modifier = Modifier
                                .focusRequester(firstChoiceFocus)
                                .onGloballyPositioned {
                                    // A Dialog owns a separate TV window. Ask
                                    // for focus exactly when its first choice
                                    // is placed, so the popup is complete and
                                    // interactive on its first visible frame.
                                    if (!initialFocusRequested) {
                                        initialFocusRequested = true
                                        firstChoiceFocus.requestFocus()
                                    }
                                },
                            onClick = { onGenreSelected(null) },
                        )
                    }
                    items(genres, key = { "genre-$it" }) { genre ->
                        GenreFilterChoice(
                            label = genre,
                            selected = genre.equals(selectedGenre, ignoreCase = true),
                            onClick = { onGenreSelected(genre) },
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun GenreFilterChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused || selected) 2.dp else 1.dp,
                color = when {
                    focused -> MinovaWhite
                    selected -> MinovaCyan
                    else -> MinovaSurfaceRaised
                },
                shape = shape,
            )
            .clip(shape)
            .background(if (selected) MinovaCyan.copy(alpha = 0.16f) else MinovaSurface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            label,
            color = if (focused || selected) MinovaWhite else MinovaMuted,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeaderItem(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(7.dp))
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) MinovaSurfaceRaised else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = if (focused) MinovaWhite else if (selected) MinovaCyan else MinovaMuted,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ShelfBrowser(
    shelves: List<BrowseShelf>,
    hero: MediaContent?,
    emptyMessage: String,
    onOpen: (MediaContent) -> Unit,
    onHighlighted: (MediaContent) -> Unit,
) {
    if (shelves.isEmpty()) return EmptyMessage(emptyMessage)
    val heroFocus = remember { FocusRequester() }
    val firstCardFocus = remember(shelves) { FocusRequester() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 76.dp)
            .background(MinovaNightDeep),
    ) {
        hero?.let { content ->
            FeaturedHero(
                content = content,
                actionFocusRequester = heroFocus,
                onDown = { firstCardFocus.requestFocus() },
                onOpen = onOpen,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(top = 14.dp, bottom = 58.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            shelves.forEachIndexed { shelfIndex, shelf ->
                item(key = shelf.key) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 48.dp, end = 48.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                shelf.title,
                                color = MinovaWhite,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${shelf.items.size} titles",
                                color = MinovaMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        LazyRow(
                            modifier = Modifier.focusGroup(),
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            items(shelf.items, key = { "${shelf.key}-${it.ratingKey}" }) { content ->
                                val firstCardModifier = if (
                                    shelfIndex == 0 && content.ratingKey == shelf.items.first().ratingKey
                                ) {
                                    Modifier.focusRequester(firstCardFocus).then(
                                        if (hero != null) Modifier.onPreviewKeyEvent { event ->
                                            if (
                                                event.type == KeyEventType.KeyDown &&
                                                event.key == Key.DirectionUp
                                            ) {
                                                heroFocus.requestFocus()
                                                true
                                            } else {
                                                false
                                            }
                                        } else Modifier,
                                    )
                                } else {
                                    Modifier
                                }
                                if (shelf.style == ShelfStyle.Landscape) {
                                    ContinueCard(
                                        content = content,
                                        modifier = firstCardModifier,
                                        onOpen = onOpen,
                                        onFocused = onHighlighted,
                                    )
                                } else {
                                    PosterCard(
                                        content = content,
                                        modifier = Modifier.width(148.dp).then(firstCardModifier),
                                        onOpen = onOpen,
                                        onFocused = onHighlighted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedHero(
    content: MediaContent,
    actionFocusRequester: FocusRequester,
    onDown: () -> Unit,
    onOpen: (MediaContent) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(MinovaBlack),
    ) {
        // A soft fill behind the image keeps the hero cinematic on aspect
        // ratios that differ from the TV without substituting any artwork.
        AsyncImage(
            model = content.backdropUrl ?: content.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.28f),
        )
        // The foreground layer uses Fit: the complete Plex backdrop is always
        // present rather than cropping faces or title artwork at TV edges.
        AsyncImage(
            model = content.backdropUrl ?: content.posterUrl,
            contentDescription = content.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.58f),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to MinovaNightDeep,
                        0.46f to MinovaNightDeep.copy(alpha = 0.91f),
                        0.76f to MinovaNightDeep.copy(alpha = 0.20f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to MinovaNightDeep,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(620.dp)
                .padding(start = 48.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = content.title,
                color = MinovaWhite,
                fontSize = if (content.title.length > 42) 27.sp else 32.sp,
                lineHeight = if (content.title.length > 42) 31.sp else 36.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val details = buildList {
                if (content.metadataLine.isNotBlank()) add(content.metadataLine)
                if (content.genres.isNotEmpty()) add(content.genres.take(3).joinToString("  •  "))
            }.joinToString("    ")
            if (details.isNotBlank()) {
                Text(
                    text = details,
                    color = MinovaMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
            content.summary?.takeIf(String::isNotBlank)?.let { summary ->
                Text(
                    text = summary,
                    color = MinovaWhite.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroAction(
                    label = if (content.viewOffsetMs > 0L) "Resume details" else "View details",
                    primary = true,
                    modifier = Modifier
                        .focusRequester(actionFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionDown
                            ) {
                                onDown()
                                true
                            } else {
                                false
                            }
                        },
                    onClick = { onOpen(content) },
                )
                content.remainingTimeLabel?.let { remaining ->
                    Text(
                        text = remaining,
                        color = MinovaCyan,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroAction(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) MinovaCyan else Color.Transparent,
                shape = shape,
            )
            .clip(shape)
            .background(
                when {
                    primary && focused -> MinovaWhite
                    primary -> MinovaWhite.copy(alpha = 0.94f)
                    focused -> MinovaSurfaceRaised
                    else -> MinovaSurface.copy(alpha = 0.92f)
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (primary) MinovaBlack else MinovaWhite,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CatalogGrid(
    media: List<MediaContent>,
    emptyMessage: String,
    onOpen: (MediaContent) -> Unit,
) {
    if (media.isEmpty()) return EmptyMessage(emptyMessage)
    val sortedMedia = remember(media) {
        media.sortedWith(
            compareBy<MediaContent> { it.title.lowercase(Locale.ROOT) }
                .thenBy(MediaContent::ratingKey),
        )
    }
    val firstIndexByBucket = remember(sortedMedia) {
        buildMap<String, Int> {
            sortedMedia.forEachIndexed { index, content ->
                putIfAbsent(content.alphabetBucket(), index)
            }
        }
    }
    val gridState = rememberLazyGridState()
    val alphabetState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val cardFocusRequesters = remember(sortedMedia) {
        sortedMedia.associate { it.ratingKey to FocusRequester() }
    }
    val bucketFocusRequesters = remember(sortedMedia) {
        AlphabetBuckets.associateWith { FocusRequester() }
    }
    var returnIndex by remember(sortedMedia) { mutableStateOf(0) }

    fun requestCardFocus(index: Int) {
        val safeIndex = index.coerceIn(sortedMedia.indices)
        returnIndex = safeIndex
        scope.launch {
            // Alphabet jumps can span hundreds of Plex items. An animated
            // scroll would visibly fly through the catalog and keep focus on
            // the rail for several seconds, so land on the indexed row first.
            gridState.scrollToItem(safeIndex)
            delay(120)
            cardFocusRequesters[sortedMedia[safeIndex].ratingKey]?.requestFocus()
        }
    }

    fun requestAlphabetFocus(index: Int) {
        val safeIndex = index.coerceIn(sortedMedia.indices)
        returnIndex = safeIndex
        val bucket = sortedMedia[safeIndex].alphabetBucket()
        val bucketIndex = AlphabetBuckets.indexOf(bucket).coerceAtLeast(0)
        scope.launch {
            alphabetState.scrollToItem(bucketIndex)
            delay(60)
            bucketFocusRequesters[bucket]?.requestFocus()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 76.dp)
            .background(MinovaNightDeep),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(GridColumnCount),
            state = gridState,
            modifier = Modifier.weight(1f).fillMaxHeight().focusGroup(),
            contentPadding = PaddingValues(start = 44.dp, end = 16.dp, top = 26.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            itemsIndexed(sortedMedia, key = { _, content -> content.ratingKey }) { index, content ->
                val atRightEdge = index % GridColumnCount == GridColumnCount - 1 || index == sortedMedia.lastIndex
                PosterCard(
                    content = content,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(cardFocusRequesters.getValue(content.ratingKey))
                        .onPreviewKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionRight &&
                                atRightEdge
                            ) {
                                requestAlphabetFocus(index)
                                true
                            } else {
                                false
                            }
                        },
                    onOpen = onOpen,
                    onFocused = { returnIndex = index },
                )
            }
        }
        AlphabetRail(
            firstIndexByBucket = firstIndexByBucket,
            listState = alphabetState,
            focusRequesters = bucketFocusRequesters,
            onSelectBucket = { bucket ->
                firstIndexByBucket[bucket]?.let(::requestCardFocus)
            },
            onReturnToGrid = { requestCardFocus(returnIndex) },
        )
    }
}

@Composable
private fun AlphabetRail(
    firstIndexByBucket: Map<String, Int>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    focusRequesters: Map<String, FocusRequester>,
    onSelectBucket: (String) -> Unit,
    onReturnToGrid: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(52.dp)
            .fillMaxHeight()
            .background(MinovaBlack.copy(alpha = 0.86f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "A–Z",
            color = MinovaMuted,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f).focusGroup(),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(AlphabetBuckets, key = { it }) { bucket ->
                AlphabetChoice(
                    bucket = bucket,
                    enabled = bucket in firstIndexByBucket,
                    modifier = Modifier.focusRequester(focusRequesters.getValue(bucket)),
                    onClick = { onSelectBucket(bucket) },
                    onLeft = onReturnToGrid,
                )
            }
        }
    }
}

@Composable
private fun AlphabetChoice(
    bucket: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLeft: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .alpha(if (enabled) 1f else 0.28f)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    onLeft()
                    true
                } else {
                    false
                }
            }
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) MinovaCyan else Color.Transparent,
                shape = shape,
            )
            .clip(shape)
            .background(if (focused) MinovaSurfaceRaised else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = bucket,
            color = if (focused) MinovaWhite else MinovaMuted,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

private fun MediaContent.alphabetBucket(): String {
    val first = title.trim().firstOrNull()?.uppercaseChar()
    return if (first != null && first in 'A'..'Z') first.toString() else "#"
}

@Composable
internal fun PosterCard(
    content: MediaContent,
    modifier: Modifier,
    onOpen: (MediaContent) -> Unit,
    onFocused: ((MediaContent) -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.045f else 1f, tween(110), label = "poster_focus")
    val shape = RoundedCornerShape(9.dp)
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused?.invoke(content)
            }
            .border(if (focused) 3.dp else 1.dp, if (focused) MinovaCyan else MinovaSurfaceRaised, shape)
            .clip(shape)
            .background(MinovaSurface)
            .clickable(role = Role.Button) { onOpen(content) },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(MinovaBlack)) {
            AsyncImage(
                model = content.posterUrl,
                contentDescription = content.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (content.progress > 0f) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth(content.progress)
                        .height(4.dp).background(MinovaTeal),
                )
            }
            if (content.isWatched) WatchedBadge(Modifier.align(Alignment.TopEnd))
        }
        // The poster height grows with adaptive grid-cell width. Keep the
        // metadata area explicit instead of forcing the whole card to 280 dp;
        // otherwise wider search cells push the year outside the card bounds.
        Column(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 9.dp, vertical = 7.dp),
        ) {
            Text(content.title, color = MinovaWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                content.remainingTimeLabel ?: content.year?.toString().orEmpty(),
                color = if (content.remainingTimeLabel != null) MinovaTeal else MinovaMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ContinueCard(
    content: MediaContent,
    modifier: Modifier = Modifier,
    onOpen: (MediaContent) -> Unit,
    onFocused: (MediaContent) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(110), label = "continue_focus")
    val shape = RoundedCornerShape(9.dp)
    Column(
        modifier = modifier
            .width(300.dp)
            .height(176.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused(content)
            }
            .border(if (focused) 3.dp else 1.dp, if (focused) MinovaCyan else MinovaSurfaceRaised, shape)
            .clip(shape)
            .background(MinovaSurface)
            .clickable(role = Role.Button) { onOpen(content) },
    ) {
        Box(Modifier.fillMaxWidth().height(124.dp).background(MinovaBlack)) {
            AsyncImage(
                model = content.backdropUrl ?: content.posterUrl,
                contentDescription = content.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp)
                    .background(MinovaSurfaceRaised),
            ) {
                Box(
                    Modifier.fillMaxWidth(content.progress).fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(MinovaCobalt, MinovaCyan, MinovaTeal))),
                )
            }
            if (content.isWatched) WatchedBadge(Modifier.align(Alignment.TopEnd))
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(content.title, color = MinovaWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                content.remainingTimeLabel ?: content.secondaryTitle.orEmpty(),
                color = MinovaTeal,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WatchedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(9.dp)
            .size(28.dp)
            .clip(CircleShape)
            .background(MinovaCyan),
        contentAlignment = Alignment.Center,
    ) {
        Text("✓", color = MinovaBlack, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyMessage(message: String) {
    Box(Modifier.fillMaxSize().padding(top = 76.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MinovaMuted, style = MaterialTheme.typography.bodyLarge)
    }
}
