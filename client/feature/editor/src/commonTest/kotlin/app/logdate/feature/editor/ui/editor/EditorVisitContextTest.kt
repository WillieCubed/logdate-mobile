package app.logdate.feature.editor.ui.editor

import app.logdate.shared.model.location.VisitMemoryContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Instant

class EditorVisitContextTest {
    private val visit = VisitMemoryContext("observation", "cafe", "Mothership Coffee", 36.1, -115.1, Instant.parse("2026-09-28T16:00:00Z"))

    @Test
    fun `ordinary editor updates keep the visit context`() {
        val original = EditorState(visitContext = visit)
        assertEquals(visit, original.copy(isModified = true).visitContext)
    }

    @Test
    fun `changing visit context invalidates the durable draft fingerprint`() {
        assertNotEquals(
            getEditorDraftFingerprint(EditorState(visitContext = visit)),
            getEditorDraftFingerprint(EditorState(visitContext = visit.copy(evidenceId = "another-visit"))),
        )
    }

    @Test
    fun `visit context participates in state equality`() {
        assertNotEquals(EditorState(visitContext = visit), EditorState())
    }
}
