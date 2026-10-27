package app.logdate.feature.editor.ui.handwriting

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.client.datastore.EntryStartMode
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.HandwritingBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.ui.foldable.FoldablePosture
import org.jetbrains.skia.Image
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Offscreen renders of the same composables exposed to Android Studio previews. */
@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class EntryPagePreviewScenesTest(
    private val name: String,
    private val width: Int,
    private val height: Int,
) {
    @Test
    fun capture() =
        runDesktopComposeUiTest(width = width, height = height) {
            val posture =
                when {
                    name.contains("book") -> FoldablePosture.Book
                    name.contains("tabletop") -> FoldablePosture.Tabletop
                    else -> FoldablePosture.Standard
                }
            var blocks: List<EntryBlockUiState> = emptyList()
            setContent {
                EntryPageStudy(
                    onBlocksChanged = { blocks = it },
                    focused = name.contains("focus"),
                    expandedMemories = name.contains("memories") && !name.contains("pull-to"),
                    dark = name.contains("dark"),
                    fold = foldFixture(posture, width, height),
                    defaultMode = if (name.contains("ink-default")) EntryStartMode.HANDWRITING else EntryStartMode.TEXT,
                )
            }
            waitForIdle()
            if (name.contains("pull-to-create")) {
                val before = blocks.map { it.id }.toSet()
                onNodeWithTag("entry_page_creation").performScrollTo()
                waitForIdle()
                onNodeWithTag("entry_capture_tools").performTouchInput {
                    down(center)
                    moveBy(Offset(0f, -240f), delayMillis = 400)
                    up()
                }
                waitForIdle()
                onNodeWithTag("entry_mode_focus").assertExists()
                assertEquals(before.size + 1, blocks.size)
                val created = blocks.single { it.id !in before }
                if (name.contains("ink-default")) {
                    assertIs<HandwritingBlockUiState>(created)
                    onNodeWithTag("handwriting_canvas").assertExists()
                } else {
                    assertIs<TextBlockUiState>(created)
                    onNodeWithTag("editor_text_input").assertExists()
                }
            }
            if (name.contains("pull-to-memories")) {
                onNodeWithTag("entry_page_scroll").performTouchInput { swipeDown() }
                waitForIdle()
                onNodeWithTag("entry_day_browser").assertExists()
            }
            File("build/reports/entry-page/$name.png").apply {
                parentFile.mkdirs()
                writeBytes(Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
            }
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> =
            listOf(
                arrayOf("phone-overview", 360, 800),
                arrayOf("phone-memories", 360, 800),
                arrayOf("phone-focus", 360, 800),
                arrayOf("phone-pull-to-create", 360, 800),
                arrayOf("phone-ink-default-pull-to-create", 360, 800),
                arrayOf("phone-pull-to-memories", 360, 800),
                arrayOf("phone-dark-overview", 360, 800),
                arrayOf("tablet-overview", 1280, 800),
                arrayOf("book-overview", 1000, 800),
                arrayOf("book-focus", 1000, 800),
                arrayOf("tabletop-focus", 1000, 800),
            )
    }
}
