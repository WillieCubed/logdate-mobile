package app.logdate.client.repository.quota

import app.logdate.shared.model.QuotaUsage

/**
 * Result wrapper for quota operations.
 */
sealed class QuotaResult<out T> {
    data class Success<T>(val data: T) : QuotaResult<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : QuotaResult<Nothing>()
}

/**
 * Data source for fetching quota usage information from the server.
 */
interface RemoteQuotaDataSource {
    
    /**
     * Fetches the current quota usage for the authenticated user.
     * 
     * @return QuotaResult containing quota usage data or error information
     */
    suspend fun getQuotaUsage(): QuotaResult<QuotaUsage>
    
    /**
     * Refreshes quota usage data from the server.
     * This may trigger a recalculation on the server side.
     * 
     * @return QuotaResult containing updated quota usage data or error information
     */
    suspend fun refreshQuotaUsage(): QuotaResult<QuotaUsage>
}