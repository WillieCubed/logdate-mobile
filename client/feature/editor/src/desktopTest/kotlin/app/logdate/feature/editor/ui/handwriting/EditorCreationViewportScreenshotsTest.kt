package app.logdate.feature.editor.ui.handwriting

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.HandwritingBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.ui.foldable.FoldablePosture
import app.logdate.ui.platform.LocalReduceMotionOverride
import org.jetbrains.skia.Image
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class EditorCreationViewportScreenshotsTest(
    private val name: String,
    private val width: Int,
    private val height: Int,
    private val book: Boolean,
) {
    @Test
    fun retainedMemoriesFitEachCreationViewport() =
        runDesktopComposeUiTest(width = width, height = height) {
            val text = TextBlockUiState(content = "A quiet walk by the water.\n\nA little time to notice the light.")
            val ink = HandwritingBlockUiState(page = entryPageStudyInk())
            var latest: List<EntryBlockUiState> = emptyList()
            setContent {
                CompositionLocalProvider(LocalReduceMotionOverride provides true) {
                    EntryPageStudy(
                        initialBlocks = listOf(text, ink),
                        fold = foldFixture(if (book) FoldablePosture.Book else FoldablePosture.Standard, width, height),
                        onBlocksChanged = { latest = it },
                    )
                }
            }
            val output =
                File(
                    System.getenv("DISK_JANITOR_ARTIFACT_ROOT") ?: "build/reports",
                    "editor-creation-viewports",
                ).apply { mkdirs() }

            fun capture(state: String) {
                onRoot().captureToImage().asSkiaBitmap().use { bitmap ->
                    Image.makeFromBitmap(bitmap).use { image ->
                        checkNotNull(image.encodeToData()).use { data -> File(output, "$name-$state.png").writeBytes(data.bytes) }
                    }
                }
            }
            onNodeWithText("Add to entry").assertIsDisplayed().performClick()
            onNodeWithTag("entry_capture_tools").assertIsDisplayed()
            capture("creation")
            onNodeWithTag("editor_text_input").performTextInput("A quiet moment.")
            onNodeWithTag("entry_capture_tools").assertIsNotDisplayed()
            capture("writing")
            onNodeWithContentDescription("Return to entry").performClick()
            onNodeWithTag("entry_add_memory").assertIsDisplayed()
            assertEquals(listOf(text.id, ink.id), latest.take(2).map { it.id })
            assertEquals(text.content, (latest.first() as TextBlockUiState).content)
            assertEquals(ink.page, (latest[1] as HandwritingBlockUiState).page)
            assertEquals("A quiet moment.", (latest.last() as TextBlockUiState).content)
            capture("overview")
            File(
                output,
                "$name.txt",
            ).writeText("Headless Compose layout fixture: ${width}x$height px at density1; book=$book; synthetic data.\n")
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun viewports() =
            listOf(
                arrayOf<Any>("normal-mobile-400x800", 400, 800, false),
                arrayOf<Any>("foldable-sized-1000x800-book", 1000, 800, true),
                arrayOf<Any>("landscape-tablet-1280x800", 1280, 800, false),
            )
    }
}
