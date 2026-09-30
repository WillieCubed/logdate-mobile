package app.logdate.feature.editor.ui

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.common.NoteEditorToolbar
import app.logdate.feature.editor.ui.content.EditorBottomContent
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.layout.ImmersiveEditorLayout
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.shared.model.Journal
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import app.logdate.ui.foldable.FoldablePosture
import app.logdate.ui.foldable.provideFoldableLayoutInfo
import app.logdate.ui.theme.LogDateTheme
import org.jetbrains.skia.Image
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** Actual shared editor rendering with deterministic window dimensions and posture inputs. */
@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class FoldableEditorScreenshotsTest(
    private val name: String,
    private val width: Int,
    private val height: Int,
    private val posture: String,
) {
    @Test
    fun empty() = capture(false)

    @Test
    fun draft() = capture(true)

    private fun capture(withContent: Boolean) =
        runDesktopComposeUiTest(width = width, height = height) {
            val journal = Journal(title = "Life with Milo")
            val root =
                generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
                    .first { File(it, "app/android-main/src/debug/assets/sample_note_photo.jpg").exists() }
            val photo = File(root, "app/android-main/src/debug/assets/sample_note_photo.jpg")
            val blocks: List<EntryBlockUiState> =
                if (withContent) {
                    listOf(
                        TextBlockUiState(
                            content =
                                "Milo's first camping trip\n\n" +
                                    "He made it about ten minutes before deciding the blanket was the better adventure.",
                        ),
                        ImageBlockUiState(uri = photo.toURI().toString(), caption = "Our campsite, just before sunset"),
                    )
                } else {
                    emptyList()
                }
            setContent {
                provideFoldableLayoutInfo(layoutInfo()) {
                    LogDateTheme(dynamicColor = false, darkTheme = false) {
                        ImmersiveEditorLayout(
                            topBarContent = { NoteEditorToolbar({}, {}, {}) },
                            editorContent = {
                                MainEditorContent(
                                    uiState =
                                        BlocksUiState(
                                            blocks = blocks,
                                            availableJournals = listOf(journal),
                                            selectedJournalIds = listOf(journal.id),
                                            onBlockFocused = {},
                                            onJournalSelectionChanged = {},
                                            onUpdateBlock = {},
                                            onCreateBlock = { _, id -> TextBlockUiState(id = id) },
                                            onDeleteBlock = {},
                                        ),
                                    shouldReturnToPickerOnBack = false,
                                    onDismissExpanded = {},
                                )
                            },
                            bottomContent = {
                                EditorBottomContent(listOf(journal), listOf(journal.id), {}, false, {})
                            },
                        )
                    }
                }
            }
            waitForIdle()
            // Allow the local-file image request to finish before capturing the rendered surface.
            Thread.sleep(750)
            waitForIdle()
            val output = File("build/reports/editor-foldables/$name-${if (withContent) "draft" else "empty"}.png")
            output.parentFile.mkdirs()
            output.writeBytes(Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
        }

    private fun layoutInfo(): FoldableLayoutInfo {
        if (posture == "flat") return FoldableLayoutInfo()
        val vertical = posture == "book" || posture == "dual"
        val gap = if (posture == "dual") 24 else 0
        val left = if (vertical) (width - gap) / 2 else 0
        val top = if (vertical) 0 else (height - gap) / 2
        val right = if (vertical) left + gap else width
        val bottom = if (vertical) height else top + gap
        return FoldableLayoutInfo(
            isFoldable = true,
            posture = if (vertical) FoldablePosture.Book else FoldablePosture.Tabletop,
            hinge =
                FoldableHingeInfo(
                    orientation = if (vertical) FoldableHingeOrientation.Vertical else FoldableHingeOrientation.Horizontal,
                    state = FoldableHingeState.HalfOpened,
                    occlusionType = if (gap == 0) FoldableOcclusionType.None else FoldableOcclusionType.Full,
                    bounds = FoldableHingeBounds(left.dp, top.dp, right.dp, bottom.dp, (right - left).dp, (bottom - top).dp),
                    isSeparating = true,
                ),
        )
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> =
            listOf(
                arrayOf("cover", 360, 800, "flat"),
                arrayOf("unfolded-portrait", 840, 960, "flat"),
                arrayOf("unfolded-landscape", 1100, 720, "flat"),
                arrayOf("book", 840, 960, "book"),
                arrayOf("dual-screen", 1100, 800, "dual"),
                arrayOf("tabletop", 960, 840, "tabletop"),
                arrayOf("compact-tabletop", 720, 640, "tabletop"),
                arrayOf("split-window", 420, 720, "flat"),
            )
    }
}
