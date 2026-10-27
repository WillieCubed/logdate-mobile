package app.logdate.feature.editor.ui.content

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.feature.editor.ui.MainEditorContent
import app.logdate.feature.editor.ui.editor.BlockType
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class EntryStartEditorTest {
    @Test
    fun `opted in real editor starts with the writing surface`() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            setContent {
                LogDateTheme(dynamicColor = false) {
                    MainEditorContent(
                        uiState =
                            BlocksUiState(
                                availableJournals = emptyList(),
                                selectedJournalIds = emptyList(),
                                onBlockFocused = {},
                                onJournalSelectionChanged = {},
                                onUpdateBlock = {},
                                onCreateBlock = { _: BlockType, id -> TextBlockUiState(id = id) },
                                onDeleteBlock = {},
                            ),
                        shouldReturnToPickerOnBack = false,
                        onDismissExpanded = {},
                        expandAnySelectedBlock = true,
                    )
                }
            }
            onNodeWithTag("entry_start_writing").assertIsDisplayed()
        }
}
