package app.logdate.client.sync

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.shared.model.CloudAccountRepository
import io.github.aakira.napier.Napier

/**
 * Runs a server call, and if it comes back 401, refreshes the access token and tries once more.
 *
 * Every upload and download call goes through here. They used to reuse a token captured once at
 * the start of a run, so an hour-old session made them fail with 401 and nothing refreshed it. A
 * full sync downloads before it uploads, so that one rejection failed the whole run -- the device
 * looked signed in and simply stopped backing anything up.
 */
internal class SyncTokenRefresher(
    private val sessionStorage: SessionStorage,
    private val cloudAccountRepository: CloudAccountRepository,
) {
    suspend fun <T> withFreshToken(
        operation: suspend (accessToken: String) -> Result<T>,
        operationName: String,
    ): Result<T> {
        val currentSession = sessionStorage.getSession()
        if (currentSession == null) {
            Napier.w("No active session for $operationName")
            return Result.failure(
                CloudApiException("NO_SESSION", "No active session available", statusCode = 401),
            )
        }

        // Try initial operation
        val initialResult = operation(currentSession.accessToken)
        if (initialResult.isSuccess) {
            return initialResult
        }

        // Check if error is 401
        val exception = initialResult.exceptionOrNull() as? CloudApiException
        if (exception?.statusCode != 401) {
            return initialResult // Not a token error, return as-is
        }

        // Token expired, attempt refresh
        Napier.i("Token expired (401) during $operationName, attempting refresh")
        val refreshResult = cloudAccountRepository.refreshAccessToken(currentSession.refreshToken)
        if (refreshResult.isFailure) {
            Napier.e("Token refresh failed: ${refreshResult.exceptionOrNull()}")
            return initialResult // Return original error if refresh fails
        }

        // Refresh succeeded, retry operation with new token
        val newToken = refreshResult.getOrNull()
        if (newToken == null) {
            Napier.w("Token refresh succeeded but returned null")
            return initialResult
        }

        // Keep the refreshed token. The account repository persists it under its own storage key,
        // which the session storage never reads, so without this the session keeps handing out the
        // token the server just rejected and every single request pays a 401 and a refresh before
        // it does any work.
        sessionStorage.saveSession(currentSession.copy(accessToken = newToken))

        Napier.d("Token refreshed successfully, retrying $operationName")
        return operation(newToken)
    }
}
