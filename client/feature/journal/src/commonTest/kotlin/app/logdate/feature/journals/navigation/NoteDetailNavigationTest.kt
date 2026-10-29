package app.logdate.feature.journals.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class NoteDetailNavigationTest {
    @Test
    fun repeatedEntryTapsDoNotStackTheSameDestination() {
        val note = Uuid.random()
        val stack = mutableListOf<NavKey>()
        repeat(5) { index ->
            if (index % 2 == 0) stack.navigateToNoteDetail(note) else stack.navigateToNoteDetail(note.toString())
        }
        assertEquals(listOf<NavKey>(NoteDetailRoute(note)), stack)
    }

    @Test
    fun closingThenReopeningTheEntryCreatesANewVisit() {
        val note = Uuid.random()
        val stack = mutableListOf<NavKey>()
        stack.navigateToNoteDetail(note)
        stack.removeAt(stack.lastIndex)
        stack.navigateToNoteDetail(note)
        assertEquals(listOf<NavKey>(NoteDetailRoute(note)), stack)
    }
}
