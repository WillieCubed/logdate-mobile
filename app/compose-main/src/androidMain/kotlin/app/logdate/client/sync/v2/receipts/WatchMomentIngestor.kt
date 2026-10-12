package app.logdate.client.sync.v2.receipts

import app.logdate.client.database.dao.moments.MarkedMomentDao
import app.logdate.client.database.dao.sync.PeerDeliveryDao
import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.database.entities.sync.PeerDeliveryEntity
import app.logdate.client.repository.moments.SyncableMarkedMomentRepository
import app.logdate.client.repository.moments.atMillisecondPrecision
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.CaptureIdentity
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.MomentEnvelopeBody
import io.github.aakira.napier.Napier
import kotlin.time.Clock

class WatchMomentIngestor(
    private val moments: SyncableMarkedMomentRepository,
    private val marks: MarkedMomentDao,
    private val deliveries: PeerDeliveryDao,
    private val outbox: SyncMetadataService,
    private val transactions: SyncTransactionManager,
    private val peers: PhoneDeliveryPeers,
    private val receipts: WatchReceiptNotifier,
    private val accepts: suspend (PeerDeliveryContext) -> Boolean,
    private val onUpdated: () -> Unit,
    private val clock: Clock = Clock.System,
) {
    suspend fun onRecord(
        sourceNodeId: String?,
        path: String,
        data: Map<String, String>,
    ) {
        val peer = sourceNodeId?.let { peers.current(it) } ?: return
        if (!accepts(peer)) return
        val envelope = (EnvelopeDataMap.fromDataMap(data) as? Decoded.Supported)?.value ?: return
        val record = WearPaths.parseWatchRecord(path) ?: return
        if (envelope.kind != RecordKind.MOMENT ||
            envelope.origin != RecordOrigin.WATCH ||
            envelope.context != peer ||
            record.kind != RecordKind.MOMENT ||
            record.linkId != peer.linkId ||
            record.id.toString() != envelope.id ||
            record.operationId != envelope.operationId ||
            envelope.operationId == null
        ) {
            return
        }
        val identity = CaptureIdentity.of(envelope.id, envelope.operationId) ?: return
        val incoming =
            if (envelope.removed) {
                null
            } else {
                try {
                    MomentEnvelopeBody.decode(envelope).atMillisecondPrecision()
                } catch (_: IllegalArgumentException) {
                    Napier.w("The watch's marked-moment body cannot be applied")
                    receipts.reject(peer, RecordKind.MOMENT, envelope.id, identity.operationId)
                    return
                }
            }
        if (envelope.removed &&
            (
                envelope.schema != 1 ||
                    envelope.enc != null ||
                    envelope.contentVersion != 0L ||
                    envelope.syncVersion < 0 ||
                    envelope.body != null ||
                    envelope.bodyRef != null ||
                    envelope.media.isNotEmpty()
            )
        ) {
            return
        }
        var queued = false
        val accepted =
            transactions.withTransaction {
                requireCurrent(peer)
                val id = record.id
                val row = marks.getById(id)
                if (row != null && row.state !in knownStates) return@withTransaction false
                if (envelope.removed) {
                    if (row?.deletedAt == null) {
                        if (row == null) {
                            val now = clock.now()
                            marks.upsert(
                                MarkedMomentEntity(
                                    id = id,
                                    capturedAt = now,
                                    timeZoneId = "",
                                    state = MarkedMomentState.OPEN,
                                    originDeviceId = "",
                                    lastUpdated = now,
                                    deletedAt = now,
                                ),
                            )
                            outbox.enqueuePending(envelope.id, EntityType.MOMENT, PendingOperation.DELETE)
                        } else if (!moments.delete(id, clock.now())) {
                            return@withTransaction false
                        }
                        queued = true
                    }
                } else if (row?.deletedAt == null) {
                    val existing = moments.getMark(id)
                    val merged =
                        moments.applyFromSync(requireNotNull(incoming).copy(syncVersion = existing?.syncVersion ?: 0))
                            ?: return@withTransaction false
                    if (merged != existing) {
                        outbox.enqueuePending(
                            envelope.id,
                            EntityType.MOMENT,
                            if (existing == null) PendingOperation.CREATE else PendingOperation.UPDATE,
                        )
                        queued = true
                    }
                }
                requireCurrent(peer)
                deliveries.retainNewest(
                    PeerDeliveryEntity(
                        peerScope = peer.ledgerScope,
                        direction = "FROM_WATCH",
                        entityType = EntityType.MOMENT.name,
                        entityId = if (envelope.removed) identity.removalLedgerKey else identity.ledgerKey,
                        contentVersion = envelope.contentVersion,
                        state = if (envelope.removed || row?.deletedAt != null) "DELETED_ON_PHONE" else "ON_PHONE",
                        stateChangedAt = clock.now().toEpochMilliseconds(),
                    ),
                )
                requireCurrent(peer)
                true
            }
        if (!accepted) {
            receipts.reject(peer, RecordKind.MOMENT, envelope.id, identity.operationId)
            return
        }
        if (queued) onUpdated()
        receipts.push(peer, envelope.id, envelope.contentVersion, identity.operationId, envelope.removed, RecordKind.MOMENT)
    }

    private suspend fun requireCurrent(peer: PeerDeliveryContext) {
        if (!peers.isCurrent(peer) || !accepts(peer)) throw StaleWatchCapture()
    }

    private val knownStates = setOf(MarkedMomentState.OPEN, MarkedMomentState.DISMISSED, MarkedMomentState.FILLED)
}
