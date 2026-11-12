package com.minova.cinema.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.minova.cinema.R
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaCredit
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.offline.OfflineDownload
import com.minova.cinema.offline.OfflineDownloadState
import com.minova.cinema.presentation.ShowDetailUiState
import com.minova.cinema.ui.browse.BrowseArtwork
import com.minova.cinema.ui.browse.StableBackdrop
import com.minova.cinema.ui.theme.MinovaBlack
import com.minova.cinema.ui.theme.MinovaCyan
import com.minova.cinema.ui.theme.MinovaMuted
import com.minova.cinema.ui.theme.MinovaNightDeep
import com.minova.cinema.ui.theme.MinovaSurface
import com.minova.cinema.ui.theme.MinovaSurfaceRaised
import com.minova.cinema.ui.theme.MinovaTeal
import kotlinx.coroutines.delay

/** TV-only cinematic details used by the opt-in Desktop-inspired interface. */
@Composable
fun ExperimentalTvDetailScreen(
    content: MediaContent,
    showDetail: ShowDetailUiState,
    trailers: List<MediaContent>,
    relatedMedia: List<MediaContent>,
    isWatched: Boolean,
    isInMyList: Boolean,
    isInContinueWatching: Boolean,
    seriesHasProgress: Boolean = false,
    onPlay: (MediaContent) -> Unit,
    onPlayTrailer: (MediaContent) -> Unit,
    onWatchedChanged: (Boolean) -> Unit,
    onToggleMyList: () -> Unit,
    onRemoveFromContinueWatching: () -> Unit,
    onOpenPerson: (MediaCredit) -> Unit = {},
    onOpenEpisode: (MediaContent) -> Unit,
    onSeasonSelected: (MediaContent) -> Unit,
    offlineDownloads: List<OfflineDownload> = emptyList(),
    onDownload: (MediaContent) -> Unit = {},
    onPlayOffline: (OfflineDownload) -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenRelated: (MediaContent) -> Unit,
    onBack: () -> Unit = {},
) {
    val primaryFocus = remember(content.ratingKey) { FocusRequester() }
    val offline = offlineDownloads.firstOrNull { it.ratingKey == content.ratingKey }
    LaunchedEffect(content.ratingKey) {
        delay(90)
        runCatching { primaryFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(MinovaNightDeep)) {
        StableBackdrop(content.backdropUrl ?: content.posterUrl, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to MinovaNightDeep,
                    0.43f to MinovaNightDeep.copy(alpha = 0.88f),
                    0.77f to MinovaNightDeep.copy(alpha = 0.18f),
                    1f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.16f),
                    0.52f to Color.Transparent,
                    0.82f to MinovaNightDeep.copy(alpha = 0.9f),
                    1f to MinovaBlack,
                ),
            ),
        )

        Column(Modifier.fillMaxSize()) {
            ExperimentalDetailHero(
                content = content,
                trailers = trailers,
                isWatched = isWatched,
                isInMyList = isInMyList,
                isInContinueWatching = isInContinueWatching,
                seriesHasProgress = seriesHasProgress,
                offline = offline,
                primaryFocus = primaryFocus,
                onPlay = onPlay,
                onPlayTrailer = onPlayTrailer,
                onWatchedChanged = onWatchedChanged,
                onToggleMyList = onToggleMyList,
                onRemoveFromContinueWatching = onRemoveFromContinueWatching,
                onDownload = onDownload,
                onPlayOffline = onPlayOffline,
                onOpenDownloads = onOpenDownloads,
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 52.dp),
            ) {
                if (content.kind == MediaKind.Show) {
                    item {
                        ExperimentalShowBrowser(
                            state = showDetail,
                            onSeasonSelected = onSeasonSelected,
                            onOpenEpisode = onOpenEpisode,
                        )
                    }
                }
                if (content.kind != MediaKind.Show && relatedMedia.isNotEmpty()) {
                    item {
                        ExperimentalRelatedShelf(
                            title = "More Like This",
                            media = relatedMedia,
                            onOpen = onOpenRelated,
                        )
                    }
                }
                val credits = when (showDetail) {
                    is ShowDetailUiState.Ready -> if (content.kind == MediaKind.Show) showDetail.show.credits else content.credits
                    else -> content.credits
                }
                if (credits.isNotEmpty()) {
                    item { ExperimentalCastShelf(credits, onOpenPerson) }
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExperimentalCircleAction(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                description = "Back",
                onClick = onBack,
            )
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.minova_cinema_wordmark),
                contentDescription = "Minova Cinema",
                contentScale = ContentScale.Fit,
                modifier = Modifier.padding(start = 18.dp).width(106.dp).height(36.dp),
            )
        }
    }
}

