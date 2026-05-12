package app.logdate.client.data.quota

import app.logdate.client.repository.quota.QuotaResult
import app.logdate.client.repository.quota.RemoteQuotaDataSource
import app.logdate.shared.model.QuotaUsage

/**
 * Quota data source for targets without authenticated quota API wiring.
 */
class UnavailableRemoteQuotaDataSource : RemoteQuotaDataSource {
    override suspend fun getQuotaUsage(): QuotaResult<QuotaUsage> = QuotaResult.Error(UNAVAILABLE_MESSAGE)

    override suspend fun refreshQuotaUsage(): QuotaResult<QuotaUsage> = QuotaResult.Error(UNAVAILABLE_MESSAGE)

    private companion object {
        const val UNAVAILABLE_MESSAGE = "Remote quota usage is unavailable on this target."
    }
}
