package app.logdate.client.sync.moments

import app.logdate.client.repository.moments.SyncableMarkedMomentRepository
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.cloud.CloudMomentDataSource

/** Moment sync stays held until the rollout flag and selected server capability permit it. */
class MomentSyncConfig(
    val cloud: CloudMomentDataSource,
    val repository: SyncableMarkedMomentRepository,
    val enabled: suspend () -> Boolean,
)

internal fun DefaultSyncManager.createMomentDownloader(config: MomentSyncConfig): MomentDownloader =
    MomentDownloader(
        config,
        syncMetadataService,
        transactionManager,
        requireNotNull(downloadInbox) { "Moment sync requires a durable download inbox" },
        tokenRefresher,
        unreadableCloudRecordStore,
        statusPublisher::handleCloudApiError,
        statusPublisher::handleSyncException,
        space,
    )

internal fun DefaultSyncManager.createMomentUploader(config: MomentSyncConfig): MomentUploader =
    MomentUploader(
        config,
        syncMetadataService,
        transactionManager,
        tokenRefresher,
        retryCoordinator,
        profile,
        {
            app.logdate.shared.model.sync.DeviceId(
                requireNotNull(deviceIdProvider) {
                    "Moment uploads require a device identity"
                }.getDeviceId().value.toString(),
            )
        },
        requireNotNull(downloadInbox) { "Moment uploads require a durable download inbox" },
        space,
    )
