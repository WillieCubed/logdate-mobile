package app.logdate.client.data.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.networking.PasskeyApiClientContract
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

    suspend fun signOut(): Result<Unit> =
        try {
            val currentAccountValue = sessionState.account
            val session = sessionStorage.getSession()

            session?.let {
                apiClient.logout(it.refreshToken).onFailure { error ->
                    Napier.w("Remote logout failed; local credentials will still be cleared", error)
                }
            }

            sessionStorage.clearSession()

            // A LogDate installation has one canonical identity. Remove the platform account
            // rather than leaving an origin-scoped blank account that can later be mistaken for
            // another selectable identity.
            if (currentAccountValue != null) {
                val platformResult =
                    platformAccountManager.removeAccount(
                        username = currentAccountValue.username,
                        backendUrl = configRepository.getCurrentBackendUrl(),
                    )

                if (platformResult.isFailure) {
                    Napier.w("Failed to remove platform account on sign-out", platformResult.exceptionOrNull())
                }
            }

            sessionState.clear()

            // Clear restore credential on sign-out — best effort, non-fatal.
            deleteRestoreKey()

            Napier.i("User signed out successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.w("Failed to sign out", e)
            Result.failure(e)
        }

    suspend fun refreshAuthentication(): Result<Unit> {
        return try {
            val session =
                sessionStorage.getSession()
                    ?: return Result.failure(Exception("No active session"))

            val refreshResult = apiClient.refreshToken(session.refreshToken)
            if (refreshResult.isFailure) {
                // If refresh fails, clear the session
                signOut()
                return Result.failure(refreshResult.exceptionOrNull()!!)
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
            signOut() // Clear session on error
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
     * Runs an authenticated call with the stored access token. If it fails, the session is
     * refreshed and the call retried once with the new token. Without a session the call never runs.
     */
    suspend fun <T> authorized(call: suspend (accessToken: String) -> Result<T>): Result<T> {
        val session = sessionStorage.getSession() ?: return Result.failure(NotSignedInException())
        val result = call(session.accessToken)
        if (result.isSuccess) return result
        if (refreshAuthentication().isFailure) return result
        val refreshed = sessionStorage.getSession() ?: return result
        return call(refreshed.accessToken)
    }

    suspend fun listPasskeys(): Result<List<PasskeyInfo>> {
        val session =
            sessionStorage.getSession()
                ?: return Result.failure(Exception("No active session"))

        val result = apiClient.listPasskeys(session.accessToken)
        if (result.isSuccess) return result

        // Same refresh-and-retry as the other authenticated calls: an expired token should cost
        // the user a refresh, not an empty list that reads as "you have no passkeys".
        val refreshResult = refreshAuthentication()
        if (!refreshResult.isSuccess) return result
        val updatedSession = sessionStorage.getSession() ?: return result
        return apiClient.listPasskeys(updatedSession.accessToken)
    }

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
}
