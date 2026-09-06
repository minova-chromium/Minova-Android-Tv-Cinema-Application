package com.minova.cinema.ui.browse

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.minova.cinema.data.local.*
import com.minova.cinema.ui.theme.MinovaCinemaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class HomeCustomizationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun openingTabHiddenShelvesAndOrderAreSavedTogether() {
        var saved = HomeLayoutPreferences()
        compose.setContent { MinovaCinemaTheme {
            HomeCustomizationDialog(
                listOf("recent" to "Recently Added", "new" to "New Releases", "action" to "Action"),
                saved, { saved = it }, {},
            )
        } }
        compose.onNodeWithText("Movies").pressOk()
        compose.onNodeWithText("✓  Action").pressOk()
        compose.onAllNodesWithText("↓ Down")[0].pressOk()
        val capture = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/minova-test-home.png",
        )
        android.os.ParcelFileDescriptor.AutoCloseInputStream(capture).use { it.readBytes() }
        compose.onNodeWithText("Save & done").pressOk()
        assertEquals("Movies", saved.openingTab)
        assertEquals(setOf("action"), saved.hiddenShelves)
        assertEquals(listOf("new", "recent", "action"), saved.shelfOrder)
    }

    private fun SemanticsNodeInteraction.pressOk() {
        performSemanticsAction(SemanticsActions.RequestFocus)
        performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
    }

    @Test fun profilePreferencesDoNotLeakAcrossAccounts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = BrowsePreferences(context, "fixture-profile-a")
        val second = BrowsePreferences(context, "fixture-profile-b")
        first.save(HomeLayoutPreferences())
        second.save(HomeLayoutPreferences())
        try {
            first.save(HomeLayoutPreferences(openingTab = "Series", hiddenShelves = setOf("action")))
            assertEquals("Series", BrowsePreferences(context, "fixture-profile-a").read().openingTab)
            assertEquals(HomeLayoutPreferences(), second.read())
        } finally { first.save(HomeLayoutPreferences()); second.save(HomeLayoutPreferences()) }
    }
}
