package com.minova.cinema.ui.browse

import androidx.activity.ComponentActivity
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.printToLog
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.layout.Column
import com.minova.cinema.ui.settings.SettingsSecondaryButton
import androidx.tv.material3.Text
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** D-Pad regression coverage for the browse entry points that have broken on TV. */
class BrowseScreenNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun featuredCarouselRetainsPlayFocusForRemotePressesAndHeldRepeats() {
        var played: MediaContent? = null
        var watchlistChanges = 0
        showBrowseScreen(mediaCount = 6, onPlayed = { played = it }, onWatchlist = { watchlistChanges++ })
        val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        compose.onNodeWithTag("header-tab-Home").performSemanticsAction(SemanticsActions.RequestFocus)
        device.pressDPadDown()
        compose.onNodeWithTag("hero-primary-action").assertIsFocused()
        device.pressDPadRight()
        compose.onNodeWithTag("hero-title").assertTextEquals("Movie 2")
        device.pressDPadRight()
        compose.onNodeWithTag("hero-title").assertTextEquals("Movie 3")
        device.pressDPadLeft()
        compose.onNodeWithTag("hero-title").assertTextEquals("Movie 2")
        val heldAt = SystemClock.uptimeMillis()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        repeat(3) { repeat ->
            assertTrue(automation.injectInputEvent(KeyEvent(heldAt, SystemClock.uptimeMillis(),
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, repeat, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_DPAD), true))
            compose.waitForIdle()
            compose.onNodeWithTag("hero-primary-action").assertIsFocused()
        }
        automation.injectInputEvent(KeyEvent(heldAt, SystemClock.uptimeMillis(),
            KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0), true)
        compose.onNodeWithTag("hero-title").assertTextEquals("Movie 5")
        device.pressDPadRight()
        device.pressDPadRight()
        compose.onNodeWithTag("hero-title").assertTextEquals("Movie 1")
        device.pressDPadLeft()
        compose.onNodeWithTag("hero-title").assertTextEquals("Movie 6")
        compose.onNodeWithTag("hero-primary-action").assertIsFocused()
        device.pressDPadCenter()
        compose.runOnIdle { assertEquals("movie-6", played?.ratingKey); assertEquals(0, watchlistChanges) }
    }

    @Test
    fun featuredWatchlistAndLibraryRemainReachableWithoutChangingTheTitle() {
        var saved: MediaContent? = null
        showBrowseScreen(mediaCount = 6, includeContinueWatching = true, onWatchlist = { saved = it })
        val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        compose.onNodeWithTag("hero-primary-action").performSemanticsAction(SemanticsActions.RequestFocus)
        device.pressDPadDown()
        compose.onNodeWithTag("hero-watchlist-action").assertIsFocused()
        device.pressDPadCenter()
        compose.runOnIdle { assertEquals("movie-1", saved?.ratingKey) }
        device.pressDPadRight()
        compose.onNodeWithTag("hero-watchlist-action").assertIsFocused()
        compose.onNodeWithTag("hero-title").assertTextEquals("Movie 1")
        device.pressDPadLeft()
        compose.onNodeWithTag("hero-primary-action").assertIsFocused()
        device.pressDPadDown()
        device.pressDPadUp()
        compose.onNodeWithTag("hero-primary-action").assertIsFocused()
        device.pressDPadDown()
        device.pressDPadDown()
        compose.onNodeWithTag("browse-first-continue").assertIsFocused()
        device.pressDPadUp()
        compose.onNodeWithTag("hero-primary-action").assertIsFocused()
    }

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
        compose.onNodeWithTag("hero-watchlist-action").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("browse-first-continue")
            .performKeyInput { pressKey(Key.DirectionDown) }

        waitUntilFocused("browse-shelf-top-picks-movie-2")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        waitUntilFocused("browse-shelf-because-you-watched-movie-2")
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
        compose.onNodeWithTag("hero-watchlist-action").assertIsFocused()
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
        compose.onNodeWithTag("hero-watchlist-action").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("browse-first-continue")
            .performKeyInput { pressKey(Key.DirectionDown) }
        waitUntilFocused("browse-shelf-top-picks-movie-2")

        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        waitUntilFocused("browse-shelf-because-you-watched-movie-2")
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

    @Test
    fun thousandTitleGridSurvivesRepeatedDirectionChanges() {
        showBrowseScreen(mediaCount = 1000)
        compose.onNodeWithTag("header-tab-Movies").performClick()
        compose.onNodeWithTag("header-action-Row view. Switch to grid view").performClick()
        compose.onNodeWithTag("catalog-grid-first-card").performSemanticsAction(SemanticsActions.RequestFocus)
        repeat(8) {
            compose.onRoot().performKeyInput { repeat(3) { pressKey(Key.DirectionRight) }; pressKey(Key.DirectionDown) }
            compose.waitForIdle()
            compose.onRoot().performKeyInput { pressKey(Key.DirectionUp); repeat(3) { pressKey(Key.DirectionLeft) } }
            compose.waitForIdle()
        }
        compose.onNodeWithTag("catalog-grid-first-card").assertIsFocused()
    }

    @Test
    fun longOkOpensActionsWithoutOpeningDetails() {
        showBrowseScreen(mediaCount = 30)
        compose.onNodeWithTag("header-tab-Movies").performClick()
        compose.onNodeWithTag("header-action-Row view. Switch to grid view").performClick()
        compose.onNodeWithTag("catalog-grid-first-card").performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { keyDown(Key.DirectionCenter); advanceEventTime(800); keyUp(Key.DirectionCenter) }
        compose.onNodeWithText("More information").assertIsDisplayed()
        compose.onNodeWithText("Close").performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Close").assertDoesNotExist()
        compose.onNodeWithTag("catalog-grid-first-card").assertIsFocused()
    }

    @Test
    fun physicalHeldOkReleaseDoesNotActivateDialogButFreshPressDoes() {
        verifyPhysicalHeldOkRelease(sendRepeats = true)
    }

    @Test
    fun physicalHeldOkReleaseWithoutRepeatsAlsoWaitsForFreshPress() {
        verifyPhysicalHeldOkRelease(sendRepeats = false)
    }

    private fun verifyPhysicalHeldOkRelease(sendRepeats: Boolean) {
        var playRequests = 0
        showBrowseScreen(mediaCount = 30, onPlayRequested = { playRequests++ })
        compose.onNodeWithTag("header-tab-Movies").performClick()
        compose.onNodeWithTag("header-action-Row view. Switch to grid view").performClick()
        compose.onNodeWithTag("catalog-grid-first-card")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()

        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun send(downAt: Long, action: Int, repeat: Int = 0) {
            assertTrue(automation.injectInputEvent(KeyEvent(
                downAt, SystemClock.uptimeMillis(), action, KeyEvent.KEYCODE_DPAD_CENTER,
                repeat, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                if (repeat == 1) KeyEvent.FLAG_LONG_PRESS else 0, InputDevice.SOURCE_DPAD,
            ), true))
        }
        val heldAt = SystemClock.uptimeMillis()
        send(heldAt, KeyEvent.ACTION_DOWN)
        try {
            compose.waitUntil(5_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasText("More information"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            // Unlike performKeyInput on the poster, these events enter the NEW
            // Android dialog window, exactly as a physical remote release does.
            compose.onNodeWithText("Play", useUnmergedTree = true)
                .assertIsDisplayed()
            if (sendRepeats) {
                send(heldAt, KeyEvent.ACTION_DOWN, repeat = 1)
                send(heldAt, KeyEvent.ACTION_DOWN, repeat = 2)
            }
        } finally {
            send(heldAt, KeyEvent.ACTION_UP)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("Releasing held OK must not start playback", 0, playRequests) }
        compose.onNodeWithText("More information").assertIsDisplayed()

        val freshAt = SystemClock.uptimeMillis()
        send(freshAt, KeyEvent.ACTION_DOWN)
        send(freshAt, KeyEvent.ACTION_UP)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("A new OK press should still select Play", 1, playRequests) }
        compose.onNodeWithText("More information").assertDoesNotExist()
    }

    @Test
    fun switchingTabsRestoresExactGridTitle() {
        showBrowseScreen(mediaCount = 100)
        compose.onNodeWithTag("header-tab-Movies").performClick()
        compose.onNodeWithTag("header-action-Row view. Switch to grid view").performClick()
        compose.onNodeWithTag("catalog-grid-first-card").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onRoot().performKeyInput { repeat(2) { pressKey(Key.DirectionRight) } }
        val expected = compose.onAllNodes(isFocused()).fetchSemanticsNodes().single().config[androidx.compose.ui.semantics.SemanticsProperties.Text]
        compose.onNodeWithTag("header-tab-Home").performClick()
        compose.onNodeWithTag("header-tab-Movies").performClick()
        compose.waitForIdle()
        val restored = compose.onAllNodes(isFocused()).fetchSemanticsNodes().single().config[androidx.compose.ui.semantics.SemanticsProperties.Text]
        org.junit.Assert.assertEquals(expected, restored)
    }

    @Test
    fun detailReturnRestoresDeepShelfPositionAfterRefresh() {
        showBrowseScreen(includeContinueWatching = true, mediaCount = 200, simulateNavigation = true)
        compose.onNodeWithTag("header-tab-Home").performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onRoot().performKeyInput { repeat(3) { pressKey(Key.DirectionDown) } }
        waitUntilFocused("browse-shelf-top-picks-movie-2")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionDown) }
        waitUntilFocused("browse-shelf-recently-added-movie-1")
        repeat(12) { compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }; compose.waitForIdle() }
        waitUntilFocused("browse-shelf-recently-added-movie-13")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Return from details").performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        try { waitUntilFocused("browse-shelf-recently-added-movie-13") }
        catch (error: Throwable) { compose.onRoot().printToLog("MinovaReturnTree"); throw error }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionUp) }
        waitUntilFocused("browse-shelf-recently-added-movie-13")
    }

    private fun showBrowseScreen(includeContinueWatching: Boolean = false, mediaCount: Int = 1, simulateNavigation: Boolean = false,
        onPlayRequested: () -> Unit = {}, onPlayed: (MediaContent) -> Unit = {},
        onWatchlist: (MediaContent) -> Unit = {}) {
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
                var details by remember { mutableStateOf(false) }
                var refreshed by remember { mutableStateOf(false) }
                val holder = rememberSaveableStateHolder()
                if (details) {
                    SettingsSecondaryButton(onClick = { refreshed = true; details = false }) { Text("Return from details") }
                } else holder.SaveableStateProvider("browse") {
                BrowseScreen(
                    catalog = CinemaCatalog(
                        serverName = "Navigation test",
                        movies = if (refreshed) listOf(movies.first().copy(
                            ratingKey = "newly-added", title = "Newly Added While Away", addedAtEpochSeconds = 99_999L,
                        )) + movies else movies,
                        shows = emptyList(),
                        continueWatching = if (includeContinueWatching) {
                            listOf(movie.copy(viewOffsetMs = 1_800_000L))
                        } else {
                            emptyList()
                        },
                    ),
                    onOpen = { if (simulateNavigation) details = true },
                    onPlay = { onPlayRequested(); onPlayed(it) },
                    onToggleMyList = onWatchlist,
                    onSettings = {},
                    onWatchlistRefresh = {},
                )
                }
            }
        }
    }

    private fun waitUntilFocused(tag: String) {
        compose.waitUntil(timeoutMillis = 2_000) {
            runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess
        }
    }
}
