package app.logdate.feature.journals.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.uuid.Uuid

/** Changes the collection's selection without adding a second journal between it and its detail. */
fun MutableList<NavKey>.selectJournal(journalId: Uuid) {
    val selected = lastOrNull() as? JournalDetailsRoute
    if (selected != null && getOrNull(lastIndex - 1) == JournalsOverviewRoute) {
        if (selected.journalId != journalId.toString()) this[lastIndex] = JournalDetailsRoute(journalId)
    } else {
        add(JournalDetailsRoute(journalId))
    }
}

/** Replaces only the selected note belonging to the retained source journal. */
fun MutableList<NavKey>.selectJournalNote(
    noteId: Uuid,
    journalId: Uuid,
) {
    val selected = lastOrNull() as? NoteDetailRoute
    val source = getOrNull(lastIndex - 1) as? JournalDetailsRoute
    val journalKey = journalId.toString()
    val next = NoteDetailRoute(noteId.toString(), journalKey)
    if (selected?.journalId == journalKey && source?.journalId == journalKey) {
        if (selected.noteId != next.noteId) this[lastIndex] = next
    } else {
        add(next)
    }
}
