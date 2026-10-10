package app.logdate.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class EditorHandoffTest {
    @Test
    fun `only a valid draft id continues a handed-off entry`() {
        val draftId = Uuid.random()

        assertEquals(draftId, handoffDraftId(draftId.toString()))
        assertNull(handoffDraftId("not-a-draft"))
        assertNull(handoffDraftId(null))
    }
}
