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
                    // This API34 fixture does not expose the non-clickable gesture handle in its accessibility windows.
                    val resources = if (navigation == "threebutton") listOf("back", "home", "recent_apps") else emptyList()
                    val controls = resources.mapNotNull { device.findObject(By.res("com.android.systemui", it))?.visibleBounds }
                    val correctInsets = if (navigation == "threebutton") bottom > gestureBottom else bottom > 0
                    if (!correctInsets || controls.size != resources.size || controls.any { it.isEmpty }) return@waitUntil false
                    check(device.takeScreenshot(screenshot))
                    val bitmap = checkNotNull(BitmapFactory.decodeFile(screenshot.absolutePath))
                    try {
                        val background = bitmap.getPixel(bitmap.width / 8, bitmap.height - bottom / 2)
                        if (navigation == "threebutton") {
                            controls.all { containsVisibleIcon(bitmap, it, background) }
                        } else {
                            containsGestureHandle(bitmap, bottom, background)
                        }
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

    private fun containsGestureHandle(
        bitmap: Bitmap,
        bottomInset: Int,
        background: Int,
    ): Boolean {
        val regionLeft = bitmap.width / 4
        val regionWidth = bitmap.width * 3 / 4 - regionLeft
        val ink =
            BooleanArray(regionWidth * bottomInset) { index ->
                colorDifference(
                    bitmap.getPixel(regionLeft + index % regionWidth, bitmap.height - bottomInset + index / regionWidth),
                    background,
                ) > 48
            }
        val queue = ArrayDeque<Int>()

        fun enqueue(index: Int) {
            if (ink[index]) {
                ink[index] = false
                queue.addLast(index)
            }
        }
        for (seed in ink.indices) {
            if (!ink[seed]) continue
            var left = regionWidth
            var right = 0
            var top = bottomInset
            var bottom = 0
            enqueue(seed)
            while (queue.isNotEmpty()) {
                val index = queue.removeFirst()
                val x = index % regionWidth
                val y = index / regionWidth
                left = min(left, x)
                right = max(right, x)
                top = min(top, y)
                bottom = max(bottom, y)
                if (x > 0) enqueue(index - 1)
                if (x + 1 < regionWidth) enqueue(index + 1)
                if (y > 0) enqueue(index - regionWidth)
                if (y + 1 < bottomInset) enqueue(index + regionWidth)
            }
            val centered = abs(regionLeft + (left + right) / 2f - bitmap.width / 2f) <= bitmap.width * 0.025f
            if (centered && right - left >= bitmap.width / 10 && bottom - top in 1..max(1, bottomInset / 4)) return true
        }
        return false
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
                if (colorDifference(pixel, background) > 48) ink++
            }
        }
        return ink >= 8
    }

    private fun colorDifference(
        pixel: Int,
        background: Int,
    ): Int =
        abs(Color.red(pixel) - Color.red(background)) +
            abs(Color.green(pixel) - Color.green(background)) + abs(Color.blue(pixel) - Color.blue(background))
}
