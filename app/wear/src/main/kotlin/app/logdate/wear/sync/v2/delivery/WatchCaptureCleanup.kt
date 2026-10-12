package app.logdate.wear.sync.v2.delivery

import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.CaptureIdentity
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlin.uuid.Uuid

/** Removes this exchange's data items only after their exact operations leave the outbox. */
class WatchCaptureCleanup(
    private val peers: WatchDeliveryPeer,
    private val outbox: SyncMetadataService,
    private val ledger: PeerDeliveryLedger,
) {
    suspend fun prune(
        peer: PeerDeliveryContext,
        transport: DataLayerTransportV2,
    ) {
        if (!peers.isCurrent(peer)) return
        val keep =
            outbox
                .getPendingUploads(EntityType.NOTE)
                .mapNotNull { pending ->
                    val identity =
                        CaptureIdentity.of(pending.entityId, pending.operationId ?: return@mapNotNull null)
                            ?: return@mapNotNull null
                    if (pending.operation == PendingOperation.DELETE) identity.removalLedgerKey else identity.ledgerKey
                }.toSet()
        val entries = ledger.observe(peer.ledgerScope, DeliveryDirection.TO_PHONE).first()
        for (entry in entries) {
            if (!peers.isCurrent(peer)) return
            if (entry.entityType != EntityType.NOTE || entry.entityId in keep) continue
            val identity = CaptureIdentity.fromLedgerKey(entry.entityId.removeSuffix(":remove")) ?: continue
            val id = Uuid.parseOrNull(identity.recordId) ?: continue
            try {
                if (transport.deleteOwn(WearPaths.watchRecord(RecordKind.NOTE, id, peer, identity.operationId)) && peers.isCurrent(peer)) {
                    ledger.forget(entry.scope, entry.direction, entry.entityType, entry.entityId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("Could not remove a completed watch data item; its delivery history is retained for another attempt", e)
            }
        }
    }
}
