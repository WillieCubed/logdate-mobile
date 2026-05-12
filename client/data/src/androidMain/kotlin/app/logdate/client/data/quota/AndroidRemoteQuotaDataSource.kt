package app.logdate.client.data.quota

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.networking.QuotaApiClientContract
import app.logdate.client.repository.quota.QuotaResult
import app.logdate.client.repository.quota.RemoteQuotaDataSource
import app.logdate.shared.model.QuotaUsage

/**
 * Android [RemoteQuotaDataSource] backed by the authenticated LogDate quota endpoint.
 */
class AndroidRemoteQuotaDataSource(
    private val apiClient: QuotaApiClientContract,
    private val sessionStorage: SessionStorage,
) : RemoteQuotaDataSource {
    override suspend fun getQuotaUsage(): QuotaResult<QuotaUsage> = fetch()

    override suspend fun refreshQuotaUsage(): QuotaResult<QuotaUsage> = fetch()

    private suspend fun fetch(): QuotaResult<QuotaUsage> {
        val token =
            sessionStorage.getSession()?.accessToken
                ?: return QuotaResult.Error("Not signed in")
        return apiClient.getQuotaUsage(token).fold(
            onSuccess = { QuotaResult.Success(it) },
            onFailure = { QuotaResult.Error(it.message ?: "Quota request failed", it) },
        )
    }
}
