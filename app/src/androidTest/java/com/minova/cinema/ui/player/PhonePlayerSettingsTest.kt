package com.minova.cinema.ui.player

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
    fun playbackSettingsOpenFromTouchControls() {
        val uiMode = compose.activity.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        assumeFalse(uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION)

        compose.waitUntilAtLeastOneExists(hasTestTag("player-playback-settings"), 8_000)
        compose.onNodeWithTag("player-playback-settings")
            .assertIsDisplayed()
            .performClick()

        compose.waitUntilAtLeastOneExists(hasText("Playback settings"), 3_000)
        compose.onNodeWithText("Playback settings").assertIsDisplayed()
        compose.onNodeWithText("Original").assertIsDisplayed().performClick()
    }
}
