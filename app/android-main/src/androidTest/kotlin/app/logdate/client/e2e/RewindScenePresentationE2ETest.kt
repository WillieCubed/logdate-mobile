@file:Suppress("ktlint:standard:function-naming")

package app.logdate.client.e2e

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.logdate.client.text.toUiText
import app.logdate.feature.rewind.ui.ImageRewindPanelUiState
import app.logdate.feature.rewind.ui.SubtitledRewindPanelUiState
import app.logdate.feature.rewind.ui.detail.RewindStoryContent
import app.logdate.feature.rewind.ui.detail.RewindStoryView
import app.logdate.ui.theme.LogDateTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Synthetic bright/dark media checks; no personal journal or location data. */
class RewindScenePresentationE2ETest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun edgeToEdge() {
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
    }

    @Test fun brightMediaKeepsItsImageAndReadableForeground() = verifyMedia(false)

    @Test fun darkMediaKeepsItsImageAndReadableForeground() = verifyMedia(true)

    private fun verifyMedia(dark: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "rewind-scene-$dark.png")
        val image = Bitmap.createBitmap(900, 1600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(if (dark) Color.rgb(24, 36, 58) else Color.rgb(216, 235, 247))
        val paint = Paint().apply { color = if (dark) Color.rgb(58, 75, 53) else Color.rgb(158, 182, 106) }
        canvas.drawCircle(240f, 1460f, 700f, paint)
        paint.color = if (dark) Color.rgb(40, 61, 44) else Color.rgb(116, 155, 90)
        canvas.drawCircle(890f, 1630f, 800f, paint)
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        val id = Uuid.parse("00000000-0000-0000-0000-000000000036")
        var exits = 0
        compose.setContent {
            LogDateTheme {
                RewindStoryView(
                    panels =
                        listOf(
                            ImageRewindPanelUiState(
                                id,
                                Instant.parse("2026-10-20T12:00:00Z"),
                                file.toURI().toString(),
                                "## A quiet walk\n\nRoom to **remember** the afternoon.",
                                "Tuesday".toUiText(),
                            ),
                            SubtitledRewindPanelUiState("Next moment", "Wednesday".toUiText(), null),
                        ),
                    onExit = { exits++ },
                    externalPause = true,
                    modifier = Modifier.fillMaxSize(),
                ) { RewindStoryContent(it) }
            }
        }
        compose.waitUntil(15_000) {
            compose
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasText("A quiet walk", substring = true),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("A quiet walk", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Tuesday").assertIsDisplayed()
        val sceneBounds = compose.onNodeWithTag("rewind-story-focus").fetchSemanticsNode().boundsInRoot
        val memoryBounds = compose.onNodeWithTag("rewind_memory_$id").fetchSemanticsNode().boundsInRoot
        assertTrue(memoryBounds.width < sceneBounds.width && memoryBounds.height < sceneBounds.height)
        assertTrue(memoryBounds.left > sceneBounds.left && memoryBounds.right < sceneBounds.right)
        val directory =
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: error("Managed capture directory required")
        val capture = File(directory, "rewind-${if (dark) "dark" else "bright"}-media-flat.png")
        capture.parentFile?.mkdirs()
        compose.waitForIdle()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val bounds = compose.onNodeWithTag("rewind_media_$id").fetchSemanticsNode().boundsInWindow
        val progress = compose.onNodeWithTag("rewind-story-progress").fetchSemanticsNode().boundsInWindow
        compose.waitUntil(10_000) {
            if (!device.takeScreenshot(capture)) return@waitUntil false
            val captured = BitmapFactory.decodeFile(capture.absolutePath) ?: return@waitUntil false
            val pixel = captured.getPixel((bounds.center.x).toInt(), (bounds.top + bounds.height * 0.35f).toInt())
            val track = captured.getPixel((progress.left + progress.width * 0.75f).toInt(), progress.center.y.toInt())
            captured.recycle()
            // Untouched image area must retain the source color, with no broad darkening overlay.
            kotlin.math.abs(Color.red(pixel) - if (dark) 24 else 216) <= 2 &&
                kotlin.math.abs(Color.green(pixel) - if (dark) 36 else 235) <= 2 &&
                kotlin.math.abs(Color.blue(pixel) - if (dark) 58 else 247) <= 2 &&
                kotlin.math.abs(Color.red(track) - 98) <= 2 &&
                kotlin.math.abs(Color.green(track) - 107) <= 2 &&
                kotlin.math.abs(Color.blue(track) - 112) <= 2
        }
        assertTrue(capture.isFile)
        compose.onNodeWithContentDescription("Close rewind").performClick()
        assertEquals(1, exits)
        compose.onNodeWithTag("rewind-story-focus").performTouchInput { click(Offset(width * 0.8f, height * 0.5f)) }
        compose.onNodeWithText("Next moment").assertIsDisplayed()
    }
}
