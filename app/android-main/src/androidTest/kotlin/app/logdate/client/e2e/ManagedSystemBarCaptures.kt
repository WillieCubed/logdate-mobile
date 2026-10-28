package app.logdate.client.e2e

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import app.logdate.client.MainActivity
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Insets can change before SystemUI's new navigation controls reach the captured frame. */
@Suppress("DEPRECATION")
internal class ManagedSystemBarCaptures(
    private val device: UiDevice,
    private val compose: ComposeTestRule,
    private val scenario: ActivityScenario<MainActivity>,
    private val output: File,
) {
    fun captureGestureAndThreeButtonBars() {
        var gestureBottom = 0
        for (navigation in listOf("gestural", "threebutton")) {
            device.executeShellCommand("cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.$navigation")
            var bottom = 0
            var top = 0
            var samples = 0
            var lastSample = 0L
            val screenshot = File(output, "mobile-$navigation-transparent-bars.png")
            try {
                compose.waitUntil(10_000) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastSample < 500 || samples >= 20) return@waitUntil false
                    lastSample = now
                    samples++
                    scenario.onActivity {
                        val window = it.window
                        assertFalse(window.isNavigationBarContrastEnforced)
                        assertFalse(window.isStatusBarContrastEnforced)
                        assertEquals(0, Color.alpha(window.navigationBarColor))
                        assertEquals(0, Color.alpha(window.statusBarColor))
                        val insets = WindowInsetsCompat.toWindowInsetsCompat(window.decorView.rootWindowInsets)
                        bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                        top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                    }
                    val resources = if (navigation == "threebutton") listOf("back", "home", "recent_apps") else listOf("home_handle")
                    val controls = resources.mapNotNull { device.findObject(By.res("com.android.systemui", it))?.visibleBounds }
                    val correctInsets = if (navigation == "threebutton") bottom > gestureBottom else bottom > 0
                    if (!correctInsets || controls.size != resources.size || controls.any { it.isEmpty }) return@waitUntil false
                    check(device.takeScreenshot(screenshot))
                    val bitmap = checkNotNull(BitmapFactory.decodeFile(screenshot.absolutePath))
                    try {
                        val background = bitmap.getPixel(bitmap.width / 8, bitmap.height - bottom / 2)
                        controls.all { containsVisibleIcon(bitmap, it, background) }
                    } finally {
                        bitmap.recycle()
                    }
                }
                val bitmap = checkNotNull(BitmapFactory.decodeFile(screenshot.absolutePath))
                try {
                    assertEquals(
                        bitmap.getPixel(bitmap.width / 8, bitmap.height - bottom - 4),
                        bitmap.getPixel(bitmap.width / 8, bitmap.height - bottom / 2),
                        "App background must continue behind $navigation navigation without a scrim",
                    )
                    assertEquals(
                        bitmap.getPixel(bitmap.width / 4, top + 4),
                        bitmap.getPixel(bitmap.width / 4, top / 2),
                        "App background must continue behind the status area without a scrim",
                    )
                } finally {
                    bitmap.recycle()
                }
                if (navigation == "gestural") gestureBottom = bottom
            } finally {
                device.dumpWindowHierarchy(File(output, "$navigation-system-ui.xml"))
                File(output, "$navigation-capture-readiness.txt").writeText("samples=$samples top=$top bottom=$bottom\n")
            }
        }
    }

    private fun containsVisibleIcon(
        bitmap: Bitmap,
        bounds: Rect,
        background: Int,
    ): Boolean {
        var ink = 0
        for (y in max(0, bounds.top) until min(bitmap.height, bounds.bottom) step 2) {
            for (x in max(0, bounds.left) until min(bitmap.width, bounds.right) step 2) {
                val pixel = bitmap.getPixel(x, y)
                val difference =
                    abs(Color.red(pixel) - Color.red(background)) +
                        abs(Color.green(pixel) - Color.green(background)) + abs(Color.blue(pixel) - Color.blue(background))
                if (difference > 48) ink++
            }
        }
        return ink >= 8
    }
}
