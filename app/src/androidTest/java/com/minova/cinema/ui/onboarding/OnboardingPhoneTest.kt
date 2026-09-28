package com.minova.cinema.ui.onboarding

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minova.cinema.ui.theme.MinovaCinemaTheme
import com.minova.cinema.presentation.PlexSignInUiState
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingPhoneTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun firstLaunchWaitsForTheUserBeforeFocusingAField() {
        val uiMode = compose.activity.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        assumeFalse(uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION)

        compose.setContent {
            MinovaCinemaTheme {
                OnboardingScreen(
                    connecting = false,
                    error = null,
                    plexSignIn = PlexSignInUiState.Idle,
                    onStartPlexSignIn = {},
                    onCancelPlexSignIn = {},
                    onSelectPlexServer = {},
                    onConnect = { _, _ -> },
                )
            }
        }

        compose.onNodeWithTag("plex-sign-in-button").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithTag("manual-setup-toggle").performClick()
        val fields = compose.onAllNodes(hasSetTextAction())
        fields.assertCountEquals(2)
        fields[0].assertIsNotFocused()
        fields[1].assertIsNotFocused()
    }
}
