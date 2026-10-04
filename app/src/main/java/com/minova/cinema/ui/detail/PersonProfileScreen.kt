package com.minova.cinema.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.PersonProfile
import com.minova.cinema.presentation.PersonProfileUiState
import com.minova.cinema.ui.theme.MinovaBlack
import com.minova.cinema.ui.theme.MinovaCyan
import com.minova.cinema.ui.theme.MinovaMuted
import com.minova.cinema.ui.theme.MinovaNightDeep
import com.minova.cinema.ui.theme.MinovaSurface
import com.minova.cinema.ui.theme.MinovaSurfaceRaised
import com.minova.cinema.ui.theme.MinovaWhite

@Composable
fun PersonProfileScreen(
    state: PersonProfileUiState,
    onBack: () -> Unit,
    onOpen: (MediaContent) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MinovaNightDeep)) {
        when (state) {
            PersonProfileUiState.Idle -> PersonProfileMessage("Select a cast member to view their profile.")
            is PersonProfileUiState.Loading -> PersonProfileMessage("Loading ${state.credit.name}…")
            is PersonProfileUiState.Error -> PersonProfileMessage(state.message)
            is PersonProfileUiState.Ready -> PersonProfileContent(state.profile, onOpen)
        }

        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 18.dp, top = 12.dp)
                .size(52.dp)
                .clip(CircleShape)
                .background(MinovaBlack.copy(alpha = 0.72f))
                .clickable(role = Role.Button, onClick = onBack)
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MinovaWhite,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun PersonProfileContent(profile: PersonProfile, onOpen: (MediaContent) -> Unit) {
    val uriHandler = LocalUriHandler.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = if (compact) 86.dp else 96.dp,
                bottom = 42.dp,
            ),
        ) {
            item {
                if (compact) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PersonPortrait(profile, 172.dp)
                        Spacer(Modifier.height(18.dp))
                        PersonCopy(profile, centered = true) { url -> uriHandler.openUri(url) }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 62.dp),
                        horizontalArrangement = Arrangement.spacedBy(38.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        PersonPortrait(profile, 260.dp)
                        Box(Modifier.weight(1f).padding(top = 12.dp)) {
                            PersonCopy(profile, centered = false) { url -> uriHandler.openUri(url) }
                        }
                    }
                }
            }

            if (profile.media.isNotEmpty()) {
                item {
                    Text(
                        "Movies & Shows in Your Library",
                        color = MinovaWhite,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(
                            start = if (compact) 22.dp else 62.dp,
                            end = 22.dp,
                            top = 36.dp,
                        ),
                    )
                }
                item {
                    LazyRow(
                        modifier = Modifier.focusGroup(),
                        contentPadding = PaddingValues(
                            horizontal = if (compact) 22.dp else 62.dp,
                            vertical = 18.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 13.dp else 20.dp),
                    ) {
                        items(profile.media, key = MediaContent::ratingKey) { content ->
                            PersonMediaCard(content, compact, onOpen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonPortrait(profile: PersonProfile, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .width(size)
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(18.dp))
            .background(MinovaSurfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        if (profile.imageUrl != null) {
            AsyncImage(
                model = profile.imageUrl,
                contentDescription = profile.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                profile.name.firstOrNull()?.uppercase() ?: "?",
                color = MinovaWhite,
                style = MaterialTheme.typography.displayLarge,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Transparent, MinovaBlack.copy(alpha = 0.32f)),
                ),
            ),
        )
    }
}

@Composable
private fun PersonCopy(
    profile: PersonProfile,
    centered: Boolean,
    openUrl: (String) -> Unit,
) {
    Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            profile.name,
            color = MinovaWhite,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        if (profile.role.isNotBlank()) {
            Text(
                profile.role,
                color = MinovaCyan,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        Text(
            profile.biography ?: "Biography information is not available yet.",
            color = MinovaWhite.copy(alpha = 0.86f),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            modifier = Modifier.padding(top = 22.dp),
        )
        Row(
            modifier = Modifier.padding(top = 20.dp).focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            profile.imdbUrl?.let { url ->
                Button(onClick = { openUrl(url) }) {
                    androidx.compose.material3.Icon(
                        Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = MinovaBlack,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("Open IMDb")
                }
            }
            profile.biographySourceUrl?.let { url ->
                Button(onClick = { openUrl(url) }) {
                    Text(profile.biographySource ?: "Biography source")
                }
            }
        }
    }
}

@Composable
private fun PersonMediaCard(
    content: MediaContent,
    compact: Boolean,
    onOpen: (MediaContent) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val width = if (compact) 128.dp else 156.dp
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .width(width)
            .graphicsLayer {
                val scale = if (focused) 1.045f else 1f
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) MinovaCyan else Color.Transparent,
                shape,
            )
            .clip(shape)
            .background(MinovaSurface)
            .clickable(role = Role.Button) { onOpen(content) }
            .focusable(),
    ) {
        AsyncImage(
            model = content.posterUrl ?: content.backdropUrl,
            contentDescription = content.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(MinovaSurfaceRaised),
        )
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(
                content.title,
                color = MinovaWhite,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                content.year?.toString() ?: content.kind.name,
                color = MinovaMuted,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun PersonProfileMessage(message: String) {
    Box(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = MinovaMuted, style = MaterialTheme.typography.titleLarge)
    }
}
