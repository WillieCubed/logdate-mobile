package app.logdate.client.sync.v2.receipts

import app.logdate.client.sync.WatchNoteAcknowledger
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.Receipt
import app.logdate.client.sync.datalayer.v2.ReceiptQuery
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.ReceiptsResponse
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.peer.delivery.CaptureIdentity
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.DeliveryState
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** An unbound legacy ingestion callback; scoped ingestion supplies its authenticated audience. */
fun interface RecordRejection {
    suspend fun reject(
        kind: RecordKind,
        id: String,
    )
}

/** Best-effort receipt pushes for a verified link; failed deliveries remain available for polling. */
class WatchReceiptNotifier(
    private val book: PhoneReceiptBook,
    private val ledger: PeerDeliveryLedger,
    private val sender: WatchPeerMessageSender,
    private val active: suspend () -> Boolean,
    private val peers: PhoneDeliveryPeers,
) : RecordRejection {
    suspend fun isCurrent(context: PeerDeliveryContext): Boolean = active() && book.isCurrent(context)

    suspend fun push(
        context: PeerDeliveryContext,
        noteId: String,
        contentVersion: Long,
        operationId: String? = null,
    ): Boolean {
        if (!isCurrent(context)) return false
        val receipt = book.receiptFor(context, ReceiptQuery(RecordKind.NOTE, noteId, contentVersion, operationId)) ?: return false
        return send(context, listOf(receipt))
    }

    suspend fun reject(
        context: PeerDeliveryContext,
        kind: RecordKind,
        id: String,
        operationId: String? = null,
    ): Boolean = send(context, listOf(PhoneReceiptBook.rejected(kind, id, operationId)))

    override suspend fun reject(
        kind: RecordKind,
        id: String,
    ) {
        Napier.w("A record rejection without a verified watch audience cannot be sent")
    }

    /** Retains failed pushes for retry, with independent history for each current link. */
    suspend fun pushBackedUp(): Int {
        if (!active()) return 0
        var delivered = 0
        for (context in peers.all()) {
            if (!isCurrent(context)) continue
            val backedUp =
                ledger
                    .inState(context.ledgerScope, DeliveryDirection.FROM_WATCH, DeliveryState.ON_PHONE)
                    .filter { it.entityType == EntityType.NOTE }
                    .mapNotNull { entry ->
                        val identity = CaptureIdentity.fromLedgerKey(entry.entityId)
                        val query =
                            ReceiptQuery(RecordKind.NOTE, identity?.recordId ?: entry.entityId, entry.contentVersion, identity?.operationId)
                        val receipt = book.receiptFor(context, query)
                        if (receipt?.state == ReceiptState.BACKED_UP) entry to receipt else null
                    }
            if (backedUp.isEmpty() || !send(context, backedUp.map { it.second }) || !isCurrent(context)) continue
            backedUp.forEach { (entry, _) -> ledger.moveTo(entry, DeliveryState.BACKED_UP) }
            delivered += backedUp.size
        }
        return delivered
    }

    @OptIn(FlowPreview::class)
    fun start(
        scope: CoroutineScope,
        outboxChanges: Flow<Int>,
    ): Job =
        scope.launch {
            outboxChanges.debounce(SETTLE_MS).collect {
                try {
                    pushBackedUp()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Napier.e("Could not tell linked watches which notes are backed up; they ask on their next sync", e)
                }
            }
        }

    private suspend fun send(
        context: PeerDeliveryContext,
        receipts: List<Receipt>,
    ): Boolean {
        if (!isCurrent(context)) return false
        val response =
            ReceiptsResponse(
                cloud = if (context.mode == LinkMode.CLOUD) CloudPresence.SIGNED_IN else CloudPresence.NO_ACCOUNT,
                receipts = receipts,
                context = context,
            )
        val bytes = WearJson.encodeToBytes(ReceiptsResponse.serializer(), response)
        if (!isCurrent(context)) return false
        if (sender.send(context, WearPaths.RECEIPTS_PUSH, bytes)) return true
        Napier.w("The linked watch did not take ${receipts.size} receipt(s); it can ask on its next sync")
        return false
    }

    private companion object {
        const val SETTLE_MS = 2_000L
    }
}

/** Records storage under the link that supplied the capture, then pushes to that watch only. */
class ReceiptAcknowledger(
    private val ledger: PeerDeliveryLedger,
    private val receipts: WatchReceiptNotifier,
    private val context: PeerDeliveryContext? = null,
    private val operationId: String? = null,
) : WatchNoteAcknowledger {
    override suspend fun acknowledge(
        noteId: Uuid,
        lastUpdated: Instant,
    ) {
        val peer = context ?: return
        if (!receipts.isCurrent(peer)) return
        val version = lastUpdated.toEpochMilliseconds()
        val id = noteId.toString()
        val key = operationId?.let { CaptureIdentity.of(id, it)?.ledgerKey ?: return } ?: id
        val entry = ledger.track(peer.ledgerScope, DeliveryDirection.FROM_WATCH, EntityType.NOTE, key, version)
        if (entry.contentVersion != version || !receipts.isCurrent(peer)) return
        if (entry.state == DeliveryState.NEW) ledger.moveTo(entry, DeliveryState.ON_PHONE)
        receipts.push(peer, id, version, operationId)
    }
}
