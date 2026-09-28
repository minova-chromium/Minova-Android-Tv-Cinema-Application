package com.minova.cinema.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.presentation.ShowDetailUiState
import com.minova.cinema.ui.theme.MinovaCinemaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DetailScreenNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun progressedSeriesDetailOffersResumeAndStartsSeriesResolution() {
        val show = MediaContent(
            ratingKey = "show-1",
            title = "Stuart Fails to Save the Universe",
            secondaryTitle = null,
            summary = "A long-running story that should continue at the correct episode.",
            tagline = null,
            year = 2026,
            durationMs = null,
            viewOffsetMs = 0L,
            posterUrl = null,
            backdropUrl = null,
            contentRating = "TV-MA",
            kind = MediaKind.Show,
            childCount = 1,
            viewedLeafCount = 1,
        )
        var played: MediaContent? = null
        compose.setContent {
            MinovaCinemaTheme {
                DetailScreen(
                    content = show,
                    showDetail = ShowDetailUiState.Ready(show, emptyList(), null, emptyList()),
                    trailers = emptyList(),
                    isWatched = false,
                    isInMyList = false,
                    isInContinueWatching = true,
                    seriesHasProgress = true,
                    onPlay = { played = it },
                    onPlayTrailer = {},
                    onWatchedChanged = {},
                    onToggleMyList = {},
                    onRemoveFromContinueWatching = {},
                    onOpenEpisode = {},
                    onSeasonSelected = {},
                )
            }
        }

        compose.onNodeWithText("Resume").assertIsDisplayed()
        compose.onNodeWithTag("detail-primary-action")
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(show, played) }
    }
}
