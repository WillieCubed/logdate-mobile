package app.logdate.client.data.account

import app.logdate.client.database.dao.HistoryOwnerAdoptionDao
import app.logdate.client.database.dao.sync.SyncMetadataDao
import app.logdate.client.sync.peer.link.OwnerRowsMover
import app.logdate.client.sync.peer.link.OwnerScope
import io.github.aakira.napier.Napier

/**
 * Moves what a device recorded before it was linked to the account it is linked to: its queued uploads
 * and download cursors, which may change servers too, and the history rows [HistoryOwnerAdoption] moves
 * when a phone signs in. Every step can run again safely, so a stopped move is finished by running it again.
 *
 * History rows stay with their server; location history does not sync on a linked watch.
 */
class OwnerAdoptionMover(
    private val syncMetadataDao: SyncMetadataDao,
    private val historyOwnerAdoptionDao: HistoryOwnerAdoptionDao,
) : OwnerRowsMover {
    override suspend fun move(
        from: OwnerScope,
        to: OwnerScope,
        deviceId: String,
    ): Int {
        val leftBehind = syncMetadataDao.reassignPending(from.ownerId, from.origin, to.ownerId, to.origin)
        syncMetadataDao.reassignCursors(from.ownerId, from.origin, to.ownerId, to.origin)
        historyOwnerAdoptionDao.adopt(from.ownerId, to.ownerId, from.origin, deviceId) { it.rewriteHistoryOwner(to.ownerId) }
        if (leftBehind > 0) {
            Napier.w("$leftBehind queued change(s) stayed with the earlier owner because the account already queues the same records")
        }
        return leftBehind
    }
}
