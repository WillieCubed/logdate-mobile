package app.logdate.client.sync.metadata

/** Whether a queued change may go straight to the cloud from this device now. */
fun interface CloudEligibility {
    suspend fun isEligible(
        entityType: EntityType,
        pending: PendingUpload,
    ): Boolean
}

/**
 * The outbox as a linked device's upload pass sees it: changes its router keeps away from the cloud,
 * such as one its phone will relay, are left out of [getPendingUploads] and nothing else. They still
 * count as pending, so downloads never overwrite them and the status still shows them.
 */
class RoutedSyncMetadataService(
    private val delegate: SyncMetadataService,
    private val eligibleForCloud: CloudEligibility,
) : SyncMetadataService by delegate {
    override suspend fun getPendingUploads(entityType: EntityType): List<PendingUpload> =
        delegate.getPendingUploads(entityType).filter { eligibleForCloud.isEligible(entityType, it) }
}
