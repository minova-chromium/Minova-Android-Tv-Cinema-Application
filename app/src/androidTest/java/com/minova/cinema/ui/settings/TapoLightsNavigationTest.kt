package com.minova.cinema.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.minova.cinema.tapo.*
import com.minova.cinema.ui.theme.MinovaCinemaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TapoLightsNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun testActionAndLastLightStayReachableWithLongStatusMessages() {
        var requested = false
        compose.setContent { MinovaCinemaTheme {
            CinemaLightsSettingsScreen(
                state = TapoLightsUiState(
                    hasCredentials = true,
                    localAccessBlockedCount = 2,
                    message = "Some lights responded but others need Third-Party Compatibility enabled. " +
                        "This long diagnostic must scroll rather than covering the device list.",
                    lights = (1..12).map { TapoLight("192.0.2.$it", "Cinema lamp $it", "L630", true, 73, true) },
                ),
                onDismiss = {}, onDiscover = {}, onChangeLogin = {}, onClearCredentials = {},
                onAssignmentChanged = { _, _ -> }, onTestLights = { requested = true },
            )
        } }
        compose.onNodeWithText("Scan for lights").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Test dim and restore").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        assertTrue(requested)
        repeat(12) {
            compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
            compose.waitForIdle()
        }
        compose.onNodeWithText("Cinema lamp 12").assertIsDisplayed().assertIsFocused()
    }
}
