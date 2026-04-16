@file:OptIn(ExperimentalComposeUiApi::class)

package app.logdate.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.awt.RenderSettings
import androidx.compose.ui.unit.dp
import app.logdate.client.ui.LockableContent
import app.logdate.feature.onboarding.ui.OnboardingStartScreenContent
import app.logdate.feature.rewind.ui.components.RewindCoverCard
import app.logdate.feature.rewind.ui.overview.RewindPreviewUiState
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
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

class DesktopScreenshotTest {
    @Test
    fun onboarding_landing_matches_baseline() {
        assertMatchesBaseline(
            scenario =
                DesktopScreenshotScenario(
                    name = "onboarding-landing",
                    width = 1366,
                    height = 900,
                ) {
                    ScreenshotSurface {
                        OnboardingStartScreenContent(
                            showLanding = true,
                            onGetStarted = {},
                            onStartFromBackup = {},
                        )
                    }
                },
        )
    }

    @Test
    fun rewind_cover_card_matches_baseline() {
        val rewind =
            RewindPreviewUiState(
                message = "You packed this week with movement, memories, and a lot of camera roll receipts.",
                rewindId = Uuid.parse("11111111-2222-3333-4444-555555555555"),
                label = "This Week",
                title = "Five cities in seven days",
                start = LocalDate(2026, 4, 6),
                end = LocalDate(2026, 4, 12),
                rewindAvailable = true,
                isViewed = false,
                entryCount = 9,
                photoCount = 41,
                peopleCount = 6,
                primaryLocation = "San Francisco",
            )

        assertMatchesBaseline(
            scenario =
                DesktopScreenshotScenario(
                    name = "rewind-cover-card",
                    width = 1280,
                    height = 720,
                ) {
                    ScreenshotSurface(contentPadding = PaddingValues(48.dp)) {
                        Box(
                            modifier =
                                Modifier
                                    .width(520.dp),
                        ) {
                            RewindCoverCard(
                                rewind = rewind,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                },
        )
    }

    @Test
    fun lock_screen_matches_baseline() {
        assertMatchesBaseline(
            scenario =
                DesktopScreenshotScenario(
                    name = "lock-screen",
                    width = 1280,
                    height = 800,
                ) {
                    ScreenshotSurface {
                        LockableContent(
                            isLocked = true,
                            displayName = "Willie",
                            onUsePasscode = {},
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                            )
                        }
                    }
                },
        )
    }
}

private data class DesktopScreenshotScenario(
    val name: String,
    val width: Int,
    val height: Int,
    val content: @Composable () -> Unit,
)

@Composable
private fun ScreenshotSurface(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    LogDateTheme(
        dynamicColor = false,
        darkTheme = false,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(contentPadding),
        ) {
            content()
        }
    }
}

private fun assertMatchesBaseline(scenario: DesktopScreenshotScenario) {
    val referenceFile = screenshotReferenceDir.resolve("${scenario.name}.png")
    val actualFile = screenshotActualDir.resolve("${scenario.name}.png")
    val diffFile = screenshotDiffDir.resolve("${scenario.name}.png")

    actualFile.parentFile.mkdirs()
    diffFile.parentFile.mkdirs()

    val rendered = renderScreenshot(scenario)
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
                appendLine("Desktop screenshot mismatch for `${scenario.name}`.")
                appendLine("Expected: ${referenceFile.absolutePath}")
                appendLine("Actual: ${actualFile.absolutePath}")
                appendLine("Diff: ${diffFile.absolutePath}")
                append("Differing pixels: ${diff.differingPixels}/${diff.totalPixels}")
            },
        )
    }
}

private fun renderScreenshot(scenario: DesktopScreenshotScenario): BufferedImage {
    lateinit var frame: JFrame
    lateinit var panel: ComposePanel

    SwingUtilities.invokeAndWait {
        panel = ComposePanel(renderSettings = RenderSettings.SwingGraphics()).apply {
            preferredSize = Dimension(scenario.width, scenario.height)
            setSize(scenario.width, scenario.height)
            setContent(scenario.content)
        }

        frame =
            JFrame("desktop-screenshot-${scenario.name}").apply {
                isUndecorated = true
                contentPane.add(panel)
                pack()
                setSize(scenario.width, scenario.height)
                isVisible = true
            }
    }

    repeat(6) {
        Thread.sleep(125)
        SwingUtilities.invokeAndWait {
            panel.invalidate()
            panel.validate()
            panel.doLayout()
            panel.renderImmediately()
        }
    }

    lateinit var image: BufferedImage
    SwingUtilities.invokeAndWait {
        panel.renderImmediately()
        image = BufferedImage(scenario.width, scenario.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        panel.paint(graphics)
        graphics.dispose()
        frame.dispose()
    }
    return image
}

private data class ImageDiff(
    val image: BufferedImage,
    val differingPixels: Int,
    val totalPixels: Int,
)

private fun compareImages(
    expected: BufferedImage,
    actual: BufferedImage,
): ImageDiff? {
    if (expected.width != actual.width || expected.height != actual.height) {
        return ImageDiff(
            image = actual,
            differingPixels = actual.width * actual.height,
            totalPixels = actual.width * actual.height,
        )
    }

    val diffImage = BufferedImage(expected.width, expected.height, BufferedImage.TYPE_INT_ARGB)
    var differingPixels = 0
    val totalPixels = expected.width * expected.height

    for (y in 0 until expected.height) {
        for (x in 0 until expected.width) {
            val expectedPixel = expected.getRGB(x, y)
            val actualPixel = actual.getRGB(x, y)
            if (expectedPixel == actualPixel) {
                diffImage.setRGB(x, y, actualPixel)
                continue
            }

            differingPixels += 1
            diffImage.setRGB(x, y, 0xFFFF0055.toInt())
        }
    }

    return if (differingPixels == 0) {
        null
    } else {
        ImageDiff(
            image = diffImage,
            differingPixels = differingPixels,
            totalPixels = totalPixels,
        )
    }
}

private val updateDesktopScreenshots: Boolean =
    System.getProperty("logdate.desktopScreenshots.update")?.toBooleanStrictOrNull() == true

private val screenshotReferenceDir: File =
    resolveRequiredDirectoryProperty("logdate.desktopScreenshots.referenceDir")

private val screenshotActualDir: File =
    resolveRequiredDirectoryProperty("logdate.desktopScreenshots.actualDir")

private val screenshotDiffDir: File =
    resolveRequiredDirectoryProperty("logdate.desktopScreenshots.diffDir")

private fun resolveRequiredDirectoryProperty(propertyName: String): File {
    val path =
        requireNotNull(System.getProperty(propertyName)) {
            "Missing required system property: $propertyName"
        }
    val directory = File(path)
    directory.toPath().takeIf { !it.exists() }?.createDirectories()
    return directory
}
