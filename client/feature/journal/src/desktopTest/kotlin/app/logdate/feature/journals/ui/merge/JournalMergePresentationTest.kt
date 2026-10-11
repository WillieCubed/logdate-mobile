package app.logdate.feature.journals.ui.merge

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.client.repository.journals.JournalMergeCandidate
import app.logdate.client.repository.journals.JournalMergeOperation
import app.logdate.client.repository.journals.JournalMergePreview
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.shared.model.Journal
import app.logdate.ui.LocalSharedTransitionScope
import app.logdate.ui.platform.LocalReduceMotionOverride
import app.logdate.ui.theme.LogDateTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class, ExperimentalSharedTransitionApi::class)
class JournalMergePresentationTest {
    private val preview = JournalMergePreview(Journal(title = "Trails"), Journal(title = "Adventures"), emptySet(), emptySet())
    private val operation =
        JournalMergeOperation(
            Uuid.random(),
            preview.source.id,
            preview.destination.id,
            emptySet(),
            JournalMergeScope("owner", "server"),
            preview.source.title,
            preview.destination.title,
        )

    @Test
    fun `selected cover travels through the existing shared transition scope`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            mainClock.autoAdvance = false
            lateinit var sharedScope: SharedTransitionScope
            var state by mutableStateOf(
                JournalMergeUiState(
                    sourceTitle = preview.source.title,
                    loading = false,
                    candidates = listOf(JournalMergeCandidate(preview.destination, 0)),
                ),
            )
            setContent {
                LogDateTheme {
                    SharedTransitionLayout {
                        sharedScope = this
                        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                            JournalMergeScreenContent(state, onChoose = { state = state.copy(stage = JournalMergeStage.Review(preview)) })
                        }
                    }
                }
            }
            mainClock.advanceTimeByFrame()
            onNodeWithText(preview.destination.title).performClick()
            mainClock.advanceTimeBy(160)
            runOnUiThread { assertTrue(sharedScope.isTransitionActive, "The selected real cover shares its identity across steps") }
            save("selection-motion-midpoint", onRoot().captureToImage())
            mainClock.advanceTimeBy(600)
            runOnIdle { assertFalse(sharedScope.isTransitionActive) }
            onNodeWithText("Merge journals").assertIsDisplayed()
        }

    @Test
    fun `fast local commit lets the books finish joining before navigation`() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            mainClock.autoAdvance = false
            var navigated = 0
            var state by mutableStateOf(JournalMergeUiState(stage = JournalMergeStage.Review(preview), loading = false))
            setContent {
                LogDateTheme {
                    JournalMergePresentation(state, { navigated++ }) { displayed, settled ->
                        JournalMergeScreenContent(displayed, onMergeAnimationFinished = settled)
                    }
                }
            }
            mainClock.advanceTimeByFrame()
            save("motion-00", onRoot().captureToImage())
            runOnUiThread { state = state.copy(stage = JournalMergeStage.Complete(operation)) }
            mainClock.advanceTimeBy(100)
            onNodeWithText("Merging…").assertIsDisplayed()
            runOnUiThread { assertEquals(0, navigated) }
            (1..12).forEach { frame ->
                mainClock.advanceTimeBy(50)
                save("motion-${frame.toString().padStart(2, '0')}", onRoot().captureToImage())
            }
            mainClock.advanceTimeBy(200)
            save("motion-13", onRoot().captureToImage())
            runOnIdle { assertEquals(1, navigated) }
        }

    @Test
    fun `reduced motion resolves the illustration and navigates without waiting`() =
        runDesktopComposeUiTest {
            var navigated = 0
            var state by mutableStateOf(JournalMergeUiState(stage = JournalMergeStage.Review(preview), loading = false))
            setContent {
                LogDateTheme {
                    CompositionLocalProvider(LocalReduceMotionOverride provides true) {
                        JournalMergePresentation(state, { navigated++ }) { displayed, settled ->
                            JournalMergeScreenContent(displayed, onMergeAnimationFinished = settled)
                        }
                    }
                }
            }
            runOnIdle { state = state.copy(stage = JournalMergeStage.Complete(operation)) }
            runOnIdle { assertEquals(1, navigated) }
        }

    @Test
    fun `reopening a completed merge navigates immediately without replaying it`() =
        runDesktopComposeUiTest {
            var navigated = 0
            setContent {
                JournalMergePresentation(JournalMergeUiState(stage = JournalMergeStage.Complete(operation)), { navigated++ }) { _, _ -> }
            }
            runOnIdle { assertEquals(1, navigated) }
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
