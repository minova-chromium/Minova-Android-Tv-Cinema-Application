package com.minova.cinema.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsActions
import com.minova.cinema.home.LightingUiState
import com.minova.cinema.presentation.NetworkAssistantUiState
import com.minova.cinema.presentation.PlexProfilesUiState
import com.minova.cinema.tapo.TapoLightsUiState
import com.minova.cinema.ui.theme.MinovaCinemaTheme
import org.junit.Rule
import org.junit.Test

/** Guards the TV-specific section rail and timer-dialog focus path. */
class SettingsScreenNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sectionRailMovesToPlaybackWithDpad() {
        showSettings()

        compose.onNodeWithTag("settings-section-Plex")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput {
                pressKey(Key.DirectionDown)
                pressKey(Key.DirectionDown)
            }

        waitUntilFocused("settings-section-Playback")
        compose.onNodeWithText("Autoplay next episode").assertIsDisplayed()
        compose.onAllNodesWithTag("settings-refresh-library").assertCountEquals(0)
    }

    @Test
    fun profilesRightMovesIntoProfilesInsteadOfPlexServer() {
        showSettings()

        compose.onNodeWithTag("settings-section-Plex")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput {
                pressKey(Key.DirectionDown)
                pressKey(Key.DirectionRight)
            }

        waitUntilFocused("settings-refresh-profiles")
        compose.onNodeWithTag("settings-refresh-profiles")
            .performKeyInput { pressKey(Key.DirectionLeft) }
        waitUntilFocused("settings-section-Profiles")
    }

    @Test
    fun timerDialogAdjustsThenMovesDownToDone() {
        showSettings()
        compose.onNodeWithTag("settings-section-Plex")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput {
                pressKey(Key.DirectionDown)
                pressKey(Key.DirectionDown)
            }
        waitUntilFocused("settings-section-Playback")
        waitUntilDisplayed("timer-setting-Ambient screensaver")

        compose.onNodeWithTag("timer-setting-Ambient screensaver")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        waitUntilDisplayed("timer-adjustment", useUnmergedTree = true)
        compose.onNodeWithTag("timer-adjustment", useUnmergedTree = true)
            .assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionDown)
            }

        compose.onNodeWithTag("timer-done", useUnmergedTree = true)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onAllNodesWithTag("timer-done", useUnmergedTree = true).assertCountEquals(0)
    }

    private fun showSettings() {
        compose.setContent {
            MinovaCinemaTheme {
                SettingsScreen(
                    serverUrl = "http://192.168.1.10:32400/",
                    autoplayNextEpisode = true,
                    inactivityCheckEnabled = true,
                    inactivityTimeoutMs = 3L * 60L * 60_000L,
                    screensaverTimeoutMs = 5L * 60_000L,
                    cinemaModeEnabled = true,
                    cinemaTrailersEnabled = true,
                    cinemaBumperConfigured = false,
                    lightingState = LightingUiState(),
                    tapoLightsState = TapoLightsUiState(),
                    profilesState = PlexProfilesUiState.Ready(emptyList()),
                    networkAssistantState = NetworkAssistantUiState.Idle,
                    onRefresh = {},
                    onChangeServer = {},
                    onAutoplayNextEpisodeChanged = {},
                    onInactivityCheckChanged = {},
                    onInactivityTimeoutChanged = {},
                    onScreensaverTimeoutChanged = {},
                    onCinemaModeChanged = {},
                    onCinemaTrailersChanged = {},
                    onChooseCinemaBumper = {},
                    onClearCinemaBumper = {},
                    onRequestHomePermission = {},
                    onRefreshLights = {},
                    onLightAssignmentChanged = { _, _ -> },
                    onSaveTapoCredentials = { _, _ -> },
                    onClearTapoCredentials = {},
                    onDiscoverTapoLights = {},
                    onTapoLightAssignmentChanged = { _, _ -> },
                    onRefreshProfiles = {},
                    onSwitchProfile = { _, _ -> },
                    onRunNetworkTest = {},
                    onRequestTvHomeChannels = {},
                )
            }
        }
    }

    private fun waitUntilFocused(tag: String) {
        compose.waitUntil(timeoutMillis = 3_000) {
            runCatching { compose.onNodeWithTag(tag).assertIsFocused() }.isSuccess
        }
    }

    private fun waitUntilDisplayed(tag: String, useUnmergedTree: Boolean = false) {
        compose.waitUntil(timeoutMillis = 3_000) {
            runCatching {
                compose.onNodeWithTag(tag, useUnmergedTree = useUnmergedTree).assertIsDisplayed()
            }.isSuccess
        }
    }
}
