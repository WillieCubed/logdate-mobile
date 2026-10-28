@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)

package app.logdate.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import app.logdate.client.text.toUiText
import app.logdate.feature.rewind.ui.ImageRewindPanelUiState
import app.logdate.feature.rewind.ui.detail.RewindStoryContent
import app.logdate.feature.rewind.ui.detail.RewindStoryView
import app.logdate.ui.theme.LogDateTheme
import coil3.ColorImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Populated production-composable renders; these fixtures do not emulate Android system bars. */
@OptIn(ExperimentalTestApi::class)
class RewindViewportCaptureTest {
    @Test fun normalMobileViewport() = capture("normal-mobile-fixture", 411, 891)

    @Test fun foldableSizedMobileViewport() = capture("foldable-sized-mobile-fixture", 674, 900)

    @Test fun landscapeTabletViewport() = capture("tablet-landscape-fixture", 1280, 800)

    @Test fun shortWindowLongCaptionKeepsContrastAtSceneEdges() = capture("short-window-long-caption", 411, 480, scrollToEnd = true)

    @Test fun firstEntryKeepsBackgroundAtSceneEdges() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            mainClock.autoAdvance = false
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    LogDateTheme(dynamicColor = false) {
                        RewindStoryView(
                            panels =
                                listOf(
                                    ImageRewindPanelUiState(
                                        Uuid.random(),
                                        Instant.parse("2026-10-20T12:00:00Z"),
                                        "fixture://entry",
                                        null,
                                        "Tuesday".toUiText(),
                                    ),
                                ),
                            onExit = {},
                            isFirstView = true,
                            externalPause = true,
                        ) { Box(Modifier.fillMaxSize().background(Color(0xFFD8EBF7))) }
                    }
                }
            }
            mainClock.advanceTimeBy(1)
            val scene = onNodeWithTag("rewind-story-focus").fetchSemanticsNode().boundsInRoot
            assertEquals(0f, scene.left, "First entry must not shrink the scene background")
            assertEquals(411f, scene.right)
            val pixels = onRoot().captureToImage().toPixelMap()
            assertEquals(Color(0xFFD8EBF7).toArgb(), pixels[2, 2].toArgb())
        }

    private fun capture(
        name: String,
        width: Int,
        height: Int,
        scrollToEnd: Boolean = false,
    ) = runDesktopComposeUiTest(width = width, height = height) {
        val id = Uuid.parse("00000000-0000-0000-0000-000000000038")
        setContent {
            val image = remember { ColorImage(Color(0xFFD8EBF7).toArgb()) }
            val handler = remember(image) { AsyncImagePreviewHandler { image } }
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1f),
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides handler,
            ) {
                LogDateTheme(dynamicColor = false) {
                    RewindStoryView(
                        panels =
                            listOf(
                                ImageRewindPanelUiState(
                                    id,
                                    Instant.parse("2026-10-20T12:00:00Z"),
                                    "fixture://rewind-photo",
                                    "## A quiet walk\n\nRoom to **remember** the afternoon." +
                                        if (scrollToEnd) {
                                            "\n\n" +
                                                (1..20).joinToString("\n\n") { "Remembering moment $it on the walk home." } +
                                                "\n\nLast detail"
                                        } else {
                                            ""
                                        },
                                    "Tuesday".toUiText(),
                                ),
                            ),
                        onExit = {},
                        externalPause = true,
                    ) { RewindStoryContent(it) }
                }
            }
        }
        onNodeWithText("A quiet walk", substring = true).assertIsDisplayed()
        val scene = onNodeWithTag("rewind-story-focus").fetchSemanticsNode().boundsInRoot
        val memory = onNodeWithTag("rewind_memory_$id").fetchSemanticsNode().boundsInRoot
        assertEquals(scene, memory)
        assertEquals(minOf(width, 1200).toFloat(), scene.width)
        assertEquals(height.toFloat(), scene.height)
        mainClock.advanceTimeBy(500)
        waitForIdle()
        if (scrollToEnd) onNodeWithText("Last detail").performScrollTo().assertIsDisplayed()
        val image = onRoot().captureToImage()
        val pixels = image.toPixelMap()
        val edgeX = scene.left.toInt() + 2
        assertTrue(pixels[edgeX, height - 2].red < 0.5f, "Caption contrast must reach the scene edge, without a padded rectangle")
        for (y in height / 2 + 1 until height) {
            assertTrue(
                kotlin.math.abs(pixels[edgeX, y].red - pixels[edgeX, y - 1].red) < 0.04f,
                "Caption contrast must not introduce a hard horizontal seam at y=$y",
            )
        }
        val directory = System.getenv("LOGDATE_REWIND_CAPTURE_DIR") ?: return@runDesktopComposeUiTest
        val output = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) for (x in 0 until image.width) output.setRGB(x, y, pixels[x, y].toArgb())
        val target = File(directory, "$name.png")
        target.parentFile.mkdirs()
        check(ImageIO.write(output, "png", target))
        File(
            directory,
            "$name.txt",
        ).writeText(
            "renderer=headless desktop Compose production Rewind composables\nviewport=${width}x${height}dp\ndensity=1\nfontScale=1\nAndroidSystemUI=not emulated\nnativeFoldPosture=not emulated\n",
        )
    }
}
