package com.minova.cinema.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.domain.MediaCredit
import com.minova.cinema.presentation.ShowDetailUiState
import com.minova.cinema.ui.theme.MinovaCyan
import com.minova.cinema.ui.theme.MinovaBlack
import com.minova.cinema.ui.theme.MinovaMuted
import com.minova.cinema.ui.theme.MinovaNightDeep
import com.minova.cinema.ui.theme.MinovaSurface
import com.minova.cinema.ui.theme.MinovaSurfaceRaised
import com.minova.cinema.ui.theme.MinovaTeal

@Composable
fun DetailScreen(
    content: MediaContent,
    showDetail: ShowDetailUiState,
    trailers: List<MediaContent>,
    isWatched: Boolean,
    isInMyList: Boolean,
    isInContinueWatching: Boolean,
    onPlay: (MediaContent) -> Unit,
    onPlayTrailer: (MediaContent) -> Unit,
    onWatchedChanged: (Boolean) -> Unit,
    onToggleMyList: () -> Unit,
    onRemoveFromContinueWatching: () -> Unit,
    onOpenEpisode: (MediaContent) -> Unit,
    onSeasonSelected: (MediaContent) -> Unit,
) {
    val detailListState = rememberLazyListState()
    val titleFocusRequester = remember(content.ratingKey) { FocusRequester() }
    val firstSeasonFocusRequester = remember(content.ratingKey) { FocusRequester() }

    // Make the title the explicit initial TV focus anchor. This eliminates the
    // focus/scroll race entirely: a wrapped title remains at item zero, and one
    // D-Pad Down moves to the primary Play/Resume row.
    LaunchedEffect(content.ratingKey) {
        detailListState.scrollToItem(0)
        titleFocusRequester.requestFocus()
        detailListState.scrollToItem(0)
    }

    Box(Modifier.fillMaxSize().background(MinovaNightDeep)) {
        AsyncImage(
            model = content.backdropUrl ?: content.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(500.dp),
        )
        Box(
            Modifier.fillMaxWidth().height(500.dp).background(
                Brush.horizontalGradient(
                    0f to MinovaNightDeep,
                    0.55f to MinovaNightDeep.copy(alpha = 0.72f),
                    1f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxWidth().height(500.dp).background(
                Brush.verticalGradient(listOf(Color.Transparent, MinovaNightDeep.copy(alpha = 0.2f), MinovaNightDeep)),
            ),
        )

        LazyColumn(
            state = detailListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 84.dp, bottom = 48.dp),
        ) {
            item {
                DetailHero(
                    content = content,
                    isWatched = isWatched,
                    isInMyList = isInMyList,
                    isInContinueWatching = isInContinueWatching,
                    trailers = trailers,
                    titleFocusRequester = titleFocusRequester,
                    firstSeasonFocusRequester = firstSeasonFocusRequester,
                    onPlay = onPlay,
                    onPlayTrailer = onPlayTrailer,
                    onWatchedChanged = onWatchedChanged,
                    onToggleMyList = onToggleMyList,
                    onRemoveFromContinueWatching = onRemoveFromContinueWatching,
                )
            }
            if (content.kind == MediaKind.Show) {
                item {
                    ShowBrowser(
                        state = showDetail,
                        titleFocusRequester = titleFocusRequester,
                        firstSeasonFocusRequester = firstSeasonFocusRequester,
                        onSeasonSelected = onSeasonSelected,
                        onOpenEpisode = onOpenEpisode,
                    )
                }
            }
            if (content.kind == MediaKind.Movie && content.credits.isNotEmpty()) {
                item { CastAndCrew(content.credits) }
            }
        }
    }
}

@Composable
private fun DetailHero(
    content: MediaContent,
    isWatched: Boolean,
    isInMyList: Boolean,
    isInContinueWatching: Boolean,
    trailers: List<MediaContent>,
    titleFocusRequester: FocusRequester,
    firstSeasonFocusRequester: FocusRequester,
    onPlay: (MediaContent) -> Unit,
    onPlayTrailer: (MediaContent) -> Unit,
    onWatchedChanged: (Boolean) -> Unit,
    onToggleMyList: () -> Unit,
    onRemoveFromContinueWatching: () -> Unit,
) {
    val primaryActionFocusRequester = remember(content.ratingKey) { FocusRequester() }

    Column(
        modifier = Modifier
            .width(650.dp)
            .heightIn(min = 440.dp)
            .padding(start = 62.dp, top = 34.dp, bottom = 28.dp),
    ) {
        content.secondaryTitle?.let {
            Text(it.uppercase(), color = MinovaCyan, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            content.title,
            color = Color.White,
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier
                .focusRequester(titleFocusRequester)
                .focusProperties {
                    down = if (content.kind == MediaKind.Show) {
                        firstSeasonFocusRequester
                    } else {
                        primaryActionFocusRequester
                    }
                }
                .focusable(),
        )
        if (content.metadataLine.isNotBlank()) {
            Text(
                content.metadataLine,
                color = MinovaMuted,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (content.genres.isNotEmpty()) {
            Text(
                content.genres.take(5).joinToString("  •  "),
                color = MinovaCyan,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        content.remainingTimeLabel?.let {
            Text(
                it,
                color = MinovaTeal,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 7.dp),
            )
        }
        content.tagline?.let {
            Text(
                it,
                color = MinovaCyan,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        content.summary?.let {
            Text(
                it,
                color = Color(0xFFD7DCDE),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (content.kind != MediaKind.Show) {
            Row(
                modifier = Modifier.padding(top = 20.dp).focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { onPlay(content) },
                    modifier = Modifier.focusRequester(primaryActionFocusRequester),
                ) {
                    Text(if (content.viewOffsetMs > 0L) "Resume" else "Play")
                }
                trailers.firstOrNull()?.let { trailer ->
                    Button(onClick = { onPlayTrailer(trailer) }) {
                        Text("Watch trailer")
                    }
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 12.dp).focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onToggleMyList) {
                Text(if (isInMyList) "Remove from Plex Watchlist" else "Add to Plex Watchlist")
            }
            Button(onClick = { onWatchedChanged(!isWatched) }) {
                Text(if (isWatched) "Mark unwatched" else "Mark watched")
            }
        }
        if (isInContinueWatching) {
            Button(
                onClick = onRemoveFromContinueWatching,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Remove from Continue Watching")
            }
        }
    }
}

@Composable
private fun ShowBrowser(
    state: ShowDetailUiState,
    titleFocusRequester: FocusRequester,
    firstSeasonFocusRequester: FocusRequester,
    onSeasonSelected: (MediaContent) -> Unit,
    onOpenEpisode: (MediaContent) -> Unit,
) {
    when (state) {
        ShowDetailUiState.Idle -> Unit
        is ShowDetailUiState.Loading -> DetailStatus("Loading seasons…")
        is ShowDetailUiState.Error -> DetailStatus(state.message)
        is ShowDetailUiState.Ready -> {
            Column {
                Text(
                    "Seasons",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 62.dp),
                )
                LazyRow(
                    modifier = Modifier.focusGroup(),
                    contentPadding = PaddingValues(horizontal = 62.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.seasons, key = { it.ratingKey }) { season ->
                        val firstSeasonModifier = if (season.ratingKey == state.seasons.firstOrNull()?.ratingKey) {
                            Modifier
                                .focusRequester(firstSeasonFocusRequester)
                                .focusProperties { up = titleFocusRequester }
                        } else {
                            Modifier
                        }
                        SeasonPosterCard(
                            season = season,
                            selected = season.ratingKey == state.selectedSeason?.ratingKey,
                            onClick = { onSeasonSelected(season) },
                            modifier = firstSeasonModifier,
                        )
                    }
                }

                if (state.selectedSeason == null) {
                    if (state.show.credits.isNotEmpty()) {
                        CastAndCrew(state.show.credits)
                    }
                } else {
                    Text(
                        state.selectedSeason.title,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 62.dp, vertical = 8.dp),
                    )
                    if (state.loadingEpisodes) {
                        DetailStatus("Loading episodes…")
                    } else {
                        LazyRow(
                            modifier = Modifier.focusGroup(),
                            contentPadding = PaddingValues(horizontal = 62.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            items(state.episodes, key = { it.ratingKey }) { episode ->
                                EpisodeCard(episode, onClick = { onOpenEpisode(episode) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonPosterCard(
    season: MediaContent,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.045f else 1f,
        animationSpec = tween(120),
        label = "season_scale",
    )
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = modifier
            .width(156.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused || selected) 3.dp else 1.dp,
                color = if (focused || selected) MinovaCyan else MinovaSurfaceRaised,
                shape = shape,
            )
            .clip(shape)
            .background(MinovaSurface)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(MinovaBlack)) {
            AsyncImage(
                model = season.posterUrl,
                contentDescription = season.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (season.isWatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MinovaCyan),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", color = MinovaBlack, fontWeight = FontWeight.Bold)
                }
            }
        }
        Column(Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                season.title,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            season.childCount?.let { count ->
                Text(
                    "$count episodes",
                    color = MinovaMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun CastAndCrew(credits: List<MediaCredit>) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(
            "Cast & Crew",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 62.dp),
        )
        LazyRow(
            modifier = Modifier.focusGroup(),
            contentPadding = PaddingValues(horizontal = 62.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(credits.take(30), key = { "${it.name}:${it.role}" }) { credit ->
                CreditCard(credit)
            }
        }
    }
}

@Composable
private fun CreditCard(credit: MediaCredit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(120),
        label = "credit_scale",
    )

    Column(
        modifier = Modifier
            .width(126.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .border(
                    if (focused) 3.dp else 1.dp,
                    if (focused) MinovaCyan else MinovaSurfaceRaised,
                    CircleShape,
                )
                .clip(CircleShape)
                .background(MinovaSurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (credit.imageUrl != null) {
                AsyncImage(
                    model = credit.imageUrl,
                    contentDescription = credit.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    credit.name.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                )
            }
        }
        Text(
            credit.name,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 9.dp),
        )
        Text(
            credit.role,
            color = MinovaMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun EpisodeCard(episode: MediaContent, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.045f else 1f, tween(120), label = "episode_scale")
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = Modifier
            .width(310.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged { focused = it.isFocused }
            .border(if (focused) 2.dp else 1.dp, if (focused) MinovaCyan else MinovaSurfaceRaised, shape)
            .clip(shape)
            .background(MinovaSurface)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(154.dp)) {
            AsyncImage(
                model = episode.backdropUrl ?: episode.posterUrl,
                contentDescription = episode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (episode.progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(episode.progress)
                        .height(4.dp)
                        .background(MinovaTeal),
                )
            }
            episode.timeLeftLabel?.let {
                Text(
                    text = it,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(MinovaBlack.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (episode.isWatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MinovaCyan),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", color = MinovaBlack, fontWeight = FontWeight.Bold)
                }
            }
        }
        Column(Modifier.fillMaxWidth().height(90.dp).padding(12.dp)) {
            Text(
                "E${episode.episodeNumber ?: ""}  ${episode.title}",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            episode.summary?.let {
                Text(
                    it,
                    color = MinovaMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailStatus(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 62.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = MinovaMuted, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(8.dp))
    }
}
