package app.logdate.wear.sync.v2.delivery

import app.logdate.client.sync.SyncResult

class WatchCaptureDelivery(
    private val notes: WatchNoteDelivery,
    private val moments: WatchMomentDelivery,
) : WatchNotesV2 {
    override suspend fun deliverIfActive(): SyncResult? {
        val note = notes.deliverIfActive() ?: return null
        val moment = moments.deliverIfActive() ?: return note
        return SyncResult(
            success = note.success && moment.success,
            uploadedItems = note.uploadedItems + moment.uploadedItems,
            downloadedItems = note.downloadedItems + moment.downloadedItems,
            conflictsResolved = note.conflictsResolved + moment.conflictsResolved,
            errors = note.errors + moment.errors,
            lastSyncTime = listOfNotNull(note.lastSyncTime, moment.lastSyncTime).maxOrNull(),
            hasMorePending = note.hasMorePending || moment.hasMorePending,
            remoteInventoryComplete = note.remoteInventoryComplete && moment.remoteInventoryComplete,
        )
    }

    suspend fun schedule(): NoteSchedule {
        val note = notes.schedule()
        if (note is NoteSchedule.Outbox) return note
        val moment = moments.schedule()
        return NoteSchedule.Ledger(listOfNotNull((note as NoteSchedule.Ledger).dueAt, (moment as? NoteSchedule.Ledger)?.dueAt).minOrNull())
    }
}
