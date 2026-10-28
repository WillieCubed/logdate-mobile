@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)

package app.logdate.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import app.logdate.client.text.toUiText
import app.logdate.feature.rewind.ui.AudioNoteRewindPanelUiState
import app.logdate.feature.rewind.ui.ImageRewindPanelUiState
import app.logdate.feature.rewind.ui.TextNoteRewindPanelUiState
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

    @Test fun normalMobileReadingWidth() = capture("normal-mobile-reading", 411, 891, reading = true)

    @Test fun foldableSizedReadingWidth() = capture("foldable-sized-reading", 674, 900, reading = true)

    @Test fun landscapeTabletReadingWidth() = capture("tablet-landscape-reading", 1280, 800, reading = true)

    @Test fun shortWindowLongCaptionKeepsContrastAtSceneEdges() = capture("short-window-long-caption", 411, 480, scrollToEnd = true)

    @Test fun normalMobileAudioWidth() = capture("normal-mobile-audio", 411, 891, reading = true, audio = true)

    @Test fun foldableSizedAudioWidth() = capture("foldable-sized-audio", 674, 900, reading = true, audio = true)

    @Test fun landscapeTabletAudioWidth() = capture("tablet-landscape-audio", 1280, 800, reading = true, audio = true)

    @Test fun tallVideoMemoryFitsBelowChrome() = capture("portrait-video-memory", 411, 891, scrollToEnd = true, portraitVideo = true)

    @Test fun tabletTallVideoMemoryFitsBelowChrome() =
        capture("tablet-portrait-video-memory", 1280, 800, scrollToEnd = true, portraitVideo = true)

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
        reading: Boolean = false,
        audio: Boolean = false,
        portraitVideo: Boolean = false,
    ) = runDesktopComposeUiTest(width = width, height = height) {
        val id = Uuid.parse("00000000-0000-0000-0000-000000000038")
        setContent {
            val image =
                remember {
                    ColorImage(
                        (
                            if (width ==
                                411
                            ) {
                                Color.White
                            } else {
                                Color(0xFFD8EBF7)
                            }
                        ).toArgb(),
                        600,
                        if (portraitVideo) 1200 else 400,
                    )
                }
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
                                if (audio) {
                                    AudioNoteRewindPanelUiState(
                                        id,
                                        Instant.parse("2026-10-20T12:00:00Z"),
                                        "fixture://audio",
                                        42000,
                                        "A quiet walk. Room to remember the afternoon.",
                                        "Tuesday".toUiText(),
                                        listOf(0.2f, 0.8f),
                                    )
                                } else if (reading) {
                                    TextNoteRewindPanelUiState(
                                        id,
                                        Instant.parse("2026-10-20T12:00:00Z"),
                                        "## A quiet walk\n\nRoom to **remember** the afternoon. A memory should remain readable on a wide display, with a calm leading edge and room around the words.",
                                        "Tuesday".toUiText(),
                                    )
                                } else {
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
                                        isVideoFrame = portraitVideo,
                                    )
                                },
                            ),
                        onExit = {},
                        externalPause = true,
                    ) { RewindStoryContent(it) }
                }
            }
        }
        onNodeWithText("A quiet walk", substring = true).assertIsDisplayed()
        val scene = onNodeWithTag("rewind-story-focus").fetchSemanticsNode().boundsInRoot
        if (!audio) {
            val memory = onNodeWithTag("rewind_memory_$id").fetchSemanticsNode().boundsInRoot
            assertEquals(scene, memory)
        }
        assertEquals(minOf(width, 1200).toFloat(), scene.width)
        assertEquals(height.toFloat(), scene.height)
        val readable = onAllNodes(hasScrollAction()).fetchSemanticsNodes().maxBy { it.boundsInRoot.width }.boundsInRoot
        assertTrue(readable.width <= if (reading) 600f else 760f, "Memory content needs its own Apple-aligned maximum: $readable")
        assertTrue(kotlin.math.abs(readable.center.x - scene.center.x) <= 1f, "Memory content must be centered inside its scene")
        if (!reading) {
            val media =
                onNode(
                    hasContentDescription(if (portraitVideo) "Tuesday" else "A quiet walk", substring = true),
                ).fetchSemanticsNode().boundsInRoot
            assertTrue(media.width <= 760f, "Media memory must keep its own maximum, independently from the scene: $media")
            val date = onNodeWithText("Tuesday").fetchSemanticsNode().boundsInRoot
            assertTrue(date.bottom <= media.top, "Media must fit below metadata without overdraw: date=$date media=$media")
            if (portraitVideo) {
                val videoLabel = onNodeWithText("Video still").fetchSemanticsNode().boundsInRoot
                val close = onNode(hasContentDescription("Close rewind")).fetchSemanticsNode().boundsInRoot
                assertTrue(videoLabel.top >= close.bottom, "Video metadata must clear the measured playback header")
            }
        }
        mainClock.advanceTimeBy(500)
        waitForIdle()
        if (scrollToEnd) onNodeWithText("Last detail").performScrollTo().assertIsDisplayed()
        val image = onRoot().captureToImage()
        val pixels = image.toPixelMap()
        val edgeX = scene.left.toInt() + 2
        assertTrue(pixels[edgeX, height - 2].red < 0.5f, "Caption contrast must reach the scene edge, without a padded rectangle")
        val linkContrast = (darkColorScheme().primary.luminance() + 0.05f) / (pixels[edgeX, height - 2].luminance() + 0.05f)
        assertTrue(linkContrast >= 4.5f, "Shared Markdown links need 4.5:1 contrast against the image caption: $linkContrast")
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
