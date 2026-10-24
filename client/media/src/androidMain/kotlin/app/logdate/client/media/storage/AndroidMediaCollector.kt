package app.logdate.client.media.storage

/** Collects journaled candidates only while publication and owner mutations are fenced out. */
class AndroidMediaCollector(
    private val fence: MediaClaimFence,
    private val census: MediaOwnerCensus,
    private val references: StoredMediaReferences,
    private val journal: AndroidMediaReclamationJournal,
    private val tickets: AndroidMediaPublicationTickets,
    private val store: AndroidCanonicalMediaStore,
) {
    suspend fun collect(): Int =
        fence.withExclusiveReclamation {
            val liveReferences = census.references().mapTo(mutableSetOf(), references::storedReference)
            val pendingTickets = tickets.pending()
            check(pendingTickets.all { it.reference != null }) { "An unresolved media publication ticket blocks collection" }
            val ticketReferences = pendingTickets.mapTo(mutableSetOf()) { references.storedReference(requireNotNull(it.reference)) }

            pendingTickets
                .filter { references.storedReference(requireNotNull(it.reference)) in liveReferences }
                .forEach { ticket -> tickets.acknowledge(ticket.id, requireNotNull(ticket.reference)) }

            var deleted = 0
            journal.pendingCandidates().forEach { candidate ->
                val canonical = references.storedReference(candidate)
                if (canonical in liveReferences || canonical in ticketReferences) {
                    // A claim can be released after this scan. Keep the durable candidate so a
                    // later collection can reclaim it once its final owner disappears.
                    return@forEach
                } else if (store.deleteOwned(candidate)) {
                    journal.acknowledgeCandidate(candidate)
                    deleted++
                }
            }
            deleted
        }
}
