package app.logdate.client.e2e

import android.content.pm.ActivityInfo
import android.graphics.Color
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.UiDevice
import app.logdate.client.MainActivity
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real app windows on one managed emulator; resizing does not simulate a physical hinge. */
@Suppress("DEPRECATION")
internal class NativeEditorWritingWindows(
    private val device: UiDevice,
    private val scenario: ActivityScenario<MainActivity>,
    private val ui: NativeEditorAssertions,
    private val output: File,
) {
    fun ime(visible: Boolean) {
        ui.await("IME visible=$visible") {
            var actual = !visible
            scenario.onActivity {
                actual =
                    WindowInsetsCompat
                        .toWindowInsetsCompat(it.window.decorView.rootWindowInsets)
                        .isVisible(WindowInsetsCompat.Type.ime())
            }
            actual == visible
        }
    }

    fun capture() {
        val oldSize = overrideValue(device.executeShellCommand("wm size"))
        val oldDensity = overrideValue(device.executeShellCommand("wm density"))
        var originalWidth = 0
        var originalHeight = 0
        scenario.onActivity {
            originalWidth = it.window.decorView.width
            originalHeight = it.window.decorView.height
        }
        try {
            captureReady("normal-mobile", originalWidth, originalHeight)
            for ((label, width) in listOf("foldable-sized" to 1000, "landscape-tablet" to 1280)) {
                device.executeShellCommand("wm density 160")
                device.executeShellCommand("wm size ${width}x800")
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                captureReady(label, width, 800)
            }
        } finally {
            device.executeShellCommand("wm size $oldSize")
            device.executeShellCommand("wm density $oldDensity")
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            ui.await("Original viewport restored") {
                var restored = false
                scenario.onActivity {
                    restored = it.window.decorView.width == originalWidth && it.window.decorView.height == originalHeight
                }
                restored
            }
        }
    }

    private fun captureReady(
        label: String,
        width: Int,
        height: Int,
    ) {
        var previous: android.graphics.Rect? = null
        var stable = 0
        ui.await("Stable native writing bounds at ${width}x$height", 15_000) {
            val field = ui.input()?.let(ui::bounds) ?: return@await false
            var ready = false
            scenario.onActivity {
                val decor = it.window.decorView
                val safe =
                    WindowInsetsCompat
                        .toWindowInsetsCompat(decor.rootWindowInsets)
                        .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                val rail = min(width.toFloat() - safe.left - safe.right, 640 * it.resources.displayMetrics.density)
                ready = decor.width == width &&
                    decor.height == height &&
                    abs(field.width() - rail) <= 1f &&
                    abs(field.exactCenterX() - (width + safe.left - safe.right) / 2f) <= 1f &&
                    field.top >= safe.top &&
                    field.bottom <= height - safe.bottom + 1
            }
            stable = if (ready && field == previous) stable + 1 else 0
            previous = field
            ready && stable >= 2
        }
        ui.assertText()
        ui.assertNoInactivePullProgress()
        assertTrue(ui.description("Return to entry")?.isEnabled == true)
        assertTrue(ui.description("Save entry")?.isEnabled == true)
        scenario.onActivity {
            assertEquals(0, Color.alpha(it.window.statusBarColor))
            assertEquals(0, Color.alpha(it.window.navigationBarColor))
            assertFalse(it.window.isNavigationBarContrastEnforced)
            assertFalse(it.window.isStatusBarContrastEnforced)
        }
        check(device.takeScreenshot(File(output, "$label-writing-${width}x$height.png")))
        File(output, "$label-writing.txt").writeText(
            "Native API34 managed emulator window ${width}x$height. Synthetic content; no physical hinge claim.\n${ui.describe()}",
        )
    }

    private fun overrideValue(report: String): String =
        report
            .lineSequence()
            .firstOrNull { it.startsWith("Override") }
            ?.substringAfter(": ")
            ?.trim() ?: "reset"
}
