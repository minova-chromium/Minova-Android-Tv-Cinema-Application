package com.minova.cinema.ui.onboarding

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minova.cinema.presentation.PlexSignInUiState
import com.minova.cinema.ui.theme.MinovaCinemaTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingTvTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun firstLaunchFocusesPlexSignIn() {
        assumeTv()
        show(PlexSignInUiState.Idle)

        compose.onNodeWithTag("plex-sign-in-button").assertIsDisplayed().assertIsFocused()
    }

    @Test
    fun waitingStateShowsScannablePhoneAuthorization() {
        assumeTv()
        show(
            PlexSignInUiState.Waiting(
                code = "A1B2",
                authorizationUrl = "https://plex.tv/link/?pin=A1B2",
            ),
        )

        compose.onNode(hasContentDescription("Plex authorization QR code"))
            .assertIsDisplayed()
        compose.onNodeWithText("Scan with your phone").assertIsDisplayed()
        compose.onNodeWithText("plex.tv/link   •   code A1B2").assertIsDisplayed()
    }

    private fun show(state: PlexSignInUiState) {
        compose.setContent {
            MinovaCinemaTheme {
                OnboardingScreen(
                    connecting = false,
                    error = null,
                    plexSignIn = state,
                    onStartPlexSignIn = {},
                    onCancelPlexSignIn = {},
                    onSelectPlexServer = {},
                    onConnect = { _, _ -> },
                )
            }
        }
    }

    private fun assumeTv() {
        val uiMode = compose.activity.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        assumeTrue(uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION)
    }
}
