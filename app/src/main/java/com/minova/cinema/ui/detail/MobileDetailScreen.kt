package com.minova.cinema.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaCredit
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.presentation.ShowDetailUiState
import com.minova.cinema.ui.browse.BrowseArtwork
import com.minova.cinema.ui.theme.MinovaBlack
import com.minova.cinema.ui.theme.MinovaCyan
import com.minova.cinema.ui.theme.MinovaMuted
import com.minova.cinema.ui.theme.MinovaNightDeep
import com.minova.cinema.ui.theme.MinovaSurface
import com.minova.cinema.ui.theme.MinovaSurfaceRaised
import com.minova.cinema.ui.theme.MinovaTeal
import com.minova.cinema.ui.theme.MinovaWhite
import com.minova.cinema.ui.cast.MinovaCastButton

@Composable
internal fun MobileDetailScreen(
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
    onBack: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val readyShow = showDetail as? ShowDetailUiState.Ready
    val playableEpisode = readyShow?.episodes?.firstOrNull { it.viewOffsetMs > 0L }
        ?: readyShow?.episodes?.firstOrNull { !it.isWatched }
        ?: readyShow?.episodes?.firstOrNull()
    LaunchedEffect(
        content.ratingKey,
        readyShow?.selectedSeason?.ratingKey,
        readyShow?.seasons?.firstOrNull()?.ratingKey,
    ) {
        if (content.kind == MediaKind.Show && readyShow?.selectedSeason == null) {
            val defaultSeason = readyShow?.seasons?.firstOrNull { (it.seasonNumber ?: 0) > 0 }
                ?: readyShow?.seasons?.firstOrNull()
            defaultSeason?.let(onSeasonSelected)
        }
    }
    val technical = content.playback?.technicalInfo
    val selectedAudio = content.playback?.audioStreams?.firstOrNull { it.selected }
    val selectedSubtitle = content.playback?.subtitles?.firstOrNull { it.selected }
    val videoLabel = buildList {
        (content.resolution ?: technical?.videoResolution)?.let { add(it.uppercase()) }
        technical?.videoCodec?.let { add(it.uppercase()) }
    }.joinToString(" · ").ifBlank { "Details available during playback" }
    val audioLabel = selectedAudio?.let { stream ->
        buildList {
            add(stream.language ?: stream.label)
            stream.codec?.let { add(it.uppercase()) }
            stream.channels?.let { add(if (it == 6) "5.1" else "$it ch") }
        }.joinToString(" · ")
    } ?: content.audioLanguages.firstOrNull() ?: "Select during playback"
    val subtitleLabel = selectedSubtitle?.let { stream ->
        listOfNotNull(stream.language ?: stream.label, stream.codec?.uppercase()).joinToString(" · ")
    } ?: "Off"

    Box(Modifier.fillMaxSize().background(MinovaNightDeep)) {
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 36.dp),
        ) {
            item {
                Box(Modifier.fillMaxWidth().height(if (landscape) 330.dp else 470.dp)) {
                    AsyncImage(
                        model = if (landscape) {
                            content.backdropUrl ?: content.posterUrl
                        } else {
                            content.posterUrl ?: content.backdropUrl
                        },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.08f),
                                0.55f to Color.Transparent,
                                1f to MinovaNightDeep,
                            ),
                        ),
                    )
                    MobileCircleButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        description = "Back",
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, top = 12.dp),
                    )
                    MinovaCastButton(
                        Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 12.dp, top = 8.dp),
                    )
                    Column(
                        Modifier.align(Alignment.BottomCenter).padding(horizontal = 18.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        content.secondaryTitle?.let {
                            Text(it.uppercase(), color = MinovaCyan, fontSize = 11.sp, letterSpacing = 1.sp)
                        }
                        Text(
                            content.title,
                            color = MinovaWhite,
                            fontSize = if (landscape) 29.sp else 32.sp,
                            lineHeight = 35.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        if (content.metadataLine.isNotBlank()) {
                            Text(
                                content.metadataLine,
                                color = MinovaWhite.copy(alpha = 0.88f),
                                modifier = Modifier.padding(top = 7.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (content.genres.isNotEmpty()) {
                            Text(
                                content.genres.take(3).joinToString("  •  "),
                                color = MinovaCyan,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 6.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (content.audienceRating != null || content.userRating != null) {
                            Row(
                                Modifier.padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                content.audienceRating?.let { MobileRatingChip("PLEX", String.format("%.1f", it)) }
                                content.userRating?.let { MobileRatingChip("YOU", String.format("%.1f", it)) }
                            }
                        }
                    }
                }
            }

            if (content.kind != MediaKind.Show) {
                item {
                    MobilePrimaryPlayButton(content = content, onClick = { onPlay(content) })
                }
            } else if (playableEpisode != null) {
                item {
                    MobilePrimaryPlayButton(
                        content = playableEpisode,
                        label = buildString {
                            append(if (playableEpisode.viewOffsetMs > 0L) "Resume" else "Play")
                            playableEpisode.secondaryTitle?.let { append("  ·  ").append(it) }
                        },
                        onClick = { onOpenEpisode(playableEpisode) },
                    )
                }
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        MobileRoundAction(
                            label = if (isInMyList) "In Watchlist" else "Watchlist",
                            icon = if (isInMyList) Icons.Rounded.Check else Icons.Rounded.Bookmark,
                            onClick = onToggleMyList,
                        )
                    }
                    trailers.firstOrNull()?.let { trailer ->
                        item { MobileRoundAction("Trailer", Icons.Rounded.Movie) { onPlayTrailer(trailer) } }
                    }
                    item {
                        MobileRoundAction(
                            label = if (isWatched) "Watched" else "Mark watched",
                            icon = Icons.Rounded.CheckCircle,
                        ) { onWatchedChanged(!isWatched) }
                    }
                    if (isInContinueWatching) {
                        item { MobileRoundAction("Remove", Icons.Rounded.DeleteOutline, onRemoveFromContinueWatching) }
                    }
                }
            }

            content.tagline?.let { tagline ->
                item {
                    Text(
                        tagline,
                        color = MinovaCyan,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    )
                }
            }
            content.summary?.let { summary ->
                item {
                    Text(
                        summary,
                        color = MinovaWhite.copy(alpha = 0.88f),
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            }
            content.credits.firstOrNull { it.role.contains("director", ignoreCase = true) }?.let { director ->
                item {
                    Text(
                        "Directed by ${director.name}",
                        color = MinovaMuted,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    )
                }
            }

            if (content.kind != MediaKind.Show) {
                item {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                        MobileTechnicalRow("Video", videoLabel)
                        MobileTechnicalRow("Audio", audioLabel)
                        MobileTechnicalRow("Subtitles", subtitleLabel, accent = selectedSubtitle != null)
                    }
                }
            }

            if (content.kind == MediaKind.Show) {
                when (showDetail) {
                    ShowDetailUiState.Idle -> Unit
                    is ShowDetailUiState.Loading -> item { MobileDetailMessage("Loading seasons…") }
                    is ShowDetailUiState.Error -> item { MobileDetailMessage(showDetail.message) }
                    is ShowDetailUiState.Ready -> {
                        item { MobileSectionTitle("Seasons") }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                                items(showDetail.seasons, key = MediaContent::ratingKey) { season ->
                                    MobileSeasonCard(season, season.ratingKey == showDetail.selectedSeason?.ratingKey) { onSeasonSelected(season) }
                                }
                            }
                        }
                        showDetail.selectedSeason?.let { season ->
                            item { MobileSectionTitle(season.title) }
                            if (showDetail.loadingEpisodes) {
                                item { MobileDetailMessage("Loading episodes…") }
                            } else {
                                items(showDetail.episodes, key = MediaContent::ratingKey) { episode ->
                                    MobileEpisodeRow(episode) { onOpenEpisode(episode) }
                                }
                            }
                        }
                        if (showDetail.selectedSeason == null && showDetail.show.credits.isNotEmpty()) {
                            item { MobileCredits(showDetail.show.credits) }
                        }
                    }
                }
            } else if (content.credits.isNotEmpty()) {
                item { MobileCredits(content.credits) }
            }
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).statusBarsPadding())
    }
}

