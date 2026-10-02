package app.logdate.feature.core.backup

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.CloudBackupDataSource
import kotlinx.coroutines.CancellationException

internal data class CloudBackupCallResult<T>(
    val session: OriginBoundSession,
    val result: Result<T>,
)

/** Refreshes one rejected access token without crossing the account or server captured at start. */
internal class CloudBackupTokenRefresher(
    private val sessionStorage: SessionStorage,
    private val dataSource: CloudBackupDataSource,
) {
    suspend fun <T> execute(
        expected: OriginBoundSession,
        operation: suspend (accessToken: String) -> Result<T>,
    ): CloudBackupCallResult<T> {
        if (sessionStorage.getOriginBoundSession() != expected) return scopeChanged<T>(expected)

        val first = invoke(expected.session.accessToken, operation)
        first.rethrowCancellation()
        if (first.isSuccess) return CloudBackupCallResult(expected, first)

        val error = first.exceptionOrNull() as? CloudApiException
        if (error?.statusCode != 401 || error.errorCode == SCOPE_CHANGED_CODE) {
            return CloudBackupCallResult(expected, first)
        }
        if (sessionStorage.getOriginBoundSession() != expected) return scopeChanged<T>(expected)

        val refreshed =
            try {
                dataSource.refreshAccessToken(expected.session.refreshToken)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return CloudBackupCallResult(expected, first)
            }
        refreshed.rethrowCancellation()
        val accessToken =
            refreshed.getOrNull()?.takeIf { it.isNotBlank() }
                ?: return CloudBackupCallResult(expected, first)

        val updatedSession = expected.session.copy(accessToken = accessToken)
        val wasStored =
            try {
                sessionStorage.replaceSessionIfCurrent(expected, updatedSession)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                false
            }
        if (!wasStored) return scopeChanged<T>(expected)

        val refreshedSession = OriginBoundSession(expected.origin, updatedSession)
        if (sessionStorage.getOriginBoundSession() != refreshedSession) return scopeChanged<T>(expected)

        val retry = invoke(accessToken, operation)
        retry.rethrowCancellation()
        if (sessionStorage.getOriginBoundSession() != refreshedSession) return scopeChanged<T>(refreshedSession)
        return CloudBackupCallResult(refreshedSession, retry)
    }

    private suspend fun <T> invoke(
        accessToken: String,
        operation: suspend (String) -> Result<T>,
    ): Result<T> =
        try {
            operation(accessToken)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }

    private fun <T> scopeChanged(session: OriginBoundSession): CloudBackupCallResult<T> =
        CloudBackupCallResult(
            session,
            Result.failure(
                CloudApiException(
                    errorCode = SCOPE_CHANGED_CODE,
                    message = "Cloud account changed during request",
                    statusCode = 401,
                ),
            ),
        )

    private fun Result<*>.rethrowCancellation() {
        val error = exceptionOrNull()
        if (error is CancellationException) throw error
    }

    private companion object {
        const val SCOPE_CHANGED_CODE = "CLOUD_SCOPE_CHANGED"
    }
}
