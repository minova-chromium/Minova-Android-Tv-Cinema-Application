package com.minova.cinema.ui.browse

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.tv.material3.Text
import com.minova.cinema.domain.CinemaCatalog
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaCollection
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.ui.settings.SettingsSecondaryButton
import com.minova.cinema.ui.theme.MinovaCinemaTheme
import org.junit.Rule
import org.junit.Test

class CollectionsNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test fun collectionPosterUsesAssignedImageInPortraitWithoutCropping() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val poster = java.io.File(context.cacheDir, "collection-poster-fixture.png")
        val bitmap = android.graphics.Bitmap.createBitmap(200, 300, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA)
        poster.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            showLibrary(posterUrl = poster.toURI().toString())
            enterCollections()
            val node = compose.onNodeWithContentDescription("Adventure collection poster", useUnmergedTree = true)
            compose.waitUntil(5_000) {
                runCatching {
                    val pixels = node.captureToImage().toPixelMap()
                    val middle = pixels[pixels.width / 2, pixels.height / 2]
                    middle.red > 0.95f && middle.blue > 0.95f && middle.green < 0.05f
                }.getOrDefault(false)
            }
            val bounds = node.fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertEquals(2f / 3f, bounds.width / bounds.height, 0.01f)
            screenshot("collections-assigned-posters")
        } finally { poster.delete() }
    }

    @Test fun serverMembershipFailureCanRetryWithoutLosingCollectionNavigation() {
        var attempts = 0
        showLibrary(loader = { if (attempts++ == 0) error("fixture error") else emptyList() })
        enterCollections()
        device.pressDPadDown()
        compose.onNodeWithTag("collection-Adventure").assertIsFocused()
        device.pressDPadCenter()
        compose.onNodeWithText("Could not load collection titles.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performSemanticsAction(SemanticsActions.RequestFocus)
        device.pressDPadCenter()
        compose.onNodeWithText("This collection has no titles.").assertIsDisplayed()
        device.pressBack()
        compose.onNodeWithTag("collection-Adventure").assertIsFocused()
    }

    @Test fun remoteOpensCollectionAndReturnsToHeader() {
        showLibrary()
        enterCollections()
        device.pressDPadDown()
        compose.onNodeWithTag("collection-Adventure").assertIsFocused()
        device.pressDPadCenter()
        compose.onNodeWithTag("collections-back").assertIsFocused()
        compose.onNodeWithText("24 titles · Release order").assertIsDisplayed()
        device.pressDPadDown()
        compose.onNodeWithTag("collection-member-title-0").assertIsFocused()
        device.pressDPadRight()
        compose.onNodeWithTag("collection-member-title-1").assertIsFocused()
        device.pressDPadUp()
        compose.onNodeWithTag("collections-back").assertIsFocused()
        device.pressDPadUp()
        compose.onNodeWithTag("header-tab-Collections").assertIsFocused()
        device.pressDPadDown()
        compose.onNodeWithTag("collections-back").assertIsFocused()
        device.pressBack()
        compose.onNodeWithTag("collection-Adventure").assertIsFocused()
        device.pressDPadUp()
        compose.onNodeWithTag("header-tab-Collections").assertIsFocused()
        screenshot("collections-overview")
    }

    @Test fun detailReturnAndTabSwitchRestoreDeepCollectionTitle() {
        showLibrary()
        enterCollections()
        device.pressDPadDown()
        compose.onNodeWithTag("collection-Adventure").assertIsFocused()
        device.pressDPadCenter()
        compose.onNodeWithTag("collections-back").assertIsFocused()
        device.pressDPadDown()
        compose.onNodeWithTag("collection-member-title-0").assertIsFocused()
        repeat(3) { row ->
            device.pressDPadDown()
            compose.waitForIdle()
            try { compose.onNodeWithTag("collection-member-title-${(row + 1) * 5}").assertIsFocused() }
            catch (error: Throwable) { compose.onRoot().printToLog("CollectionFocus"); screenshot("collections-focus-failure"); throw error }
        }
        device.pressDPadRight()
        compose.onNodeWithTag("collection-member-title-16").assertIsFocused()
        device.pressDPadCenter()
        compose.onNodeWithText("Return from details").performSemanticsAction(SemanticsActions.RequestFocus)
        device.pressDPadCenter()
        compose.onNodeWithTag("collection-member-title-16").assertIsFocused()
        compose.onNodeWithTag("header-tab-Movies").performClick()
        compose.onNodeWithTag("header-tab-Collections").performClick()
        compose.onNodeWithTag("collection-member-title-16").assertIsFocused()
        screenshot("collections-titles")
    }

    @Test fun backReturnsToScrolledCollectionCard() {
        showLibrary()
        enterCollections()
        device.pressDPadDown()
        repeat(4) { device.pressDPadDown(); compose.waitForIdle() }
        compose.onNodeWithTag("collection-Collection 19").assertIsFocused()
        device.pressDPadCenter()
        compose.onNodeWithTag("collections-back").assertIsFocused()
        device.pressDPadCenter()
        compose.onNodeWithTag("collection-Collection 19").assertIsFocused()
    }

    @Test fun emptyCollectionsKeepNavigationUsable() {
        showLibrary(empty = true)
        enterCollections()
        device.pressDPadDown()
        compose.onNodeWithText("No collections were found. Add titles to a collection in your library, then refresh.").assertIsDisplayed()
        device.pressDPadRight()
        compose.onNodeWithTag("header-tab-Watchlist").assertIsFocused()
        device.pressDPadCenter()
        compose.onNodeWithTag("collections-grid").assertDoesNotExist()
    }

    @Test fun headerRemainsReachableWithLargeText() {
        showLibrary(largeText = true)
        compose.onNodeWithTag("header-tab-Home").performSemanticsAction(SemanticsActions.RequestFocus)
        repeat(3) { device.pressDPadRight() }
        compose.onNodeWithTag("header-tab-Collections").assertIsFocused().assertIsDisplayed()
        device.pressDPadCenter()
        device.pressDPadRight()
        compose.onNodeWithTag("header-tab-Watchlist").assertIsFocused().assertIsDisplayed()
        device.pressDPadRight()
        compose.onNodeWithTag("header-action-Search").assertIsFocused().assertIsDisplayed()
        device.pressDPadRight()
        compose.onNodeWithTag("header-action-Settings").assertIsFocused().assertIsDisplayed()
        screenshot("collections-large-text")
    }

    private fun enterCollections() {
        compose.onNodeWithTag("header-tab-Collections").performSemanticsAction(SemanticsActions.RequestFocus)
        device.pressDPadCenter()
        compose.onNodeWithTag("collections-grid").assertIsDisplayed()
    }

    private fun showLibrary(empty: Boolean = false, largeText: Boolean = false, posterUrl: String? = null,
        loader: (suspend (String) -> List<MediaContent>)? = null) {
        val titles = List(24) { index ->
            MediaContent(ratingKey = "title-$index", title = "Title $index", secondaryTitle = null,
                summary = null, tagline = null, year = 2000 + index, durationMs = 7_200_000,
                viewOffsetMs = 0, posterUrl = null, backdropUrl = null, contentRating = null,
                kind = if (index == 1) MediaKind.Show else MediaKind.Movie,
                collections = if (empty) emptyList() else listOf("Adventure", "Collection ${index.toString().padStart(2, '0')}"))
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 1.2f else 1f)) {
                MinovaCinemaTheme {
                    var details by remember { mutableStateOf(false) }
                    val holder = rememberSaveableStateHolder()
                    if (details) SettingsSecondaryButton(onClick = { details = false }) { Text("Return from details") }
                    else holder.SaveableStateProvider("browse") {
                        BrowseScreen(CinemaCatalog("Test library", titles.filter { it.kind == MediaKind.Movie },
                            titles.filter { it.kind == MediaKind.Show }, emptyList(),
                            collections = titles.flatMap { it.collections }.distinct().sorted().map { name ->
                                MediaCollection(name, name, posterUrl, "Test library", titles.count { name in it.collections })
                            }),
                            onOpen = { details = true }, onPlay = {}, onToggleMyList = {}, onSettings = {}, onWatchlistRefresh = {},
                            loadCollectionMembers = loader)
                    }
                }
            }
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /sdcard/Download/minova-$name.png")
        ).use { it.readBytes() }
    }
}
