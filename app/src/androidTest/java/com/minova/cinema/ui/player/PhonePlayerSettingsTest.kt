package com.minova.cinema.ui.player

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minova.cinema.showcase.PlayerSettingsShowcaseActivity
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeFalse
import org.junit.runner.RunWith

/** Verifies the production touch path used by the phone video player. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PhonePlayerSettingsTest {
    @get:Rule
    val compose = createAndroidComposeRule<PlayerSettingsShowcaseActivity>()

    @Test
    fun phoneTransportUsesIconsAndOneTapSubtitleToggle() {
        val uiMode = compose.activity.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        assumeFalse(uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION)

        compose.waitUntilAtLeastOneExists(hasTestTag("player-play-pause"), 8_000)
        compose.onNodeWithTag("player-seek-back").assertIsDisplayed()
        compose.onNodeWithTag("player-play-pause").assertIsDisplayed()
        compose.onNodeWithTag("player-seek-forward").assertIsDisplayed()
        compose.onNodeWithTag("player-subtitles-toggle").assertIsDisplayed()
        compose.onNodeWithTag("player-playback-settings").assertDoesNotExist()

        compose.waitUntilAtLeastOneExists(hasContentDescription("Turn subtitles on"), 3_000)
        compose.onNodeWithTag("player-subtitles-toggle").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Turn subtitles off"), 3_000)
    }
}
