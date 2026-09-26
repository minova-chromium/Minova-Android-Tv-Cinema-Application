package com.minova.cinema.showcase

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.io.File
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.minova.cinema.PromoCaptureContract
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deliberately paced UI Automator sequence used by scripts/record_trailer.ps1.
 * It records the real focus, transition and pre-roll code without depending on
 * a private Plex server or allowing an unpredictable trailer to begin.
 */
@RunWith(AndroidJUnit4::class)
class TrailerShowcaseAutomation {
    @Test
    fun recordFreshTrailerShowcase() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val launchContext = ApplicationProvider.getApplicationContext<Context>()
        val telemetry = targetContext.getSharedPreferences(
            TrailerShowcaseActivity.TELEMETRY_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        telemetry.edit().clear().commit()

        val waitForCaptureSignal =
            InstrumentationRegistry.getArguments().getString("waitForCaptureSignal") == "true"
        val launchIntent = Intent(launchContext, TrailerShowcaseActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(PromoCaptureContract.EXTRA_PROMO_RECORDING, true)
            .putExtra(PromoCaptureContract.EXTRA_DISABLE_PLEX_TRAILERS, true)
            .putExtra(
                TrailerShowcaseActivity.EXTRA_WAIT_FOR_CAPTURE_SIGNAL,
                waitForCaptureSignal,
            )

        // The PowerShell runner starts the Activity just before screenrecord so
        // the captured file begins on the Minova intro rather than the Android
        // launcher. Gradle/Android Studio runs still work by launching here.
        val activityAlreadyVisible = device.hasObject(By.pkg(targetContext.packageName))
        val scenario = if (activityAlreadyVisible) null else {
            ActivityScenario.launch<TrailerShowcaseActivity>(launchIntent)
        }
        try {
            if (waitForCaptureSignal) {
                val readyFile = File(targetContext.cacheDir, TrailerShowcaseActivity.CAPTURE_READY_FILE)
                val goFile = File(targetContext.cacheDir, TrailerShowcaseActivity.CAPTURE_GO_FILE)
                readyFile.delete()
                goFile.delete()
                assertTrue("Could not arm capture", readyFile.createNewFile())
                val deadline = SystemClock.elapsedRealtime() + 20_000L
                while (!goFile.exists() && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(40)
                }
                assertTrue("Recorder did not release the showcase", goFile.exists())
                scenario?.onActivity { it.releaseCapture() }
                readyFile.delete()
                goFile.delete()
            }

            // Scene 1 — keep the cold-launch logo animation on screen, then
            // use OK to advance at exactly the requested trailer tempo.
            device.waitForIdle()
            // The external runner releases the Activity from its armed black
            // frame only after screenrecord is confirmed alive.
            SystemClock.sleep(300)
            SystemClock.sleep(2_500)
            if (!device.hasObject(By.text("Home"))) device.pressDPadCenter()
            assertTrue("Browse screen did not appear", device.wait(Until.hasObject(By.text("Home")), 5_000))

            // Scene 2 — two complete shelves, not one synthetic card. Each
            // beat allows focus scale and backdrop crossfade to finish.
            SystemClock.sleep(850)
            repeat(3) {
                device.pressDPadRight()
                SystemClock.sleep(600)
            }
            device.pressDPadDown()
            SystemClock.sleep(700)
            repeat(2) {
                device.pressDPadLeft()
                SystemClock.sleep(600)
            }
            device.pressDPadCenter()

            // Scene 3 — metadata and the capability badges are visible long
            // enough to be read before Play receives the next OK command.
            assertTrue("Detail view did not appear", device.wait(Until.hasObject(By.text("AFR MATCH")), 4_000))
            SystemClock.sleep(2_000)
            device.pressDPadCenter()

            // Scene 4 — this is the production pre-roll component. The extra
            // line is asserted here and is impossible to enable in release.
            assertTrue(
                "Minova pre-roll did not appear",
                device.wait(Until.hasObject(By.text("BROUGHT TO YOU BY MINOVA")), 3_000),
            )
            assertTrue(
                "Capture-only disclaimer is missing",
                device.wait(Until.hasObject(By.text("Can be disabled in settings")), 2_000),
            )
            assertFalse(
                "A Plex trailer started during the capture",
                device.hasObject(By.textContains("Trailer ·")),
            )

            // Scene 5 — playback begins after the deterministic four-second
            // card. The debug host sends the same boolean event consumed by
            // the Cinema Mode light hooks and records proof for this process.
            assertTrue(
                "Playback scene did not begin",
                device.wait(Until.hasObject(By.text("PLAYBACK STARTED")), 7_000),
            )
            assertTrue(
                "TP-Link Tapo dim listener did not fire",
                waitForTelemetry(telemetry, TrailerShowcaseActivity.KEY_TAPO_DIM_EVENT_FIRED),
            )
            SystemClock.sleep(2_000)

            // Scene 6 — a remote Back gesture returns to a clean branded end
            // frame, giving the recording a deterministic edit point.
            device.pressBack()
            assertTrue(
                "Showcase did not finish cleanly",
                device.wait(Until.hasObject(By.text("SHOWCASE COMPLETE")), 4_000),
            )
            SystemClock.sleep(2_000)
        } finally {
            // During capture the Activity must remain on the branded end card
            // until screenrecord receives SIGINT. Android Studio test runs can
            // still close it normally.
            if (!waitForCaptureSignal) scenario?.close()
        }
    }

    private fun waitForTelemetry(
        preferences: android.content.SharedPreferences,
        key: String,
    ): Boolean {
        repeat(50) {
            if (preferences.getBoolean(key, false)) return true
            Thread.sleep(100)
        }
        return false
    }
}