@Composable
private fun MobileCircleButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(52.dp).clip(CircleShape).background(MinovaBlack.copy(alpha = 0.68f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icon, description, tint = MinovaWhite, modifier = Modifier.size(27.dp))
    }
}

@Composable
private fun MobileRatingChip(source: String, rating: String) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(MinovaBlack.copy(alpha = 0.72f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(source, color = MinovaCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(rating, color = MinovaWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MobilePrimaryPlayButton(
    content: MediaContent,
    label: String = if (content.viewOffsetMs > 0L) {
        "Resume${content.remainingTimeLabel?.let { "  ·  $it" }.orEmpty()}"
    } else {
        "Play"
    },
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
            .height(56.dp).clip(RoundedCornerShape(28.dp)).background(MinovaCyan)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        androidx.compose.material3.Icon(Icons.Rounded.PlayArrow, null, tint = MinovaBlack, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = MinovaBlack, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MobileRoundAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        Modifier.width(82.dp).clickable(onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(50.dp).clip(CircleShape).background(MinovaSurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(icon, null, tint = MinovaWhite, modifier = Modifier.size(25.dp))
        }
        Text(
            label,
            color = MinovaMuted,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun MobileTechnicalRow(label: String, value: String, accent: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, color = MinovaMuted, fontSize = 16.sp, modifier = Modifier.width(105.dp))
        Text(value, color = if (accent) MinovaCyan else MinovaWhite.copy(alpha = 0.88f), fontSize = 16.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MobileSectionTitle(title: String) {
    Text(title, color = MinovaWhite, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 5.dp))
}

@Composable
private fun MobileSeasonCard(season: MediaContent, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.height(44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (selected) MinovaCyan else MinovaSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (selected) {
            androidx.compose.material3.Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = MinovaBlack,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            season.title,
            color = if (selected) MinovaBlack else MinovaWhite,
            maxLines = 1,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun MobileEpisodeRow(episode: MediaContent, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp)).background(MinovaSurface).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(132.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
            BrowseArtwork(episode.backdropUrl ?: episode.posterUrl, episode.title, Modifier.fillMaxSize(), ContentScale.Crop)
            if (episode.progress > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(episode.progress).height(4.dp).background(MinovaTeal))
            if (episode.isWatched) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp)
                        .background(MinovaCyan, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Rounded.Check,
                        contentDescription = "Watched",
                        tint = MinovaBlack,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(episode.secondaryTitle ?: episode.title, color = MinovaWhite, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(
                if (episode.isWatched) "Watched · ${episode.metadataLine}" else episode.remainingTimeLabel ?: episode.metadataLine,
                color = if (episode.isWatched) MinovaCyan else MinovaMuted,
                maxLines = 1,
                fontSize = 12.sp,
            )
        }
        androidx.compose.material3.Icon(Icons.Rounded.PlayArrow, "Play", tint = MinovaCyan)
    }
}

@Composable
private fun MobileCredits(credits: List<MediaCredit>) {
    Column(Modifier.padding(top = 14.dp)) {
        MobileSectionTitle("Cast & crew")
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(credits, key = { "${it.name}-${it.role}" }) { credit ->
                Column(Modifier.width(104.dp)) {
                    AsyncImage(credit.imageUrl, credit.name, contentScale = ContentScale.Crop, modifier = Modifier.size(92.dp).clip(RoundedCornerShape(12.dp)).background(MinovaSurface))
                    Text(credit.name, color = MinovaWhite, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
                    Text(credit.role, color = MinovaMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun MobileDetailMessage(message: String) {
    Text(message, color = MinovaMuted, modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp))
}
