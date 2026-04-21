package app.logdate.ui.common.transitions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.uuid.Uuid

/**
 * Ensures that the generation of transition keys for UI animations is consistent
 * and unique.
 *
 * This suite verifies that shared element transition keys, particularly for the note
 * viewer, are correctly derived from entity IDs to prevent animation conflicts
 * between different pieces of content.
 */
class TransitionKeysTest {
    @Test
    fun `note viewer transition includes note id`() {
        val noteId = Uuid.parse("9a84eb26-76db-46fe-8cc3-440d66e6546a")

        assertEquals("note-viewer-$noteId", TransitionKeys.noteViewerTransition(noteId))
    }

    @Test
    fun `note viewer transitions stay distinct per note`() {
        val firstNoteId = Uuid.parse("29db2994-e5aa-4dce-a502-aee9c1ec0e2e")
        val secondNoteId = Uuid.parse("8d77ff2e-8ece-4bfc-a65a-c3c7a66f312a")

        assertNotEquals(
            TransitionKeys.noteViewerTransition(firstNoteId),
            TransitionKeys.noteViewerTransition(secondNoteId),
        )
    }
}
