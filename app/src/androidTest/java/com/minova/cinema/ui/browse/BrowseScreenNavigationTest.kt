package com.minova.cinema.ui.browse

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
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

    @Test
    fun multipleHomeShelvesRemainNavigableAfterRepeatedUpDownMoves() {
        showBrowseScreen(includeContinueWatching = true, mediaCount = 14)
        compose.onNodeWithTag("header-tab-Home")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("hero-primary-action")
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("browse-first-continue")
            .performKeyInput { pressKey(Key.DirectionDown) }

        waitUntilFocused("browse-shelf-top-picks-movie-2")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        waitUntilFocused("browse-shelf-because-movie-1-movie-2")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        waitUntilFocused("browse-shelf-recently-added-movie-1")
        compose.onRoot().performKeyInput {
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionUp)
        }
        waitUntilFocused("browse-shelf-top-picks-movie-2")

        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        waitUntilFocused("browse-first-continue")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        waitUntilFocused("browse-shelf-top-picks-movie-2")
    }

    @Test
    fun horizontalBrowsingCanReturnUpAndDownWithoutHeaderLock() {
        showBrowseScreen(includeContinueWatching = true, mediaCount = 14)
        compose.onNodeWithTag("header-tab-Home")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("hero-primary-action")
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("browse-first-continue")
            .performKeyInput { pressKey(Key.DirectionDown) }
        waitUntilFocused("browse-shelf-top-picks-movie-2")

        compose.onRoot().performKeyInput {
            repeat(4) { pressKey(Key.DirectionRight) }
            pressKey(Key.DirectionUp)
        }
        waitUntilFocused("browse-first-continue")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        waitUntilFocused("browse-shelf-top-picks-movie-6")
    }

    @Test
    fun eachShelfRestoresItsOwnHorizontalPositionAfterVerticalNavigation() {
        showBrowseScreen(includeContinueWatching = true, mediaCount = 14)
        compose.onNodeWithTag("header-tab-Home")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("hero-primary-action")
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("browse-first-continue")
            .performKeyInput { pressKey(Key.DirectionDown) }
        waitUntilFocused("browse-shelf-top-picks-movie-2")

        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        waitUntilFocused("browse-shelf-because-movie-1-movie-2")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        waitUntilFocused("browse-shelf-recently-added-movie-1")
        compose.onRoot().performKeyInput { repeat(4) { pressKey(Key.DirectionRight) } }
        waitUntilFocused("browse-shelf-recently-added-movie-5")

        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        waitUntilFocused("browse-shelf-new-releases-movie-1")
        compose.onRoot().performKeyInput { repeat(2) { pressKey(Key.DirectionRight) } }
        waitUntilFocused("browse-shelf-new-releases-movie-3")

        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        waitUntilFocused("browse-shelf-recently-added-movie-5")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        waitUntilFocused("browse-shelf-new-releases-movie-3")
    }

    private fun showBrowseScreen(includeContinueWatching: Boolean = false, mediaCount: Int = 1) {
        val movies = List(mediaCount) { index ->
            MediaContent(
                ratingKey = "movie-${index + 1}",
                title = "Movie ${index + 1}",
                secondaryTitle = null,
                summary = "A test movie used to verify television focus navigation.",
                tagline = null,
                year = 2026 - index,
                durationMs = 7_200_000L,
                viewOffsetMs = 0L,
                posterUrl = null,
                backdropUrl = null,
                contentRating = "PG-13",
                kind = MediaKind.Movie,
                genres = listOf("Drama"),
                addedAtEpochSeconds = 2_000L - index,
            )
        }
        val movie = movies.first()
        compose.setContent {
            MinovaCinemaTheme {
                BrowseScreen(
                    catalog = CinemaCatalog(
                        serverName = "Navigation test",
                        movies = movies,
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
