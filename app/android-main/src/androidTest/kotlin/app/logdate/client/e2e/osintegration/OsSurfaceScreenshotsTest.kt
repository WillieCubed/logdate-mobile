package app.logdate.client.e2e.osintegration

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import app.logdate.client.datastore.featureflags.FeatureFlag
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real system UI captures for the Android OS integration surfaces.
 *
 * Each test drives Android into one state and saves a screenshot through [OsSurfaceCapture]. These
 * are reviewed evidence, not pixel-compared goldens: system UI differs slightly between emulator
 * images. Run with `./run screenshots:os`.
 */
@RunWith(AndroidJUnit4::class)
class OsSurfaceScreenshotsTest {
    @Before fun pinStatusBar() {
        OsSurfaceCapture.enterDemoMode()
    }

    @After fun releaseStatusBar() {
        OsSurfaceCapture.stopRecording()
        OsSurfaceCapture.collapseShade()
        OsSurfaceCapture.exitDemoMode()
        OsSurfaceCapture.device.pressHome()
    }

    @Test fun launcherShortcuts() {
        OsSurfaceCapture.openLauncherShortcuts()
        // Launchers list a shortcut by its long label when there is room.
        OsSurfaceCapture.waitForText("Record a voice entry")
        OsSurfaceCapture.capture(surface = "launcher-shortcuts", state = "long-press")
        OsSurfaceCapture.dumpHierarchy("launcher-long-press")
    }

    /**
     * Assistant actions, driven the way an assistant would through `cmd app_function`. Android 16
     * added the command; the output is saved as text evidence next to the screenshots.
     */
    @Test fun assistantActions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return
        OsSurfaceCapture.prepareOnboardedApp()
        OsSurfaceCapture.setSystemSearchVisibility(false)
        OsSurfaceCapture.enableFlags(FeatureFlag.APP_FUNCTIONS)
        OsSurfaceCapture.awaitComponentEnabled("app.logdate.client.appfunctions.LogDateAppFunctionService")
        Thread.sleep(5_000L)

        val pkg = OsSurfaceCapture.packageName
        val functions = "app.logdate.client.appfunctions.LogDateAppFunctions"
        fun execute(
            function: String,
            parameters: String,
        ) = OsSurfaceCapture.shellScript(
            "cmd app_function execute-app-function --package $pkg --function '$functions#$function' --parameters '$parameters'",
        )

        val listing =
            OsSurfaceCapture
                .shellScript("cmd app_function list-app-functions")
                .lines()
                .filter { it.contains(functions) || it.contains(pkg) }
                .joinToString("\n")
        val created = execute("createTextEntry", """{"text":["Walked the long way home along the river."]}""")
        val refused = execute("searchEntries", """{"query":["river"]}""")
        OsSurfaceCapture.setSystemSearchVisibility(true)
        Thread.sleep(1_000L)
        val found = execute("searchEntries", """{"query":["river"]}""")
        OsSurfaceCapture.writeEvidence(
            surface = "app-functions",
            state = "assistant-actions",
            text =
                buildString {
                    appendLine("# list-app-functions (LogDate)")
                    appendLine(listing)
                    appendLine()
                    appendLine("# createTextEntry")
                    appendLine(created.trim())
                    appendLine()
                    appendLine("# searchEntries with system search visibility off")
                    appendLine(refused.trim())
                    appendLine()
                    appendLine("# searchEntries with system search visibility on")
                    appendLine(found.trim())
                },
        )

