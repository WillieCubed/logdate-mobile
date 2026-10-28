package app.logdate.client.e2e

import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.UiDevice
import app.logdate.client.MainActivity
import java.io.File
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Resizes only the owned managed emulator; these are window-size checks, not hinge hardware proof. */
@Suppress("DEPRECATION")
internal class ManagedEditorWindowCaptures(
    private val device: UiDevice,
    private val compose: ComposeTestRule,
    private val scenario: ActivityScenario<MainActivity>,
    private val output: File,
    private val awaitCamera: () -> Unit,
) {
    fun captureCameraSizes() =
        captureSizes("camera", awaitCamera) {
            val shutter =
                compose
                    .onNodeWithContentDescription("Take Photo")
                    .assertIsDisplayed()
                    .assertIsEnabled()
                    .fetchSemanticsNode()
                    .boundsInRoot
            val chooser = compose.onNodeWithTag("entry_creation_actions").fetchSemanticsNode().boundsInRoot
            assertTrue(shutter.bottom <= chooser.top, "Shutter must clear creation controls")
        }

    fun captureReviewSizes() =
        captureSizes("review", ::awaitReviewImage) {
            val panel = compose.onNodeWithTag("camera_review_surface").fetchSemanticsNode().boundsInRoot
            scenario.onActivity {
                val view = it.window.decorView
                val density = it.resources.displayMetrics.density
                val safe =
                    WindowInsetsCompat
                        .toWindowInsetsCompat(view.rootWindowInsets)
                        .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                assertTrue(panel.width <= min(view.width.toFloat() - safe.left - safe.right, 640 * density) - 32 * density + 1f)
                assertTrue(panel.bottom <= view.height - safe.bottom - 16 * density + 1f)
                assertTrue(panel.left >= safe.left + 16 * density - 1f)
                assertTrue(panel.right <= view.width - safe.right - 16 * density + 1f)
            }
            compose.onNodeWithText("Retake").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithText("Use Photo").assertIsDisplayed().assertIsEnabled()
        }

    fun awaitReviewImage() {
        compose.onNodeWithContentDescription("Captured photo").assertIsDisplayed()
        var lastSample = 0L
        var samples = 0
        compose.waitUntil(10_000) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastSample < 500 || samples >= 20) return@waitUntil false
            lastSample = now
            samples++
            val image = File(output, "review-image-pixels.png")
            check(device.takeScreenshot(image))
            val bitmap = checkNotNull(BitmapFactory.decodeFile(image.absolutePath))
            try {
                var visible = 0
                for (y in 3..5) {
                    for (x in 2..6) {
                        val color = bitmap.getPixel(bitmap.width * x / 8, bitmap.height * y / 8)
                        if (Color.red(color) + Color.green(color) + Color.blue(color) > 120) visible++
                    }
                }
                visible >= 5
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun captureSizes(
        stage: String,
        awaitReady: () -> Unit,
        validate: () -> Unit,
    ) {
        val oldSize = overrideValue(device.executeShellCommand("wm size"))
        val oldDensity = overrideValue(device.executeShellCommand("wm density"))
        try {
            for ((label, width) in listOf("foldable-sized" to 1000, "landscape-tablet" to 1280)) {
                device.executeShellCommand("wm density 160")
                device.executeShellCommand("wm size ${width}x800")
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                compose.waitUntil(15_000) {
                    var matches = false
                    scenario.onActivity { matches = it.window.decorView.width == width && it.window.decorView.height == 800 }
                    matches
                }
                awaitReady()
                validate()
                scenario.onActivity {
                    assertEquals(0, Color.alpha(it.window.statusBarColor))
                    assertEquals(0, Color.alpha(it.window.navigationBarColor))
                    assertFalse(it.window.isNavigationBarContrastEnforced)
                    assertFalse(it.window.isStatusBarContrastEnforced)
                }
                check(device.takeScreenshot(File(output, "$label-$stage-window-${width}x800.png")))
                File(output, "$label-$stage-window.txt").writeText(
                    "Native app on API34 managed emulator, resized ${width}x800px at density160. " +
                        "Synthetic emulated camera. No physical foldable/hinge behavior claimed.\n",
                )
            }
        } finally {
            device.executeShellCommand("wm size $oldSize")
            device.executeShellCommand("wm density $oldDensity")
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            compose.waitUntil(15_000) {
                var portrait = false
                scenario.onActivity { portrait = it.window.decorView.height > it.window.decorView.width }
                portrait
            }
            awaitReady()
        }
    }

    private fun overrideValue(report: String): String =
        report
            .lineSequence()
            .firstOrNull { it.startsWith("Override") }
            ?.substringAfter(": ")
            ?.trim() ?: "reset"
}
