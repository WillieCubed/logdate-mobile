package app.logdate.wear.sync.v2.delivery

import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.metadata.CloudEligibility
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.CaptureIdentity
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.DeliveryState
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

/** The exact captures a single delivery run releases to the watch's cloud engine. */
data class CloudCapture(
    val pending: PendingUpload,
    val contentVersion: Long,
)

class ReleasedToCloud(
    private val ledger: PeerDeliveryLedger,
    private val peers: WatchDeliveryPeer,
    private val outbox: SyncMetadataService,
) : CloudEligibility {
    private data class Release(
        val peer: PeerDeliveryContext,
        val captures: List<CloudCapture>,
    )

    private val runLock = Mutex()
    private val activeRun = AtomicReference<Release?>(null)

    suspend fun <T> withCaptures(
        peer: PeerDeliveryContext,
        captures: List<CloudCapture>,
        block: suspend () -> T,
    ): T =
        runLock.withLock {
            val run = Release(peer, captures.toList())
            activeRun.set(run)
            try {
                block()
            } finally {
                activeRun.compareAndSet(run, null)
            }
        }

    override suspend fun isEligible(
        entityType: EntityType,
        pending: PendingUpload,
    ): Boolean {
        if (entityType != EntityType.NOTE || pending.operation == PendingOperation.UPDATE) return false
        val run = activeRun.get() ?: return false
        val peer = run.peer
        if (peer.mode != LinkMode.CLOUD || !peer.isValid() || !peers.isCurrent(peer)) return false
        val scope = pending.scope ?: return false
        if (scope.ownerId != peer.accountId || scope.serverOrigin != peer.serverOrigin || pending.operationId.isNullOrBlank()) return false
        val capture = run.captures.firstOrNull { sameOperation(it.pending, pending) } ?: return false
        val identity = CaptureIdentity.of(pending.entityId, pending.operationId) ?: return false
        val key = if (pending.operation == PendingOperation.DELETE) identity.removalLedgerKey else identity.ledgerKey
        val entry = ledger.get(peer.ledgerScope, DeliveryDirection.TO_PHONE, EntityType.NOTE, key) ?: return false
        if (entry.state != DeliveryState.CLOUD_PENDING ||
            entry.holdReason != null ||
            entry.contentVersion != capture.contentVersion
        ) {
            return false
        }
        if (!outbox.isCurrentOperation(EntityType.NOTE, pending)) return false
        return peers.isCurrent(peer) && activeRun.get() === run
    }

    private fun sameOperation(
        captured: PendingUpload,
        candidate: PendingUpload,
    ): Boolean =
        captured.entityId == candidate.entityId &&
            captured.scope == candidate.scope &&
            captured.operationId == candidate.operationId &&
            captured.operation == candidate.operation
}
