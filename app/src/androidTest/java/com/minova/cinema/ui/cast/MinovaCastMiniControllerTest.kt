package com.minova.cinema.ui.cast

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.minova.cinema.cast.CastPlaybackStatus
import com.minova.cinema.cast.MinovaCastState
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.ui.theme.MinovaCinemaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MinovaCastMiniControllerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun connectedMediaShowsPhoneRemoteAndActionsWork() {
        var openCount = 0
        var pauseCount = 0
        var stopCount = 0

        compose.setContent {
            MinovaCinemaTheme {
                MinovaCastMiniController(
                    state = MinovaCastState(
                        connected = true,
                        deviceName = "Living room TV",
                        content = testMovie(),
                        status = CastPlaybackStatus.Playing,
                        positionMs = 30_000L,
                        durationMs = 120_000L,
                    ),
                    onOpen = { openCount += 1 },
                    onTogglePlayback = { pauseCount += 1 },
                    onStop = { stopCount += 1 },
                )
            }
        }

        compose.onNodeWithText("The Test Movie").assertIsDisplayed().performClick()
        compose.onNodeWithText("Playing on Living room TV").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Stop casting").assertIsDisplayed().performClick()

        compose.runOnIdle {
            assertEquals(1, openCount)
            assertEquals(1, pauseCount)
            assertEquals(1, stopCount)
        }
    }

    private fun testMovie() = MediaContent(
        ratingKey = "42",
        title = "The Test Movie",
        secondaryTitle = null,
        summary = null,
        tagline = null,
        year = 2026,
        durationMs = 120_000L,
        viewOffsetMs = 30_000L,
        posterUrl = null,
        backdropUrl = null,
        contentRating = "PG",
        kind = MediaKind.Movie,
    )
}
