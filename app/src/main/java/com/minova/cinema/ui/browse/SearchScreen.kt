package com.minova.cinema.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.ui.theme.MinovaCyan
import com.minova.cinema.ui.theme.MinovaMuted
import com.minova.cinema.ui.theme.MinovaNightDeep
import com.minova.cinema.ui.theme.MinovaSurface
import com.minova.cinema.ui.theme.MinovaSurfaceRaised
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Searches the complete movie and show collections returned by Plex.
 *
 * Android TV supplies its own remote-friendly keyboard when this field gains
 * focus. Physical and Bluetooth keyboards work through the same BasicTextField.
 */
@Composable
internal fun SearchScreen(
    movies: List<MediaContent>,
    shows: List<MediaContent>,
    onOpen: (MediaContent) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var fieldFocused by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val allMedia = remember(movies, shows) {
        (movies + shows).distinctBy { it.ratingKey }
    }
    val results = remember(query, allMedia) { searchMedia(allMedia, query) }

    // Entering the Search tab immediately opens the Android TV keyboard.
    LaunchedEffect(Unit) {
        delay(120)
        fieldFocus.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinovaNightDeep)
            .padding(top = 76.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it.take(100) },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                    fontWeight = FontWeight.Medium,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier
                    .width(570.dp)
                    .height(58.dp)
                    .focusRequester(fieldFocus)
                    .onFocusChanged {
                        fieldFocused = it.isFocused
                        if (it.isFocused) keyboard?.show()
                    },
                decorationBox = { input ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MinovaSurface, RoundedCornerShape(10.dp))
                            .border(
                                if (fieldFocused) 3.dp else 1.dp,
                                if (fieldFocused) MinovaCyan else MinovaSurfaceRaised,
                                RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isBlank()) {
                            Text(
                                "Search movies, series, genres or years",
                                color = MinovaMuted,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        input()
                    }
                },
            )
            Text(
                when {
                    query.isBlank() -> "${allMedia.size} titles available"
                    results.size == 1 -> "1 result"
                    else -> "${results.size} results"
                },
                color = if (query.isBlank()) MinovaMuted else MinovaCyan,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                query.isBlank() -> SearchMessage("Type a title, genre, year, movie, or series name.")
                results.isEmpty() -> SearchMessage("No matching titles were found on this Plex server.")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(148.dp),
                    modifier = Modifier.fillMaxSize().focusGroup(),
                    contentPadding = PaddingValues(
                        start = 48.dp,
                        end = 48.dp,
                        top = 12.dp,
                        bottom = 48.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    items(results, key = { it.ratingKey }) { content ->
                        PosterCard(content, Modifier.fillMaxWidth(), onOpen)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(bottom = 60.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = MinovaMuted, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Token-based, case-insensitive matching with title-prefix results first. */
private fun searchMedia(media: List<MediaContent>, rawQuery: String): List<MediaContent> {
    val query = rawQuery.trim().lowercase(Locale.ROOT)
    if (query.isBlank()) return emptyList()
    val tokens = query.split(Regex("\\s+")).filter(String::isNotBlank)

    return media.asSequence()
        .map { item ->
            val title = item.title.lowercase(Locale.ROOT)
            val kind = when (item.kind) {
                MediaKind.Movie -> "movie film"
                MediaKind.Show -> "show series tv"
                else -> ""
            }
            val searchable = buildString {
                append(title)
                append(' ')
                append(item.secondaryTitle.orEmpty().lowercase(Locale.ROOT))
                append(' ')
                append(item.year?.toString().orEmpty())
                append(' ')
                append(item.genres.joinToString(" ").lowercase(Locale.ROOT))
                append(' ')
                append(kind)
            }
            item to when {
                title == query -> 0
                title.startsWith(query) -> 1
                title.contains(query) -> 2
                tokens.all(searchable::contains) -> 3
                else -> Int.MAX_VALUE
            }
        }
        .filter { it.second != Int.MAX_VALUE }
        .sortedWith(compareBy<Pair<MediaContent, Int>> { it.second }.thenBy { it.first.title })
        .map { it.first }
        .toList()
}
