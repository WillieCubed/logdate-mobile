package app.logdate.client.sync

import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService

/** Settle matching uploads, or bind newer transcript work to its successfully created record. */
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
        !metadata.isCurrentOperation(strategy.entityType, pending)
    ) {
        return false
    }
    if (!strategy.sameUploadedFields(local, remote)) {
        if (pending.operation != PendingOperation.CREATE ||
            strategy.syncVersionOf(local) != 0L ||
            strategy.syncVersionOf(remote) <= 0L ||
            !strategy.sameCreateBaseFields(local, remote) ||
            !metadata.bindCreateToServerVersion(strategy.entityType, pending, strategy.syncVersionOf(remote))
        ) {
            return false
        }
        strategy.acknowledgeUpload(local, remote)
        return true
    }
    if (!strategy.acknowledgeUpload(local, remote)) return false
    return metadata.settleIfCurrent(
        strategy.entityType,
        pending,
        strategy.lastUpdatedOf(remote),
        strategy.syncVersionOf(remote),
    )
}
