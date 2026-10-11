package app.logdate.client.sync.v2.receipts

import app.logdate.client.database.dao.sync.PeerDeliveryDao
import app.logdate.client.database.entities.sync.PeerDeliveryEntity
import app.logdate.client.repository.journals.JournalNote

/** Capture origin is independent of delivery audience and survives a link being replaced. */
class WatchCaptureProvenance(
    private val dao: PeerDeliveryDao,
) {
    suspend fun remember(note: JournalNote) {
        dao.retainNewest(
            PeerDeliveryEntity(
                peerScope = SCOPE,
                direction = "FROM_WATCH",
                entityType = "NOTE",
                entityId = note.uid.toString(),
                contentVersion = note.lastUpdated.toEpochMilliseconds(),
                state = "NEW",
                stateChangedAt = note.lastUpdated.toEpochMilliseconds(),
            ),
        )
    }

    companion object {
        const val SCOPE = "watch_capture_origin"
    }
}
