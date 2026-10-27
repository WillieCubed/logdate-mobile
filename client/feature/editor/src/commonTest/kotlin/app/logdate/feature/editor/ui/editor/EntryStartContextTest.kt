package app.logdate.feature.editor.ui.editor

import app.logdate.client.repository.journals.JournalNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class EntryStartContextTest {
    @Test
    fun `earlier notes remain context when the editor state is copied`() {
        val now = Clock.System.now()
        val note = JournalNote.Text(content = "An earlier moment", creationTimestamp = now, lastUpdated = now)
        val state = EditorState(todayNotes = listOf(note)).copy(isLoading = false)
        assertEquals(listOf(note), state.todayNotes)
        assertTrue(state.blocks.isEmpty())
        assertFalse(state.isDirty)
        assertTrue(state.canExitWithoutSaving)
    }
}
