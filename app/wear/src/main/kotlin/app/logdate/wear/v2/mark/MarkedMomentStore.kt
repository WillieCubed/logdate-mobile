package app.logdate.wear.v2.mark

import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.dao.sync.PeerDeliveryDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.database.entities.sync.PeerDeliveryEntity
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** The delivery ledger values a mark waiting for the phone is recorded with; the watch's sync router reads them. */
object MarkDelivery {
    const val PEER_SCOPE = "phone"
    const val DIRECTION = "TO_PHONE"
    const val STATE_WAITING = "WAITING"

    /** Marks go only to a phone that lists `moments.v1` in its state document. */
    const val REASON_PEER_LACKS_MOMENTS = "PEER_LACKS_MOMENTS"
}

/** Saves a watch capture, its cloud operation and its phone delivery in one database transaction. */
class MarkedMomentStore(
    private val markedMomentDao: MarkedMomentDao,
    private val peerDeliveryDao: PeerDeliveryDao,
    private val syncMetadataService: SyncMetadataService,
    private val clock: Clock = Clock.System,
    private val transactions: SyncTransactionManager,
) {
    /** Saves a new capture once; replay never replaces an existing resolution or tombstone. */
    suspend fun create(mark: MarkedMomentEntity) =
        transactions.withTransaction {
            if (markedMomentDao.getById(mark.id) != null) return@withTransaction
            val stored = mark.copy(capturedAt = mark.capturedAt.milliseconds(), lastUpdated = mark.lastUpdated.milliseconds())
            markedMomentDao.upsert(stored)
            syncMetadataService.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.CREATE)
            recordWaiting(stored)
        }

    /** Fills missing sensor readings without replacing a resolution received while sensors ran. */
    suspend fun update(mark: MarkedMomentEntity): MarkedMomentEntity? =
        transactions.withTransaction {
            val current = markedMomentDao.getById(mark.id) ?: return@withTransaction null
            if (current.deletedAt != null ||
                current.state !in knownStates ||
                current.capturedAt.milliseconds() != mark.capturedAt.milliseconds() ||
                current.originDeviceId != mark.originDeviceId
            ) {
                return@withTransaction current
            }
            val addLocation =
                current.latitude == null && current.longitude == null && mark.latitude != null && mark.longitude != null
            val sensed =
                current.copy(
                    latitude = if (addLocation) mark.latitude else current.latitude,
                    longitude = if (addLocation) mark.longitude else current.longitude,
                    locationAccuracy = if (addLocation) mark.locationAccuracy else current.locationAccuracy,
                    heartRateBpm = current.heartRateBpm ?: mark.heartRateBpm,
                )
            if (sensed == current) return@withTransaction current
            val stored =
                sensed.copy(
                    lastUpdated =
                        maxOf(
                            mark.lastUpdated.milliseconds(),
                            current.lastUpdated.milliseconds() + 1.milliseconds,
                        ),
                )
            markedMomentDao.upsert(stored)
            syncMetadataService.enqueuePending(mark.id.toString(), EntityType.MOMENT, PendingOperation.UPDATE)
            recordWaiting(stored)
            stored
        }

    private fun Instant.milliseconds(): Instant = Instant.fromEpochMilliseconds(toEpochMilliseconds())

    private val knownStates = setOf(MarkedMomentState.OPEN, MarkedMomentState.DISMISSED, MarkedMomentState.FILLED)

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
