package app.logdate.client.e2e

import android.graphics.Color
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Actual native unconfigured-map fallback, production components and synthetic memories. */
@SdkSuppress(minSdkVersion = 34)
class PlacesMapFallbackE2ETest {
    @Test(timeout = 180_000)
    fun placesRemainUsefulWithoutAStandaloneMapErrorAtThreeWindowSizes() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val oldSize = overrideValue(device.executeShellCommand("wm size"))
        val oldDensity = overrideValue(device.executeShellCommand("wm density"))
        val output = File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")), "places-fallback")
        check(output.mkdirs() || output.isDirectory)
        val opened = AtomicReference<String?>()
        try {
            for ((label, size) in listOf(
                "normal-mobile" to (411 to 891),
                "foldable-sized-mobile" to (720 to 960),
                "landscape-tablet" to (1280 to 800),
            )) {
                device.executeShellCommand("wm density 160")
                device.executeShellCommand("wm size ${size.first}x${size.second}")
                ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                        activity.enableEdgeToEdge(
                            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                        )
                        activity.window.isNavigationBarContrastEnforced = false
                        activity.setContent { PlacesSheetFixture(nativeMap = true, onOpenPlace = opened::set) }
                    }
                    device.waitForIdle(2_000)
                    scenario.onActivity { check(it.window.decorView.width == size.first && it.window.decorView.height == size.second) }
                    val cafe = checkNotNull(device.wait(Until.findObject(By.text("Riverside Café")), 10_000))
                    assertTrue(cafe.visibleBounds.top < size.second / 2, "Cards must use the former map area")
                    assertNull(device.findObject(By.textContains("Map unavailable")))
                    assertNull(device.findObject(By.text("Map")))
                    assertNull(device.findObject(By.desc("Show map")))
                    if (label == "normal-mobile") {
                        val search = checkNotNull(device.findObject(By.clazz("android.widget.EditText")))
                        search.text = "River walk"
                        checkNotNull(device.wait(Until.findObject(By.text("River walk")), 5_000))
                        check(device.wait(Until.gone(By.text("Riverside Café")), 5_000))
                        search.text = ""
                        checkNotNull(device.wait(Until.findObject(By.text("Riverside Café")), 5_000)).click()
                        device.waitForIdle(1_000)
                        assertEquals("cafe", opened.get(), "The real place identity must reach the existing action")
                        var imeVisible = false
                        scenario.onActivity {
                            imeVisible =
                                it.window.decorView.rootWindowInsets
                                    .isVisible(WindowInsets.Type.ime())
                        }
                        if (imeVisible) device.pressBack()
                        device.waitForIdle(1_000)
                        checkNotNull(device.findObject(By.text("Riverside Café")))
                    }
                    check(device.takeScreenshot(File(output, "$label-places-fallback-${size.first}x${size.second}.png")))
                    File(output, "$label-window.txt").writeText(
                        "viewport=${size.first}x${size.second}; requested density=160; resized API34 window\n" +
                            device.executeShellCommand("wm size") + device.executeShellCommand("wm density") +
                            "font_scale=" + device.executeShellCommand("settings get system font_scale"),
                    )
                }
            }
            File(output, "provenance.txt").writeText(
                "API34 managed Pixel 9 Pro; density160 resized windows. Production Places components with synthetic memories. " +
                    "Actual native unconfigured-map readiness fallback, not loaded tiles, hinge hardware or MainActivity bar acceptance.",
            )
        } finally {
            device.executeShellCommand("wm size $oldSize")
            device.executeShellCommand("wm density $oldDensity")
        }
    }

    private fun overrideValue(value: String): String =
        value
            .lineSequence()
            .firstOrNull { it.startsWith("Override") }
            ?.substringAfter(":")
            ?.trim() ?: "reset"
}
