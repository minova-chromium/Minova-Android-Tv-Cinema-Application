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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

private enum class BrowseTab(val label: String) {
    Home("Home"), Movies("Movies"), Series("Series"), MyList("Watchlist"), Search("Search"),
}

private enum class BrowseLayout { Rows, Grid }
private enum class ShelfStyle { Landscape, Poster }

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

    LaunchedEffect(gridGenres, selectedGenre) {
        if (selectedGenre != null && gridGenres.none { it.equals(selectedGenre, ignoreCase = true) }) {
            selectedGenre = null
        }
    }

    Box(Modifier.fillMaxSize().background(MinovaNightDeep)) {
        Header(
            selectedTab = tab,
            layout = layout,
            showLayout = tab != BrowseTab.Home && tab != BrowseTab.Search,
            showFilter = layout == BrowseLayout.Grid && gridGenres.isNotEmpty(),
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
            CatalogGrid(filteredGridItems, emptyMessage, onOpen)
        } else {
            ShelfBrowser(shelves, emptyMessage, onOpen)
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
            .background(MinovaBlack)
            .padding(horizontal = 42.dp)
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
                .padding(start = 12.dp, end = 28.dp)
                .width(126.dp)
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
            HeaderItem(if (layout == BrowseLayout.Rows) "View: Rows" else "View: Grid", false) {
                onLayoutChanged(if (layout == BrowseLayout.Rows) BrowseLayout.Grid else BrowseLayout.Rows)
            }
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
    emptyMessage: String,
    onOpen: (MediaContent) -> Unit,
) {
    if (shelves.isEmpty()) return EmptyMessage(emptyMessage)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = 76.dp),
        contentPadding = PaddingValues(top = 22.dp, bottom = 52.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        items(shelves, key = { it.key }) { shelf ->
            Column {
                Text(
                    shelf.title,
                    color = MinovaWhite,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 48.dp, bottom = 10.dp),
                )
                LazyRow(
                    modifier = Modifier.focusGroup(),
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(shelf.items, key = { "${shelf.key}-${it.ratingKey}" }) { content ->
                        if (shelf.style == ShelfStyle.Landscape) {
                            ContinueCard(content, onOpen)
                        } else {
                            PosterCard(content, Modifier.width(148.dp), onOpen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogGrid(
    media: List<MediaContent>,
    emptyMessage: String,
    onOpen: (MediaContent) -> Unit,
) {
    if (media.isEmpty()) return EmptyMessage(emptyMessage)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(148.dp),
        modifier = Modifier.fillMaxSize().padding(top = 76.dp).focusGroup(),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 26.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        items(media, key = { it.ratingKey }) { content ->
            PosterCard(content, Modifier.fillMaxWidth(), onOpen)
        }
    }
}

@Composable
internal fun PosterCard(content: MediaContent, modifier: Modifier, onOpen: (MediaContent) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.035f else 1f, tween(90), label = "poster_focus")
    val shape = RoundedCornerShape(9.dp)
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged { focused = it.isFocused }
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
private fun ContinueCard(content: MediaContent, onOpen: (MediaContent) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, tween(90), label = "continue_focus")
    val shape = RoundedCornerShape(9.dp)
    Column(
        modifier = Modifier
            .width(286.dp)
            .height(196.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged { focused = it.isFocused }
            .border(if (focused) 3.dp else 1.dp, if (focused) MinovaCyan else MinovaSurfaceRaised, shape)
            .clip(shape)
            .background(MinovaSurface)
            .clickable(role = Role.Button) { onOpen(content) },
    ) {
        Box(Modifier.fillMaxWidth().height(145.dp).background(MinovaBlack)) {
            AsyncImage(
                model = content.backdropUrl ?: content.posterUrl,
                contentDescription = content.title,
                contentScale = ContentScale.Crop,
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
