package app.logdate.feature.editor.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import app.logdate.feature.editor.ui.blocks.MemoryBlockSurface
import app.logdate.feature.editor.ui.common.LOGDATE_EDITOR_DRAFTS_BUTTON_TAG
import app.logdate.feature.editor.ui.common.LOGDATE_EDITOR_SAVE_BUTTON_TAG
import app.logdate.feature.editor.ui.common.NoteEditorToolbar
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.layout.LocalEditorRecordingActive
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class RecordingOptionsVisibilityTest {
    @Test
    fun `recording dismisses toolbar options and restores them closed`() =
        runSkikoComposeUiTest(size = Size(390f, 780f)) {
            val recording = mutableStateOf(false)
            setContent {
                LogDateTheme(dynamicColor = false) {
                    NoteEditorToolbar({}, {}, {}, draftCount = 1, optionsVisible = !recording.value)
                }
            }
            onNodeWithContentDescription("More options").performClick()
            onNodeWithText("Manage drafts").assertIsDisplayed()
            runOnIdle { recording.value = true }
            waitForIdle()
            onNodeWithContentDescription("More options").assertDoesNotExist()
            onNodeWithText("Manage drafts").assertDoesNotExist()
            onNodeWithTag(LOGDATE_EDITOR_DRAFTS_BUTTON_TAG).assertDoesNotExist()
            onNodeWithTag(LOGDATE_EDITOR_SAVE_BUTTON_TAG).assertIsDisplayed()
            onNodeWithContentDescription("Back").assertIsDisplayed()
            runOnIdle { recording.value = false }
            waitForIdle()
            onNodeWithContentDescription("More options").assertIsDisplayed()
            onNodeWithText("Manage drafts").assertDoesNotExist()
        }

    @Test
    fun `recording dismisses block options across the entry and restores them closed`() =
        runSkikoComposeUiTest(size = Size(390f, 780f)) {
            val recording = mutableStateOf(false)
            val block = TextBlockUiState(content = "Earlier memory")
            setContent {
                LogDateTheme(dynamicColor = false) {
                    CompositionLocalProvider(LocalEditorRecordingActive provides recording.value) {
                        MemoryBlockSurface(block, false, {}, {}, {}) { Text(block.content) }
                    }
                }
            }
            onNodeWithTag("block_menu_${block.id}").performClick()
            onNodeWithText("Remove from entry").assertIsDisplayed()
            runOnIdle { recording.value = true }
            waitForIdle()
            onNodeWithTag("block_menu_${block.id}").assertDoesNotExist()
            onNodeWithText("Remove from entry").assertDoesNotExist()
            onNodeWithText(block.content).assertIsDisplayed()
            runOnIdle { recording.value = false }
            waitForIdle()
            onNodeWithTag("block_menu_${block.id}").assertIsDisplayed()
            onNodeWithText("Remove from entry").assertDoesNotExist()
        }
}
