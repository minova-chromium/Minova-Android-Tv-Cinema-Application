package com.minova.cinema.ui.experience

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.tv.material3.Text
import com.minova.cinema.domain.*
import com.minova.cinema.ui.settings.SettingsPrimaryButton
import com.minova.cinema.ui.settings.SettingsSecondaryButton
import com.minova.cinema.ui.theme.MinovaMuted

@Composable
internal fun PlaybackRecoveryDialog(message: String, canLowerQuality: Boolean, onRetry: () -> Unit,
    onLowerQuality: () -> Unit, onReturn: () -> Unit) {
    CinemaPanel("Playback interrupted", onReturn, "Return to details") {
        item { Text(message, color = MinovaMuted) }
        item { Text("Your playback position is preserved. Check the connection to your Plex server, then choose an action.", color = MinovaMuted) }
        item { SettingsPrimaryButton(onClick = onRetry) { Text("Retry from this position") } }
        item { SettingsSecondaryButton(onClick = onLowerQuality, enabled = canLowerQuality) { Text("Try lower quality") } }
    }
}

@Composable
internal fun MovieFinishedDialog(movie: MediaContent, catalog: CinemaCatalog, onRate: (Int, (Boolean) -> Unit) -> Unit,
    onOpen: (MediaContent) -> Unit, onHome: () -> Unit) {
    var rating by remember(movie.ratingKey) { mutableIntStateOf(((movie.userRating ?: 0.0) / 2).toInt()) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val related = remember(movie, catalog.movies) { relatedTitles(movie, catalog.movies) }
    val next = remember(movie, catalog.movies) {
        movie.collections.firstNotNullOfOrNull { name -> collectionMembers(catalog.movies, name)
            .firstOrNull { it.ratingKey != movie.ratingKey && !it.isWatched } }
    }
    CinemaPanel("Finished · ${movie.title}", onHome, "Home") {
        item { Text("Nothing starts automatically. Choose another title or return Home.", color = MinovaMuted) }
        item { ChoiceStrip("Your Plex rating", (1..5).map { "$it" to "★".repeat(it) }, "$rating") { if (!saving) rating = it.toInt() } }
        item { SettingsSecondaryButton(enabled = rating > 0 && !saving, onClick = {
            saving = true
            onRate(rating * 2) { saved -> saving = false; message = if (saved) "Rating saved to Plex" else "Plex couldn't save the rating. Please try again." }
        }) { Text(if (saving) "Saving…" else "Save rating") } }
        message?.let { item { Text(it, color = MinovaMuted) } }
        next?.let { item { Text("Next unwatched in your Plex collection", color = MinovaMuted); MediaChoice(it, onOpen) } }
        if (related.isNotEmpty()) item { Text("More from your library", color = MinovaMuted) }
        items(related, key = { it.ratingKey }) { MediaChoice(it, onOpen) }
    }
}
