package app.logdate.ui.creation

import kotlin.test.Test
import kotlin.test.assertEquals

class EntryCreationPhaseTest {
    @Test
    fun dockUsesTheLastPartOfTheSameRouteProgressInBothDirections() {
        assertEquals(0f, entryCreationBarProgress(0f))
        assertEquals(0f, entryCreationBarProgress(0.55f))
        assertEquals(0.5f, entryCreationBarProgress(0.775f), 0.0001f)
        assertEquals(1f, entryCreationBarProgress(1f))
        assertEquals(0f, entryCreationBarProgress(-1f))
        assertEquals(1f, entryCreationBarProgress(2f))
    }
}
