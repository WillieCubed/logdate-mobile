package app.logdate.feature.journals.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class JournalSelectionNavigationTest {
    private object Caller : NavKey

    private val journalA = Uuid.parse("00000000-0000-0000-0000-000000000701")
    private val journalB = Uuid.parse("00000000-0000-0000-0000-000000000702")
    private val noteA = Uuid.parse("00000000-0000-0000-0000-000000000703")
    private val noteB = Uuid.parse("00000000-0000-0000-0000-000000000704")

    @Test
    fun selectingAnotherJournalKeepsTheRetainedCollectionAdjacentAndBackReturnsToIt() {
        val stack = mutableListOf<NavKey>(Caller, JournalsOverviewRoute, JournalDetailsRoute(journalA))
        stack.selectJournal(journalB)
        assertEquals(listOf<NavKey>(Caller, JournalsOverviewRoute, JournalDetailsRoute(journalB)), stack)
        stack.removeAt(stack.lastIndex)
        assertEquals(listOf<NavKey>(Caller, JournalsOverviewRoute), stack)
    }

    @Test
    fun firstJournalSelectionPreservesTheCaller() {
        val stack = mutableListOf<NavKey>(Caller, JournalsOverviewRoute)
        stack.selectJournal(journalA)
        assertEquals(listOf<NavKey>(Caller, JournalsOverviewRoute, JournalDetailsRoute(journalA)), stack)
    }

    @Test
    fun selectingTheSameJournalPreservesItsVisitAndMergeNotice() {
        val selected = JournalDetailsRoute(journalA.toString(), mergedSourceTitle = "Earlier field notes")
        val stack = mutableListOf<NavKey>(Caller, JournalsOverviewRoute, selected)
        stack.selectJournal(journalA)
        assertEquals(listOf<NavKey>(Caller, JournalsOverviewRoute, selected), stack)
    }

    @Test
    fun journalNavigationOutsideTheCollectionPreservesPurposefulReadingHistory() {
        val stack = mutableListOf<NavKey>(Caller, JournalDetailsRoute(journalA))
        stack.selectJournal(journalB)
        assertEquals(listOf<NavKey>(Caller, JournalDetailsRoute(journalA), JournalDetailsRoute(journalB)), stack)
    }

    @Test
    fun selectingAnotherJournalNoteKeepsItsJournalAdjacentAndBackReturnsToIt() {
        val stack = mutableListOf<NavKey>(Caller, JournalsOverviewRoute, JournalDetailsRoute(journalA), journalNote(noteA, journalA))
        stack.selectJournalNote(noteB, journalA)
        assertEquals(listOf<NavKey>(Caller, JournalsOverviewRoute, JournalDetailsRoute(journalA), journalNote(noteB, journalA)), stack)
        stack.removeAt(stack.lastIndex)
        assertEquals(listOf<NavKey>(Caller, JournalsOverviewRoute, JournalDetailsRoute(journalA)), stack)
    }

    @Test
    fun firstNoteSelectionAttachesItsJournalContextWithoutDiscardingTheCaller() {
        val stack = mutableListOf<NavKey>(Caller, JournalDetailsRoute(journalA))
        stack.selectJournalNote(noteA, journalA)
        assertEquals(listOf<NavKey>(Caller, JournalDetailsRoute(journalA), journalNote(noteA, journalA)), stack)
    }

    @Test
    fun selectingTheSameJournalNoteDoesNotCreateAnotherVisit() {
        val stack = mutableListOf<NavKey>(Caller, JournalDetailsRoute(journalA), journalNote(noteA, journalA))
        stack.selectJournalNote(noteA, journalA)
        assertEquals(listOf<NavKey>(Caller, JournalDetailsRoute(journalA), journalNote(noteA, journalA)), stack)
    }

    @Test
    fun journalNoteSelectionDoesNotRewriteAnExternalNoteVisit() {
        val external = NoteDetailRoute(noteA)
        val stack = mutableListOf<NavKey>(Caller, external)
        stack.selectJournalNote(noteB, journalA)
        assertEquals(listOf<NavKey>(Caller, external, journalNote(noteB, journalA)), stack)
    }

    @Test
    fun selectingANoteForAnotherJournalPreservesTheExistingContext() {
        val stack = mutableListOf<NavKey>(Caller, JournalDetailsRoute(journalA), journalNote(noteA, journalA))
        stack.selectJournalNote(noteB, journalB)
        assertEquals(
            listOf<NavKey>(Caller, JournalDetailsRoute(journalA), journalNote(noteA, journalA), journalNote(noteB, journalB)),
            stack,
        )
    }

    private fun journalNote(
        noteId: Uuid,
        journalId: Uuid,
    ) = NoteDetailRoute(noteId.toString(), journalId.toString())
}
