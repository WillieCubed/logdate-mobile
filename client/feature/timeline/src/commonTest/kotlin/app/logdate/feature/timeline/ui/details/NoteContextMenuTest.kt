package app.logdate.feature.timeline.ui.details

import app.logdate.ui.common.EditorWindows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class NoteContextMenuTest {
    private val noteId = Uuid.parse("b6bb58a1-7ac6-4d96-96aa-a565b2c92314")

    @Test
    fun `an entry offers nothing where LogDate cannot open another window`() {
        assertEquals(emptyList(), noteContextMenuItems(noteId, windows = null, openInNewWindowLabel = "Open in new window"))
    }

    @Test
    fun `open in new window opens that entry in its own window`() {
        val opened = mutableListOf<String>()
        val windows =
            object : EditorWindows {
                override fun openNewEntry(journalId: String?) = Unit

                override fun openEntry(entryId: String) {
                    opened += entryId
                }
            }

        val items = noteContextMenuItems(noteId, windows, openInNewWindowLabel = "Open in new window")
        items.single().onClick()

        assertEquals("Open in new window", items.single().label)
        assertEquals(listOf(noteId.toString()), opened)
    }
}