@Composable
private fun ExperimentalDetailHero(
    content: MediaContent,
    trailers: List<MediaContent>,
    isWatched: Boolean,
    isInMyList: Boolean,
    isInContinueWatching: Boolean,
    seriesHasProgress: Boolean,
    offline: OfflineDownload?,
    primaryFocus: FocusRequester,
    onPlay: (MediaContent) -> Unit,
    onPlayTrailer: (MediaContent) -> Unit,
    onWatchedChanged: (Boolean) -> Unit,
    onToggleMyList: () -> Unit,
    onRemoveFromContinueWatching: () -> Unit,
    onDownload: (MediaContent) -> Unit,
    onPlayOffline: (OfflineDownload) -> Unit,
    onOpenDownloads: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().height(348.dp)
            .padding(start = 76.dp, top = 72.dp, end = 52.dp),
    ) {
        Text(
            if (content.kind == MediaKind.Show) "TV SHOW" else if (content.kind == MediaKind.Episode) "EPISODE" else "MOVIE",
            color = MinovaCyan,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            content.title,
            color = Color.White,
            style = MaterialTheme.typography.displayMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(760.dp).padding(top = 4.dp),
        )
        Text(
            buildList {
                if (content.metadataLine.isNotBlank()) add(content.metadataLine)
                content.resolution?.let(::add)
                if (content.genres.isNotEmpty()) add(content.genres.take(3).joinToString(" · "))
            }.joinToString("  •  "),
            color = Color.White.copy(alpha = 0.82f),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(820.dp).padding(top = 9.dp),
        )
        content.summary?.let {
            Text(
                it,
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(730.dp).padding(top = 12.dp),
            )
        }
        Row(
            modifier = Modifier.padding(top = 18.dp).focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            ExperimentalDetailAction(
                label = when {
                    content.kind == MediaKind.Show && seriesHasProgress -> "Resume"
                    content.viewOffsetMs > 0L -> "Resume"
                    else -> "Play"
                },
                icon = Icons.Rounded.PlayArrow,
                primary = true,
                modifier = Modifier.focusRequester(primaryFocus),
                onClick = { onPlay(content) },
            )
            trailers.firstOrNull()?.let { trailer ->
                ExperimentalDetailAction(
                    label = "Trailer",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = { onPlayTrailer(trailer) },
                )
            }
            ExperimentalDetailAction(
                label = if (isInMyList) "Saved" else "Watchlist",
                icon = Icons.Rounded.Bookmark,
                onClick = onToggleMyList,
            )
            ExperimentalDetailAction(
                label = if (isWatched) "Watched" else "Mark watched",
                icon = Icons.Rounded.Check,
                onClick = { onWatchedChanged(!isWatched) },
            )
            when (offline?.state) {
                OfflineDownloadState.Ready -> ExperimentalDetailAction(
                    label = "Play offline",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = { onPlayOffline(offline) },
                )
                OfflineDownloadState.Preparing,
                OfflineDownloadState.Downloading,
                OfflineDownloadState.Paused -> ExperimentalDetailAction(
                    label = "Downloading",
                    icon = Icons.Rounded.Download,
                    onClick = onOpenDownloads,
                )
                else -> ExperimentalDetailAction(
                    label = "Download",
                    icon = Icons.Rounded.Download,
                    onClick = { onDownload(content) },
                )
            }
        }
        if (isInContinueWatching) {
            Row(Modifier.padding(top = 10.dp)) {
                ExperimentalDetailAction(
                    label = "Remove from Continue Watching",
                    icon = Icons.Rounded.DeleteOutline,
                    compact = true,
                    onClick = onRemoveFromContinueWatching,
                )
                if (content.viewOffsetMs > 0L) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        content.remainingTimeLabel.orEmpty(),
                        color = MinovaTeal,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExperimentalDetailAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(if (compact) 40.dp else 48.dp),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (primary) MinovaCyan else MinovaSurface.copy(alpha = 0.94f),
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
            Modifier.fillMaxHeight().padding(horizontal = if (compact) 14.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, null, modifier = Modifier.size(if (compact) 18.dp else 21.dp))
            Text(label, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun ExperimentalCircleAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(CircleShape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color(0xCC121C25),
            contentColor = Color.White,
            focusedContainerColor = MinovaCyan,
            focusedContentColor = Color(0xFF001419),
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, description, modifier = Modifier.size(25.dp))
        }
    }
}

@Composable
private fun ExperimentalShowBrowser(
    state: ShowDetailUiState,
    onSeasonSelected: (MediaContent) -> Unit,
    onOpenEpisode: (MediaContent) -> Unit,
) {
    when (state) {
        ShowDetailUiState.Idle -> Unit
        is ShowDetailUiState.Loading -> ExperimentalDetailMessage("Loading seasons…")
        is ShowDetailUiState.Error -> ExperimentalDetailMessage(state.message)
        is ShowDetailUiState.Ready -> Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            LaunchedEffect(state.show.ratingKey, state.selectedSeason?.ratingKey, state.seasons) {
                if (state.selectedSeason == null) {
                    state.seasons.firstOrNull()?.let(onSeasonSelected)
                }
            }
            Text(
                "EPISODES",
                color = MinovaCyan,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 76.dp),
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().focusGroup(),
                contentPadding = PaddingValues(horizontal = 76.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(state.seasons, key = MediaContent::ratingKey) { season ->
                    ExperimentalSeasonTab(
                        season = season,
                        selected = season.ratingKey == state.selectedSeason?.ratingKey,
                        onClick = { onSeasonSelected(season) },
                    )
                }
            }
            if (state.loadingEpisodes) {
                ExperimentalDetailMessage("Loading episodes…")
            } else if (state.episodes.isEmpty()) {
                ExperimentalDetailMessage("No episodes were found for this season.")
            } else {
                val selectedEpisode = state.episodes.firstOrNull { it.viewOffsetMs > 0L && !it.isWatched }
                    ?: state.episodes.firstOrNull { !it.isWatched }
                    ?: state.episodes.first()
                Text(
                    "${selectedEpisode.episodeNumber ?: ""}  ${selectedEpisode.title}",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 76.dp, top = 2.dp),
                )
                selectedEpisode.summary?.let {
                    Text(
                        it,
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(800.dp).padding(start = 76.dp, top = 5.dp),
                    )
                }
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).focusGroup(),
                    contentPadding = PaddingValues(horizontal = 76.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.episodes, key = MediaContent::ratingKey) { episode ->
                        ExperimentalEpisodeCard(episode, onClick = { onOpenEpisode(episode) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ExperimentalSeasonTab(
    season: MediaContent,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(42.dp),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MinovaCyan.copy(alpha = 0.2f) else MinovaSurface,
            contentColor = Color.White,
            focusedContainerColor = MinovaCyan,
            focusedContentColor = Color(0xFF001419),
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MinovaCyan else MinovaSurfaceRaised),
                0.dp,
                RoundedCornerShape(8.dp),
            ),
            focusedBorder = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                0.dp,
                RoundedCornerShape(8.dp),
            ),
        ),
    ) {
        Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Text(season.title, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExperimentalEpisodeCard(episode: MediaContent, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.width(326.dp).height(183.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.scale(1.035f) else Modifier),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = MinovaSurface,
            focusedContainerColor = MinovaSurfaceRaised,
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), 0.dp, shape,
            ),
            focusedBorder = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(3.dp, MinovaCyan), 0.dp, shape,
            ),
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            BrowseArtwork(
                episode.backdropUrl ?: episode.posterUrl,
                episode.title,
                Modifier.fillMaxSize(),
                ContentScale.Crop,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.Transparent, 0.52f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.96f)),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp)) {
                Text(
                    "${episode.episodeNumber ?: ""}  ${episode.title}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    episode.remainingTimeLabel ?: episode.metadataLine,
                    color = Color.White.copy(alpha = 0.68f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
            if (episode.isWatched) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(10.dp).size(25.dp).background(MinovaCyan, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("✓", color = Color(0xFF001419), fontWeight = FontWeight.Black) }
            }
            if (episode.progress > 0f) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp)
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(Modifier.fillMaxWidth(episode.progress).fillMaxHeight().background(MinovaCyan))
                }
            }
        }
    }
}

