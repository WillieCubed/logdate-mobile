@file:OptIn(ExperimentalComposeUiApi::class)

package app.logdate.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.unit.dp
import app.logdate.client.ui.LockableContent
import app.logdate.screenshots.shared.ScreenshotSceneVariant
import app.logdate.screenshots.shared.SharedScreenshotCatalog
import app.logdate.screenshots.shared.SharedScreenshotSceneId
import app.logdate.screenshots.shared.SharedScreenshotSceneSpec
import app.logdate.screenshots.shared.screenshotBaselineName
import app.logdate.ui.theme.LogDateTheme
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopScreenshotTest {
    @Test
    fun shared_catalog_matches_baselines() {
        val sceneFilter = desktopScreenshotSceneFilter
        SharedScreenshotCatalog.allScenes
            .filter { scene -> sceneFilter == null || scene.id.value.contains(sceneFilter) }
            .forEach { scene ->
                scene.variants.forEach { variant ->
                    assertMatchesBaseline(scene = scene, variant = variant)
                }
            }
    }

    @Test
    fun lock_screen_matches_baseline() {
        assertMatchesBaseline(
            baselineName = "lock-screen",
            width = 1280,
            height = 800,
            darkTheme = false,
        ) {
            LockableContent(
                isLocked = true,
                displayName = "Willie",
                onUsePasscode = {},
            ) {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun ScreenshotSurface(
    darkTheme: Boolean,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable () -> Unit,
) {
    LogDateTheme(
        dynamicColor = false,
        darkTheme = darkTheme,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
        ) {
            content()
        }
    }
}

private fun assertMatchesBaseline(
    scene: SharedScreenshotSceneSpec,
    variant: ScreenshotSceneVariant,
) {
    val baselineName = screenshotBaselineName(scene, variant)
    assertMatchesBaseline(
        baselineName = baselineName,
        width = variant.viewport.widthDp,
        height = variant.viewport.heightDp,
        darkTheme = variant.viewport.darkTheme,
        contentPadding = sceneContentPadding(scene),
        content = scene.content,
    )
}

private fun assertMatchesBaseline(
    baselineName: String,
    width: Int,
    height: Int,
    darkTheme: Boolean,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    val referenceFile = screenshotReferenceDir.resolve("$baselineName.png")
    val actualFile = screenshotActualDir.resolve("$baselineName.png")
    val diffFile = screenshotDiffDir.resolve("$baselineName.png")

    actualFile.parentFile.mkdirs()
    diffFile.parentFile.mkdirs()

    val rendered =
        renderScreenshot(
            width = width,
            height = height,
            darkTheme = darkTheme,
            contentPadding = contentPadding,
            content = content,
            frameName = baselineName,
        )
    ImageIO.write(rendered, "png", actualFile)

    if (updateDesktopScreenshots) {
        referenceFile.parentFile.mkdirs()
        ImageIO.write(rendered, "png", referenceFile)
        return
    }

    assertTrue(referenceFile.exists(), "Missing desktop screenshot baseline: ${referenceFile.absolutePath}")

    val expected =
        ImageIO.read(referenceFile)
            ?: throw IOException("Unable to read baseline screenshot: ${referenceFile.absolutePath}")

    val diff = compareImages(expected = expected, actual = rendered)
    if (diff != null) {
        ImageIO.write(diff.image, "png", diffFile)
        throw AssertionError(
            buildString {
                appendLine("Desktop screenshot mismatch for `$baselineName`.")
                appendLine("Expected: ${referenceFile.absolutePath}")
                appendLine("Actual: ${actualFile.absolutePath}")
                appendLine("Diff: ${diffFile.absolutePath}")
                append("Differing pixels: ${diff.differingPixels}/${diff.totalPixels}")
            },
        )
    } else if (diffFile.exists()) {
        diffFile.delete()
    }
}

private fun renderScreenshot(
    width: Int,
    height: Int,
    darkTheme: Boolean,
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
    frameName: String,
): BufferedImage {
    // Warm Compose/Skiko in a throwaway pass so the persisted capture comes from a settled renderer.
    renderSingleScreenshot(
        width = width,
        height = height,
        darkTheme = darkTheme,
        contentPadding = contentPadding,
        content = content,
        frameName = "$frameName-warmup",
    )

    return renderSingleScreenshot(
        width = width,
        height = height,
        darkTheme = darkTheme,
        contentPadding = contentPadding,
        content = content,
        frameName = frameName,
    )
}

private fun renderSingleScreenshot(
    width: Int,
    height: Int,
    darkTheme: Boolean,
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
    frameName: String,
): BufferedImage {
    lateinit var frame: JFrame
    lateinit var panel: ComposePanel

    SwingUtilities.invokeAndWait {
        panel =
            ComposePanel(
                renderSettings =
                    androidx.compose.ui.awt.RenderSettings
                        .SwingGraphics(),
            ).apply {
                preferredSize = Dimension(width, height)
                setSize(width, height)
                isFocusable = false
                focusTraversalKeysEnabled = false
                setContent {
                    ScreenshotSurface(
                        darkTheme = darkTheme,
                        contentPadding = contentPadding,
                        content = content,
                    )
                }
            }

        frame =
            JFrame("desktop-screenshot-$frameName").apply {
                isUndecorated = true
                focusableWindowState = false
                contentPane.add(panel)
                pack()
                setSize(width, height)
                if (desktopScreenshotVisibleMode) {
                    setLocationByPlatform(true)
                    isVisible = true
                } else {
                    setLocation(-10_000, -10_000)
                    addNotify()
                }
            }
    }

    var previousCapture: BufferedImage? = null
    var stableCapture: BufferedImage? = null
    var stableFrameCount = 0

    for (attempt in 0 until 20) {
        Thread.sleep(125)
        SwingUtilities.invokeAndWait {
            panel.invalidate()
            panel.validate()
            panel.doLayout()
            panel.renderImmediately()
        }

        val currentCapture = capturePanel(panel = panel, width = width, height = height)
        if (previousCapture != null && imagesMatch(previousCapture, currentCapture)) {
            stableFrameCount++
            if (stableFrameCount >= 2) {
                stableCapture = currentCapture
                break
            }
        } else {
            stableFrameCount = 0
        }
        previousCapture = currentCapture
    }

    SwingUtilities.invokeAndWait {
        frame.dispose()
    }

    return stableCapture ?: checkNotNull(previousCapture) { "Failed to render desktop screenshot for $frameName" }
}

private fun sceneContentPadding(scene: SharedScreenshotSceneSpec): PaddingValues =
    if (scene.id == SharedScreenshotSceneId.RewindCoverCard) {
        PaddingValues(48.dp)
    } else {
        PaddingValues(0.dp)
    }

private data class ImageDiff(
    val image: BufferedImage,
    val differingPixels: Int,
    val totalPixels: Int,
)

private fun capturePanel(
    panel: ComposePanel,
    width: Int,
    height: Int,
): BufferedImage {
    lateinit var image: BufferedImage
    SwingUtilities.invokeAndWait {
        panel.renderImmediately()
        image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        panel.paint(graphics)
        graphics.dispose()
    }
    return image
}

private fun imagesMatch(
    first: BufferedImage,
    second: BufferedImage,
): Boolean = compareImages(expected = first, actual = second) == null

private fun compareImages(
    expected: BufferedImage,
    actual: BufferedImage,
): ImageDiff? {
    if (expected.width != actual.width || expected.height != actual.height) {
        val maxWidth = maxOf(expected.width, actual.width)
        val maxHeight = maxOf(expected.height, actual.height)
        val diff = BufferedImage(maxWidth, maxHeight, BufferedImage.TYPE_INT_ARGB)
        return ImageDiff(
            image = diff,
            differingPixels = maxWidth * maxHeight,
            totalPixels = maxWidth * maxHeight,
        )
    }

    val width = expected.width
    val height = expected.height
    val diffImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    var differingPixels = 0

    for (y in 0 until height) {
        for (x in 0 until width) {
            val expectedPixel = expected.getRGB(x, y)
            val actualPixel = actual.getRGB(x, y)
            if (expectedPixel != actualPixel) {
                diffImage.setRGB(x, y, 0xFFFF0000.toInt())
                differingPixels++
            } else {
                diffImage.setRGB(x, y, expectedPixel)
            }
        }
    }

    return if (differingPixels == 0) {
        null
    } else {
        ImageDiff(
            image = diffImage,
            differingPixels = differingPixels,
            totalPixels = width * height,
        )
    }
}

private val updateDesktopScreenshots: Boolean =
    System.getProperty("logdate.desktopScreenshots.update")?.toBooleanStrictOrNull() == true

private val desktopScreenshotVisibleMode: Boolean =
    System.getProperty("logdate.desktopScreenshots.visible")?.toBooleanStrictOrNull() == true

private val desktopScreenshotSceneFilter: String? =
    System
        .getProperty("logdate.desktopScreenshots.sceneFilter")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private val screenshotReferenceDir: File =
    resolveRequiredDirectoryProperty("logdate.desktopScreenshots.referenceDir")

private val screenshotActualDir: File =
    resolveRequiredDirectoryProperty("logdate.desktopScreenshots.actualDir")

private val screenshotDiffDir: File =
    resolveRequiredDirectoryProperty("logdate.desktopScreenshots.diffDir")

private fun resolveRequiredDirectoryProperty(propertyName: String): File {
    val rawValue =
        System.getProperty(propertyName)
            ?: error("Missing required desktop screenshot system property `$propertyName`.")

    val directory = File(rawValue)
    val path = directory.toPath()
    if (!path.exists()) {
        path.createDirectories()
    }
    return directory
}
