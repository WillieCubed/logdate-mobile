package app.logdate.client.e2e

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.logdate.client.EditorActivity
import app.logdate.client.datastore.EntryStartMode
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.ensureLogDateApplicationStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.test.assertTrue

/** Real production editor and IME. Only run through a repository-declared Managed Device. */
class EditorSafeAreaAcceptanceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test(timeout = 240_000)
    fun chromeFitsSystemBarsAcrossNavigationRotationAndKeyboardChanges() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")), "editor-safe-area")
        output.mkdirs()
        val device = UiDevice.getInstance(instrumentation)
        ensureLogDateApplicationStarted(instrumentation.targetContext.applicationContext as Application)
        val preferences = GlobalContext.get().get<LogdatePreferencesDataSource>()
        val previousDefault = runBlocking { preferences.observeEntryStartMode().first() }
        val previousIme = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        val overlays = device.executeShellCommand("cmd overlay list")
        val previousNavigation =
            overlays
                .lineSequence()
                .firstOrNull { it.startsWith("[x]") && it.contains("com.android.internal.systemui.navbar.") }
                ?.substringAfter("[x] ")
                ?.trim()
        check(previousNavigation != null) { "Cannot restore navigation mode: $overlays" }
        val cutoutOverlay = "com.android.internal.display.cutout.emulation.double"
        check(overlays.contains(cutoutOverlay)) { "Managed image lacks double-cutout emulation" }
        val previousCutout =
            overlays
                .lineSequence()
                .firstOrNull { it.startsWith("[x]") && it.contains(".display.cutout.emulation.") }
                ?.substringAfter("[x] ")
                ?.trim()
        try {
            device.executeShellCommand("cmd overlay enable-exclusive --category $cutoutOverlay")
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
            runBlocking { preferences.setEntryStartMode(EntryStartMode.TEXT) }
            for (mode in listOf("gestural", "threebutton")) {
                val overlay = "com.android.internal.systemui.navbar.$mode"
                check(overlays.contains(overlay)) { "Managed image does not offer $overlay" }
                device.executeShellCommand("cmd overlay enable-exclusive --category $overlay")
                ActivityScenario.launch<EditorActivity>(Intent(instrumentation.targetContext, EditorActivity::class.java)).use { scenario ->
                    compose.waitUntil(20_000) { compose.onAllNodesWithTag("editor_text_input").fetchSemanticsNodes().isNotEmpty() }
                    for ((name, orientation) in listOf(
                        "portrait" to ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                        "landscape" to ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                    )) {
                        scenario.onActivity { it.requestedOrientation = orientation }
                        compose.waitUntil(15_000) {
                            var matches = false
                            scenario.onActivity {
                                matches =
                                    (it.window.decorView.width > it.window.decorView.height) == (name == "landscape")
                            }
                            matches && compose.onAllNodesWithTag("editor_text_input").fetchSemanticsNodes().isNotEmpty()
                        }
                        compose.onNodeWithTag("editor_text_input").performClick().performTextInput("A quiet walk by the water. ")
                        waitForIme(scenario, true)
                        assertChromeFits(scenario, "$mode-$name-keyboard")
                        check(device.takeScreenshot(File(output, "$mode-$name-keyboard.png")))
                        device.pressBack()
                        waitForIme(scenario, false)
                        assertChromeFits(scenario, "$mode-$name")
                        check(device.takeScreenshot(File(output, "$mode-$name.png")))
                    }
                }
            }
            ActivityScenario.launch<EditorActivity>(Intent(instrumentation.targetContext, EditorActivity::class.java)).use { scenario ->
                compose.waitUntil(20_000) { compose.onAllNodesWithTag("editor_text_input").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("editor_text_input").performClick().performTextInput("Keep the controls above this thought.")
                waitForIme(scenario, true)
                compose.mainClock.autoAdvance = false
                try {
                    compose.onNodeWithContentDescription("Return to entry").performTouchInput { click() }
                    compose.mainClock.advanceTimeBy(64)
                    compose.onNodeWithContentDescription("Close").assertIsDisplayed()
                    check(device.takeScreenshot(File(output, "return-mid-keyboard.png")))
                    waitForIme(scenario, false)
                    compose.mainClock.advanceTimeBy(1200)
                    check(device.takeScreenshot(File(output, "overview-before-focus.png")))
                    compose.onNodeWithText("Keep the controls above this thought.").performTouchInput { click() }
                    compose.waitUntil(10_000) {
                        compose.mainClock.advanceTimeByFrame()
                        compose.onAllNodesWithContentDescription("Return to entry").fetchSemanticsNodes().isNotEmpty()
                    }
                    compose.mainClock.advanceTimeBy(64)
                    compose.onNodeWithContentDescription("Return to entry").assertIsDisplayed()
                    check(device.takeScreenshot(File(output, "focus-mid-interrupted.png")))
                    compose.onNodeWithContentDescription("Return to entry").performTouchInput { click() }
                    compose.mainClock.advanceTimeBy(64)
                    compose.onNodeWithContentDescription("Close").assertIsDisplayed()
                    check(device.takeScreenshot(File(output, "return-mid-interrupted.png")))
                } finally {
                    compose.mainClock.autoAdvance = true
                }
            }
        } finally {
            if (previousCutout != null) {
                device.executeShellCommand("cmd overlay enable-exclusive --category $previousCutout")
            } else {
                device.executeShellCommand("cmd overlay disable $cutoutOverlay")
            }
            device.executeShellCommand("cmd overlay enable-exclusive --category $previousNavigation")
            device.executeShellCommand(
                if (previousIme ==
                    "null"
                ) {
                    "settings delete secure show_ime_with_hard_keyboard"
                } else {
                    "settings put secure show_ime_with_hard_keyboard $previousIme"
                },
            )
            runBlocking { preferences.setEntryStartMode(previousDefault) }
        }
    }

    private fun waitForIme(
        scenario: ActivityScenario<EditorActivity>,
        visible: Boolean,
    ) {
        compose.waitUntil(10_000) {
            var matches = false
            scenario.onActivity {
                matches =
                    (ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true) == visible
            }
            matches
        }
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(2_000)
        compose.waitForIdle()
    }

    private fun assertChromeFits(
        scenario: ActivityScenario<EditorActivity>,
        label: String,
    ) {
        compose.waitForIdle()
        val nodes = listOf(compose.onNodeWithContentDescription("Return to entry"), compose.onNodeWithTag("entry_capture_tools"))
        var left = 0f
        var top = 0f
        var right = 0f
        var bottom = 0f
        scenario.onActivity {
            val view = it.window.decorView
            val density = it.resources.displayMetrics.density
            val insets =
                checkNotNull(ViewCompat.getRootWindowInsets(view)).getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime(),
                )
            left = insets.left / density
            top = insets.top / density
            right = (view.width - insets.right) / density
            bottom = (view.height - insets.bottom) / density
        }
        val field = compose.onNodeWithTag("editor_text_input").getUnclippedBoundsInRoot()
        assertTrue(
            field.bottom - field.top >=
                androidx.compose.ui.unit
                    .Dp(24f),
            "$label: no visible writing line: $field",
        )
        assertTrue(field.top.value >= top && field.bottom.value <= bottom, "$label: writing overlaps system UI: $field")
        for (node in nodes) {
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue(bounds.right > bounds.left && bounds.bottom > bounds.top, "$label: missing chrome $bounds")
            assertTrue(
                bounds.left.value >= left - 1 && bounds.right.value <= right + 1,
                "$label: chrome $bounds crosses horizontal safe area $left..$right",
            )
            assertTrue(
                bounds.top.value >= top - 1 && bounds.bottom.value <= bottom + 1,
                "$label: chrome $bounds crosses vertical safe area $top..$bottom",
            )
        }
    }
}
