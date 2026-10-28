package app.logdate.client.e2e

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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

/** The creation anchor may be below the viewport; its interactive selector must remain safe. */
class EditorLongPageAcceptanceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test(timeout = 180_000)
    fun longContentKeepsCaptureAboveNavigationAndLastLineCanScrollClear() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val output = File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")), "editor-long-page")
        check(output.mkdirs() || output.isDirectory)
        ensureLogDateApplicationStarted(instrumentation.targetContext.applicationContext as Application)
        val preferences = GlobalContext.get().get<LogdatePreferencesDataSource>()
        val previousMode = runBlocking { preferences.observeEntryStartMode().first() }
        val overlays = device.executeShellCommand("cmd overlay list")
        val previousNavigation =
            checkNotNull(
                overlays
                    .lineSequence()
                    .firstOrNull { it.startsWith("[x]") && it.contains("com.android.internal.systemui.navbar.") }
                    ?.substringAfter("[x] ")
                    ?.trim(),
            )
        try {
            runBlocking { preferences.setEntryStartMode(EntryStartMode.TEXT) }
            for (mode in listOf("gestural", "threebutton")) {
                val overlay = "com.android.internal.systemui.navbar.$mode"
                check(overlays.contains(overlay))
                device.executeShellCommand("cmd overlay enable-exclusive --category $overlay")
                for ((name, orientation) in listOf(
                    "portrait" to ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                    "landscape" to ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                )) {
                    val text = (1..24).joinToString("\n\n") { "Retained detail $it from a quiet walk by the water." }
                    val intent =
                        Intent(instrumentation.targetContext, EditorActivity::class.java)
                            .putExtra("entry_focus", true)
                    ActivityScenario.launch<EditorActivity>(intent).use { scenario ->
                        scenario.onActivity { it.requestedOrientation = orientation }
                        compose.waitUntil(20_000) {
                            var rotated = false
                            scenario.onActivity {
                                rotated =
                                    (it.window.decorView.width > it.window.decorView.height) == (name == "landscape")
                            }
                            rotated && compose.onAllNodesWithTag("editor_text_input").fetchSemanticsNodes().isNotEmpty()
                        }
                        compose.onNodeWithTag("editor_text_input").performClick().performTextInput(text)
                        compose.onNodeWithTag("editor_text_input").assertTextContains(text)
                        if (compose.onAllNodesWithContentDescription("Return to entry").fetchSemanticsNodes().isNotEmpty()) {
                            compose.onNodeWithContentDescription("Return to entry").performClick()
                        }
                        compose.waitUntil(10_000) {
                            var keyboard = true
                            scenario.onActivity {
                                keyboard =
                                    ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                            }
                            !keyboard && compose.onAllNodesWithTag("entry_journal_chooser").fetchSemanticsNodes().isNotEmpty()
                        }
                        compose.waitForIdle()
                        val longText = compose.onNodeWithTag("editor_text_input").getUnclippedBoundsInRoot()
                        val initialCapture = compose.onNodeWithTag("entry_capture_tools").getUnclippedBoundsInRoot()
                        assertTrue(longText.bottom > initialCapture.bottom, "Fixture must overflow before any scroll: $longText")
                        assertSafeCapture(scenario, "$mode-$name-initial")
                        check(device.takeScreenshot(File(output, "$mode-$name-initial.png")))
                        compose.onNodeWithTag("entry_page_creation").performScrollTo()
                        compose.waitForIdle()
                        assertSafeCapture(scenario, "$mode-$name-end")
                        val textBounds = compose.onNodeWithTag("editor_text_input").getUnclippedBoundsInRoot()
                        val capture = compose.onNodeWithTag("entry_capture_tools").getUnclippedBoundsInRoot()
                        assertTrue(
                            textBounds.bottom.value <= capture.top.value + 1f,
                            "Last content line cannot scroll clear of the selector: $textBounds, $capture",
                        )
                        check(device.takeScreenshot(File(output, "$mode-$name-end.png")))
                    }
                }
            }
        } finally {
            device.executeShellCommand("cmd overlay enable-exclusive --category $previousNavigation")
            runBlocking { preferences.setEntryStartMode(previousMode) }
        }
    }

    private fun assertSafeCapture(
        scenario: ActivityScenario<EditorActivity>,
        label: String,
    ) {
        var left = 0f
        var top = 0f
        var right = 0f
        var bottom = 0f
        scenario.onActivity {
            val view = it.window.decorView
            val scale = it.resources.displayMetrics.density
            val insets =
                checkNotNull(ViewCompat.getRootWindowInsets(view)).getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime(),
                )
            left = insets.left / scale
            top = insets.top / scale
            right = (view.width - insets.right) / scale
            bottom = (view.height - insets.bottom) / scale
        }
        val capture = compose.onNodeWithTag("entry_capture_tools").getUnclippedBoundsInRoot()
        assertTrue(
            capture.top.value >= top - 1 && capture.bottom.value <= bottom + 1,
            "$label selector overlaps system UI: $capture vs $top..$bottom",
        )
        assertTrue(
            capture.left.value >= left - 1 && capture.right.value <= right + 1,
            "$label selector crosses horizontal safe bounds",
        )
    }
}
