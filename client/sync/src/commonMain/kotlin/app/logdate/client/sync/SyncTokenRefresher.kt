package app.logdate.client.sync

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.networking.PasskeyApiErrorCodes
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.CloudRequestBinding
import app.logdate.client.sync.cloud.CloudRequestLocationProvider
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.model.CloudAccountRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

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
    fun currentUploadScope(): UploadScope? = sessionStorage.getOriginBoundSession()?.let { UploadScope(it.session.accountId, it.origin) }

    suspend fun <T> withFreshToken(
        operation: suspend (accessToken: String) -> Result<T>,
        operationName: String,
        expectedScope: UploadScope? = null,
    ): Result<T> {
        val locationProvider = cloudAccountRepository as? CloudRequestLocationProvider
        val binding = captureBinding(locationProvider).getOrElse { return Result.failure(it) }
        val currentSession = binding?.session ?: sessionStorage.getSession()
        if (currentSession == null) {
            Napier.w("No active session for")
            return Result.failure(
                CloudApiException("NO_SESSION", "No active session available", statusCode = 401),
            )
        }

        if (expectedScope != null && !expectedScope.matches(binding, currentSession)) {
            return Result.failure(scopeChanged())
        }

        // Try initial operation
        val initialResult = runBound(binding) { operation(currentSession.accessToken) }
        initialResult.rethrowCancellation()
        if (binding != null && !isStillBound(binding, locationProvider)) return Result.failure(scopeChanged())
        if (initialResult.isSuccess) {
            return initialResult
        }

        // Check if error is 401
        val exception = initialResult.exceptionOrNull() as? CloudApiException
        if (exception?.statusCode != 401) {
            return initialResult // Not a token error, return as-is
        }

        return refreshAndRetry(operation, initialResult, currentSession, binding, locationProvider)
    }

    /**
     * Captures the request destination together with the session bound to it. Fails with
     * [scopeChanged] when the capture throws or the bound session belongs to another origin; succeeds
     * with null when the account repository has no location to bind to.
     */
    private fun captureBinding(locationProvider: CloudRequestLocationProvider?): Result<CloudRequestBinding?> {
        if (locationProvider == null) return Result.success(null)
        val location =
            try {
                locationProvider.captureLocation()
            } catch (
                cancelled: CancellationException,
            ) {
                throw cancelled
            } catch (_: Exception) {
                return Result.failure(scopeChanged())
            }
        val bound = sessionStorage.getOriginBoundSession()
        if (bound == null || bound.origin != location.origin || !locationProvider.isCurrentOrigin(location.origin)) {
            return Result.failure(scopeChanged())
        }
        return Result.success(CloudRequestBinding(location, bound.session))
    }

    private fun UploadScope.matches(
        binding: CloudRequestBinding?,
        session: UserSession,
    ): Boolean = binding != null && ownerId == session.accountId && serverOrigin == binding.location.origin

    private suspend fun <R> runBound(
        binding: CloudRequestBinding?,
        block: suspend () -> R,
    ): R =
        if (binding == null) {
            block()
        } else {
            withContext(binding) { block() }
        }

    private suspend fun <T> refreshAndRetry(
        operation: suspend (accessToken: String) -> Result<T>,
        initialResult: Result<T>,
        currentSession: UserSession,
        binding: CloudRequestBinding?,
        locationProvider: CloudRequestLocationProvider?,
    ): Result<T> {
        // Token expired, attempt refresh
        Napier.i("Token expired (401), attempting refresh")
        if (binding != null && !isStillBound(binding, locationProvider)) return Result.failure(scopeChanged())
        val refreshResult = runBound(binding) { cloudAccountRepository.refreshAccessToken(currentSession.refreshToken) }
        refreshResult.rethrowCancellation()
        if (refreshResult.isFailure) {
            val refreshError = refreshResult.exceptionOrNull()
            Napier.e("Token refresh failed")
            // Settings observes this same session. A refused refresh token must stop looking
            // signed in, while an outage must preserve credentials for the next attempt.
            if (refreshError is CloudApiException &&
                refreshError.statusCode == 401 &&
                refreshError.errorCode in
                setOf(PasskeyApiErrorCodes.INVALID_REFRESH_TOKEN, PasskeyApiErrorCodes.REFRESH_TOKEN_REVOKED) &&
                sessionStorage.getSession() == currentSession
            ) {
                sessionStorage.clearSession()
            }
            return Result.failure(refreshError ?: initialResult.exceptionOrNull() ?: scopeChanged())
        }

        // Refresh succeeded, retry operation with new token
        val newToken = refreshResult.getOrNull()
        if (newToken == null) {
            Napier.w("Token refresh succeeded but returned null")
            return initialResult
        }

        // A sign-out or a new sign-in may finish while the refresh request is in flight.
        if (sessionStorage.getSession() != currentSession) return initialResult

        // Keep the refreshed token. The account repository persists it under its own storage key,
        // which the session storage never reads, so without this the session keeps handing out the
        // token the server just rejected and every single request pays a 401 and a refresh before
        // it does any work.
        if (binding != null && !isStillBound(binding, locationProvider)) return Result.failure(scopeChanged())
        val updatedSession = currentSession.copy(accessToken = newToken)
        if (!saveRefreshedSession(binding, currentSession, updatedSession, locationProvider)) {
            return Result.failure(scopeChanged())
        }

        Napier.d("Token refreshed successfully, retrying")
        val refreshedBinding = binding?.let { CloudRequestBinding(it.location, updatedSession) }
        val retried = runBound(refreshedBinding) { operation(newToken) }
        retried.rethrowCancellation()
        if (refreshedBinding != null && !isStillBound(refreshedBinding, locationProvider)) {
            return Result.failure(scopeChanged())
        }
        return retried
    }

    /** Returns false when a bound session was replaced or its origin changed while refreshing. */
    private suspend fun saveRefreshedSession(
        binding: CloudRequestBinding?,
        currentSession: UserSession,
        updatedSession: UserSession,
        locationProvider: CloudRequestLocationProvider?,
    ): Boolean {
        if (binding == null) {
            sessionStorage.saveSession(updatedSession)
            return true
        }
        if (!sessionStorage.replaceSessionIfCurrent(
                OriginBoundSession(binding.location.origin, currentSession),
                updatedSession,
            )
        ) {
            return false
        }
        return locationProvider?.isCurrentOrigin(binding.location.origin) == true
    }

    private fun Result<*>.rethrowCancellation() {
        val failure = exceptionOrNull()
        if (failure is CancellationException) throw failure
    }

    private fun isStillBound(
        binding: CloudRequestBinding,
        provider: CloudRequestLocationProvider?,
    ): Boolean {
        val current = sessionStorage.getOriginBoundSession() ?: return false
        return provider?.isCurrentOrigin(binding.location.origin) == true &&
            current.origin == binding.location.origin &&
            current.session == binding.session
    }

    private fun scopeChanged() =
        CloudApiException(
            "CLOUD_SCOPE_CHANGED",
            "Cloud account changed during request",
            statusCode = 401,
        )

    suspend fun getAccessToken(): String? =
        try {
            val provider = cloudAccountRepository as? CloudRequestLocationProvider
            val session =
                if (provider == null) {
                    sessionStorage.getSession()
                } else {
                    sessionStorage.getOriginBoundSession()?.takeIf { provider.isCurrentOrigin(it.origin) }?.session
                }
            if (session != null) {
                session.accessToken
            } else {
                Napier.w("No active session found, cannot retrieve access token")
                null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Napier.e("Failed to get access token")
            null
        }

    suspend fun isAuthenticated(): Boolean = getAccessToken() != null
}
