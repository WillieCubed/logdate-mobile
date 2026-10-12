package app.logdate.client.sync.v2.receipts

import app.logdate.client.database.dao.sync.PeerDeliveryDao
import app.logdate.client.database.entities.sync.PeerDeliveryEntity
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordEnvelope
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.CaptureIdentity
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.DeliveryState
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** A removal and its anti-resurrection barrier share the phone's data transaction. */
class WatchCaptureRemovals(
    private val notes: SyncableJournalNotesRepository,
    private val transactions: SyncTransactionManager,
    private val dao: PeerDeliveryDao,
    private val peers: PhoneDeliveryPeers,
    private val receipts: WatchReceiptNotifier,
    private val journals: JournalContentRepository,
    private val outbox: SyncMetadataService,
    private val onUpdated: () -> Unit = {},
) {
    suspend fun remove(
        peer: PeerDeliveryContext,
        envelope: RecordEnvelope,
    ) {
        if (!envelope.removed ||
            envelope.context != peer ||
            envelope.kind != RecordKind.NOTE ||
            envelope.origin != RecordOrigin.WATCH ||
            envelope.contentVersion != 0L ||
            envelope.syncVersion < 0 ||
            envelope.body != null ||
            envelope.bodyRef != null ||
            envelope.media.isNotEmpty()
        ) {
            return
        }
        val id = Uuid.parseOrNull(envelope.id)?.takeIf { it.toString() == envelope.id } ?: return
        val identity = envelope.operationId?.let { CaptureIdentity.of(envelope.id, it) } ?: return
        if (!peers.isCurrent(peer)) return
        transactions.withTransaction {
            requireCurrent(peer)
            if (!isRemoved(peer, id)) {
                notes.deleteFromSync(id)
                journals.removeContentFromAllJournals(id)
                outbox.enqueuePending(envelope.id, EntityType.NOTE, PendingOperation.DELETE)
            }
            requireCurrent(peer)
            remember(removalScope(peer), envelope.id, Long.MAX_VALUE)
            remember(peer.ledgerScope, identity.removalLedgerKey, 0)
            requireCurrent(peer)
        }
        onUpdated()
        receipts.push(peer, envelope.id, 0, identity.operationId, removed = true)
    }

    suspend fun isRemoved(
        peer: PeerDeliveryContext,
        id: Uuid,
    ): Boolean =
        dao.get(removalScope(peer), DeliveryDirection.FROM_WATCH.name, EntityType.NOTE.name, id.toString())?.state ==
            DeliveryState.DELETED_ON_PHONE.name

    private suspend fun remember(
        scope: String,
        key: String,
        version: Long,
    ) {
        dao.retainNewest(
            PeerDeliveryEntity(
                peerScope = scope,
                direction = DeliveryDirection.FROM_WATCH.name,
                entityType = EntityType.NOTE.name,
                entityId = key,
                contentVersion = version,
                state = DeliveryState.DELETED_ON_PHONE.name,
                stateChangedAt = Clock.System.now().toEpochMilliseconds(),
            ),
        )
    }

    private suspend fun requireCurrent(peer: PeerDeliveryContext) {
        if (!peers.isCurrent(peer)) throw StaleWatchCapture()
    }

    companion object {
        fun removalScope(peer: PeerDeliveryContext): String = "${peer.ledgerScope}/removed"
    }
}
