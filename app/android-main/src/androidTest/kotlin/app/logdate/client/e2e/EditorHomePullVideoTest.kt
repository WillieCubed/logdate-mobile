package app.logdate.client.e2e

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.logdate.client.MainActivity
import app.logdate.client.datastore.EntryStartMode
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.testing.onboarding.DEBUG_SKIP_ONBOARDING_EXTRA
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Native Choreographer, public accessibility and actual touch events: no virtual Compose clock. */
@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class EditorHomePullVideoTest {
    @Test(timeout = 120_000)
    fun homePullCancelCommitAndRapidModesRetainTheActualMemory() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ensureLogDateApplicationStarted(context.applicationContext as Application)
        val device = UiDevice.getInstance(instrumentation)
        val ui = NativeEditorAssertions(device, context.packageName)
        val preferences = GlobalContext.get().get<LogdatePreferencesDataSource>()
        val previousMode = runBlocking { preferences.observeEntryStartMode().first() }
        val settings =
            listOf(
                "system" to "show_touches",
                "secure" to "show_ime_with_hard_keyboard",
                "global" to "enable_back_animation",
                "global" to "animator_duration_scale",
                "global" to "transition_animation_scale",
                "global" to "window_animation_scale",
            ).associateWith { (namespace, key) -> device.executeShellCommand("settings get $namespace $key").trim() }
        val output =
            File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")), "editor-home-pull-real-time")
        output.mkdirs()
        try {
            settings.keys.forEach { (namespace, key) ->
                device.executeShellCommand("settings put $namespace $key 1")
                check(device.executeShellCommand("settings get $namespace $key").trim().toFloat() == 1f)
            }
            check(device.executeShellCommand("settings get secure navigation_mode").trim() == "2")
            runBlocking { preferences.setEntryStartMode(EntryStartMode.TEXT) }
            ActivityScenario
                .launch<MainActivity>(
                    Intent(context, MainActivity::class.java).putExtra(DEBUG_SKIP_ONBOARDING_EXTRA, true),
                ).use { scenario ->
                    try {
                        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                        ui.await("Real Home creation action", 25_000) {
                            ui.description("Add a memory") != null ||
                                ui.text("Create Entry") != null
                        }
                        val windows = NativeEditorWritingWindows(device, scenario, ui, output)
                        ManagedEditorRecording(device, output).use { recording ->
                            val started = SystemClock.elapsedRealtime()
                            SystemClock.sleep(700)
                            val pull = ManagedHomeEntryPullGesture(device)
                            pull.perform(retreat = true) { ui.assertArmedHomePull(output, "armed-before-cancel") }
                            ui.await("Cancelled pull removes its preview", 5_000) {
                                ui.description("Release to start a new entry") == null &&
                                    ui.description("Pull down to start a new entry") == null
                            }
                            assertTrue(ui.description("Add a memory") != null)
                            assertTrue(ui.description("Return to entry") == null)
                            pull.perform(retreat = false) { ui.assertArmedHomePull(output, "armed-before-commit") }
                            val memory = ui.memoryOwner(ui.awaitInput())
                            ui.await("Mode bar follows committed route") { ui.description("Handwrite")?.isEnabled == true }
                            SystemClock.sleep(400)
                            ui.tapDescription("Handwrite")
                            ui.await("Actual handwriting input") { ui.input() == null && ui.text("Add handwriting") != null }
                            ui.tapDescription("Write")
                            ui.awaitInput()
                            ui.tapDescription("Handwrite")
                            ui.await("Interrupted switch reaches handwriting") { ui.input() == null && ui.text("Add handwriting") != null }
                            ui.tapDescription("Write")
                            assertEquals(memory, ui.memoryOwner(ui.awaitInput()), "Empty mode reversal retains the actual memory owner")
                            ui.tap(ui.awaitInput())
                            windows.ime(true)
                            for (word in listOf("A", "%squiet", "%smoment.")) {
                                device.executeShellCommand("input text $word")
                                SystemClock.sleep(250)
                            }
                            ui.assertText()
                            ui.await("Content removes creation controls") {
                                ui.description("Handwrite") == null &&
                                    ui.description("Write") == null
                            }
                            ui.tapDescription("Return to entry")
                            ui.await("Done retains content in overview") {
                                ui.text("Add to entry") != null &&
                                    ui.description("Return to entry") == null
                            }
                            ui.assertText()
                            ui.tap(ui.awaitInput())
                            windows.ime(true)
                            device.pressBack()
                            windows.ime(false)
                            val field = AccessibilityNodeInfo.obtain(ui.awaitStableInput())
                            val full = ui.bounds(field)
                            val back = ManagedEditorBackGesture(device)
                            back.perform(cancel = true) { ui.assertPartialBack(full, output) }
                            ui.await("Cancelled Back restores the same focused geometry", 8_000) {
                                ui.input()?.let {
                                    ui.bounds(it).let { now ->
                                        abs(now.width() - full.width()) <= 1 &&
                                            abs(now.height() - full.height()) <= 1
                                    }
                                } ==
                                    true
                            }
                            assertEquals(field, ui.awaitInput(), "Cancelled Back retains public accessibility identity")
                            ui.assertText()
                            back.perform(cancel = false) { ui.assertPartialBack(full, output) }
                            ui.await("Committed Back returns the same text to overview") { ui.text("Add to entry") != null }
                            assertEquals(field, ui.awaitInput(), "Committed Back keeps the same attached input")
                            ui.assertText()
                            recording.assertStillRecording()
                            val remaining = 14_000 - (SystemClock.elapsedRealtime() - started)
                            if (remaining > 0) SystemClock.sleep(remaining)
                        }
                        ui.tap(ui.awaitInput())
                        windows.ime(true)
                        device.pressBack()
                        windows.ime(false)
                        windows.capture()
                    } catch (failure: Throwable) {
                        File(output, "failure-before-scenario-close.txt").writeText(failure.stackTraceToString())
                        File(output, "failure-native-tree.txt").writeText(ui.describe())
                        device.takeScreenshot(File(output, "failure-before-close.png"))
                        throw failure
                    }
                }
        } finally {
            runBlocking { preferences.setEntryStartMode(previousMode) }
            settings.forEach { (setting, value) ->
                val (namespace, key) = setting
                device.executeShellCommand(
                    if (value == "null") "settings delete $namespace $key" else "settings put $namespace $key $value",
                )
            }
        }
    }
}
