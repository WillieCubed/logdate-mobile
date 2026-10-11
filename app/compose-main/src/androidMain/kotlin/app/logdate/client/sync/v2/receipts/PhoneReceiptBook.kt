package app.logdate.client.sync.v2.receipts

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.v2.CloudPresence
import app.logdate.client.sync.datalayer.v2.Receipt
import app.logdate.client.sync.datalayer.v2.ReceiptQuery
import app.logdate.client.sync.datalayer.v2.ReceiptState
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.peer.delivery.DeliveryDirection
import app.logdate.client.sync.peer.delivery.PeerDeliveryLedger
import app.logdate.client.sync.peer.delivery.PeerScopes
import kotlin.uuid.Uuid

/**
 * What the phone has of each record a watch sent, as a receipt. A note is backed up only when the phone
 * is signed in, the note has a server version, and no change to it is waiting in the outbox.
 */
class PhoneReceiptBook(
    private val notes: JournalNotesRepository,
    private val outbox: SyncMetadataService,
    private val sessions: SessionStorage,
    private val ledger: PeerDeliveryLedger,
) {
    fun cloudPresence(): CloudPresence = if (sessions.getSession() == null) CloudPresence.NO_ACCOUNT else CloudPresence.SIGNED_IN

    fun accountScope(): Pair<String, String>? = sessions.getOriginBoundSession()?.let { it.origin to it.session.accountId }

    /** Null when the phone has no copy of that version yet, so the watch sends it again. */
    suspend fun receiptFor(query: ReceiptQuery): Receipt? {
        val session = sessions.getOriginBoundSession()
        val id = Uuid.parseOrNull(query.id)
        if (query.kind != RecordKind.NOTE || id == null) return rejected(query.kind, query.id)
        val note = notes.getNoteById(id)
        if (note == null) {
            val received = ledger.get(PeerScopes.WATCHES, DeliveryDirection.FROM_WATCH, EntityType.NOTE, query.id)
            if (sessions.getOriginBoundSession() != session) return null
            return received?.takeIf { it.contentVersion >= query.contentVersion }?.let {
                Receipt(RecordKind.NOTE, query.id, it.contentVersion, ReceiptState.DELETED_ON_PHONE)
            }
        }
        val version = note.lastUpdated.toEpochMilliseconds()
        if (version < query.contentVersion) return null
        val backedUp = cloudPresence() == CloudPresence.SIGNED_IN && note.syncVersion > 0 && !outbox.hasPending(EntityType.NOTE, query.id)
        if (sessions.getOriginBoundSession() != session) return null
        return Receipt(
            kind = RecordKind.NOTE,
            id = query.id,
            contentVersion = version,
            state = if (backedUp) ReceiptState.BACKED_UP else ReceiptState.ON_PHONE,
            serverVersion = note.syncVersion.takeIf { backedUp },
        )
    }

    companion object {
        /** A rejection names the highest version, so it covers every version of the record. */
        const val ANY_VERSION = Long.MAX_VALUE

        fun rejected(
            kind: RecordKind,
            id: String,
        ): Receipt = Receipt(kind, id, ANY_VERSION, ReceiptState.REJECTED_UNSUPPORTED)
    }
}
