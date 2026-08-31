package com.minova.cinema.ui.browse

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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

private const val GridColumnCount = 5
private val AlphabetBuckets = listOf("#") + ('A'..'Z').map(Char::toString)

private data class DiscoveryShelf(
    val key: String,
    val title: String,
    val media: List<MediaContent>,
)

@Composable
fun BrowseScreen(
    catalog: CinemaCatalog,
    onOpen: (MediaContent) -> Unit,
    onPlay: (MediaContent) -> Unit,
    onToggleMyList: (MediaContent) -> Unit,
    onSettings: () -> Unit,
    onWatchlistRefresh: () -> Unit,
) {
    var tab by remember { mutableStateOf(BrowseTab.Home) }
    var layout by remember { mutableStateOf(BrowseLayout.Rows) }
    var selectedGenre by remember(tab) { mutableStateOf<String?>(null) }
    var filterOpen by remember { mutableStateOf(false) }
    val browseItems = remember(catalog, tab) {
        when (tab) {
            BrowseTab.Home -> (catalog.movies + catalog.shows).distinctBy(MediaContent::ratingKey)
            BrowseTab.Movies -> catalog.movies
            BrowseTab.Series -> catalog.shows
            BrowseTab.MyList -> catalog.myList
            BrowseTab.Search -> emptyList()
        }
    }
    val continueWatching = remember(catalog, tab) {
        when (tab) {
            BrowseTab.Home -> catalog.continueWatching
            BrowseTab.Movies -> catalog.continueWatching.filter { it.kind == MediaKind.Movie }
            BrowseTab.Series -> catalog.continueWatching.filter {
                it.kind == MediaKind.Show || it.kind == MediaKind.Season || it.kind == MediaKind.Episode
            }
            BrowseTab.MyList -> {
                val saved = catalog.myList.mapTo(mutableSetOf(), MediaContent::ratingKey)
                catalog.continueWatching.filter { it.ratingKey in saved }
            }
            BrowseTab.Search -> emptyList()
        }
    }
    val gridGenres = remember(browseItems) {
        browseItems
            .flatMap { it.genres }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    val filteredGridItems = remember(browseItems, selectedGenre) {
        selectedGenre?.let { genre ->
            browseItems.filter { item ->
                item.genres.any { it.equals(genre, ignoreCase = true) }
            }
        } ?: browseItems
    }
    val heroCandidates = remember(continueWatching, filteredGridItems) {
        (continueWatching + filteredGridItems).distinctBy(MediaContent::ratingKey)
    }
    val homeFeatured = remember(catalog) {
        buildFeaturedCarousel(
            (catalog.movies + catalog.shows).distinctBy(MediaContent::ratingKey),
        )
    }
    val homeShelves = remember(catalog) {
        buildHomeDiscoveryShelves(catalog)
    }
    var highlightedContent by remember(tab) {
        mutableStateOf(heroCandidates.firstOrNull())
    }
    var pendingHighlightedContent by remember(tab) { mutableStateOf<MediaContent?>(null) }
    var homeFeaturedIndex by remember { mutableStateOf(0) }
    val heroFocus = remember(tab) { FocusRequester() }
    val firstContinueFocus = remember(tab) { FocusRequester() }
    val firstGenreFocus = remember(tab) { FocusRequester() }
    val firstPosterFocus = remember(tab, selectedGenre) { FocusRequester() }
    val firstGridPosterFocus = remember(tab, selectedGenre) { FocusRequester() }

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

    LaunchedEffect(tab, pendingHighlightedContent?.ratingKey) {
        val pending = pendingHighlightedContent ?: return@LaunchedEffect
        if (tab != BrowseTab.Home) {
            // Avoid flashing every intermediate backdrop while the user is
            // quickly travelling across a poster row with the D-Pad.
            delay(150)
            highlightedContent = pending
        }
    }

    LaunchedEffect(homeFeatured.size) {
        homeFeaturedIndex = homeFeaturedIndex.coerceIn(0, (homeFeatured.lastIndex).coerceAtLeast(0))
    }

    LaunchedEffect(tab, homeFeatured.size, homeFeaturedIndex) {
        if (tab == BrowseTab.Home && homeFeatured.size > 1) {
            delay(15_000)
            homeFeaturedIndex = (homeFeaturedIndex + 1) % homeFeatured.size
        }
    }

    Box(Modifier.fillMaxSize().background(MinovaBlack)) {
        val requestFirstContentFocus = {
            when {
                tab != BrowseTab.Home && layout == BrowseLayout.Grid && filteredGridItems.isNotEmpty() ->
                    firstGridPosterFocus.requestFocus()
                (if (tab == BrowseTab.Home) homeFeatured.getOrNull(homeFeaturedIndex) else highlightedContent) != null ->
                    heroFocus.requestFocus()
                continueWatching.isNotEmpty() -> firstContinueFocus.requestFocus()
                gridGenres.isNotEmpty() -> firstGenreFocus.requestFocus()
                filteredGridItems.isNotEmpty() -> firstPosterFocus.requestFocus()
            }
            Unit
        }
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
            onDown = requestFirstContentFocus,
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
                firstCardFocusRequester = firstGridPosterFocus,
                onOpen = onOpen,
            )
        } else {
            CinematicBrowser(
                hero = if (tab == BrowseTab.Home) {
                    homeFeatured.getOrNull(homeFeaturedIndex) ?: highlightedContent
                } else highlightedContent,
                heroCarouselPosition = if (tab == BrowseTab.Home) homeFeaturedIndex else null,
                heroCarouselCount = if (tab == BrowseTab.Home) homeFeatured.size else 0,
                onPreviousHero = {
                    if (homeFeatured.isNotEmpty()) {
                        homeFeaturedIndex = (homeFeaturedIndex - 1 + homeFeatured.size) % homeFeatured.size
                    }
                },
                onNextHero = {
                    if (homeFeatured.isNotEmpty()) {
                        homeFeaturedIndex = (homeFeaturedIndex + 1) % homeFeatured.size
                    }
                },
                continueWatching = continueWatching,
                media = filteredGridItems,
                genres = gridGenres,
                selectedGenre = selectedGenre,
                browseTitle = when (tab) {
                    BrowseTab.Movies -> "Browse Movies"
                    BrowseTab.Series -> "Browse Series"
                    BrowseTab.MyList -> "Browse Watchlist"
                    else -> "Browse Movies & Series"
                },
                homeMode = tab == BrowseTab.Home,
                homeShelves = homeShelves,
                emptyMessage = emptyMessage,
                onOpen = onOpen,
                onPlay = onPlay,
                isInMyList = { content -> catalog.myList.any { it.ratingKey == content.ratingKey } },
                onToggleMyList = onToggleMyList,
                onGenreSelected = { selectedGenre = it },
                heroFocus = heroFocus,
                firstContinueFocus = firstContinueFocus,
                firstGenreFocus = firstGenreFocus,
                firstPosterFocus = firstPosterFocus,
                onHighlighted = { content ->
                    if (tab != BrowseTab.Home) pendingHighlightedContent = content
                },
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

private fun buildFeaturedCarousel(media: List<MediaContent>): List<MediaContent> {
    val withBackdrops = media.filter { !it.backdropUrl.isNullOrBlank() }
    val source = if (withBackdrops.isNotEmpty()) withBackdrops else media
    return source.sortedWith(
        compareByDescending<MediaContent> { it.year ?: Int.MIN_VALUE }
            .thenByDescending { it.addedAtEpochSeconds ?: Long.MIN_VALUE }
            .thenBy { it.title.lowercase(Locale.ROOT) },
    ).take(10)
}

private fun buildHomeDiscoveryShelves(catalog: CinemaCatalog): List<DiscoveryShelf> {
    val media = (catalog.movies + catalog.shows).distinctBy(MediaContent::ratingKey)
    if (media.isEmpty()) return emptyList()
    val recentlyAdded = media.sortedWith(
        compareByDescending<MediaContent> { it.addedAtEpochSeconds ?: Long.MIN_VALUE }
            .thenByDescending { it.year ?: Int.MIN_VALUE },
    )
    val newReleases = media.sortedWith(
        compareByDescending<MediaContent> { it.year ?: Int.MIN_VALUE }
            .thenByDescending { it.addedAtEpochSeconds ?: Long.MIN_VALUE },
    )
    val genreGroups = sortedMapOf<String, MutableList<MediaContent>>(String.CASE_INSENSITIVE_ORDER)
    media.forEach { content ->
        content.genres.distinct().filter(String::isNotBlank).forEach { genre ->
            genreGroups.getOrPut(genre) { mutableListOf() }.add(content)
        }
    }
    val history = (media.filter(MediaContent::isWatched) + catalog.continueWatching)
        .distinctBy(MediaContent::ratingKey)
    val preferredGenres = history.flatMap(MediaContent::genres)
        .groupingBy { it.lowercase(Locale.ROOT) }
        .eachCount()
        .entries
        .sortedByDescending(Map.Entry<String, Int>::value)
        .map(Map.Entry<String, Int>::key)
    val watchedKeys = history.mapTo(mutableSetOf(), MediaContent::ratingKey)
    val topPicks = media.asSequence()
        .filterNot { it.ratingKey in watchedKeys }
        .map { item ->
            val affinity = item.genres.sumOf { genre ->
                val index = preferredGenres.indexOf(genre.lowercase(Locale.ROOT))
                if (index < 0) 0 else (preferredGenres.size - index).coerceAtMost(8)
            }
            item to (affinity * 10 + ((item.audienceRating ?: 0.0) * 2).toInt())
        }
        .sortedByDescending(Pair<MediaContent, Int>::second)
        .map(Pair<MediaContent, Int>::first)
        .take(30)
        .toList()
    val recentReference = history.maxByOrNull { it.addedAtEpochSeconds ?: Long.MIN_VALUE }
    val becauseYouWatched = recentReference?.let { watched ->
        val genres = watched.genres.map { it.lowercase(Locale.ROOT) }.toSet()
        media.filter { candidate ->
            candidate.ratingKey !in watchedKeys && candidate.genres.any {
                it.lowercase(Locale.ROOT) in genres
            }
        }.sortedByDescending { it.audienceRating ?: 0.0 }.take(30)
    }.orEmpty()
    val unfinishedSeries = catalog.continueWatching.filter {
        it.kind == MediaKind.Episode || it.kind == MediaKind.Show || it.kind == MediaKind.Season
    }
    return buildList {
        if (topPicks.isNotEmpty()) add(DiscoveryShelf("top-picks", "Top Picks for You", topPicks))
        if (becauseYouWatched.isNotEmpty() && recentReference != null) {
            add(
                DiscoveryShelf(
                    "because-${recentReference.ratingKey}",
                    "Because You Watched ${recentReference.title}",
                    becauseYouWatched,
                ),
            )
        }
        if (unfinishedSeries.isNotEmpty()) {
            add(DiscoveryShelf("finish-series", "Finish Your Series", unfinishedSeries))
        }
        add(DiscoveryShelf("recently-added", "Recently Added", recentlyAdded.take(30)))
        add(DiscoveryShelf("new-releases", "New Releases", newReleases.take(30)))
        genreGroups.forEach { (genre, titles) ->
            add(DiscoveryShelf("genre-$genre", genre, titles))
        }
    }.filter { it.media.isNotEmpty() }
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
    onDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .zIndex(10f)
            .fillMaxWidth()
            .height(58.dp)
            .background(
                Brush.verticalGradient(
                    listOf(MinovaBlack.copy(alpha = 0.94f), MinovaBlack.copy(alpha = 0.64f), Color.Transparent),
                ),
            )
            .padding(horizontal = 32.dp)
            .focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = "Minova Cinema",
            modifier = Modifier.size(26.dp).clip(CircleShape),
        )
        Image(
            painter = painterResource(R.drawable.minova_wordmark),
            contentDescription = "Minova",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(start = 9.dp)
                .width(75.dp)
                .height(22.dp),
        )
        Image(
            painter = painterResource(R.drawable.cinema_wordmark),
            contentDescription = "Cinema",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(start = 5.dp, end = 28.dp)
                .width(80.dp)
                .height(22.dp),
        )
        BrowseTab.entries.forEach { item ->
            if (item == BrowseTab.Search) {
                HeaderIconItem(
                    icon = Icons.Default.Search,
                    contentDescription = "Search",
                    selected = item == selectedTab,
                    onDown = onDown,
                    onClick = { onTabSelected(item) },
                )
            } else {
                HeaderItem(item.label, item == selectedTab, onDown) { onTabSelected(item) }
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
                onDown = onDown,
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
                onDown = onDown,
                onClick = onFilter,
            )
        }
        HeaderIconItem(
            icon = Icons.Default.Settings,
            contentDescription = "Settings",
            selected = false,
            onDown = onDown,
            onClick = onSettings,
        )
    }
}

@Composable
private fun HeaderIconItem(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onDown: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .testTag("header-action-$contentDescription")
            .padding(horizontal = 3.dp)
            .size(44.dp)
            .clip(RoundedCornerShape(7.dp))
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    onDown()
                    true
                } else false
            }
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
private fun HeaderItem(label: String, selected: Boolean, onDown: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .testTag("header-tab-$label")
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(7.dp))
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    onDown()
                    true
                } else false
            }
            .background(if (focused) MinovaWhite.copy(alpha = 0.11f) else Color.Transparent)
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
private fun CinematicBrowser(
    hero: MediaContent?,
    heroCarouselPosition: Int?,
    heroCarouselCount: Int,
    onPreviousHero: () -> Unit,
    onNextHero: () -> Unit,
    continueWatching: List<MediaContent>,
    media: List<MediaContent>,
    genres: List<String>,
    selectedGenre: String?,
    browseTitle: String,
    homeMode: Boolean,
    homeShelves: List<DiscoveryShelf>,
    emptyMessage: String,
    onOpen: (MediaContent) -> Unit,
    onPlay: (MediaContent) -> Unit,
    isInMyList: (MediaContent) -> Boolean,
    onToggleMyList: (MediaContent) -> Unit,
    onGenreSelected: (String?) -> Unit,
    heroFocus: FocusRequester,
    firstContinueFocus: FocusRequester,
    firstGenreFocus: FocusRequester,
    firstPosterFocus: FocusRequester,
    onHighlighted: (MediaContent) -> Unit,
) {
    if (hero == null && continueWatching.isEmpty() && media.isEmpty()) return EmptyMessage(emptyMessage)

    val quickGenres = remember(genres, selectedGenre) {
        val fixedGenres = genres.take(6)
        buildList<String?> {
            add(null)
            addAll(fixedGenres)
            selectedGenre
                ?.takeIf { selected -> fixedGenres.none { it.equals(selected, ignoreCase = true) } }
                ?.let(::add)
        }
    }
    var contentBrowsing by remember(homeMode) { mutableStateOf(false) }
    var pendingContentBrowsing by remember(homeMode) { mutableStateOf(false) }
    var revealCatalogAndFocus by remember(homeMode) { mutableStateOf(false) }
    var restoreHomeFocus by remember { mutableStateOf(false) }
    val catalogScrimAlpha by animateFloatAsState(
        targetValue = if (!homeMode && contentBrowsing) 0.34f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "catalog_backdrop_scrim",
    )

    LaunchedEffect(contentBrowsing, restoreHomeFocus) {
        if (!contentBrowsing && restoreHomeFocus) {
            delay(360)
            if (continueWatching.isNotEmpty()) firstContinueFocus.requestFocus()
            else heroFocus.requestFocus()
            restoreHomeFocus = false
        }
    }

    LaunchedEffect(pendingContentBrowsing) {
        if (pendingContentBrowsing) {
            // Keep the currently focused Continue card in the composition
            // until the complete D-Pad press has settled. Removing it during
            // KeyDown could make Compose dispatch the remaining key sequence
            // as a click on the title that just gained focus.
            delay(140)
            contentBrowsing = true
            pendingContentBrowsing = false
        }
    }

    LaunchedEffect(revealCatalogAndFocus) {
        if (revealCatalogAndFocus) {
            // Let the D-Pad key-up finish on the current card, then reveal the
            // catalog and move focus once its controls are in composition.
            delay(140)
            contentBrowsing = true
            delay(180)
            when {
                quickGenres.isNotEmpty() -> firstGenreFocus.requestFocus()
                media.isNotEmpty() -> firstPosterFocus.requestFocus()
            }
            revealCatalogAndFocus = false
        }
    }

    Box(Modifier.fillMaxSize().background(MinovaBlack)) {
        if (hero != null) {
            AnimatedContent(
                targetState = hero,
                transitionSpec = {
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = 460,
                            delayMillis = 190,
                            easing = FastOutSlowInEasing,
                        ),
                    ) togetherWith fadeOut(
                        animationSpec = tween(
                            durationMillis = 190,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                },
                contentKey = { it.ratingKey },
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
                label = "featured_backdrop_fade_through",
            ) { content ->
                AsyncImage(
                    model = content.backdropUrl ?: content.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to MinovaNightDeep,
                        0.52f to MinovaNightDeep.copy(alpha = 0.68f),
                        1f to Color.Transparent,
                    ),
                ),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to MinovaNightDeep.copy(alpha = 0.08f),
                        0.68f to Color.Transparent,
                        1f to MinovaBlack,
                    ),
                ),
            )
            if (catalogScrimAlpha > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MinovaNightDeep.copy(alpha = catalogScrimAlpha)),
                )
            }
        }

        // This composition intentionally stays fixed instead of using a
        // vertical lazy list. Compose's automatic bring-into-view behavior
        // made the entire hero jump underneath the header whenever focus
        // entered a shelf. Horizontal shelves still scroll independently.
        Column(Modifier.fillMaxSize().padding(top = 58.dp)) {
            AnimatedVisibility(
                visible = !contentBrowsing,
                enter = fadeIn(tween(320)) + expandVertically(
                    animationSpec = tween(460),
                    expandFrom = Alignment.Top,
                ),
                exit = fadeOut(tween(300)) + shrinkVertically(
                    animationSpec = tween(460),
                    shrinkTowards = Alignment.Top,
                ),
            ) {
                Column {
                    Box(Modifier.fillMaxWidth().height(206.dp)) {
                        hero?.let { content ->
                            HeroContent(
                                content = content,
                                carouselPosition = heroCarouselPosition,
                                carouselCount = heroCarouselCount,
                                onPrevious = onPreviousHero,
                                onNext = onNextHero,
                                inMyList = isInMyList(content),
                                focusRequester = heroFocus,
                                onDown = {
                                    when {
                                        continueWatching.isNotEmpty() -> firstContinueFocus.requestFocus()
                                        homeMode && homeShelves.isNotEmpty() -> firstGenreFocus.requestFocus()
                                        !homeMode -> revealCatalogAndFocus = true
                                    }
                                },
                                onOpen = onOpen,
                                onPlay = onPlay,
                                onToggleMyList = onToggleMyList,
                            )
                        }
                    }

                    if (continueWatching.isNotEmpty()) {
                        Column(Modifier.height(153.dp)) {
                            SectionHeading("Continue Watching")
                            LazyRow(
                                modifier = Modifier.focusGroup(),
                                contentPadding = PaddingValues(horizontal = 34.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(continueWatching, key = { "continue-${it.ratingKey}" }) { content ->
                                    val first = content.ratingKey == continueWatching.first().ratingKey
                                    ContinueCard(
                                        content = content,
                                        modifier = if (first) {
                                            Modifier
                                                .testTag("browse-first-continue")
                                                .focusRequester(firstContinueFocus)
                                        } else {
                                            Modifier
                                        },
                                        onOpen = onOpen,
                                        onFocused = onHighlighted,
                                        onUp = { heroFocus.requestFocus() },
                                        onDown = {
                                            if (homeMode && homeShelves.isNotEmpty()) firstGenreFocus.requestFocus()
                                            else revealCatalogAndFocus = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (homeMode) {
                HomeDiscoveryFeed(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (contentBrowsing) MinovaNightDeep else Color.Transparent),
                    shelves = homeShelves,
                    firstFocusRequester = firstGenreFocus,
                    onOpen = onOpen,
                    onHighlighted = { content ->
                        pendingContentBrowsing = true
                        onHighlighted(content)
                    },
                    onUpFromFirst = {
                        pendingContentBrowsing = false
                        restoreHomeFocus = true
                        contentBrowsing = false
                    },
                )
            } else AnimatedVisibility(
                visible = contentBrowsing,
                enter = fadeIn(tween(360)) + expandVertically(
                    animationSpec = tween(460, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ),
                exit = fadeOut(tween(220)) + shrinkVertically(
                    animationSpec = tween(360, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ),
            ) {
                Column {
                    SectionHeading(browseTitle)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().height(42.dp).focusGroup(),
                        contentPadding = PaddingValues(horizontal = 34.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(quickGenres, key = { it ?: "all" }) { genre ->
                            val first = genre == quickGenres.first()
                            GenrePill(
                                label = genre ?: "All",
                                selected = genre == selectedGenre,
                                modifier = if (first) {
                                    Modifier
                                        .testTag("browse-first-genre")
                                        .focusRequester(firstGenreFocus)
                                } else {
                                    Modifier
                                },
                                onFocused = { contentBrowsing = true },
                                onUp = {
                                    pendingContentBrowsing = false
                                    restoreHomeFocus = true
                                    contentBrowsing = false
                                },
                                onDown = { if (media.isNotEmpty()) firstPosterFocus.requestFocus() },
                                onClick = { onGenreSelected(genre) },
                            )
                        }
                    }
                    if (media.isEmpty()) {
                        Text(
                            emptyMessage,
                            color = MinovaMuted,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 34.dp, vertical = 24.dp),
                        )
                    } else {
                        LazyRow(
                            modifier = Modifier.focusGroup(),
                            contentPadding = PaddingValues(start = 34.dp, end = 34.dp, top = 10.dp, bottom = 22.dp),
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                        ) {
                            items(media, key = { "browse-${it.ratingKey}" }) { content ->
                                val first = content.ratingKey == media.first().ratingKey
                                CinematicPosterCard(
                                    content = content,
                                    modifier = if (first) Modifier.focusRequester(firstPosterFocus) else Modifier,
                                    onOpen = onOpen,
                                    onFocused = {
                                        contentBrowsing = true
                                        onHighlighted(it)
                                    },
                                    onUp = { firstGenreFocus.requestFocus() },
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !homeMode && !contentBrowsing && media.isNotEmpty(),
            enter = fadeIn(tween(420)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp),
        ) {
            BrowseCatalogCue(
                browseTitle = browseTitle,
                itemCount = media.size,
            )
        }
    }
}

/**
 * The movie/series hero deliberately uses the whole screen. This persistent,
 * focus-neutral handle makes the hidden catalog discoverable without adding a
 * second row of controls or stealing a D-Pad press from the hero actions.
 */
@Composable
private fun BrowseCatalogCue(
    browseTitle: String,
    itemCount: Int,
) {
    val transition = rememberInfiniteTransition(label = "browse_catalog_hint")
    val arrowOffset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "browse_catalog_hint_arrow",
    )
    val title = browseTitle.removePrefix("Browse ").lowercase(Locale.ROOT)
    Box(
        modifier = Modifier
            .testTag("browse-catalog-cue")
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, MinovaWhite.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .background(MinovaNightDeep.copy(alpha = 0.92f))
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Image(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                colorFilter = ColorFilter.tint(MinovaCyan),
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { translationY = arrowOffset },
            )
            Column {
                Text(
                    "Press Down to browse $title",
                    color = MinovaWhite,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "$itemCount titles · genres · A–Z grid",
                    color = MinovaMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun HomeDiscoveryFeed(
    modifier: Modifier,
    shelves: List<DiscoveryShelf>,
    firstFocusRequester: FocusRequester,
    onOpen: (MediaContent) -> Unit,
    onHighlighted: (MediaContent) -> Unit,
    onUpFromFirst: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rowFocusRequesters = remember(shelves) {
        shelves.mapIndexed { index, _ -> if (index == 0) firstFocusRequester else FocusRequester() }
    }

    fun focusRow(index: Int) {
        if (index !in shelves.indices) return
        if (listState.layoutInfo.visibleItemsInfo.any { it.index == index }) {
            rowFocusRequesters[index].requestFocus()
            return
        }
        scope.launch {
            listState.animateScrollToItem(index)
            delay(80)
            rowFocusRequesters[index].requestFocus()
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().focusGroup(),
        contentPadding = PaddingValues(bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(shelves, key = DiscoveryShelf::key) { shelf ->
            val shelfIndex = shelves.indexOfFirst { it.key == shelf.key }
            Column {
                SectionHeading(shelf.title)
                LazyRow(
                    modifier = Modifier.focusGroup(),
                    contentPadding = PaddingValues(start = 34.dp, end = 34.dp, top = 3.dp, bottom = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    items(shelf.media, key = { "${shelf.key}-${it.ratingKey}" }) { content ->
                        val first = content.ratingKey == shelf.media.first().ratingKey
                        CinematicPosterCard(
                            content = content,
                            width = 108.dp,
                            modifier = if (first) {
                                Modifier.focusRequester(rowFocusRequesters[shelfIndex])
                            } else Modifier,
                            onOpen = onOpen,
                            onFocused = onHighlighted,
                            onUp = {
                                if (shelfIndex == 0) onUpFromFirst()
                                else focusRow(shelfIndex - 1)
                            },
                            onDown = { focusRow(shelfIndex + 1) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 34.dp, end = 34.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            title,
            color = MinovaWhite,
            fontSize = 19.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HeroContent(
    content: MediaContent,
    carouselPosition: Int?,
    carouselCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    inMyList: Boolean,
    focusRequester: FocusRequester,
    onDown: () -> Unit,
    onOpen: (MediaContent) -> Unit,
    onPlay: (MediaContent) -> Unit,
    onToggleMyList: (MediaContent) -> Unit,
) {
    val directlyPlayable = content.kind == MediaKind.Movie ||
        content.kind == MediaKind.Episode || content.kind == MediaKind.Extra
    val titleWraps = content.title.length > 34
    Column(
        modifier = Modifier
            .width(510.dp)
            .height(206.dp)
            .padding(start = 34.dp, top = 24.dp, bottom = 8.dp),
    ) {
        if (carouselPosition != null && carouselCount > 1) {
            HeroCarouselIndicator(
                activeIndex = carouselPosition,
                count = carouselCount,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Text(
            content.title,
            color = MinovaWhite,
            fontSize = when {
                content.title.length > 55 -> 24.sp
                titleWraps -> 26.sp
                else -> 34.sp
            },
            lineHeight = if (titleWraps) 29.sp else 37.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (content.metadataLine.isNotBlank()) {
            Text(
                content.metadataLine,
                color = MinovaWhite.copy(alpha = 0.72f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        content.summary?.takeIf(String::isNotBlank)?.let { summary ->
            Text(
                summary,
                color = MinovaWhite.copy(alpha = 0.82f),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = if (titleWraps) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HeroAction(
                label = when {
                    !directlyPlayable -> "View details"
                    content.viewOffsetMs > 0L -> "Resume"
                    else -> "Play"
                },
                icon = Icons.Default.PlayArrow,
                primary = true,
                modifier = Modifier
                    .testTag("hero-primary-action")
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> if (carouselCount > 1) {
                            onPrevious(); true
                        } else false
                        Key.DirectionRight -> if (carouselCount > 1) {
                            // Advance immediately, while allowing normal focus
                            // movement to the adjacent Watchlist action.
                            onNext(); false
                        } else false
                        Key.DirectionDown -> { onDown(); true }
                        else -> false
                    }
                },
                onClick = { if (directlyPlayable) onPlay(content) else onOpen(content) },
            )
            HeroAction(
                label = if (inMyList) "In Watchlist" else "Watchlist",
                icon = if (inMyList) Icons.Default.Check else Icons.Default.Add,
                primary = false,
                modifier = Modifier.onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> if (carouselCount > 1) {
                            // Return to Play and move the carousel back in the
                            // same spatial direction.
                            onPrevious(); false
                        } else false
                        Key.DirectionRight -> if (carouselCount > 1) {
                            onNext(); true
                        } else false
                        Key.DirectionDown -> { onDown(); true }
                        else -> false
                    }
                },
                onClick = { onToggleMyList(content) },
            )
        }
    }
}

@Composable
private fun HeroCarouselIndicator(
    activeIndex: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == activeIndex
            val scale by animateFloatAsState(
                targetValue = if (active) 1.25f else 1f,
                animationSpec = tween(durationMillis = 190),
                label = "featured_dot_$index",
            )
            Box(
                Modifier
                    .size(7.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .border(1.dp, MinovaCyan.copy(alpha = if (active) 1f else 0.72f), CircleShape)
                    .background(if (active) MinovaCyan else Color.Transparent),
            )
        }
    }
}

@Composable
private fun HeroAction(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = modifier
            .height(39.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) MinovaWhite else Color.Transparent,
                shape = shape,
            )
            .clip(shape)
            .background(
                when {
                    primary -> MinovaCyan
                    focused -> MinovaSurfaceRaised
                    else -> MinovaSurface.copy(alpha = 0.92f)
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 17.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(
                imageVector = icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(if (primary) MinovaBlack else MinovaWhite),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                color = if (primary) MinovaBlack else MinovaWhite,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun GenrePill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .height(28.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { onUp(); true }
                    Key.DirectionDown -> { onDown(); true }
                    else -> false
                }
            }
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) MinovaWhite else if (selected) MinovaCyan else MinovaWhite.copy(alpha = 0.25f),
                shape,
            )
            .clip(shape)
            .background(if (selected) MinovaCyan else MinovaSurface.copy(alpha = 0.88f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) MinovaBlack else MinovaWhite,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun CinematicPosterCard(
    content: MediaContent,
    width: Dp = 132.dp,
    modifier: Modifier = Modifier,
    onOpen: (MediaContent) -> Unit,
    onFocused: (MediaContent) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(110), label = "cinema_poster_focus")
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .width(width)
            .aspectRatio(2f / 3f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused(content)
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { onUp(); true }
                    Key.DirectionDown -> { onDown(); true }
                    else -> false
                }
            }
            .border(if (focused) 3.dp else 1.dp, if (focused) MinovaCyan else MinovaWhite.copy(alpha = 0.16f), shape)
            .clip(shape)
            .background(MinovaSurface)
            .clickable(role = Role.Button) { onOpen(content) },
    ) {
        AsyncImage(
            model = content.posterUrl,
            contentDescription = content.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxWidth().height(58.dp).align(Alignment.BottomCenter).background(
                Brush.verticalGradient(listOf(Color.Transparent, MinovaBlack.copy(alpha = 0.92f))),
            ),
        )
        Text(
            content.title,
            color = MinovaWhite,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
        if (content.progress > 0f) {
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth(content.progress).height(4.dp)
                    .background(Brush.horizontalGradient(listOf(MinovaCobalt, MinovaCyan, MinovaTeal))),
            )
        }
        if (content.isWatched) WatchedBadge(Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun CatalogGrid(
    media: List<MediaContent>,
    emptyMessage: String,
    firstCardFocusRequester: FocusRequester,
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
    val cardFocusRequesters = remember(sortedMedia, firstCardFocusRequester) {
        sortedMedia.mapIndexed { index, content ->
            content.ratingKey to if (index == 0) firstCardFocusRequester else FocusRequester()
        }.toMap()
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
            .padding(top = 58.dp)
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
                        .then(if (index == 0) Modifier.testTag("catalog-grid-first-card") else Modifier)
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
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(110), label = "continue_focus")
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = modifier
            .width(220.dp)
            .aspectRatio(16f / 9f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused(content)
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { onUp(); true }
                    Key.DirectionDown -> { onDown(); true }
                    else -> false
                }
            }
            .border(if (focused) 3.dp else 1.dp, if (focused) MinovaCyan else MinovaSurfaceRaised, shape)
            .clip(shape)
            .background(MinovaSurface)
            .clickable(role = Role.Button) { onOpen(content) },
    ) {
        AsyncImage(
            model = content.backdropUrl ?: content.posterUrl,
            contentDescription = content.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxWidth().height(50.dp).align(Alignment.BottomCenter).background(
                Brush.verticalGradient(listOf(Color.Transparent, MinovaBlack.copy(alpha = 0.94f))),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(start = 9.dp, end = 9.dp, bottom = 9.dp)) {
            Text(
                content.title,
                color = MinovaWhite,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                content.remainingTimeLabel ?: content.secondaryTitle.orEmpty(),
                color = MinovaWhite.copy(alpha = 0.88f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp)
                .background(MinovaWhite.copy(alpha = 0.20f)),
        ) {
            Box(
                Modifier.fillMaxWidth(content.progress).fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(MinovaCobalt, MinovaCyan, MinovaTeal))),
            )
        }
        if (content.isWatched) WatchedBadge(Modifier.align(Alignment.TopEnd))
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
    Box(Modifier.fillMaxSize().padding(top = 58.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MinovaMuted, style = MaterialTheme.typography.bodyLarge)
    }
}
