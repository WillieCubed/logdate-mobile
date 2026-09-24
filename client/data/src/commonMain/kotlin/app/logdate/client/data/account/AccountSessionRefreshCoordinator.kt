package app.logdate.client.data.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.networking.PasskeyApiErrorCodes
import app.logdate.client.networking.PasskeyApiException
import app.logdate.client.repository.account.NotSignedInException
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.PasskeyInfo
import io.github.aakira.napier.Napier

/**
 * Keeps the LogDate Cloud session alive and serves the authenticated passkey endpoints.
 *
 * Every authenticated call here follows the same "fail once, refresh, retry once" shape, and
 * [refreshAuthentication] itself repairs the platform account manager's copy of the tokens --
 * registering the platform account outright if this device authenticated without ever running
 * account creation locally.
 */
internal class AccountSessionRefreshCoordinator(
    private val apiClient: PasskeyApiClientContract,
    private val sessionStorage: SessionStorage,
    private val platformAccountManager: PlatformAccountManager,
    private val configRepository: LogDateConfigRepository,
    private val sessionState: PasskeyAccountSessionState,
    private val deleteRestoreKey: suspend () -> Result<Unit>,
) {
    /**
     * Updates tokens for an existing platform account, falling back to registering the
     * account when the platform account manager reports it doesn't exist yet.
     *
     * A platform account can be missing even after a successful LogDate Cloud
     * authentication: e.g. signing in to an already-existing account on a device that
     * never ran [PasskeyRegistrationCoordinator.createAccountWithPasskey] locally (a fresh
     * install, or a reinstall). In that case `updateTokens` alone can never succeed, so it
     * needs to be created instead. Platform-account-manager failures are non-fatal to the
     * caller either way.
     */
    suspend fun updateTokensOrRegisterPlatformAccount(
        account: LogDateAccount,
        accessToken: String,
        refreshToken: String,
        backendUrl: String,
    ) {
        val updateResult =
            platformAccountManager.updateTokens(
                username = account.username,
                backendUrl = backendUrl,
                accessToken = accessToken,
                refreshToken = refreshToken,
            )

        if (updateResult.isSuccess) return

        val addResult =
            platformAccountManager.addAccount(
                account = account,
                accessToken = accessToken,
                refreshToken = refreshToken,
                backendUrl = backendUrl,
            )

        if (addResult.isFailure) {
            Napier.w("Failed to update tokens in platform account manager", updateResult.exceptionOrNull())
        }
    }

    /** Deletes the account on the server, then clears this device's credentials for it. */
    suspend fun deleteAccount(): Result<Unit> =
        authorized { accessToken -> apiClient.deleteAccount(accessToken) }
            .onSuccess {
                Napier.i("Account deleted on the server; clearing local credentials")
                clearLocalCredentials()
            }

    suspend fun signOut(): Result<Unit> =
        try {
            sessionStorage.getSession()?.let {
                apiClient.logout(it.refreshToken).onFailure { error ->
                    Napier.w("Remote logout failed; local credentials will still be cleared", error)
                }
            }
            clearLocalCredentials()
            Napier.i("User signed out successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.w("Failed to sign out", e)
            Result.failure(e)
        }

    private suspend fun clearLocalCredentials() {
        val currentAccountValue = sessionState.account
        sessionStorage.clearSession()

        // A LogDate installation has one canonical identity. Remove the platform account
        // rather than leaving an origin-scoped blank account that can later be mistaken for
        // another selectable identity.
        if (currentAccountValue != null) {
            platformAccountManager
                .removeAccount(
                    username = currentAccountValue.username,
                    backendUrl = configRepository.getCurrentBackendUrl(),
                ).onFailure { Napier.w("Failed to remove platform account on sign-out", it) }
        }

        sessionState.clear()

        // Clear restore credential on sign-out — best effort, non-fatal.
        deleteRestoreKey()
    }

    suspend fun refreshAuthentication(): Result<Unit> {
        return try {
            val session =
                sessionStorage.getSession()
                    ?: return Result.failure(Exception("No active session"))

            val refreshResult = apiClient.refreshToken(session.refreshToken)
            if (refreshResult.isFailure) {
                val error = refreshResult.exceptionOrNull()!!
                // Only a server that refuses the refresh token ends the session. A network error
                // or an outage leaves it in place for the next attempt.
                if (error.isRejected(REJECTED_REFRESH_TOKEN_CODES)) signOut()
                return Result.failure(error)
            }

            val newAccessToken = refreshResult.getOrThrow()

            // Update session with new access token
            val updatedSession = session.copy(accessToken = newAccessToken)
            sessionStorage.saveSession(updatedSession)

            // Update access token in platform account manager (or register it, if this
            // device signed in to an existing LogDate Cloud account without ever running
            // the account-creation flow locally).
            val currentAccountValue = sessionState.account
            if (currentAccountValue != null) {
                updateTokensOrRegisterPlatformAccount(
                    account = currentAccountValue,
                    accessToken = newAccessToken,
                    refreshToken = session.refreshToken,
                    backendUrl = configRepository.getCurrentBackendUrl(),
                )
            }

            Napier.i("Authentication refreshed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.w("Failed to refresh authentication", e)
            Result.failure(e)
        }
    }

    suspend fun getAccountInfo(): Result<LogDateAccount> {
        return try {
            val session =
                sessionStorage.getSession()
                    ?: return Result.failure(Exception("No active session"))

            val result = apiClient.getAccountInfo(session.accessToken)
            if (result.isSuccess) {
                val account = result.getOrThrow()
                sessionState.updateAccount(account)
                return Result.success(account)
            } else {
                // Try refreshing token and retry once
                val refreshResult = refreshAuthentication()
                if (refreshResult.isSuccess) {
                    val updatedSession = sessionStorage.getSession()!!
                    val retryResult = apiClient.getAccountInfo(updatedSession.accessToken)
                    if (retryResult.isSuccess) {
                        val account = retryResult.getOrThrow()
                        sessionState.updateAccount(account)
                        return Result.success(account)
                    }
                }
                return result
            }
        } catch (e: Exception) {
            Napier.w("Failed to get account info", e)
            Result.failure(e)
        }
    }

    /**
     * Runs an authenticated call with the stored access token. If the server rejects the token,
     * the session is refreshed and the call retried once with the new token. Any other failure is
     * returned as is, so a call that isn't safe to repeat never runs twice. Without a session the
     * call never runs.
     */
    suspend fun <T> authorized(call: suspend (accessToken: String) -> Result<T>): Result<T> {
        val session = sessionStorage.getSession() ?: return Result.failure(NotSignedInException())
        val result = call(session.accessToken)
        if (!result.exceptionOrNull().isRejected(REJECTED_ACCESS_TOKEN_CODES)) return result
        if (refreshAuthentication().isFailure) return result
        val refreshed = sessionStorage.getSession() ?: return result
        return call(refreshed.accessToken)
    }

    suspend fun listPasskeys(): Result<List<PasskeyInfo>> = authorized { apiClient.listPasskeys(it) }

    suspend fun deletePasskey(credentialId: String): Result<Unit> {
        return try {
            val session =
                sessionStorage.getSession()
                    ?: return Result.failure(Exception("No active session"))

            val result = apiClient.deletePasskey(session.accessToken, credentialId)
            if (result.isSuccess) {
                Napier.i("Passkey deleted successfully: $credentialId")
                return Result.success(Unit)
            } else {
                // Try refreshing token and retry once
                val refreshResult = refreshAuthentication()
                if (refreshResult.isSuccess) {
                    val updatedSession = sessionStorage.getSession()!!
                    val retryResult = apiClient.deletePasskey(updatedSession.accessToken, credentialId)
                    if (retryResult.isSuccess) {
                        Napier.i("Passkey deleted successfully after token refresh: $credentialId")
                        return Result.success(Unit)
                    }
                }
                return result
            }
        } catch (e: Exception) {
            Napier.w("Failed to delete passkey: $credentialId", e)
            Result.failure(e)
        }
    }

    /**
     * Run [block] with a valid access token, transparently refreshing on first failure.
     *
     * Used everywhere we make an authenticated request. Without this, every authenticated call
     * site would have to inline the same "fail → refreshAuthentication → retry once" structure,
     * and missing it (e.g. updateAccountProfile, restore-key registration) silently surfaces a
     * generic error to users when the access token has expired in the background.
     *
     * Refreshes opportunistically on any failure, not just 401, to match the existing inline
     * pattern in [getAccountInfo] / [deletePasskey]. The cost is one extra refresh attempt when
     * a non-auth failure happens; the benefit is symmetry with the existing pattern and no need
     * to introspect server error shapes.
     */
    suspend fun <T> withFreshAccessToken(block: suspend (accessToken: String) -> Result<T>): Result<T> {
        val session =
            sessionStorage.getSession()
                ?: return Result.failure(IllegalStateException("No active session"))

        val first = block(session.accessToken)
        if (first.isSuccess) return first

        val refreshResult = refreshAuthentication()
        if (refreshResult.isFailure) return first

        val updatedSession = sessionStorage.getSession() ?: return first
        return block(updatedSession.accessToken)
    }

    private fun Throwable?.isRejected(codes: Set<String>): Boolean = this is PasskeyApiException && errorCode in codes

    private companion object {
        val REJECTED_ACCESS_TOKEN_CODES = setOf(PasskeyApiErrorCodes.INVALID_TOKEN, PasskeyApiErrorCodes.UNAUTHORIZED)
        val REJECTED_REFRESH_TOKEN_CODES =
            setOf(PasskeyApiErrorCodes.INVALID_REFRESH_TOKEN, PasskeyApiErrorCodes.REFRESH_TOKEN_REVOKED)
    }
}
