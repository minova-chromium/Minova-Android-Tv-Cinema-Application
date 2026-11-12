package com.minova.cinema.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.minova.cinema.R
import com.minova.cinema.data.local.HomeLayoutPreferences
import com.minova.cinema.domain.CinemaCatalog
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.ui.theme.MinovaBlack
import com.minova.cinema.ui.theme.MinovaCyan
import com.minova.cinema.ui.theme.MinovaMuted
import com.minova.cinema.ui.theme.MinovaNightDeep
import com.minova.cinema.ui.theme.MinovaSurface
import com.minova.cinema.ui.theme.MinovaSurfaceRaised
import com.minova.cinema.ui.theme.MinovaTeal
import com.minova.cinema.ui.theme.MinovaWhite
import kotlinx.coroutines.delay

/**
 * Controller-first port of the Desktop experimental renderer. It deliberately
 * lives beside [BrowseScreen] so the established TV interface remains a safe
 * default and both renderers share the same Plex catalogue and actions.
 */
@Composable
fun ExperimentalTvBrowseScreen(
    catalog: CinemaCatalog,
    onOpen: (MediaContent) -> Unit,
    onPlay: (MediaContent) -> Unit,
    onToggleMyList: (MediaContent) -> Unit,
    onSettings: () -> Unit,
    onWatchlistRefresh: () -> Unit,
    homePreferences: HomeLayoutPreferences = HomeLayoutPreferences(),
    onDiscover: () -> Unit = {},
    onRefresh: () -> Unit = {},
    refreshing: Boolean = false,
    onHighlighted: (MediaContent?) -> Unit = {},
    loadCollectionMembers: (suspend (String) -> List<MediaContent>)? = null,
) {
    var destination by rememberSaveable {
        mutableStateOf(ExperimentalDestination.fromOpeningTab(homePreferences.openingTab))
    }
    var layout by rememberSaveable { mutableStateOf(ExperimentalLayout.Rows) }
    val heroFocus = remember { FocusRequester() }
    val layoutFocus = remember { FocusRequester() }
    val gridFirstFocus = remember { FocusRequester() }
    val collectionFocus = remember { FocusRequester() }
    val railFocus = remember { ExperimentalDestination.entries.associateWith { FocusRequester() } }
    val allMedia = remember(catalog.movies, catalog.shows) {
        (catalog.movies + catalog.shows).distinctBy(MediaContent::ratingKey)
    }
    val shelves = remember(catalog, destination) { experimentalShelves(catalog, destination) }
    val gridMedia = remember(catalog.movies, catalog.shows, catalog.myList, destination) {
        val source = when (destination) {
            ExperimentalDestination.Movies -> catalog.movies
            ExperimentalDestination.Series -> catalog.shows
            ExperimentalDestination.Watchlist -> catalog.myList
            else -> emptyList()
        }
        source.distinctBy(MediaContent::ratingKey).sortedWith(
            compareBy<MediaContent> { it.title.trim().lowercase() }.thenBy(MediaContent::ratingKey),
        )
    }
    val heroChoices = remember(destination, shelves, allMedia) {
        shelves.flatMap(ExperimentalShelf::media).distinctBy(MediaContent::ratingKey).ifEmpty {
            when (destination) {
                ExperimentalDestination.Movies -> catalog.movies
                ExperimentalDestination.Series -> catalog.shows
                ExperimentalDestination.Watchlist -> catalog.myList
                else -> allMedia
            }
        }
    }
    var highlighted by remember(destination) { mutableStateOf(heroChoices.firstOrNull()) }

    LaunchedEffect(destination, layout) {
        if (destination == ExperimentalDestination.Watchlist) onWatchlistRefresh()
        if (!destination.supportsGrid && layout == ExperimentalLayout.Grid) {
            layout = ExperimentalLayout.Rows
            return@LaunchedEffect
        }
        highlighted = heroChoices.firstOrNull()
        if (destination.hasHero || destination == ExperimentalDestination.Collections) {
            delay(90)
            runCatching {
                when {
                    destination == ExperimentalDestination.Collections -> collectionFocus.requestFocus()
                    layout == ExperimentalLayout.Grid -> gridFirstFocus.requestFocus()
                    else -> heroFocus.requestFocus()
                }
            }
        }
    }
    LaunchedEffect(heroChoices) {
        if (highlighted?.ratingKey !in heroChoices.map(MediaContent::ratingKey)) {
            highlighted = heroChoices.firstOrNull()
        }
    }
    LaunchedEffect(highlighted?.ratingKey) { onHighlighted(highlighted) }
    BackHandler(enabled = destination == ExperimentalDestination.Search) {
        destination = ExperimentalDestination.Home
    }

    Box(Modifier.fillMaxSize().background(MinovaBlack)) {
        if (destination.hasHero) {
            StableBackdrop(highlighted?.backdropUrl ?: highlighted?.posterUrl, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to MinovaNightDeep,
                        0.46f to MinovaNightDeep.copy(alpha = 0.78f),
                        0.78f to MinovaNightDeep.copy(alpha = 0.12f),
                        1f to Color.Transparent,
                    ),
                ),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.12f),
                        0.56f to Color.Transparent,
                        0.84f to MinovaNightDeep.copy(alpha = 0.82f),
                        1f to MinovaBlack,
                    ),
                ),
            )
        }

        Row(Modifier.fillMaxSize()) {
            ExperimentalNavigationRail(
                selected = destination,
                focusRequesters = railFocus,
                onSelected = { selected ->
                    when (selected) {
                        ExperimentalDestination.Settings -> onSettings()
                        ExperimentalDestination.Discover -> onDiscover()
                        else -> destination = selected
                    }
                },
            )

            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (destination) {
                    ExperimentalDestination.Search -> SearchScreen(
                        movies = catalog.movies,
                        shows = catalog.shows,
                        onOpen = onOpen,
                    )
                    ExperimentalDestination.Collections -> CollectionsScreen(
                        catalog = catalog,
                        entryFocus = collectionFocus,
                        onHeaderFocus = {
                            railFocus.getValue(ExperimentalDestination.Collections).requestFocus()
                        },
                        onOpen = onOpen,
                        loadMembers = loadCollectionMembers,
                    )
                    else -> ExperimentalBrowseContent(
                        destination = destination,
                        hero = highlighted,
                        shelves = shelves,
                        gridMedia = gridMedia,
                        heroFocus = heroFocus,
                        layoutFocus = layoutFocus,
                        gridFirstFocus = gridFirstFocus,
                        navigationFocus = railFocus.getValue(destination),
                        layout = layout,
                        onLayoutChanged = { layout = it },
                        isInMyList = { item -> catalog.myList.any { it.ratingKey == item.ratingKey } },
                        onOpen = onOpen,
                        onPlay = onPlay,
                        onToggleMyList = onToggleMyList,
                        onHighlighted = { highlighted = it },
                        onRefresh = onRefresh,
                        refreshing = refreshing,
                    )
                }
            }
        }
    }
}

