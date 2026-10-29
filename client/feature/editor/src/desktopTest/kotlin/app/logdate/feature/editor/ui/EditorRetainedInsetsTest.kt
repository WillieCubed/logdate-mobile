package app.logdate.feature.editor.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.logdate.client.datastore.EntryStartMode
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.handwriting.EntryPageChrome
import app.logdate.feature.editor.ui.handwriting.EntryPageOverview
import app.logdate.feature.editor.ui.handwriting.EntryPageOverviewState
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.ui.theme.LogDateTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class EditorRetainedInsetsTest(
    private val direction: LayoutDirection,
    private val left: Int,
    private val right: Int,
) {
    @Test
    fun retainedWritingRespectsPhysicalCutoutEdgesInBothDirections() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            val block = TextBlockUiState(content = "Retained writing clear of the cutout")
            val editor = EditorState(blocks = listOf(block), expandedBlockId = block.id)
            val page = EntryPageOverviewState(false)
            val ui =
                BlocksUiState(
                    blocks = editor.blocks,
                    expandedBlockId = block.id,
                    availableJournals = emptyList(),
                    selectedJournalIds = emptyList(),
                    onBlockFocused = {},
                    onJournalSelectionChanged = {},
                    onUpdateBlock = {},
                    onCreateBlock = { _, _ -> block },
                    onDeleteBlock = {},
                )
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    LogDateTheme(dynamicColor = false) {
                        EntryPageChrome(
                            editor,
                            page,
                            EntryStartMode.TEXT,
                            {},
                            {},
                            {},
                            safeInsets = WindowInsets(left, 24, right, 16),
                            header = {},
                        ) {
                            MainEditorContent(
                                ui,
                                false,
                                {},
                                expandAnySelectedBlock = true,
                                floatingChrome = true,
                                overviewContent = { EntryPageOverview(it, emptyList(), page, EntryStartMode.TEXT) },
                            )
                        }
                    }
                }
            }
            val field = onNodeWithTag("editor_text_input").getUnclippedBoundsInRoot()
            assertEquals(left.dp, field.left)
            assertEquals((400 - right).dp, field.right)
            assertEquals(88.dp, field.top)
            assertEquals(784.dp, field.bottom)
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}-left={1}-right={2}")
        fun cases(): List<Array<Any>> =
            listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).flatMap { listOf(arrayOf(it, 72, 8), arrayOf(it, 8, 72)) }
    }
}
