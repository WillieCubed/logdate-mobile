package app.logdate.client.e2e

import android.content.pm.ActivityInfo
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.platform.PlatformSheet
import app.logdate.ui.theme.LogDateTheme
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

@OptIn(ExperimentalMaterial3Api::class)
class SurfaceSafeAreaAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test(timeout = 90_000)
    fun settingsProtectHorizontalContentAndKeyboard() =
        withLandscapeCutout("settings") { output, device ->
            compose.setContent {
                LogDateTheme(dynamicColor = false) {
                    SettingsScaffold("Your preferences", {}, maxContentWidth = 1200.dp) {
                        item {
                            val text = remember { mutableStateOf("A place to write") }
                            TextField(text.value, { text.value = it }, Modifier.fillMaxWidth().testTag("field"))
                        }
                        item { Button({}, Modifier.fillMaxWidth().testTag("action")) { Text("Save preferences") } }
                    }
                }
            }
            assertHorizontalSafe("field")
            check(device.takeScreenshot(File(output, "landscape.png")))
            compose.onNodeWithTag("field").performClick().performTextInput(" today")
            compose.waitUntil(10_000) {
                ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) ==
                    true
            }
            compose.waitForIdle()
            check(device.takeScreenshot(File(output, "landscape-keyboard.png")))
            device.pressBack()
        }

    @Test(timeout = 90_000)
    fun modalSheetProtectsCutoutsAndNavigationBar() =
        withLandscapeCutout("sheet") { output, device ->
            compose.setContent {
                LogDateTheme(dynamicColor = false) {
                    PlatformSheet({}, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                        Text("Keep this moment", Modifier.fillMaxWidth().testTag("sheet_content"))
                        Box(Modifier.height(40.dp))
                        Button({}, Modifier.fillMaxWidth().testTag("sheet_action")) { Text("Keep writing") }
                    }
                }
            }
            assertHorizontalSafe("sheet_content")
            assertHorizontalSafe("sheet_action")
            check(device.takeScreenshot(File(output, "landscape.png")))
        }

    private fun assertHorizontalSafe(tag: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val density = compose.activity.resources.displayMetrics.density
        val safe =
            checkNotNull(ViewCompat.getRootWindowInsets(view)).getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
        val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertTrue(bounds.left.value >= safe.left / density - 1, "$tag: $bounds overlaps left inset $safe")
        assertTrue(bounds.right.value <= (view.width - safe.right) / density + 1, "$tag: $bounds overlaps right inset $safe")
    }

    private fun withLandscapeCutout(
        name: String,
        body: (File, UiDevice) -> Unit,
    ) {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val output =
            File(
                checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),
                "surface-safe-area/$name",
            ).apply {
                mkdirs()
            }
        val overlay = "com.android.internal.display.cutout.emulation.double"
        val overlays = device.executeShellCommand("cmd overlay list")
        check(overlays.contains(overlay))
        val previous =
            overlays
                .lineSequence()
                .firstOrNull {
                    it.startsWith("[x]") && it.contains(".display.cutout.emulation.")
                }?.substringAfter("[x] ")
                ?.trim()
        val previousIme = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        try {
            device.executeShellCommand("cmd overlay enable-exclusive --category $overlay")
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
            compose.runOnUiThread {
                compose.activity.enableEdgeToEdge()
                compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            compose.waitUntil(10_000) { compose.activity.window.decorView.width > compose.activity.window.decorView.height }
            body(output, device)
        } finally {
            device.executeShellCommand(
                if (previous ==
                    null
                ) {
                    "cmd overlay disable $overlay"
                } else {
                    "cmd overlay enable-exclusive --category $previous"
                },
            )
            device.executeShellCommand(
                if (previousIme ==
                    "null"
                ) {
                    "settings delete secure show_ime_with_hard_keyboard"
                } else {
                    "settings put secure show_ime_with_hard_keyboard $previousIme"
                },
            )
        }
    }
}
