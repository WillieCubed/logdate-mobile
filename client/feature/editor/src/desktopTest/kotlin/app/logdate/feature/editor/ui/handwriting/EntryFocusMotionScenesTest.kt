package app.logdate.feature.editor.ui.handwriting

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.HandwritingBlockUiState
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.ui.theme.LogDateTheme
import org.jetbrains.skia.Image
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class EntryFocusMotionScenesTest(
    private val name: String,
    private val width: Int,
    private val height: Int,
    private val kind: String,
) {
    @Test
    fun recordFocusAndReturn() =
        runDesktopComposeUiTest(width = width, height = height) {
            val ink = HandwritingBlockUiState(page = sampleInk())
            val text = TextBlockUiState(content = "Took the long way home.\n\nThe light was too good to miss.")
            val photo = ImageBlockUiState(uri = samplePhotoUri(), caption = "A quiet moment outside")
            val audio = AudioBlockUiState()
            setContent {
                LogDateTheme(dynamicColor = false) {
                    EntryStartPrototype(
                        initialBlocks = listOf(ink, text, photo, audio),
                        focusAnyBlock = true,
                        previewPresentations = mapOf(recorderFixture(audio)),
                    )
                }
            }
            val directory = File("build/reports/entry-focus-motion/$name/$kind").apply { mkdirs() }
            directory.listFiles()?.filter { it.name.matches(Regex("frame-\\d{4}\\.png")) }?.forEach { check(it.delete()) }
            var index = 0

            fun frames(count: Int) {
                repeat(count) {
                    mainClock.advanceTimeBy(40)
                    File(directory, "frame-${(index++).toString().padStart(4, '0')}.png").writeBytes(
                        Image.makeFromBitmap(onNodeWithTag("stories_editor").captureToImage().asSkiaBitmap()).encodeToData()!!.bytes,
                    )
                }
            }
            val block =
                when (kind) {
                    "text" -> text
                    "ink" -> ink
                    "photo" -> photo
                    else -> audio
                }
            onNodeWithTag("editor_block_list").performScrollToNode(hasTestTag("focus_block_${block.id}"))
            mainClock.autoAdvance = false
            frames(8)
            onNodeWithTag("focus_block_${block.id}").performClick()
            frames(16)
            frames(10)
            onNodeWithContentDescription("Return to entry").performClick()
            frames(16)
            frames(8)
            onNodeWithTag("entry_mode_overview").assertExists()
            mainClock.autoAdvance = true
            File(directory, "frames.txt").writeText(index.toString())
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}-{3}")
        fun variants(): List<Array<Any>> =
            listOf("text", "ink", "photo", "recorder").flatMap { kind ->
                listOf(arrayOf("phone", 360, 800, kind), arrayOf("tablet", 1280, 800, kind))
            }
    }
}
