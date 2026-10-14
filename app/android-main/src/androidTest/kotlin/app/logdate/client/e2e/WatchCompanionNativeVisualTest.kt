package app.logdate.client.e2e

import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.logdate.feature.core.settings.ui.watch.WatchStatus
import app.logdate.feature.core.settings.ui.watch.v2.WatchCompanionPage
import app.logdate.feature.core.settings.ui.watch.v2.WatchCompanionPreferences
import app.logdate.feature.core.settings.ui.watch.v2.WatchCompanionSections
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.watch.NativeTodayTilePreview
import app.logdate.ui.watch.TodayTilePresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.coroutines.coroutineContext

/** Android's real dynamic palette and animator setting, on a Managed Device only. */
class WatchCompanionNativeVisualTest {
    private val systemMotionScale =
        object : MotionDurationScale {
            override val scaleFactor: Float
                get() =
                    Settings.Global.getFloat(
                        InstrumentationRegistry.getInstrumentation().targetContext.contentResolver,
                        Settings.Global.ANIMATOR_DURATION_SCALE,
                        1f,
                    )
        }

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(effectContext = systemMotionScale)

    @Test(timeout = 120_000)
    fun todayPreviewRendersTheNativeTileWithoutPhoneActions() {
        assertTrue("Emulator required", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val preview =
            mutableStateOf<TodayTilePresentation?>(
                TodayTilePresentation("Today", "7 moments today", "Breakfast by the window", "Record"),
            )
        compose.setContent {
            LogDateTheme(dynamicColor = false) { NativeTodayTilePreview(preview.value) }
        }

        // The actual Android view renderer completes asynchronously outside Compose's idling registry.
        fun containsText(
            view: android.view.View,
            text: String,
        ): Boolean =
            (view is android.widget.TextView && view.text.toString() == text) ||
                (view is android.view.ViewGroup && (0 until view.childCount).any { containsText(view.getChildAt(it), text) })
        compose.waitUntil(30_000) {
            var rendered = false
            instrumentation.runOnMainSync { rendered = containsText(compose.activity.window.decorView, "7 moments today") }
            rendered
        }
        instrumentation.runOnMainSync {
            assertTrue(containsText(compose.activity.window.decorView, "Breakfast by the window"))
        }
        // The tile is a read-only illustration, so native clickable descendants are excluded from TalkBack.
        compose.onNodeWithText("Record").assertDoesNotExist()
        compose.runOnIdle { preview.value = TodayTilePresentation("Today", "8 moments today", "A later moment", "Record") }
        compose.waitUntil(30_000) {
            var replaced = false
            instrumentation.runOnMainSync {
                replaced = containsText(compose.activity.window.decorView, "8 moments today") &&
                    !containsText(compose.activity.window.decorView, "7 moments today")
            }
            replaced
        }
        compose.runOnIdle { preview.value = null }
        compose.waitForIdle()
        instrumentation.runOnMainSync {
            assertTrue(!containsText(compose.activity.window.decorView, "8 moments today"))
        }
    }

    @Test(timeout = 120_000)
    fun populatedCompanionUsesDynamicColorsAndReducedMotion() {
        assertTrue(Build.VERSION.SDK_INT >= 31)
        assertTrue("Emulator required", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val output =
            File(
                requireNotNull(
                    InstrumentationRegistry.getArguments().getString("additionalTestOutputDir"),
                ),
                "watch-companion-native",
            ).apply { mkdirs() }
        val originalScale = device.executeShellCommand("settings get global animator_duration_scale").trim()
        val dark = mutableStateOf(false)
        val textScale = mutableStateOf(1f)
        val actualPrimary = mutableStateOf<Color?>(null)
        val expectedPrimary = mutableStateOf<Color?>(null)
        val observedMotion = mutableStateOf<Float?>(null)
        try {
            device.executeShellCommand("settings put global animator_duration_scale 1")
            compose.setContent {
                val context = LocalContext.current
                LogDateTheme(darkTheme = dark.value, dynamicColor = true) {
                    val primary = MaterialTheme.colorScheme.primary
                    val expected = if (dark.value) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
                    SideEffect {
                        actualPrimary.value = primary
                        expectedPrimary.value = expected
                    }
                    LaunchedEffect(dark.value, textScale.value) {
                        val scale = coroutineContext[MotionDurationScale]
                        snapshotFlow { scale?.scaleFactor }.collect { observedMotion.value = it }
                    }
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, textScale.value)) {
                        WatchCompanionPage(
                            WatchStatus.Ready("Pixel Watch", true),
                            null,
                            {},
                            {},
                            {},
                            {},
                            {},
                            {},
                            {},
                            {},
                            {},
                            preferences = { captureActions ->
                                WatchCompanionSections(
                                    WatchCompanionPreferences(30, true, true, true, true),
                                    false,
                                    false,
                                    {},
                                    {},
                                    {},
                                    {},
                                    {},
                                    captureActions = captureActions,
                                )
                            },
                        )
                    }
                }
            }
            compose.waitUntil { actualPrimary.value != null && observedMotion.value == 1f }
            assertEquals(expectedPrimary.value, actualPrimary.value)
            device.takeScreenshot(File(output, "dynamic-light.png"))
            compose.runOnIdle { dark.value = true }
            compose.waitForIdle()
            assertEquals(expectedPrimary.value, actualPrimary.value)
            device.takeScreenshot(File(output, "dynamic-dark.png"))
            device.executeShellCommand("settings put global animator_duration_scale 0")
            compose.runOnIdle { textScale.value = 2f }
            compose.waitUntil { observedMotion.value == 0f }
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Remove watch"))
            compose.onNodeWithText("Remove watch").assertIsDisplayed()
            device.takeScreenshot(File(output, "large-text-reduced-motion-bottom.png"))
        } finally {
            if (originalScale == "null") {
                device.executeShellCommand("settings delete global animator_duration_scale")
            } else {
                device.executeShellCommand("settings put global animator_duration_scale $originalScale")
            }
        }
    }
}
