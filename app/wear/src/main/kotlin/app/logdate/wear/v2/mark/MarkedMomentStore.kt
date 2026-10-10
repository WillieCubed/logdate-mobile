package app.logdate.wear.v2.mark

import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.dao.sync.PeerDeliveryDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.sync.PeerDeliveryEntity
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import kotlin.time.Clock

/** The delivery ledger values a mark waiting for the phone is recorded with; the watch's sync router reads them. */
object MarkDelivery {
    const val PEER_SCOPE = "phone"
    const val DIRECTION = "TO_PHONE"
    const val STATE_WAITING = "WAITING"

    /** Marks go only to a phone that lists `moments.v1` in its state document. */
    const val REASON_PEER_LACKS_MOMENTS = "PEER_LACKS_MOMENTS"
}

/**
 * Saves the moments marked on this watch and queues each one for backup: the outbox gets a MOMENT
 * change, and the delivery ledger records that the mark waits for a phone that takes marks. The
 * mark is written first, so a crash between the writes can only leave a mark that is not queued
 * yet, never a queued change without its mark.
 */
class MarkedMomentStore(
    private val markedMomentDao: MarkedMomentDao,
    private val peerDeliveryDao: PeerDeliveryDao,
    private val syncMetadataService: SyncMetadataService,
    private val clock: Clock = Clock.System,
) {
    /** Saves a new mark and queues its creation. */
    suspend fun create(mark: MarkedMomentEntity) {
        markedMomentDao.upsert(mark)
        syncMetadataService.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE)
        recordWaiting(mark)
    }

    /** Saves a change to a mark, such as its place arriving, and queues it. */
    suspend fun update(mark: MarkedMomentEntity) {
        markedMomentDao.upsert(mark)
        syncMetadataService.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.UPDATE)
        recordWaiting(mark)
    }

    private suspend fun recordWaiting(mark: MarkedMomentEntity) {
        peerDeliveryDao.upsert(
            PeerDeliveryEntity(
                peerScope = MarkDelivery.PEER_SCOPE,
                direction = MarkDelivery.DIRECTION,
                entityType = EntityType.MOMENT.name,
                entityId = mark.id.toString(),
                contentVersion = mark.lastUpdated.toEpochMilliseconds(),
                state = MarkDelivery.STATE_WAITING,
                stateChangedAt = clock.now().toEpochMilliseconds(),
                holdReason = MarkDelivery.REASON_PEER_LACKS_MOMENTS,
            ),
        )
    }
}
