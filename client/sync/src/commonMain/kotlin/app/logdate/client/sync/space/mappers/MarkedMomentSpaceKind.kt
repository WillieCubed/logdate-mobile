package app.logdate.client.sync.space.mappers

import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.SyncableMarkedMomentRepository
import app.logdate.client.sync.conflict.ConflictResolution
import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.conflict.DeletionResolution
import app.logdate.client.sync.conflict.MarkedMomentConflictResolver
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.space.SpaceKind
import app.logdate.client.sync.space.SpaceLocalRecords
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Marked moments as a kind of the personal space: one `app.logdate.journal.markedMoment` per mark.
 *
 * Two devices that changed a mark meet in [MarkedMomentConflictResolver]: its state only moves
 * forward, the notes that filled it only grow, and the newer of the other fields wins. A mark
 * deleted here is deleted for good, because a mark is a reminder, not something to bring back.
 *
 * @param marks the stored marks, written without queueing another upload.
 */
fun markedMomentSpaceKind(
    marks: SyncableMarkedMomentRepository,
    resolver: MarkedMomentConflictResolver = MarkedMomentConflictResolver(),
): SpaceKind<MarkedMoment> =
    SpaceKind.single(EntityType.MOMENT, MarkedMomentRecordMapper, StoredMarkedMoments(marks, resolver)) { mark, version ->
        mark.copy(syncVersion = version)
    }

private class StoredMarkedMoments(
    private val marks: SyncableMarkedMomentRepository,
    resolver: MarkedMomentConflictResolver,
) : SpaceLocalRecords<MarkedMoment> {
    override val conflictResolver: ConflictResolver<MarkedMoment> =
        object : ConflictResolver<MarkedMoment> {
            override fun resolve(
                local: MarkedMoment,
                remote: MarkedMoment,
                localTimestamp: Instant,
                remoteTimestamp: Instant,
            ): ConflictResolution<MarkedMoment> {
                val merged = resolver.merge(local, remote)
                return when {
                    merged.sameContent(remote) -> ConflictResolution.KeepRemote(remote)
                    merged.sameContent(local) -> ConflictResolution.KeepLocal(local)
                    else -> ConflictResolution.Merge(merged)
                }
            }

            override fun resolveDeletion(
                remote: MarkedMoment,
                remoteTimestamp: Instant,
            ): DeletionResolution = DeletionResolution.DELETE
        }

    override suspend fun load(entityId: String): MarkedMoment? = Uuid.parseOrNull(entityId)?.let { marks.getMark(it) }

    override fun lastUpdatedOf(item: MarkedMoment): Instant = item.lastUpdated

    override suspend fun acknowledgeUpload(
        item: MarkedMoment,
        seq: Long,
        syncedAt: Instant,
    ) {
        marks.applyFromSync(item.copy(syncVersion = seq))
    }

    override suspend fun replaceLocal(item: MarkedMoment) {
        // A mark deleted here has a deletion waiting to upload, and stays deleted.
        marks.applyFromSync(item)
    }

    private fun MarkedMoment.sameContent(other: MarkedMoment) = copy(syncVersion = 0) == other.copy(syncVersion = 0)
}
