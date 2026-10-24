package app.logdate.client.data.restore

import kotlin.uuid.Uuid

internal data class RetainedJournalMembership(
    val journalId: Uuid,
    val noteId: Uuid,
    val deleted: Boolean,
)

/** Legacy rows supply links only when the canonical table has no row for that pair. */
internal fun retainedJournalMemberships(
    canonical: List<RetainedJournalMembership>,
    legacy: List<RetainedJournalMembership>,
): Set<Pair<Uuid, Uuid>> {
    val canonicalPairs = canonical.mapTo(mutableSetOf()) { it.journalId to it.noteId }
    return buildSet {
        canonical.filterNot { it.deleted }.forEach { add(it.journalId to it.noteId) }
        legacy.filterNot { (it.journalId to it.noteId) in canonicalPairs }.forEach { add(it.journalId to it.noteId) }
    }
}
