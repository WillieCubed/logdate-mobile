package app.logdate.client.sync.v2.receipts

import app.logdate.client.sync.WatchMessageSender
import app.logdate.client.sync.WatchNoteAcknowledger
import app.logdate.client.sync.datalayer.v2.Receipt
import app.logdate.client.sync.datalayer.v2.ReceiptQuery
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.ReceiptsResponse
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.DeliveryState
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PeerScopes
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

/** Tells the watches that a record they sent cannot be read here, so they stop sending it. */
fun interface RecordRejection {
    suspend fun reject(
        kind: RecordKind,
        id: String,
    )
}

/**
 * Pushes receipts to the watches as soon as something changes: a note arriving, the phone's outbox
 * draining, a record refused. Pushes are best effort; the watch also asks with a request.
 */
class WatchReceiptNotifier(
    private val book: PhoneReceiptBook,
    private val ledger: PeerDeliveryLedger,
    private val sender: WatchMessageSender,
    private val active: suspend () -> Boolean,
) : RecordRejection {
    suspend fun push(
        noteId: String,
        contentVersion: Long,
    ) {
        if (!active()) return
        val accountScope = book.accountScope()
        val receipt = book.receiptFor(ReceiptQuery(RecordKind.NOTE, noteId, contentVersion)) ?: return
        send(listOf(receipt), accountScope)
    }

    override suspend fun reject(
        kind: RecordKind,
        id: String,
    ) {
        if (!active()) return
        send(listOf(PhoneReceiptBook.rejected(kind, id)), book.accountScope())
    }

    /** Pushes a backed-up receipt for each watch note that was only on the phone until now; returns how many. */
    suspend fun pushBackedUp(): Int {
        if (!active()) return 0
        val accountScope = book.accountScope()
        val backedUp =
            ledger.inState(PeerScopes.WATCHES, DeliveryDirection.FROM_WATCH, DeliveryState.ON_PHONE).mapNotNull { entry ->
                val receipt = book.receiptFor(ReceiptQuery(RecordKind.NOTE, entry.entityId, entry.contentVersion))
                if (receipt?.state == ReceiptState.BACKED_UP) entry to receipt else null
            }
        if (book.accountScope() != accountScope || !active()) return 0
        if (backedUp.isEmpty()) return 0
        backedUp.forEach { (entry, _) -> ledger.moveTo(entry, DeliveryState.BACKED_UP) }
        send(backedUp.map { it.second }, accountScope)
        return backedUp.size
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
                    Napier.e("Could not tell the watches which of their notes are backed up; they ask on their next sync", e)
                }
            }
        }

    private suspend fun send(
        receipts: List<Receipt>,
        accountScope: Pair<String, String>?,
    ) {
        if (!active() || book.accountScope() != accountScope) return
        val response = ReceiptsResponse(cloud = book.cloudPresence(), receipts = receipts)
        val bytes = WearJson.encodeToBytes(ReceiptsResponse.serializer(), response)
        if (!sender.send(WearPaths.RECEIPTS_PUSH, bytes)) {
            Napier.w("No watch took the phone's ${receipts.size} receipt(s); each watch asks for them on its next sync")
        }
    }

    private companion object {
        const val SETTLE_MS = 2_000L
    }
}

/** The version 2 acknowledgement: the note is recorded as received from the watch and an on-phone receipt goes out. */
class ReceiptAcknowledger(
    private val ledger: PeerDeliveryLedger,
    private val receipts: WatchReceiptNotifier,
) : WatchNoteAcknowledger {
    override suspend fun acknowledge(
        noteId: Uuid,
        lastUpdated: Instant,
    ) {
        val version = lastUpdated.toEpochMilliseconds()
        val entry = ledger.track(PeerScopes.WATCHES, DeliveryDirection.FROM_WATCH, EntityType.NOTE, noteId.toString(), version)
        if (entry.state == DeliveryState.NEW) ledger.moveTo(entry, DeliveryState.ON_PHONE)
        receipts.push(noteId.toString(), version)
    }
}
