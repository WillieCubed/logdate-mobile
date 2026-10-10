package app.logdate.server.logdate

import app.logdate.shared.model.sync.JournalMergeRequest
import app.logdate.shared.model.sync.JournalMergeResponse
import java.util.UUID

/** Applies permanent journal redirects and serializes canonical writes with merge work. */
internal class MergeAwareLogDateCollectionsRepository(
    private val delegate: LogDateCollectionsRepository,
    private val store: JournalMergeStore,
) : LogDateCollectionsRepository by delegate {
    private val mergeService = JournalMergeService(delegate, store)

    override suspend fun merge(
        userId: UUID,
        sourceId: String,
        request: JournalMergeRequest,
    ): JournalMergeResponse = mergeService.merge(userId, sourceId, request)

    override suspend fun journalMergeDestination(
        userId: UUID,
        id: String,
    ): String? = store.withAccountLock(userId) { resolveMergeDestination(id, store.operations(userId)).takeUnless { it == id } }

    override suspend fun upsertJournal(
        userId: UUID,
        journal: LogDateJournal,
    ): LogDateJournal =
        store.withAccountLock(userId) {
            rejectMergedJournal(userId, journal.id)
            delegate.upsertJournal(userId, journal)
        }

    override suspend fun createJournalIfAbsent(
        userId: UUID,
        journal: LogDateJournal,
    ): LogDateJournal? =
        store.withAccountLock(userId) {
            rejectMergedJournal(userId, journal.id)
            delegate.createJournalIfAbsent(userId, journal)
        }

    override suspend fun getJournal(
        userId: UUID,
        id: String,
    ): LogDateJournal? =
        store.withAccountLock(userId) {
            if (isMerged(userId, id)) null else delegate.getJournal(userId, id)
        }

    override suspend fun journalExists(
        userId: UUID,
        id: String,
    ): Boolean = getJournal(userId, id) != null

    override suspend fun listJournals(userId: UUID): List<LogDateJournal> =
        store.withAccountLock(userId) {
            val sources =
                store
                    .operations(userId)
                    .filter { it.started }
                    .map { it.sourceId }
                    .toSet()
            delegate.listJournals(userId).filterNot { it.id in sources }
        }

    override suspend fun deleteJournal(
        userId: UUID,
        id: String,
        deletedAt: Long,
    ) {
        store.withAccountLock(userId) {
            if (!isMerged(userId, id)) delegate.deleteJournal(userId, id, deletedAt)
        }
    }

    override suspend fun journalChanges(
        userId: UUID,
        since: Long,
        limit: Int,
    ): LogDateChangeSet<LogDateJournal, LogDateJournalDeletion> =
        store.withAccountLock(userId) {
            val operations = store.operations(userId)
            val sources = operations.filter { it.started }.map { it.sourceId }.toSet()
            val pageSize = limit.coerceAtLeast(1)
            val fetchSize = (pageSize.toLong() + sources.size).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val underlying = delegate.journalChanges(userId, since, fetchSize)
            val changes = underlying.changes.filterNot { it.id in sources }
            val deletions =
                underlying.deletions.filterNot { it.id in sources } +
                    operations.mapNotNull { operation ->
                        val version = operation.tombstoneVersion
                        if (!operation.completed || version == null || version <= since) return@mapNotNull null
                        LogDateJournalDeletion(
                            operation.sourceId,
                            checkNotNull(operation.deletedAt),
                            version,
                            resolveMergeDestination(operation.destinationId, operations),
                        )
                    }
            val ordered = (changes.map { it.version to it.id } + deletions.map { it.serverVersion to it.id }).sortedBy { it.first }
            val selected = ordered.take(pageSize)
            val ids = selected.map { it.second }.toSet()
            LogDateChangeSet(
                changes.filter { it.id in ids },
                deletions.filter { it.id in ids },
                selected.lastOrNull()?.first ?: underlying.lastTimestamp,
                underlying.hasMore || ordered.size > pageSize,
            )
        }

    override suspend fun upsertAssociations(
        userId: UUID,
        associations: List<LogDateAssociation>,
    ): List<LogDateAssociation> =
        store.withAccountLock(userId) {
            val operations = store.operations(userId)
            val redirected =
                associations
                    .map { association ->
                        val destination = resolveMergeDestination(association.journalId, operations)
                        if (destination != association.journalId && delegate.getJournal(userId, destination) == null) {
                            throw JournalMergeDestinationMissingException()
                        }
                        association.copy(journalId = destination)
                    }.distinctBy { it.journalId to it.entryId }
            retainPendingMemberships(userId, associations, operations)
            delegate.upsertAssociations(userId, redirected)
        }

    private suspend fun retainPendingMemberships(
        userId: UUID,
        associations: List<LogDateAssociation>,
        operations: List<JournalMergeOperation>,
    ) {
        val redirects = operations.filter { it.started }.associateBy { it.sourceId }
        val additions = mutableMapOf<String, MutableSet<String>>()
        associations.forEach { association ->
            var source = association.journalId
            while (true) {
                val operation = redirects[source] ?: break
                if (!operation.completed) {
                    additions.getOrPut(operation.operationId) { mutableSetOf() }.add(association.entryId)
                }
                source = operation.destinationId
            }
        }
        operations.forEach { operation ->
            val retained = additions[operation.operationId] ?: return@forEach
            store.save(userId, operation.copy(contentIds = (operation.contentIds + retained).distinct()))
        }
    }

    override suspend fun deleteAssociations(
        userId: UUID,
        associations: List<LogDateAssociationRef>,
        deletedAt: Long,
    ) {
        store.withAccountLock(userId) {
            val operations = store.operations(userId)
            val current = associations.filter { resolveMergeDestination(it.journalId, operations) == it.journalId }
            if (current.isNotEmpty()) delegate.deleteAssociations(userId, current, deletedAt)
        }
    }

    override suspend fun upsertEntry(
        userId: UUID,
        entry: LogDateEntry,
    ): LogDateEntry = store.withAccountLock(userId) { delegate.upsertEntry(userId, entry) }

    override suspend fun createEntryIfAbsent(
        userId: UUID,
        entry: LogDateEntry,
    ): LogDateEntry? = store.withAccountLock(userId) { delegate.createEntryIfAbsent(userId, entry) }

    override suspend fun deleteEntry(
        userId: UUID,
        id: String,
        deletedAt: Long,
    ) {
        store.withAccountLock(userId) { delegate.deleteEntry(userId, id, deletedAt) }
    }

    override suspend fun upsertDraft(
        userId: UUID,
        draft: LogDateDraft,
    ): LogDateDraft = store.withAccountLock(userId) { delegate.upsertDraft(userId, draft.redirected(store.operations(userId))) }

    override suspend fun getDraft(
        userId: UUID,
        id: String,
    ): LogDateDraft? = store.withAccountLock(userId) { delegate.getDraft(userId, id)?.redirected(store.operations(userId)) }

    override suspend fun draftChanges(
        userId: UUID,
        since: Long,
        limit: Int,
    ): LogDateChangeSet<LogDateDraft, LogDateDraftDeletion> =
        store.withAccountLock(userId) {
            val operations = store.operations(userId)
            delegate.draftChanges(userId, since, limit).let { page -> page.copy(changes = page.changes.map { it.redirected(operations) }) }
        }

    override suspend fun deleteDraft(
        userId: UUID,
        id: String,
        deletedAt: Long,
    ) {
        store.withAccountLock(userId) { delegate.deleteDraft(userId, id, deletedAt) }
    }

    override suspend fun purgeTombstones(
        userId: UUID,
        olderThan: Long,
    ): LogDateCollectionsPurgeResult =
        store.withAccountLock(userId) {
            delegate.purgeTombstones(userId, olderThan).also { store.purgeTombstones(userId, olderThan) }
        }

    override suspend fun purgeTombstones(olderThan: Long): LogDateCollectionsPurgeResult =
        delegate.purgeTombstones(olderThan).also { store.purgeTombstones(null, olderThan) }

    private fun LogDateDraft.redirected(operations: List<JournalMergeOperation>): LogDateDraft =
        copy(journalIds = journalIds.map { resolveMergeDestination(it, operations) }.distinct())

    private suspend fun rejectMergedJournal(
        userId: UUID,
        id: String,
    ) {
        val destination = resolveMergeDestination(id, store.operations(userId))
        if (destination != id) throw JournalMergedException(destination)
    }

    private suspend fun isMerged(
        userId: UUID,
        id: String,
    ): Boolean = resolveMergeDestination(id, store.operations(userId)) != id
}
