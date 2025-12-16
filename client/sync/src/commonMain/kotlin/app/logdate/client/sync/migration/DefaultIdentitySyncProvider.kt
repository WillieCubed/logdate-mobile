package app.logdate.client.sync.migration

import app.logdate.client.device.identity.DeviceIdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

/**
 * Default implementation of IdentitySyncProvider that uses DeviceIdProvider.
 */
class DefaultIdentitySyncProvider(
    private val deviceIdProvider: DeviceIdProvider
) : IdentitySyncProvider {
    
    override fun getUserId(): Flow<Uuid> {
        return deviceIdProvider.getDeviceId()
    }
    
    override suspend fun syncIdentity(userId: Uuid): Flow<MigrationProgress> {
        // Create a dummy migration progress
        return deviceIdProvider.getDeviceId().map { currentId ->
            MigrationProgress(
                inProgress = false,
                oldId = currentId,
                newId = userId,
                itemsProcessed = 1,
                totalItems = 1
            )
        }
    }
}