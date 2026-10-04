package app.logdate.client.sync

import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService

/** A lost upload response can be settled only by matching its exact current operation and fields. */
internal suspend fun <T : Any> acknowledgeUploadedOperation(
    strategy: DownloadStrategy<T>,
    local: T,
    remote: T,
    pending: PendingUpload?,
    metadata: SyncMetadataService,
): Boolean {
    if (pending == null ||
        pending.operation == PendingOperation.DELETE ||
        strategy.syncVersionOf(remote) < strategy.syncVersionOf(local) ||
        !strategy.sameUploadedFields(local, remote) ||
        !metadata.isCurrentOperation(strategy.entityType, pending) ||
        !strategy.acknowledgeUpload(local, remote)
    ) {
        return false
    }
    return metadata.settleIfCurrent(
        strategy.entityType,
        pending,
        strategy.lastUpdatedOf(remote),
        strategy.syncVersionOf(remote),
    )
}
