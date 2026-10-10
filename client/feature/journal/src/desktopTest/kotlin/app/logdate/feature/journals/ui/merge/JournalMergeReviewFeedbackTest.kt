package app.logdate.feature.journals.ui.merge

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import app.logdate.client.repository.journals.JournalMergePreview
import app.logdate.shared.model.Journal
import app.logdate.ui.theme.LogDateTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class JournalMergeReviewFeedbackTest {
    private val source = Journal(title = "Summer in the mountains")
    private val destination = Journal(title = "Journeys with friends and family")
    private val preview = JournalMergePreview(source, destination, setOf(Uuid.random()), setOf(Uuid.random()))

    @Test
    fun `local failure stays visible beside confirmation after scrolling`() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            var state by mutableStateOf(JournalMergeUiState(loading = false, stage = JournalMergeStage.Review(preview)))
            setContent {
                LogDateTheme {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                        JournalMergeScreenContent(state, onConfirm = { state = state.copy(error = JournalMergeError.Failed) })
                    }
                }
            }
            onNodeWithText("Merge journals").performScrollTo().performClick()
            onNodeWithText("Couldn’t merge these journals. Nothing was changed.")
                .assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            val notice = onNodeWithText("Couldn’t merge these journals. Nothing was changed.").getUnclippedBoundsInRoot()
            val viewport = onRoot().getUnclippedBoundsInRoot()
            assertTrue(notice.top >= viewport.top && notice.bottom <= viewport.bottom, "The full failure message must fit in the viewport")
            save("failure-large-text", onRoot().captureToImage())
        }

    @Test
    fun `changed review returns to updated count before another explicit confirmation`() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            var confirmed = 0
            val changed = preview.copy(sourceContentIds = preview.sourceContentIds + Uuid.random())
            var state by mutableStateOf(JournalMergeUiState(loading = false, stage = JournalMergeStage.Review(preview)))
            setContent {
                LogDateTheme {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                        JournalMergeScreenContent(state, onConfirm = {
                            confirmed++
                            state = state.copy(stage = JournalMergeStage.Review(changed), error = JournalMergeError.Changed)
                        })
                    }
                }
            }
            onNodeWithText("Merge journals").performScrollTo().performClick()
            onNodeWithText("3 items after merging").assertIsDisplayed()
            save("changed-count-large-text", onRoot().captureToImage())
            assertEquals(1, confirmed)
            onNodeWithText("These journals changed. Review the updated details and confirm again.")
                .performScrollTo()
                .assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            save("changed-notice-large-text", onRoot().captureToImage())
            onNodeWithText("Merge journals").performScrollTo().performClick()
            assertEquals(2, confirmed)
        }

    private fun save(
        name: String,
        image: ImageBitmap,
    ) {
        val directory = System.getenv("LOGDATE_MERGE_SCREENSHOT_DIR") ?: return
        val pixels = image.toPixelMap()
        val output = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) output.setRGB(x, y, pixels[x, y].toArgb())
        }
        val file = File(directory, "$name.png")
        file.parentFile.mkdirs()
        ImageIO.write(output, "png", file)
    }
}
