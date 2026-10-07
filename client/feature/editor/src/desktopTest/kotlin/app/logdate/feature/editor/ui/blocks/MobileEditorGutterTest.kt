package app.logdate.feature.editor.ui.blocks

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.MainEditorContent
import app.logdate.feature.editor.ui.VisitMemoryContextBanner
import app.logdate.feature.editor.ui.common.NoteEditorToolbar
import app.logdate.feature.editor.ui.content.EditorBottomContent
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.layout.ImmersiveEditorLayout
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.shared.model.Journal
import app.logdate.shared.model.location.VisitMemoryContext
import app.logdate.ui.theme.LogDateTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class MobileEditorGutterTest {
    @Test
    fun `new entry choices share the journal selector gutter`() = verifyGutter(false)

    @Test
    fun `visit context preserves the same gutter for the new entry`() = verifyGutter(true)

    private fun verifyGutter(showVisitContext: Boolean) =
        runSkikoComposeUiTest(size = Size(390f, 780f)) {
            val journal = Journal(title = "Personal")
            setContent {
                LogDateTheme(dynamicColor = false) {
                    ImmersiveEditorLayout(
                        topBarContent = { NoteEditorToolbar({}, {}, {}) },
                        bottomContent = { EditorBottomContent(listOf(journal), listOf(journal.id), {}, false, {}) },
                        editorContent = {
                            Column {
                                if (showVisitContext) {
                                    VisitMemoryContextBanner(
                                        VisitMemoryContext("visit", null, "Campus", 0.0, 0.0, Instant.parse("2026-10-06T08:00:00Z")),
                                    )
                                }
                                MainEditorContent(
                                    modifier = Modifier.weight(1f),
                                    uiState =
                                        BlocksUiState(
                                            availableJournals = listOf(journal),
                                            selectedJournalIds = listOf(journal.id),
                                            onBlockFocused = {},
                                            onJournalSelectionChanged = {},
                                            onUpdateBlock = {},
                                            onCreateBlock = { _, _ -> TextBlockUiState() },
                                            onDeleteBlock = {},
                                        ),
                                    shouldReturnToPickerOnBack = false,
                                    onDismissExpanded = {},
                                )
                            }
                        },
                    )
                }
            }
            waitForIdle()
            val directory = File(System.getProperty("logdate.review.screenshots", "/private/tmp/logdate-review-screenshots"))
            directory.mkdirs()
            File(directory, if (showVisitContext) "editor-visit-context-gutters.png" else "editor-new-entry-gutters.png").writeBytes(
                requireNotNull(Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData()).bytes,
            )
            val audio = onNodeWithTag("editor_start_audio_block").getUnclippedBoundsInRoot()
            val gallery = onNodeWithText("Gallery").getUnclippedBoundsInRoot()
            val journalBounds = onNodeWithText("Personal").getUnclippedBoundsInRoot()
            val back = onNodeWithContentDescription("Back").getUnclippedBoundsInRoot()
            val root = onRoot().getUnclippedBoundsInRoot()
            assertEquals(16.dp, back.top, "Toolbar visible surfaces need a consistent safe top margin")
            assertEquals(root.bottom - 16.dp, journalBounds.bottom, "Journal needs the same bottom margin")
            assertEquals(16.dp, journalBounds.top - gallery.bottom, "Major surfaces need a consistent vertical gap")
            assertEquals(16.dp, gallery.top - audio.bottom)
            if (!showVisitContext) assertEquals(16.dp, audio.top - back.bottom)
            assertEquals(journalBounds.right, audio.right, "The new-entry grid must share the journal selector's outer gutter")
        }
}