private enum class ExperimentalDestination(
    val label: String,
    val icon: ImageVector,
    val hasHero: Boolean = true,
    val supportsGrid: Boolean = false,
) {
    Home("Home", Icons.Rounded.Home),
    Movies("Movies", Icons.Rounded.Movie, supportsGrid = true),
    Series("TV shows", Icons.Rounded.Tv, supportsGrid = true),
    Collections("Collections", Icons.Rounded.CollectionsBookmark, false),
    Watchlist("Watchlist", Icons.Rounded.Bookmark, supportsGrid = true),
    Search("Search", Icons.Rounded.Search, false),
    Discover("Discover", Icons.Rounded.Explore, false),
    Settings("Settings", Icons.Rounded.Settings, false),
    ;

    companion object {
        fun fromOpeningTab(value: String): ExperimentalDestination = when (value.lowercase()) {
            "movies" -> Movies
            "series" -> Series
            "collections" -> Collections
            "mylist", "watchlist" -> Watchlist
            "search" -> Search
            else -> Home
        }
    }
}

private enum class ExperimentalLayout { Rows, Grid }

private data class ExperimentalShelf(val title: String, val media: List<MediaContent>)

@Composable
private fun ExperimentalNavigationRail(
    selected: ExperimentalDestination,
    focusRequesters: Map<ExperimentalDestination, FocusRequester>,
    onSelected: (ExperimentalDestination) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .fillMaxHeight()
            .background(Color(0xE6071018))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f))
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = "Minova Cinema",
            modifier = Modifier.size(39.dp).clip(CircleShape),
        )
        Spacer(Modifier.height(20.dp))
        ExperimentalDestination.entries.forEach { item ->
            if (item == ExperimentalDestination.Settings) Spacer(Modifier.weight(1f))
            ExperimentalRailItem(
                destination = item,
                selected = item == selected,
                focusRequester = focusRequesters.getValue(item),
                onClick = { onSelected(item) },
            )
            if (item == ExperimentalDestination.Search) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ExperimentalRailItem(
    destination: ExperimentalDestination,
    selected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .size(48.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.scale(1.04f) else Modifier),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MinovaCyan.copy(alpha = 0.16f) else Color.Transparent,
            focusedContainerColor = MinovaCyan,
            pressedContainerColor = MinovaTeal,
        ),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = destination.icon,
                contentDescription = destination.label,
                tint = if (focused) Color(0xFF001419) else if (selected) MinovaCyan else MinovaMuted,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ExperimentalBrowseContent(
    destination: ExperimentalDestination,
    hero: MediaContent?,
    shelves: List<ExperimentalShelf>,
    gridMedia: List<MediaContent>,
    heroFocus: FocusRequester,
    layoutFocus: FocusRequester,
    gridFirstFocus: FocusRequester,
    navigationFocus: FocusRequester,
    layout: ExperimentalLayout,
    onLayoutChanged: (ExperimentalLayout) -> Unit,
    isInMyList: (MediaContent) -> Boolean,
    onOpen: (MediaContent) -> Unit,
    onPlay: (MediaContent) -> Unit,
    onToggleMyList: (MediaContent) -> Unit,
    onHighlighted: (MediaContent) -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(start = 48.dp, top = 16.dp, end = 42.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.minova_cinema_wordmark),
                contentDescription = "Minova Cinema",
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(110.dp).height(38.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "EXPERIMENTAL TV",
                color = MinovaCyan,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .border(1.dp, MinovaCyan.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
            if (destination.supportsGrid) {
                Spacer(Modifier.width(10.dp))
                ExperimentalLayoutButton(
                    layout = layout,
                    focusRequester = layoutFocus,
                    downFocus = if (layout == ExperimentalLayout.Grid) gridFirstFocus else heroFocus,
                    onClick = {
                        onLayoutChanged(
                            if (layout == ExperimentalLayout.Rows) ExperimentalLayout.Grid
                            else ExperimentalLayout.Rows,
                        )
                    },
                )
            }
        }

        if (layout == ExperimentalLayout.Grid && destination.supportsGrid) {
            ExperimentalMediaGrid(
                destination = destination,
                media = gridMedia,
                firstFocus = gridFirstFocus,
                navigationFocus = navigationFocus,
                layoutFocus = layoutFocus,
                onOpen = onOpen,
                onFocused = onHighlighted,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            return@Column
        }

        Column(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(start = 48.dp, top = 7.dp, end = 42.dp),
        ) {
            if (hero == null) {
                Text("Nothing to show yet", color = MinovaWhite, style = MaterialTheme.typography.headlineLarge)
                Text("Refresh your Plex library to load this section.", color = MinovaMuted)
            } else {
                Text(
                    destination.label.uppercase(),
                    color = MinovaCyan,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    hero.title,
                    color = Color.White,
                    style = if (hero.title.length > 32) {
                        MaterialTheme.typography.headlineLarge
                    } else {
                        MaterialTheme.typography.displaySmall
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(650.dp).padding(top = 2.dp),
                )
                Text(
                    buildList {
                        if (hero.metadataLine.isNotBlank()) add(hero.metadataLine)
                        hero.resolution?.let(::add)
                    }.joinToString("  •  "),
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                hero.summary?.let {
                    Text(
                        it,
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(650.dp).padding(top = 5.dp),
                    )
                }
                Row(
                    Modifier.padding(top = 11.dp).focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExperimentalHeroAction(
                        label = if (hero.viewOffsetMs > 0L || hero.kind == MediaKind.Show && hero.viewedLeafCount > 0) "Resume" else "Play",
                        icon = Icons.Rounded.PlayArrow,
                        primary = true,
                        modifier = Modifier
                            .focusRequester(heroFocus)
                            .testTag("experimental-hero-primary")
                            .focusProperties {
                                left = navigationFocus
                                up = layoutFocus
                            },
                        onClick = { onPlay(hero) },
                    )
                    ExperimentalHeroAction(
                        label = "Details",
                        icon = Icons.Rounded.Info,
                        modifier = Modifier.testTag("experimental-hero-details"),
                        onClick = { onOpen(hero) },
                    )
                    ExperimentalHeroAction(
                        label = if (isInMyList(hero)) "Saved" else "Watchlist",
                        icon = Icons.Rounded.Bookmark,
                        onClick = { onToggleMyList(hero) },
                    )
                    ExperimentalHeroAction(
                        label = if (refreshing) "Refreshing" else "Refresh",
                        icon = Icons.Rounded.Apps,
                        onClick = onRefresh,
                    )
                }
            }
        }
        if (shelves.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Text(
                    "No titles are available in this section.",
                    color = MinovaMuted,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 28.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 30.dp),
            ) {
                items(shelves, key = ExperimentalShelf::title) { shelf ->
                    ExperimentalMediaShelf(
                        shelf = shelf,
                        navigationFocus = navigationFocus,
                        onOpen = onOpen,
                        onFocused = onHighlighted,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExperimentalLayoutButton(
    layout: ExperimentalLayout,
    focusRequester: FocusRequester,
    downFocus: FocusRequester,
    onClick: () -> Unit,
) {
    val switchTo = if (layout == ExperimentalLayout.Rows) ExperimentalLayout.Grid else ExperimentalLayout.Rows
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(40.dp)
            .focusRequester(focusRequester)
            .focusProperties { down = downFocus }
            .testTag("experimental-layout-${layout.name.lowercase()}"),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = MinovaSurface.copy(alpha = 0.86f),
            contentColor = Color.White,
            focusedContainerColor = MinovaCyan,
            focusedContentColor = Color(0xFF001419),
        ),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                0.dp,
                RoundedCornerShape(10.dp),
            ),
            focusedBorder = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(2.dp, MinovaCyan),
                0.dp,
                RoundedCornerShape(10.dp),
            ),
        ),
    ) {
        Row(
            Modifier.fillMaxHeight().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                if (switchTo == ExperimentalLayout.Grid) Icons.Rounded.GridView else Icons.Rounded.ViewAgenda,
                contentDescription = "Switch to ${switchTo.name.lowercase()} view",
                modifier = Modifier.size(20.dp),
            )
            Text(switchTo.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ExperimentalMediaGrid(
    destination: ExperimentalDestination,
    media: List<MediaContent>,
    firstFocus: FocusRequester,
    navigationFocus: FocusRequester,
    layoutFocus: FocusRequester,
    onOpen: (MediaContent) -> Unit,
    onFocused: (MediaContent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(start = 48.dp, end = 42.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                destination.label,
                color = MinovaWhite,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(12.dp))
            Text("${media.size} titles  •  A–Z", color = MinovaMuted, style = MaterialTheme.typography.bodyMedium)
        }
        if (media.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No titles are available in this section.", color = MinovaMuted)
            }
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier.fillMaxWidth().weight(1f).focusGroup(),
            contentPadding = PaddingValues(start = 48.dp, top = 4.dp, end = 42.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(media, key = { _, item -> item.ratingKey }) { index, content ->
                ExperimentalPosterCard(
                    content = content,
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .focusProperties {
                            if (index < 6) up = layoutFocus
                            if (index % 6 == 0) left = navigationFocus
                        },
                    onOpen = onOpen,
                    onFocused = onFocused,
                )
            }
        }
    }
}

@Composable
private fun ExperimentalPosterCard(
    content: MediaContent,
    modifier: Modifier = Modifier,
    onOpen: (MediaContent) -> Unit,
    onFocused: (MediaContent) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = { onOpen(content) },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .testTag("experimental-grid-${content.ratingKey}")
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused(content)
            }
            .then(if (focused) Modifier.scale(1.035f) else Modifier),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = MinovaSurface,
            focusedContainerColor = MinovaSurfaceRaised,
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                0.dp,
                shape,
            ),
            focusedBorder = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(3.dp, MinovaCyan),
                0.dp,
                shape,
            ),
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            BrowseArtwork(
                url = content.posterUrl ?: content.backdropUrl,
                description = content.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.96f),
                    ),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp)) {
                Text(
                    content.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    content.secondaryTitle ?: content.metadataLine,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (content.isWatched) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(7.dp).size(21.dp)
                        .background(MinovaCyan, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("✓", color = Color(0xFF001419), fontWeight = FontWeight.Black) }
            }
            if (content.progress > 0f) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)
                        .background(Color.White.copy(alpha = 0.26f)),
                ) {
                    Box(
                        Modifier.fillMaxWidth(content.progress).fillMaxHeight().background(MinovaCyan),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExperimentalHeroAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (primary) MinovaCyan else MinovaSurface.copy(alpha = 0.92f),
            contentColor = if (primary) Color(0xFF001419) else Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF001419),
            pressedContainerColor = MinovaTeal,
            pressedContentColor = Color(0xFF001419),
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(
            focusedScale = 1f,
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                0.dp,
                RoundedCornerShape(999.dp),
            ),
            focusedBorder = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(1.dp, MinovaCyan),
                0.dp,
                RoundedCornerShape(999.dp),
            ),
        ),
    ) {
        Row(
            Modifier.fillMaxHeight().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, modifier = Modifier.size(21.dp))
            Text(label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExperimentalMediaShelf(
    shelf: ExperimentalShelf,
    navigationFocus: FocusRequester,
    onOpen: (MediaContent) -> Unit,
    onFocused: (MediaContent) -> Unit,
) {
    Column(Modifier.fillMaxWidth().height(188.dp)) {
        Text(
            shelf.title,
            color = MinovaWhite,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().focusGroup(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            items(shelf.media, key = { "${shelf.title}:${it.ratingKey}" }) { content ->
                ExperimentalLandscapeCard(
                    content = content,
                    modifier = if (content.ratingKey == shelf.media.firstOrNull()?.ratingKey) {
                        Modifier.focusProperties { left = navigationFocus }
                    } else Modifier,
                    onOpen = onOpen,
                    onFocused = onFocused,
                )
            }
        }
    }
}

@Composable
private fun ExperimentalLandscapeCard(
    content: MediaContent,
    modifier: Modifier = Modifier,
    onOpen: (MediaContent) -> Unit,
    onFocused: (MediaContent) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = { onOpen(content) },
        modifier = modifier
            .width(258.dp)
            .height(142.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused(content)
            }
            .then(if (focused) Modifier.scale(1.035f) else Modifier),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = MinovaSurface,
            focusedContainerColor = MinovaSurfaceRaised,
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                0.dp,
                shape,
            ),
            focusedBorder = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(3.dp, MinovaCyan),
                0.dp,
                shape,
            ),
        ),
        tonalElevation = if (focused) 8.dp else 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            BrowseArtwork(
                url = content.backdropUrl ?: content.posterUrl,
                description = content.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.48f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.93f),
                    ),
                ),
            )
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
            ) {
                Text(
                    content.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    content.secondaryTitle ?: content.metadataLine,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (content.isWatched) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(9.dp).size(23.dp)
                        .background(MinovaCyan, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("✓", color = Color(0xFF001419), fontWeight = FontWeight.Black) }
            }
            if (content.progress > 0f) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp)
                        .background(Color.White.copy(alpha = 0.26f)),
                ) {
                    Box(
                        Modifier.fillMaxWidth(content.progress).fillMaxHeight()
                            .background(MinovaCyan),
                    )
                }
            }
        }
    }
}