        OsSurfaceCapture.shell("am start -n $pkg/app.logdate.client.MainActivity -f 0x10000000")
        OsSurfaceCapture.device.wait(Until.findObject(By.textContains("Walked the long way home")), 10_000L)
            ?: OsSurfaceCapture.waitForText("Walked the long way home along the river.")
        OsSurfaceCapture.capture(surface = "app-functions", state = "entry-from-assistant")
    }

    @Test fun captureWidgetInPicker() {
        OsSurfaceCapture.prepareOnboardedApp()
        OsSurfaceCapture.enableFlags(FeatureFlag.LOCK_SCREEN_CAPTURE_WIDGET)
        OsSurfaceCapture.awaitComponentEnabled("app.logdate.client.feature.widgets.CaptureWidgetReceiver")
        OsSurfaceCapture.openLauncherShortcuts()
        OsSurfaceCapture.waitForText("Widgets").click()
        OsSurfaceCapture.waitForText("Memories")
        OsSurfaceCapture.scrollUntilText("Capture")
        Thread.sleep(2_000L)
        OsSurfaceCapture.capture(surface = "widgets", state = "capture-widget-picker")
    }

    @Test fun pinJournal() {
        OsSurfaceCapture.prepareOnboardedApp()
        OsSurfaceCapture.openJournal(OsSurfaceCapture.createJournal("Summer in Lisbon"))
        val options =
            OsSurfaceCapture.device.wait(Until.findObject(By.desc("Journal options")), 5_000L)
                ?: OsSurfaceCapture.waitForText("Journal settings")
        options.click()
        OsSurfaceCapture.waitForText("Add to home screen")
        OsSurfaceCapture.capture(surface = "launcher-shortcuts", state = "journal-menu")
        OsSurfaceCapture.waitForText("Add to home screen").click()
        Thread.sleep(2_500L)
        OsSurfaceCapture.capture(surface = "launcher-shortcuts", state = "pin-journal")
    }

    /** Android 17 lets people float any app from its launcher menu; LogDate must work at that size. */
    @Test fun appBubble() {
        if (Build.VERSION.SDK_INT < 37) return
        OsSurfaceCapture.prepareOnboardedApp()
        OsSurfaceCapture.openLauncherShortcuts()
        OsSurfaceCapture.waitForText("Bubble").click()
        OsSurfaceCapture.device.wait(Until.findObject(By.desc("Search")), 10_000L)
        Thread.sleep(3_000L)
        OsSurfaceCapture.device.wait(Until.findObject(By.text("Got it")), 2_000L)?.click()
        OsSurfaceCapture.capture(surface = "bubbles", state = "app-bubble")
    }

    @Test fun quickSettingsTilesAndLiveUpdate() {
        OsSurfaceCapture.prepareOnboardedApp()
        OsSurfaceCapture.enableFlags(FeatureFlag.QUICK_SETTINGS_TILES, FeatureFlag.LIVE_UPDATES)
        OsSurfaceCapture.addQuickSettingsTile("app.logdate.client.tiles.NewEntryTileService")
        OsSurfaceCapture.addQuickSettingsTile("app.logdate.client.tiles.RecordVoiceTileService")

        OsSurfaceCapture.openQuickSettings()
        val recordVoiceTile = OsSurfaceCapture.waitForText("Record voice")
        OsSurfaceCapture.capture(surface = "quick-settings-tiles", state = "idle")

        recordVoiceTile.click()
        OsSurfaceCapture.waitForText("Finish")
        OsSurfaceCapture.capture(surface = "quick-settings-tiles", state = "recording-editor")
        OsSurfaceCapture.writeEvidence(
            surface = "live-updates",
            state = "recording-promotion",
            text = OsSurfaceCapture.recordingPromotionReport(),
        )

        // Android shows a Live Update's chip only while its app is not on screen.
        OsSurfaceCapture.device.pressHome()
        Thread.sleep(3_000L)
        OsSurfaceCapture.capture(surface = "live-updates", state = "recording-status-chip")

        OsSurfaceCapture.openNotifications()
        OsSurfaceCapture.capture(surface = "live-updates", state = "recording-notification")
        OsSurfaceCapture.collapseShade()

        OsSurfaceCapture.openQuickSettings()
        OsSurfaceCapture.capture(surface = "quick-settings-tiles", state = "recording")
        OsSurfaceCapture.collapseShade()
    }

    @Test fun recordingBubble() {
        OsSurfaceCapture.prepareOnboardedApp()
        OsSurfaceCapture.enableFlags(FeatureFlag.RECORDING_BUBBLE, FeatureFlag.LIVE_UPDATES)
        OsSurfaceCapture.allowBubbles()

        OsSurfaceCapture.openCapture(mode = "voice")
        Thread.sleep(6_000L)

        OsSurfaceCapture.openNotifications()
        OsSurfaceCapture.waitForText("Float")
        OsSurfaceCapture.capture(surface = "bubbles", state = "float-action")
        OsSurfaceCapture.device.findObject(By.text("Float")).click()
        OsSurfaceCapture.collapseShade()

        // On phones the bubble opens by itself. On tablets it lands in the Bubble Bar; open it there.
        if (OsSurfaceCapture.device.wait(Until.findObject(By.desc("Open LogDate")), 4_000L) == null) {
            OsSurfaceCapture.waitForText("Voice recorder").click()
        }
        OsSurfaceCapture.waitForText("Open LogDate")
        // The first bubble shows Android's one-time "Control bubbles anytime" tip; dismiss it.
        OsSurfaceCapture.device.wait(Until.findObject(By.text("Got it")), 3_000L)?.click()
        Thread.sleep(2_000L)
        OsSurfaceCapture.capture(surface = "bubbles", state = "recorder-expanded")

        OsSurfaceCapture.device.pressHome()
        Thread.sleep(1_500L)
        OsSurfaceCapture.capture(surface = "bubbles", state = "recorder-collapsed")
    }
}
