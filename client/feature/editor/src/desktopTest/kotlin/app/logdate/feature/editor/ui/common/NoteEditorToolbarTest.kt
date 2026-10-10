package app.logdate.feature.editor.ui.common

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class NoteEditorToolbarTest {
    @Test
    fun `screen readers hear the save and drafts buttons by name`() =
        runSkikoComposeUiTest(size = Size(390f, 780f)) {
            setContent {
                LogDateTheme(dynamicColor = false) { NoteEditorToolbar({}, {}, {}, draftCount = 1) }
            }
            onNodeWithTag(LOGDATE_EDITOR_SAVE_BUTTON_TAG).assertContentDescriptionEquals("Save entry")
            onNodeWithTag(LOGDATE_EDITOR_DRAFTS_BUTTON_TAG).assertContentDescriptionEquals(LOAD_DRAFTS)
        }

    private companion object {
        const val LOAD_DRAFTS = "Load drafts"
    }
}
