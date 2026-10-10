package app.logdate.client

import app.logdate.feature.editor.ui.editor.DraftState
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class EditorHandoffTest {
    @Test
    fun `an empty editor hands off as a new entry`() {
        assertEquals(EditorHandoff.NewEntry, editorHandoffFor(EditorState()))
    }

    @Test
    fun `an editor with a draft hands off that draft`() {
        val draftId = Uuid.random()
        val state =
            EditorState(blocks = listOf(TextBlockUiState(content = "Halfway up the trail")), draftState = DraftState.Active(draftId))

        assertEquals(EditorHandoff.Draft(draftId), editorHandoffFor(state))
    }

    @Test
    fun `writing that is not saved as a draft yet cannot be handed off`() {
        val state = EditorState(blocks = listOf(TextBlockUiState(content = "Halfway up the trail")))

        assertNull(editorHandoffFor(state))
    }

    @Test
    fun `only a valid draft id continues a handed-off entry`() {
        val draftId = Uuid.random()

        assertEquals(draftId, handoffDraftId(draftId.toString()))
        assertNull(handoffDraftId("not-a-draft"))
        assertNull(handoffDraftId(null))
    }
}
