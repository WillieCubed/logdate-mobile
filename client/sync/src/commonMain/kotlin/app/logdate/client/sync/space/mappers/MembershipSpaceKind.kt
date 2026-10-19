package app.logdate.client.sync.space.mappers

import app.logdate.client.repository.journals.SyncableJournalContentRepository
import app.logdate.client.sync.conflict.ConflictResolution
import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.conflict.DeletionResolution
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.space.SpaceItemChanges
import app.logdate.client.sync.space.SpaceKind
import app.logdate.client.sync.space.SpaceLocalRecords
import app.logdate.client.sync.space.SpacePart
import app.logdate.client.sync.space.SpacePartChange
import app.logdate.client.sync.space.canonicalRecordKey
import kotlinx.coroutines.flow.first
import kotlin.time.Instant

/**
 * Journal memberships as a kind of the personal space: one `app.logdate.journal.membership` record
 * for each entry in a journal.
 *
 * The outbox names a membership `<journal id>::<entry id>`, which is what [SpaceKind] calls the
 * item's id here; its record key is `<journal id>:<entry id>`.
 *
 * Not registered in the personal space yet: the membership download has to read staged space
 * records first (see `SpaceRecordRegistry.WIRED_ENTITY_TYPES`).
 *
 * @param content the stored memberships, written without queueing another upload.
 */
fun membershipSpaceKind(content: SyncableJournalContentRepository): SpaceKind<JournalMembership> {
    val mapper = MembershipRecordMapper()
    val main =
        SpacePart<JournalMembership, JournalMembership>(
            mapper = mapper,
            recordsOf = { listOf(it) },
            ownerOf = ::pendingIdOf,
            rkeysOf = { entityId -> listOf(rkeyOfPendingId(entityId)) },
        )
    return SpaceKind(
        entityType = EntityType.ASSOCIATION,
        main = main,
        local = StoredMemberships(content),
        withSyncVersion = { membership, version -> membership.copy(syncVersion = version) },
        isItemId = { AssociationPendingKey.fromPendingId(it) != null },
    ) { changes: SpaceItemChanges<JournalMembership>, _ ->
        (changes.of(main).lastOrNull() as? SpacePartChange.Written)?.value
    }
}

private fun pendingIdOf(rkey: String): String {
    val parts = canonicalRecordKey(rkey).split(':')
    require(parts.size == 2) { "A membership key names a journal and an entry" }
    return "${parts[0]}::${parts[1]}"
}

private fun rkeyOfPendingId(entityId: String): String {
    val key = requireNotNull(AssociationPendingKey.fromPendingId(entityId)) { "An association names a journal and an entry" }
    return MembershipRecordMapper.rkeyFor(key.journalId, key.contentId)
}

private val UNKNOWN_TIME = Instant.fromEpochMilliseconds(0)

/**
 * Two copies of a membership are the same fact. When both recorded when the entry was added, the
 * earlier time is the one every device settles on; a copy that recorded none takes the other's.
 */
private class EarliestMembership : ConflictResolver<JournalMembership> {
    override fun resolve(
        local: JournalMembership,
        remote: JournalMembership,
        localTimestamp: Instant,
        remoteTimestamp: Instant,
    ): ConflictResolution<JournalMembership> {
        val earliest = listOfNotNull(local.createdAt, remote.createdAt).minOrNull()
        return if (earliest == local.createdAt) ConflictResolution.KeepLocal(local) else ConflictResolution.KeepRemote(remote)
    }

    /** A membership has no edits to keep, so removing it here removes it everywhere. */
    override fun resolveDeletion(
        remote: JournalMembership,
        remoteTimestamp: Instant,
    ): DeletionResolution = DeletionResolution.DELETE
}

private class StoredMemberships(
    private val content: SyncableJournalContentRepository,
) : SpaceLocalRecords<JournalMembership> {
    override val conflictResolver: ConflictResolver<JournalMembership> = EarliestMembership()

    override suspend fun load(entityId: String): JournalMembership? {
        val key = AssociationPendingKey.fromPendingId(entityId) ?: return null
        val journals = content.observeJournalsForContent(key.contentId).first()
        if (journals.none { it.id == key.journalId }) return null
        return JournalMembership(key.journalId, key.contentId)
    }

    override fun lastUpdatedOf(item: JournalMembership): Instant = item.createdAt ?: UNKNOWN_TIME

    override suspend fun acknowledgeUpload(
        item: JournalMembership,
        seq: Long,
        syncedAt: Instant,
    ) = Unit

    override suspend fun replaceLocal(item: JournalMembership) {
        content.addContentToJournalFromSync(item.entryId, item.journalId)
    }
}