@Composable
private fun ExperimentalRelatedShelf(
    title: String,
    media: List<MediaContent>,
    onOpen: (MediaContent) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            title,
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 76.dp, bottom = 10.dp),
        )
        LazyRow(
            modifier = Modifier.focusGroup(),
            contentPadding = PaddingValues(horizontal = 76.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(media.take(20), key = MediaContent::ratingKey) { item ->
                ExperimentalRelatedCard(item, onOpen)
            }
        }
    }
}

@Composable
private fun ExperimentalRelatedCard(content: MediaContent, onOpen: (MediaContent) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = { onOpen(content) },
        modifier = Modifier.width(286.dp).height(160.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.scale(1.035f) else Modifier),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = MinovaSurface,
            focusedContainerColor = MinovaSurfaceRaised,
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), 0.dp, shape),
            focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, MinovaCyan), 0.dp, shape),
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            BrowseArtwork(content.backdropUrl ?: content.posterUrl, content.title, Modifier.fillMaxSize(), ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.94f))))
            Text(
                content.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
            )
        }
    }
}

@Composable
private fun ExperimentalCastShelf(
    credits: List<MediaCredit>,
    onOpenPerson: (MediaCredit) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
        Text(
            "Cast & Crew",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 76.dp, bottom = 10.dp),
        )
        LazyRow(
            modifier = Modifier.focusGroup(),
            contentPadding = PaddingValues(horizontal = 76.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            items(credits.take(30), key = { "${it.name}:${it.role}" }) { credit ->
                var focused by remember { mutableStateOf(false) }
                Column(
                    Modifier.width(118.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        onClick = { onOpenPerson(credit) },
                        modifier = Modifier.size(104.dp)
                            .onFocusChanged { focused = it.isFocused }
                            .then(if (focused) Modifier.scale(1.05f) else Modifier),
                        enabled = !credit.personId.isNullOrBlank(),
                        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(CircleShape),
                        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                            containerColor = MinovaSurfaceRaised,
                            focusedContainerColor = MinovaCyan,
                        ),
                        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
                            border = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, MinovaSurfaceRaised), 0.dp, CircleShape),
                            focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, MinovaCyan), 0.dp, CircleShape),
                        ),
                    ) {
                        if (credit.imageUrl != null) {
                            AsyncImage(
                                model = credit.imageUrl,
                                contentDescription = credit.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(credit.name.take(1), style = MaterialTheme.typography.headlineLarge)
                            }
                        }
                    }
                    Text(
                        credit.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        credit.role,
                        color = MinovaMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExperimentalDetailMessage(message: String) {
    Text(
        message,
        color = MinovaMuted,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 76.dp, vertical = 24.dp),
    )
}
