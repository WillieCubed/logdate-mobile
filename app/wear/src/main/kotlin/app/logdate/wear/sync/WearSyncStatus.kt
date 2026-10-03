package app.logdate.wear.sync

import app.logdate.client.sync.SyncStatus
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncMetadataService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The watch's sync status as it changes: whether a phone is in reach, how much is waiting, whether a
 * run is in progress, when the last one finished, and whether anything has been set aside.
 *
 * Every input is a flow, so a capture joining the outbox, an acknowledgement clearing it, a run
 * starting or finishing, an item being set aside, and the phone coming or going each change the
 * status as they happen.
 */
internal fun wearSyncStatus(
    running: Flow<Boolean>,
    syncMetadataService: SyncMetadataService,
    deadLetterStore: SyncDeadLetterStore,
    phoneReachability: PhoneReachability,
): Flow<SyncStatus> =
    combine(
        running,
        syncMetadataService.observePendingCount(),
        deadLetterStore.observe(),
        phoneReachability.observe(),
    ) { isSyncing, pendingUploads, deadLetters, phone ->
        SyncStatus(
            isEnabled = phone != null,
            lastSyncTime = syncMetadataService.getLastSyncTime(EntityType.NOTE),
            pendingUploads = pendingUploads,
            isSyncing = isSyncing,
            hasErrors = deadLetters.isNotEmpty(),
        )
    }