private fun experimentalShelves(
    catalog: CinemaCatalog,
    destination: ExperimentalDestination,
): List<ExperimentalShelf> {
    val source = when (destination) {
        ExperimentalDestination.Movies -> catalog.movies
        ExperimentalDestination.Series -> catalog.shows
        ExperimentalDestination.Watchlist -> catalog.myList
        else -> (catalog.movies + catalog.shows).distinctBy(MediaContent::ratingKey)
    }
    val continueWatching = when (destination) {
        ExperimentalDestination.Movies -> catalog.continueWatching.filter { it.kind == MediaKind.Movie }
        ExperimentalDestination.Series -> catalog.continueWatching.filter {
            it.kind == MediaKind.Show || it.kind == MediaKind.Season || it.kind == MediaKind.Episode
        }
        ExperimentalDestination.Watchlist -> {
            val keys = catalog.myList.mapTo(mutableSetOf(), MediaContent::ratingKey)
            catalog.continueWatching.filter { it.ratingKey in keys }
        }
        else -> catalog.continueWatching
    }
    val shelves = mutableListOf<ExperimentalShelf>()
    if (continueWatching.isNotEmpty()) shelves += ExperimentalShelf("Continue Watching", continueWatching.take(24))
    when (destination) {
        ExperimentalDestination.Home -> {
            buildHomeDiscoveryShelves(catalog).take(7).forEach { shelf ->
                if (shelf.media.isNotEmpty()) shelves += ExperimentalShelf(shelf.title, shelf.media.take(24))
            }
        }
        ExperimentalDestination.Watchlist -> if (source.isNotEmpty()) {
            shelves += ExperimentalShelf("Your Plex Watchlist", source.take(40))
        }
        ExperimentalDestination.Movies,
        ExperimentalDestination.Series -> {
            val recent = source.sortedByDescending { it.addedAtEpochSeconds ?: 0L }.take(30)
            if (recent.isNotEmpty()) shelves += ExperimentalShelf("Recently Added", recent)
            val topRated = source.filter { it.audienceRating != null }
                .sortedByDescending { it.audienceRating }.take(30)
            if (topRated.isNotEmpty()) shelves += ExperimentalShelf("Top Rated", topRated)
            source.flatMap(MediaContent::genres)
                .filter(String::isNotBlank)
                .groupingBy { it }.eachCount().entries
                .sortedByDescending(Map.Entry<String, Int>::value)
                .take(6)
                .forEach { (genre) ->
                    val items = source.filter { item -> item.genres.any { it.equals(genre, true) } }.take(30)
                    if (items.isNotEmpty()) shelves += ExperimentalShelf(genre, items)
                }
        }
        else -> Unit
    }
    val seen = mutableSetOf<String>()
    return shelves.mapNotNull { shelf ->
        val unique = shelf.media.filter { seen.add(it.ratingKey) }
        if (unique.isEmpty()) null else shelf.copy(media = unique)
    }
}
