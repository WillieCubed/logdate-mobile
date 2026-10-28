@file:Suppress("ktlint:standard:function-naming")

package app.logdate.client.e2e

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.logdate.client.text.toUiText
import app.logdate.feature.rewind.ui.ImageRewindPanelUiState
import app.logdate.feature.rewind.ui.LocationMapRewindPanelUiState
import app.logdate.feature.rewind.ui.MapPanelPoint
import app.logdate.feature.rewind.ui.RewindPanelUiState
import app.logdate.feature.rewind.ui.detail.RewindStoryContent
import app.logdate.feature.rewind.ui.detail.RewindStoryView
import app.logdate.ui.theme.LogDateTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Native managed-device captures; each run records its real viewport and Android device model. */
class RewindTransparentBarsE2ETest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun edgeToEdge() {
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
    }

    @Test fun geographyHasContinuousTransparentBars() {
        val panel =
            mutableStateOf<RewindPanelUiState>(
                LocationMapRewindPanelUiState(
                    listOf(MapPanelPoint(36.1699, -115.1398), MapPanelPoint(36.1716, -115.1460), MapPanelPoint(36.1740, -115.1320)),
                    "Where your week was".toUiText(),
                    "Your recorded locations".toUiText(),
                ),
            )
        compose.setContent {
            LogDateTheme {
                RewindStoryView(panels = listOf(panel.value), onExit = {}, externalPause = true) { RewindStoryContent(it) }
            }
        }
        awaitState("Map loaded")
        val scene = compose.onNodeWithTag("rewind-story-focus").fetchSemanticsNode().boundsInRoot
        assertEquals(scene, compose.onNodeWithTag("rewind-native-map").fetchSemanticsNode().boundsInRoot)
        assertTransparentWindow()
        capture("map")
    }

    @Test fun mediaHasContinuousTransparentBars() {
        val panel = mutableStateOf<RewindPanelUiState>(LocationMapRewindPanelUiState(emptyList(), "".toUiText(), "".toUiText()))
        compose.setContent {
            LogDateTheme {
                RewindStoryView(panels = listOf(panel.value), onExit = {}, externalPause = true) { RewindStoryContent(it) }
            }
        }
        val file = File(compose.activity.cacheDir, "transparent-rewind-fixture.png")
        val image = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        image.eraseColor(SOURCE_COLOR)
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        compose.runOnUiThread {
            panel.value =
                ImageRewindPanelUiState(
                    Uuid.parse("00000000-0000-0000-0000-000000000037"),
                    Instant.parse("2026-10-20T12:00:00Z"),
                    file.toURI().toString(),
                    null,
                    "Tuesday".toUiText(),
                )
        }
        awaitState("Memory loaded")
        assertTransparentWindow()
        val target = capture("media")
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val bounds = compose.onNodeWithTag("rewind-story-focus").fetchSemanticsNode().boundsInWindow
        val view = compose.activity.window.decorView
        val rootInsets = checkNotNull(ViewCompat.getRootWindowInsets(view))
        val insets = rootInsets.getInsets(WindowInsetsCompat.Type.statusBars())
        val nav = rootInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
        compose.waitUntil(10_000) {
            check(device.takeScreenshot(target))
            val bitmap = BitmapFactory.decodeFile(target.absolutePath) ?: return@waitUntil false

            fun matches(
                x: Float,
                y: Float,
            ): Boolean {
                val pixel = bitmap.getPixel(x.toInt().coerceIn(0, bitmap.width - 1), y.toInt().coerceIn(0, bitmap.height - 1))
                return kotlin.math.abs(Color.red(pixel) - Color.red(SOURCE_COLOR)) <= 2 &&
                    kotlin.math.abs(Color.green(pixel) - Color.green(SOURCE_COLOR)) <= 2 &&
                    kotlin.math.abs(Color.blue(pixel) - Color.blue(SOURCE_COLOR)) <= 2
            }
            val statusMatches = insets.top == 0 || listOf(0.3f, 0.7f).all { matches(bounds.left + bounds.width * it, insets.top * 0.5f) }
            val navigationMatches =
                when {
                    nav.bottom > 0 -> listOf(0.1f, 0.9f).all { matches(bounds.left + bounds.width * it, bitmap.height - nav.bottom * 0.5f) }
                    nav.left > 0 -> listOf(0.3f, 0.7f).all { matches(nav.left * 0.5f, bounds.top + bounds.height * it) }
                    nav.right > 0 -> listOf(0.3f, 0.7f).all { matches(bitmap.width - nav.right * 0.5f, bounds.top + bounds.height * it) }
                    else -> true
                }
            bitmap.recycle()
            statusMatches && navigationMatches
        }
        compose.waitUntil(5_000) {
            val insideFullWidthScene = bounds.width >= view.width - 2
            WindowInsetsControllerCompat(compose.activity.window, view).isAppearanceLightStatusBars == insideFullWidthScene
        }
        capture("media")
    }

    @Suppress("DEPRECATION")
    private fun assertTransparentWindow() {
        compose.runOnIdle {
            val window = compose.activity.window
            assertEquals(Color.TRANSPARENT, window.statusBarColor)
            assertEquals(Color.TRANSPARENT, window.navigationBarColor)
            assertEquals(Color.TRANSPARENT, window.navigationBarDividerColor)
            if (Build.VERSION.SDK_INT >= 29) {
                assertFalse(window.isStatusBarContrastEnforced)
                assertFalse(window.isNavigationBarContrastEnforced)
            }
        }
    }

    private fun awaitState(description: String) {
        compose.waitUntil(60_000) {
            compose
                .onAllNodes(
                    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun capture(kind: String): File {
        val out = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: error("Managed capture output required")
        val directory = File(out, "rewind-transparent-bars").apply { mkdirs() }
        val metrics = compose.activity.resources.displayMetrics
        val window = compose.activity.windowManager.currentWindowMetrics.bounds
        val config = compose.activity.resources.configuration
        val role =
            when {
                config.screenWidthDp >= 1000 && config.screenWidthDp > config.screenHeightDp -> "tablet-landscape"
                config.smallestScreenWidthDp >= 600 -> "foldable-sized-mobile"
                else -> "normal-mobile"
            }
        File(
            directory,
            "viewport.txt",
        ).writeText(
            "role=$role\nviewport=${window.width()}x${window.height()}px\ndensity=${metrics.densityDpi}dpi\nconfiguration=${config.screenWidthDp}x${config.screenHeightDp}dp\nsmallestWidth=${config.smallestScreenWidthDp}dp\nAndroid=${Build.VERSION.SDK_INT}\nmodel=${Build.MODEL}\n",
        )
        val file = File(directory, "$role-$kind.png")
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(file))
        return file
    }

    private companion object {
        val SOURCE_COLOR = Color.rgb(216, 235, 247)
    }
}
