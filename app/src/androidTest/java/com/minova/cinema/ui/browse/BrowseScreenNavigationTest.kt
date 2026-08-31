package com.minova.cinema.ui.browse

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsActions
import com.minova.cinema.domain.CinemaCatalog
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.ui.theme.MinovaCinemaTheme
import org.junit.Rule
import org.junit.Test

/** D-Pad regression coverage for the browse entry points that have broken on TV. */
class BrowseScreenNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun moviesHeroExplainsDownNavigation() {
        showBrowseScreen()

        compose.onNodeWithTag("header-tab-Movies").performClick()

        compose.onNodeWithTag("browse-catalog-cue").assertIsDisplayed()
    }

    @Test
    fun downFromGridHeaderFocusesFirstMovieInsteadOfAlphabetRail() {
        showBrowseScreen()
        compose.onNodeWithTag("header-tab-Movies").performClick()

        val layoutToggle = compose.onNodeWithTag(
            "header-action-Row view. Switch to grid view",
        )
        layoutToggle.performSemanticsAction(SemanticsActions.RequestFocus)
        layoutToggle.performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("header-action-Grid view. Switch to row view")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }

        compose.onNodeWithTag("catalog-grid-first-card").assertIsFocused()
    }

    @Test
    fun rowModeTraversesHeroContinueCatalogAndBackWithDpad() {
        showBrowseScreen(includeContinueWatching = true)
        val moviesTab = compose.onNodeWithTag("header-tab-Movies")
        moviesTab.performSemanticsAction(SemanticsActions.RequestFocus)
        moviesTab.performClick()

        compose.onNodeWithTag("header-tab-Movies")
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("hero-primary-action").assertIsFocused()

        compose.onNodeWithTag("hero-primary-action")
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("browse-first-continue").assertIsFocused()

        compose.onNodeWithTag("browse-first-continue")
            .performKeyInput { pressKey(Key.DirectionDown) }
        waitUntilFocused("browse-first-genre")

        compose.onNodeWithTag("browse-first-genre")
            .performKeyInput { pressKey(Key.DirectionUp) }
        waitUntilFocused("browse-first-continue")

        compose.onNodeWithTag("browse-first-continue")
            .performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("hero-primary-action").assertIsFocused()
    }

    private fun showBrowseScreen(includeContinueWatching: Boolean = false) {
        val movie = MediaContent(
            ratingKey = "movie-1",
            title = "Alpha Movie",
            secondaryTitle = null,
            summary = "A test movie used to verify television focus navigation.",
            tagline = null,
            year = 2026,
            durationMs = 7_200_000L,
            viewOffsetMs = 0L,
            posterUrl = null,
            backdropUrl = null,
            contentRating = "PG-13",
            kind = MediaKind.Movie,
            genres = listOf("Drama"),
        )
        compose.setContent {
            MinovaCinemaTheme {
                BrowseScreen(
                    catalog = CinemaCatalog(
                        serverName = "Navigation test",
                        movies = listOf(movie),
                        shows = emptyList(),
                        continueWatching = if (includeContinueWatching) {
                            listOf(movie.copy(viewOffsetMs = 1_800_000L))
                        } else {
                            emptyList()
                        },
                    ),
                    onOpen = {},
                    onPlay = {},
                    onToggleMyList = {},
                    onSettings = {},
                    onWatchlistRefresh = {},
                )
            }
        }
    }

    private fun waitUntilFocused(tag: String) {
        compose.waitUntil(timeoutMillis = 2_000) {
            runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess
        }
    }
}
