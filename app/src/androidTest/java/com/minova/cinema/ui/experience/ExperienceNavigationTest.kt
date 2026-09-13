package com.minova.cinema.ui.experience

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.minova.cinema.data.local.*
import com.minova.cinema.domain.*
import com.minova.cinema.ui.theme.MinovaCinemaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExperienceNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val device get() = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureExperience") != "true") return
        compose.waitForIdle()
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/minova-test-experience-$name.png",
        )
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }
    private fun movie(id: String) = MediaContent(id, title = "Feature $id", secondaryTitle = null, summary = null,
        tagline = null, year = 2025, durationMs = 90 * 60_000L, viewOffsetMs = 0, posterUrl = null,
        backdropUrl = null, contentRating = null, kind = MediaKind.Movie, genres = listOf("Action"), collections = listOf("Saga"))

    @Test fun filtersCanBeAdjustedWithRemoteAndApplyRemainsReachable() {
        var applied: LibraryFilter? = null
        compose.setContent { MinovaCinemaTheme { LibraryFilterDialog(listOf(movie("1")), LibraryFilter()) { applied = it } } }
        compose.onNodeWithTag("cinema-panel-done").assertIsFocused()
        device.pressDPadDown()
        device.pressDPadRight()
        device.pressDPadCenter()
        compose.waitForIdle()
        compose.onNodeWithText("✓  Unwatched").assertExists()
        capture("filters")
        device.pressDPadUp()
        compose.onNodeWithTag("cinema-panel-done").assertIsFocused()
        device.pressDPadCenter()
        compose.runOnIdle { assertEquals(WatchFilter.Unwatched, applied?.watch) }
    }

    @Test fun recoveryWindowConsumesOrphanHoldReleaseAndAcceptsFreshOk() {
        var returned = 0
        compose.setContent { MinovaCinemaTheme {
            PlaybackRecoveryDialog("Connection lost", true, {}, {}, { returned++ })
        } }
        compose.onNodeWithTag("cinema-panel-done").assertIsFocused()
        capture("recovery")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis() - 1200
        automation.injectInputEvent(KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER, 2), true)
        automation.injectInputEvent(KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
            KeyEvent.KEYCODE_DPAD_CENTER, 0), true)
        compose.runOnIdle { assertEquals(0, returned) }
        device.pressDPadCenter()
        compose.runOnIdle { assertEquals(1, returned) }
    }

    @Test fun finishedScreenNeverAutoplaysAndHomeHandlesRemoteOk() {
        var opened = 0
        var homes = 0
        val catalog = CinemaCatalog("Fixture", listOf(movie("1"), movie("2")), emptyList(), emptyList())
        compose.setContent { MinovaCinemaTheme {
            MovieFinishedDialog(movie("1"), catalog, { _, done -> done(true) }, { opened++ }, { homes++ })
        } }
        compose.mainClock.advanceTimeBy(20_000)
        compose.runOnIdle { assertEquals(0, opened); assertEquals(0, homes) }
        capture("finished")
        compose.onNodeWithTag("cinema-panel-done").assertIsFocused()
        device.pressDPadCenter()
        compose.runOnIdle { assertEquals(1, homes); assertEquals(0, opened) }
    }

    @Test fun largeTextSettingsScrollToStorageAndBackClosesDialog() {
        var closed = 0
        var settings by mutableStateOf(ExperienceSettings(textScale = 1.2f, strongFocus = true))
        compose.setContent {
            MinovaCinemaTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalExperienceSettings provides settings,
                    LocalDensity provides Density(density.density, density.fontScale * 1.2f)) {
                    ExperienceSettingsDialog(settings, { settings = it }, {}, {}, { closed++ })
                }
            }
        }
        compose.onNodeWithTag("cinema-panel-list").performScrollToNode(hasText("Clear artwork cache only"))
        compose.onNodeWithText("Clear artwork cache only").assertIsDisplayed()
        capture("large-text")
        device.pressBack()
        compose.runOnIdle { assertEquals(1, closed) }
    }

    @Test fun clearingArtworkPreservesPlexConnectionAndPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".debug")) // Never seed credentials in the production copy.
        val preferences = PlexPreferences(context)
        val previous = preferences.readConnection()
        val fixture = com.minova.cinema.data.remote.PlexConnection("https://plex.example.invalid/", "fixture-not-a-real-token")
        preferences.saveConnection(fixture)
        val experience = ExperiencePreferences(context, "cache-clear-fixture")
        experience.save(ExperienceSettings(audioLanguage = "nl", textScale = 1.1f))
        try {
            compose.setContent { MinovaCinemaTheme { ExperienceSettingsDialog(experience.read(), {}, {}, {}, {}) } }
            compose.onNodeWithTag("cinema-panel-list").performScrollToNode(hasText("Clear artwork cache only"))
            compose.onNodeWithText("Clear artwork cache only")
                .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.onNodeWithText("Clear artwork cache only").assertIsFocused()
            device.pressDPadCenter()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Artwork cleared.", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithText("Couldn't clear artwork", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Artwork cleared.", substring = true).assertIsDisplayed()
            assertEquals(fixture, preferences.readConnection())
            assertEquals("nl", experience.read().audioLanguage)
            assertEquals(1.1f, experience.read().textScale)
        } finally {
            if (previous != null) preferences.saveConnection(previous) else preferences.clearConnection()
        }
    }

    @Test fun discoverySwitchesSectionsWithRealRemoteKeysAndMultipleTitles() {
        val catalog = CinemaCatalog("Fixture", (1..20).map { movie("$it") }, emptyList(), emptyList())
        compose.setContent { MinovaCinemaTheme { DiscoveryDialog(catalog, {}, { _, _ -> }, {}) } }
        device.pressDPadDown()
        device.pressDPadRight()
        device.pressDPadCenter()
        compose.onNodeWithText("✓  Collections").assertExists()
        device.pressDPadDown()
        compose.waitForIdle()
        device.pressDPadRight()
        compose.waitForIdle()
        device.pressDPadCenter()
        compose.waitForIdle()
        compose.onNodeWithText("Saga · release order · 20 titles").assertExists()
        capture("collections")
        repeat(4) { device.pressDPadDown() }
        compose.waitForIdle()
        repeat(4) { device.pressDPadUp() }
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun preferencesAndCustomPresetAreIsolatedByProfile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val unique = java.util.UUID.randomUUID().toString()
        val first = ExperiencePreferences(context, "$unique:first")
        val second = ExperiencePreferences(context, "$unique:second")
        val settings = ExperienceSettings(audioLanguage = "nl", reducedMotion = true)
        first.save(settings)
        first.saveTracks("movie", TitleTrackPreference(audioId = 4, subtitlesOff = true))
        first.saveCustomPreset(CustomCinemaPreset(true, false, true, true, 10, 25))
        assertEquals(settings, ExperiencePreferences(context, "$unique:first").read())
        assertEquals(ExperienceSettings(), second.read())
        assertNull(second.tracks("movie"))
        assertNull(second.customPreset())
        assertEquals(25, first.customPreset()?.restore)
        first.resetTracks("movie")
        assertNull(first.tracks("movie"))
        assertEquals(settings, first.read())
    }
}
