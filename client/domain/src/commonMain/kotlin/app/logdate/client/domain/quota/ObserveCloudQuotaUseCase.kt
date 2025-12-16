package app.logdate.client.domain.quota

import app.logdate.shared.model.CloudQuotaManager
import app.logdate.shared.model.CloudStorageQuota
import kotlinx.coroutines.flow.Flow

/**
 * Use case for observing cloud storage quota information.
 */
class ObserveCloudQuotaUseCase(
    private val quotaManager: CloudQuotaManager
) {
    
    /**
     * Observes quota changes in real-time.
     */
    operator fun invoke(): Flow<CloudStorageQuota> {
        return quotaManager.observeQuota()
    }
}