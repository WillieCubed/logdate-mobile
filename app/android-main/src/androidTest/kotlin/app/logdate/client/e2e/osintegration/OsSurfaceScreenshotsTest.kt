package app.logdate.client.e2e.osintegration

import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.feature.widgets.CaptureWidget
import app.logdate.client.feature.widgets.FixedMemoryWidget
import app.logdate.client.feature.widgets.NewEntryWidget
import app.logdate.client.feature.widgets.OnThisDayWidget
import app.logdate.client.feature.widgets.OnThisDayWidgetState
import app.logdate.client.sync.SyncForegroundNotification
import app.logdate.feature.core.export.ExportNotificationHelper
import app.logdate.feature.core.restore.RestoreNotificationHelper
import app.logdate.feature.core.restore.RestoreStage
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern
import kotlin.uuid.Uuid
import app.logdate.client.sync.R as SyncR

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

    /** Android's keyboard shortcut helper (Meta+/) listing LogDate's shortcuts. */
    @Test fun keyboardShortcutHelper() {
        OsSurfaceCapture.prepareOnboardedApp()
        OsSurfaceCapture.openMainActivity()
        OsSurfaceCapture.withoutOnScreenKeyboards {
            HardwareKeyboard().use { keyboard ->
                keyboard.press(HardwareKeyboard.KEY_LEFTMETA, HardwareKeyboard.KEY_SLASH)
                // The helper opens on the focused app's shortcuts, grouped under its name.
                OsSurfaceCapture.waitForText("Record a voice entry")
                Thread.sleep(1_000L)
                OsSurfaceCapture.capture(surface = "keyboard-pointer-drag-and-drop", state = "shortcut-helper")
            }
        }
    }

    /**
     * A restore in progress as a Live Update with one bar segment per phase, then the finished and
     * failed states, whose titles Android 17 colors green and red.
     */
    @Test fun transferLiveUpdates() {
        OsSurfaceCapture.prepareOnboardedApp()
        OsSurfaceCapture.enableFlags(FeatureFlag.LIVE_UPDATES)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val restore = RestoreNotificationHelper(context, Uuid.random())
        val export = ExportNotificationHelper(context, Uuid.random())
        OsSurfaceCapture.device.pressHome()
        OsSurfaceCapture.postTransferNotification(restore.createForegroundInfo(RestoreStage.RESTORING_NOTES))
        OsSurfaceCapture.capture(surface = "live-updates", state = "transfer-status-chip")
        OsSurfaceCapture.openNotifications()
        OsSurfaceCapture.waitForText("Restoring data")
        OsSurfaceCapture.capture(surface = "live-updates", state = "transfer-progress")
        OsSurfaceCapture.postTransferNotification(restore.createCompletionInfo())
        OsSurfaceCapture.postTransferNotification(export.createErrorInfo("The folder you chose is no longer available."))
        OsSurfaceCapture.waitForText("Export failed")
        OsSurfaceCapture.capture(surface = "live-updates", state = "transfer-finished")
    }

    /** A backup to LogDate Cloud as a Live Update, with the share of entries uploaded in its chip. */
    @Test fun syncLiveUpdate() {
        OsSurfaceCapture.prepareOnboardedApp()
        OsSurfaceCapture.enableFlags(FeatureFlag.LIVE_UPDATES)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        OsSurfaceCapture.device.pressHome()
        OsSurfaceCapture.postTransferNotification(
            SyncForegroundNotification.info(
                context,
                context.getString(SyncR.string.sync_notification_progress, 128, 340),
                completed = 128,
                total = 340,
            ),
        )
        OsSurfaceCapture.capture(surface = "live-updates", state = "sync-status-chip")
        OsSurfaceCapture.openNotifications()
        OsSurfaceCapture.waitForText("Backing up your journal")
        OsSurfaceCapture.capture(surface = "live-updates", state = "sync-progress")
    }

    /** The splash screen on a fresh start: Android 12's system splash, or the compat splash before it. */
    @Test fun splashScreen() {
        OsSurfaceCapture.prepareOnboardedApp()
        OsSurfaceCapture.captureSplash(surface = "splash", state = "launch", background = SPLASH_BACKGROUND)
    }

    /** A capture reminder answered from the notification shade with Write. */
    @Test fun notificationQuickReply() {
        OsSurfaceCapture.prepareOnboardedApp()
        OsSurfaceCapture.enableFlags(FeatureFlag.NOTIFICATION_QUICK_REPLY)
        OsSurfaceCapture.postEveningCaptureReminder()
        OsSurfaceCapture.openNotifications()
        OsSurfaceCapture.waitForText("Write").click()
        val field = OsSurfaceCapture.device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000L)
        field?.text = "Sunset over the bay, then dinner on the steps."
        Thread.sleep(1_000L)
        OsSurfaceCapture.capture(surface = "notification-quick-reply", state = "reply-field")
        OsSurfaceCapture.device.wait(Until.findObject(By.desc("Send")), 3_000L)?.click()
        OsSurfaceCapture.waitForText("Entry saved")
        Thread.sleep(800L)
        OsSurfaceCapture.capture(surface = "notification-quick-reply", state = "saved")
    }

    /** On a tablet, a long-press on a journal opens its menu, whose "New entry in a new window" opens the editor beside LogDate. */
    @Test fun editorWindowSideBySide() {
        if (OsSurfaceCapture.formFactor != "tablet") return
        OsSurfaceCapture.prepareOnboardedApp()
        OsSurfaceCapture.createJournal("Summer in Lisbon")
        OsSurfaceCapture.openMainActivity()
        OsSurfaceCapture.waitForText("Journals").click()
        OsSurfaceCapture.longPress(OsSurfaceCapture.waitForText("Summer in Lisbon"))
        val newWindow = OsSurfaceCapture.waitForText("New entry in a new window")
        Thread.sleep(500L)
        OsSurfaceCapture.capture(surface = "keyboard-pointer-drag-and-drop", state = "journal-context-menu")
        newWindow.click()
        Thread.sleep(4_000L)
        OsSurfaceCapture.capture(surface = "large-screens-and-desktop-windowing", state = "side-by-side")
    }

    /**
     * LogDate in Android desktop windowing: the main window, and a new entry opened from a journal's
     * menu in a window of its own. Desktop windowing needs developer settings and a restart, which a
     * managed device cannot do, so this runs only when `scripts/desktop-windowing-screenshots.sh`
     * passes `logdate.desktopWindowing=true` from an emulator that has desktop windowing turned on.
     */
    @Test fun desktopWindows() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("logdate.desktopWindowing") == "true")
        OsSurfaceCapture.prepareOnboardedApp()
        OsSurfaceCapture.createJournal("Summer in Lisbon")
        OsSurfaceCapture.shell(
            "am start --windowingMode $WINDOWING_MODE_FREEFORM -n ${OsSurfaceCapture.packageName}/app.logdate.client.MainActivity",
        )
        OsSurfaceCapture.waitForText("Journals").click()
        OsSurfaceCapture.longPress(OsSurfaceCapture.waitForText("Summer in Lisbon"))
        OsSurfaceCapture.waitForText("New entry in a new window").click()
        Thread.sleep(4_000L)
        OsSurfaceCapture.capture(surface = "large-screens-and-desktop-windowing", state = "desktop-windows")
        // The labels Android holds for each window, which the caption bar and Recents may show.
        val labels = OsSurfaceCapture.shell("dumpsys activity activities").lines().filter { "taskDescription: label" in it }
        OsSurfaceCapture.writeEvidence("large-screens-and-desktop-windowing", "desktop-window-labels", labels.joinToString("\n") { it.trim() })
    }

    /**
     * On a tablet, a long-press on an entry in a day opens its menu, whose "Open in new window" opens
     * that entry in the editor beside LogDate.
     */
    @Test fun entryWindowSideBySide() {
        if (OsSurfaceCapture.formFactor != "tablet") return
        val entry = "Walked the long way home along the river. The light on the water was gold."
        OsSurfaceCapture.prepareOnboardedApp()
        OsSurfaceCapture.createTextEntry(entry)
        OsSurfaceCapture.openMainActivity()
        OsSurfaceCapture.waitForText(entry).click()
        // The day opens beside the timeline, so the entry is on screen twice; its menu is on the copy
        // in the day, the rightmost one.
        val deadline = System.currentTimeMillis() + 10_000L
        while (OsSurfaceCapture.device.findObjects(By.text(entry)).size < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(250L)
        }
        // Lets the day finish opening before the long-press.
        Thread.sleep(1_500L)
        val copies = OsSurfaceCapture.device.findObjects(By.text(entry))
        val inTimeline = copies.minBy { it.visibleCenter.x }.visibleBounds
        OsSurfaceCapture.longPress(copies.maxBy { it.visibleCenter.x })
        val openInNewWindow = OsSurfaceCapture.waitForText("Open in new window")
        // The menu belongs to the entry in the day, so it opens to the right of the timeline's copy.
        check(openInNewWindow.visibleBounds.left > inTimeline.right) {
            "The entry's menu opened at ${openInNewWindow.visibleBounds}, over the timeline at $inTimeline"
        }
        Thread.sleep(500L)
        OsSurfaceCapture.capture(surface = "keyboard-pointer-drag-and-drop", state = "entry-context-menu")
        openInNewWindow.click()
        Thread.sleep(4_000L)
        OsSurfaceCapture.capture(surface = "large-screens-and-desktop-windowing", state = "entry-side-by-side")
    }

    /** A photo copied in another app, pasted into a new entry with Ctrl+V from a USB keyboard. */
    @Test fun pastePhotoIntoEntry() {
        if (OsSurfaceCapture.formFactor != "tablet") return
        OsSurfaceCapture.prepareOnboardedApp()
        OsSurfaceCapture.copyPhotoToClipboard()
        OsSurfaceCapture.openCapture("text")
        OsSurfaceCapture.waitForText("Write something").click()
        OsSurfaceCapture.waitForText("What's on your mind?")
        OsSurfaceCapture.withoutOnScreenKeyboards {
            HardwareKeyboard().use { keyboard ->
                keyboard.press(HardwareKeyboard.KEY_LEFTCTRL, HardwareKeyboard.KEY_V)
                Thread.sleep(4_000L)
                OsSurfaceCapture.capture(surface = "keyboard-pointer-drag-and-drop", state = "paste-photo")
            }
        }
    }

    /**
     * "hello" written with a pen in a new entry: once while the pen is still down, with the ink on
     * screen, and once after the keyboard's handwriting recognition has typed it into the entry.
     */
    @Test fun stylusHandwriting() {
        if (OsSurfaceCapture.formFactor != "tablet") return
        OsSurfaceCapture.prepareOnboardedApp()
        OsSurfaceCapture.shell("settings put secure stylus_handwriting_enabled 1")
        OsSurfaceCapture.openCapture("text")
        val stylus = Stylus()
        stylus.tap(OsSurfaceCapture.waitForText("Write something").visibleCenter)
        dismissStylusTutorial()
        OsSurfaceCapture.waitForText("What's on your mind?")
        val field = OsSurfaceCapture.device.findObject(By.clazz("android.widget.EditText")).visibleBounds
        awaitHandwritingModel(stylus, field)
        dismissStylusTutorial()
        hideOnScreenKeyboard()
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val baseline = PointF(field.left + HANDWRITING_INSET_DP * density, field.top + HANDWRITING_BASELINE_DP * density)
        stylus.write(Stylus.hello(origin = baseline, unit = HANDWRITING_X_HEIGHT_DP * density)) {
            OsSurfaceCapture.capture(surface = "keyboard-pointer-drag-and-drop", state = "stylus-handwriting")
        }
        awaitTypedHello("stylus-handwriting")
        OsSurfaceCapture.capture(surface = "keyboard-pointer-drag-and-drop", state = "stylus-handwriting-typed")
    }

    /**
     * Writing with a pen on controls that only open a text field: the editor's "Write something"
     * card starts a text entry, and the Home search pill opens search, each with the handwriting
     * typed in.
     */
    @Test fun stylusWritingOnControls() {
        if (OsSurfaceCapture.formFactor != "tablet") return
        OsSurfaceCapture.prepareOnboardedApp()
        OsSurfaceCapture.shell("settings put secure stylus_handwriting_enabled 1")
        // Something for the handwritten search to find.
        OsSurfaceCapture.createTextEntry("Said hello to the new neighbors on the walk home.")
        val stylus = Stylus()
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density

        // Gboard's handwriting model is fetched in the search field, which leaves the editor's
        // "Write something" card untouched for the capture.
        OsSurfaceCapture.openMainActivity()
        OsSurfaceCapture.returnHome()
        OsSurfaceCapture.waitForText("Search your log").click()
        val searchField = OsSurfaceCapture.device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000L)
        checkNotNull(searchField) { "Search did not open" }
        awaitHandwritingModel(stylus, searchField.visibleBounds)
        dismissStylusTutorial()
        hideOnScreenKeyboard()
        OsSurfaceCapture.returnHome()

        OsSurfaceCapture.openCapture("text")
        OsSurfaceCapture.waitForText("Write something")
        val card = OsSurfaceCapture.device.findObject(By.clickable(true).hasDescendant(By.text("Write something"))).visibleBounds
        // The card opens a text entry as soon as the pen touches it, so the word is centered to stay
        // inside the entry's text area while it is written.
        val wordWidth = Stylus.HELLO_WIDTH * HANDWRITING_X_HEIGHT_DP * density
        val onCard = PointF(card.exactCenterX() - wordWidth / 2, card.bottom - HANDWRITING_INSET_DP * density)
        stylus.write(Stylus.hello(origin = onCard, unit = HANDWRITING_X_HEIGHT_DP * density)) {
            OsSurfaceCapture.capture(surface = "keyboard-pointer-drag-and-drop", state = "stylus-write-card")
        }
        awaitTypedHello("stylus-write-card")
        OsSurfaceCapture.capture(surface = "keyboard-pointer-drag-and-drop", state = "stylus-write-card-typed")

        OsSurfaceCapture.returnHome()
        val pill = OsSurfaceCapture.device.findObject(By.clickable(true).hasDescendant(By.text("Search your log"))).visibleBounds
        // Smaller writing that starts on the pill; strokes may run past its edges.
        val onPill = PointF(pill.left + HANDWRITING_INSET_DP * density, pill.bottom - SEARCH_BASELINE_INSET_DP * density)
        stylus.write(Stylus.hello(origin = onPill, unit = SEARCH_X_HEIGHT_DP * density)) {
            OsSurfaceCapture.capture(surface = "keyboard-pointer-drag-and-drop", state = "stylus-search")
        }
        awaitTypedHello("stylus-search")
        OsSurfaceCapture.capture(surface = "keyboard-pointer-drag-and-drop", state = "stylus-search-typed")
    }

    /**
     * Waits for "hello" to be typed in a text field. If it never is, saves the screen and Gboard's
     * handwriting state under `failures/` and fails.
     */
    private fun awaitTypedHello(state: String) {
        val typed = OsSurfaceCapture.device.wait(Until.findObject(By.text(Pattern.compile("(?i)hello\\s*"))), 10_000L)
        if (typed != null) {
            Thread.sleep(1_000L)
            return
        }
        OsSurfaceCapture.capture(surface = "failures", state = "$state-not-typed")
        val handwriting = OsSurfaceCapture.shell("dumpsys input_method").lines().filter { "tylus" in it || "andwriting" in it }
        OsSurfaceCapture.writeEvidence(surface = "failures", state = "$state-not-typed", text = handwriting.joinToString("\n"))
        error("The handwriting was not typed ($state)")
    }

    /**
     * Puts the on-screen keyboard away, as a person does before writing with a pen. Back closes the
     * keyboard and leaves the entry focused. Gboard's small handwriting toolbar is left alone.
     */
    private fun hideOnScreenKeyboard() {
        val screenHeight = OsSurfaceCapture.device.displayHeight
        val keyboardShown =
            InstrumentationRegistry.getInstrumentation().uiAutomation.windows.any { window ->
                val bounds = Rect().also(window::getBoundsInScreen)
                window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD && bounds.height() > screenHeight / 4
            }
        if (!keyboardShown) return
        OsSurfaceCapture.device.pressBack()
        Thread.sleep(1_000L)
    }

    /** Closes the handwriting tutorial Gboard offers over the app the first time it reads a pen. */
    private fun dismissStylusTutorial() {
        OsSurfaceCapture.device.wait(Until.findObject(By.text("Try out your stylus")), 3_000L)?.let {
            OsSurfaceCapture.waitForText("Cancel").click()
        }
    }

    /**
     * Waits until Gboard can read handwriting. A fresh emulator's Gboard downloads its handwriting
     * model the first time a pen writes in a text field, and ignores the pen until the download
     * finishes, so a short stroke in [field] starts the download. Whatever the strokes typed is
     * cleared again.
     */
    private fun awaitHandwritingModel(
        stylus: Stylus,
        field: Rect,
    ) {
        fun ready() =
            OsSurfaceCapture.shell("dumpsys input_method").lines().any { "handwriting_recognition" in it && "_reco_" in it }
        if (ready()) return
        val y = field.centerY().toFloat()
        stylus.write(listOf(listOf(PointF(field.left + 100f, y), PointF(field.left + 160f, y))))
        val deadline = System.currentTimeMillis() + HANDWRITING_MODEL_TIMEOUT_MS
        while (!ready()) {
            check(System.currentTimeMillis() < deadline) { "Gboard did not finish downloading its handwriting model" }
            Thread.sleep(2_000L)
        }
        // Gboard offers its handwriting tutorial the first time it reads a pen, which can be now or
        // during a later capture, so one more stroke brings it up here, where it is closed.
        stylus.write(listOf(listOf(PointF(field.left + 100f, y), PointF(field.left + 160f, y))))
        dismissStylusTutorial()
        Thread.sleep(2_000L)
        OsSurfaceCapture.device.findObject(By.clazz("android.widget.EditText"))?.clear()
    }

    /**
     * Each widget as a launcher draws it, in light, dark, right-to-left, and 200% text. Rendering
     * does not depend on the screen, so it runs on the phone only.
     */
    @Test fun widgetRenders() {
        if (OsSurfaceCapture.formFactor != "phone") return
        val widgets =
            listOf(
                Triple("capture-widget", CaptureWidget(), 250 to 80),
                Triple("new-entry-widget", NewEntryWidget(), 250 to 180),
                Triple("on-this-day-widget", OnThisDayWidget(), 250 to 180),
                Triple("on-this-day-text-widget", OnThisDayWidget(previewState = TEXT_MEMORY), 250 to 180),
                Triple("pinned-memory-widget", FixedMemoryWidget(), 250 to 180),
            )
        for ((name, widget, size) in widgets) {
            for (look in WidgetRenders.Look.entries) {
                val image = WidgetRenders.render(widget, size.first, size.second, look)
                OsSurfaceCapture.saveImage(surface = "widgets", state = "$name-${look.label}", image = image)
            }
        }
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

    private companion object {
        /** `WindowConfiguration.WINDOWING_MODE_FREEFORM`, which `am start --windowingMode` takes. */
        const val WINDOWING_MODE_FREEFORM = 5

        /** Where the handwriting starts in the entry's text field, and how large it is, in dp. */
        const val HANDWRITING_INSET_DP = 48
        const val HANDWRITING_BASELINE_DP = 160
        const val HANDWRITING_X_HEIGHT_DP = 40
        const val HANDWRITING_MODEL_TIMEOUT_MS = 180_000L

        /** Writing on the search pill: smaller, with the baseline just inside the pill's bottom edge. */
        const val SEARCH_X_HEIGHT_DP = 20
        const val SEARCH_BASELINE_INSET_DP = 10

        /** A memory with no photo, so the widget shows its text, which follows the font size. */
        val TEXT_MEMORY =
            OnThisDayWidgetState.HasMemory(
                dateIso = "2025-10-10",
                dateFormatted = "October 10, 2025",
                summary = "We stayed out until the rain stopped, then walked home the long way.",
                thumbnailUri = null,
            )

        /** `logdate_splash_background`, the launcher icon's brown. */
        const val SPLASH_BACKGROUND = 0xFF3B2929.toInt()
    }
}
