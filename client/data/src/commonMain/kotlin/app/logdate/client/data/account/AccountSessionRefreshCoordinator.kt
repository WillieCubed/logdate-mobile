package app.logdate.client.data.account

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionInstallationGuard
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.device.addAccountIfCurrent
import app.logdate.client.device.removeAccountIfSignedOut
import app.logdate.client.device.updateTokensIfCurrent
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.networking.PasskeyApiErrorCodes
import app.logdate.client.networking.PasskeyApiException
import app.logdate.client.repository.account.NotSignedInException
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.PasskeyInfo
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
    private val apiForOrigin: (String) -> PasskeyApiClientContract = { apiClient },
    private val prepareRestoreKeyClear: suspend (String, suspend () -> Boolean) -> Unit = { _, _ -> },
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
        val isCurrent = {
            val bound = sessionStorage.getOriginBoundSession()
            val current = bound?.session ?: sessionStorage.getSession()
            configRepository.getCurrentBackendUrl() == backendUrl &&
                (bound == null || bound.origin == backendUrl) &&
                current?.accountId == account.id.toString() &&
                current.refreshToken == refreshToken
        }
        val updateResult =
            platformAccountManager.updateTokensIfCurrent(
                username = account.username,
                backendUrl = backendUrl,
                accessToken = accessToken,
                refreshToken = refreshToken,
                isCurrent = isCurrent,
            )

        if (updateResult.isSuccess) return

        val addResult =
            platformAccountManager.addAccountIfCurrent(
                account = account,
                accessToken = accessToken,
                refreshToken = refreshToken,
                backendUrl = backendUrl,
                isCurrent = isCurrent,
            )

        if (addResult.isFailure) {
            Napier.w("Failed to update tokens in platform account manager", updateResult.exceptionOrNull())
        }
    }

    /** Deletes the account on the server, then clears only its retained local sign-in. */
    suspend fun deleteAccount(): Result<Unit> {
        val captured = sessionStorage.captureAuthenticationSnapshot()
        if (captured == null && sessionStorage.supportsGuardedAuthentication) return Result.failure(NotSignedInException())
        val binding = captured?.session ?: sessionStorage.getOriginBoundSession()
        val original = if (captured != null) captured.session?.session else binding?.session ?: sessionStorage.getSession()
        if (original == null) return Result.failure(NotSignedInException())
        val origin = captured?.guard?.origin ?: binding?.origin ?: configRepository.getCurrentBackendUrl()
        val api = apiForOrigin(origin)
        return authorized(binding, expectedAuthorization = captured?.guard) { accessToken -> api.deleteAccount(accessToken) }.onSuccess {
            clearLocalCredentials(origin, original, captured?.guard)
        }
    }

    suspend fun signOut(): Result<Unit> =
        try {
            val captured = sessionStorage.captureAuthenticationSnapshot()
            if (captured == null && sessionStorage.supportsGuardedAuthentication) return Result.failure(NotSignedInException())
            val binding = if (captured != null) captured.session else sessionStorage.getOriginBoundSession()
            val original = if (captured != null) binding?.session else binding?.session ?: sessionStorage.getSession()
            val origin = captured?.guard?.origin ?: binding?.origin ?: configRepository.getCurrentBackendUrl()
            clearLocalCredentials(origin, original, captured?.guard)
            original?.let {
                apiForOrigin(origin).logout(it.refreshToken).onFailure { error ->
                    Napier.w("Remote logout failed; local sign-out remains committed", error)
                }
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Napier.w("Failed to sign out", error)
            Result.failure(error)
        }

    private suspend fun clearLocalCredentials(
        origin: String,
        original: UserSession?,
        authorization: SessionInstallationGuard?,
    ) {
        val account = sessionState.account
        if (!commitSignOut(origin, original, authorization)) return
        // Local sign-out commits before asynchronous platform cleanup. Later login owns its state.
        if (account != null && original != null) {
            val removal =
                if (sessionStorage.supportsGuardedAuthentication) {
                    platformAccountManager.removeAccountIfSignedOut(account.username, origin) {
                        configRepository.getCurrentBackendUrl() == origin && sessionStorage.isSignedOutForProviderCleanup()
                    }
                } else {
                    platformAccountManager.removeAccountIfMatching(account.username, origin, original.refreshToken)
                }
            removal.onFailure { Napier.w("Failed to remove platform account on sign-out", it) }
        }
        if (sessionStorage.getSession() == null) deleteRestoreKey()
    }

    private suspend fun commitSignOut(
        origin: String,
        original: UserSession?,
        authorization: SessionInstallationGuard?,
    ): Boolean {
        suspend fun isCurrent(): Boolean {
            if (configRepository.getCurrentBackendUrl() != origin) return false
            if (authorization != null) return sessionStorage.isAuthenticationGuardCurrent(authorization)
            val current = sessionStorage.getSession()
            return if (original == null) {
                current == null
            } else {
                current?.accountId == original.accountId && current.refreshToken == original.refreshToken
            }
        }
        while (isCurrent()) {
            val guard = sessionStorage.captureAuthenticationGuard()
            if (sessionStorage.supportsGuardedAuthentication && guard == null) {
                kotlinx.coroutines.yield()
                continue
            }
            try {
                prepareRestoreKeyClear(origin, ::isCurrent)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Napier.w("Provider cleanup will reconcile after local sign-out", error)
            }
            if (!isCurrent()) return false
            if (guard != null) {
                if (sessionStorage.clearSessionIfUnchanged(guard) { sessionState.clear() }) return true
                kotlinx.coroutines.yield()
            } else {
                sessionStorage.clearSession()
                sessionState.clear()
                return true
            }
        }
        return false
    }

    suspend fun refreshAuthentication(expected: OriginBoundSession? = null): Result<Unit> {
        return try {
            val captured = sessionStorage.captureAuthenticationSnapshot()
            if (captured == null && sessionStorage.supportsGuardedAuthentication) return Result.failure(NotSignedInException())
            val binding = if (captured != null) captured.session else sessionStorage.getOriginBoundSession()
            if (expected != null &&
                (
                    binding?.origin != expected.origin ||
                        binding.session.accountId != expected.session.accountId ||
                        binding.session.refreshToken != expected.session.refreshToken
                )
            ) {
                return Result.failure(NotSignedInException())
            }
            if (binding == null && sessionStorage.supportsGuardedAuthentication) return Result.failure(NotSignedInException())
            val session =
                binding?.session ?: sessionStorage.getSession()
                    ?: return Result.failure(NotSignedInException())
            val origin = binding?.origin ?: configRepository.getCurrentBackendUrl()
            val refreshResult = apiForOrigin(origin).refreshToken(session.refreshToken)
            if (refreshResult.isFailure) {
                val error = refreshResult.exceptionOrNull()!!
                if (error.isRejected(REJECTED_REFRESH_TOKEN_CODES)) rejectSession(binding, captured?.guard, session, origin)
                return Result.failure(error)
            }

            val newAccessToken = refreshResult.getOrThrow()

            // Update session with new access token
            val updatedSession = session.copy(accessToken = newAccessToken)
            if (binding != null) {
                check(
                    sessionStorage.replaceSessionIfAuthorized(binding, updatedSession, captured?.guard),
                ) { "Authentication changed during refresh" }
            } else {
                check(configRepository.getCurrentBackendUrl() == origin && sessionStorage.getSession() == session) {
                    "Authentication changed during refresh"
                }
                sessionStorage.saveSession(updatedSession)
            }
            currentCoroutineContext().ensureActive()
            check(configRepository.getCurrentBackendUrl() == origin && sessionStorage.getSession() == updatedSession) {
                "Authentication changed during refresh"
            }

            // Update access token in platform account manager (or register it, if this
            // device signed in to an existing LogDate Cloud account without ever running
            // the account-creation flow locally).
            val currentAccountValue = sessionState.account
            if (currentAccountValue != null) {
                updateTokensOrRegisterPlatformAccount(
                    account = currentAccountValue,
                    accessToken = newAccessToken,
                    refreshToken = session.refreshToken,
                    backendUrl = origin,
                )
            }

            Napier.i("Authentication refreshed successfully")
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Napier.w("Failed to refresh authentication", e)
            Result.failure(e)
        }
    }

    private suspend fun rejectSession(
        binding: OriginBoundSession?,
        guard: SessionInstallationGuard?,
        session: UserSession,
        origin: String,
    ) {
        if (guard != null) {
            sessionStorage.clearSessionIfUnchanged(guard) { sessionState.clear() }
        } else if (binding != null) {
            sessionStorage.clearSessionIfCurrent(binding) { sessionState.clear() }
        } else if (configRepository.getCurrentBackendUrl() == origin && sessionStorage.getSession() == session) {
            signOut()
        }
    }

    suspend fun getAccountInfo(): Result<LogDateAccount> =
        try {
            val binding = sessionStorage.getOriginBoundSession()
            val session = binding?.session ?: sessionStorage.getSession() ?: return Result.failure(NotSignedInException())
            val origin = binding?.origin ?: configRepository.getCurrentBackendUrl()
            val expected = binding ?: OriginBoundSession(origin, session)
            val api = apiForOrigin(origin)
            val result = authorized(binding, retryAnyFailure = true) { api.getAccountInfo(it) }
            val account = result.getOrElse { return Result.failure(it) }
            check(account.id.toString() == session.accountId) { "Account identity changed" }
            if (!sessionStorage.runIfSessionCurrent(expected) { sessionState.updateAccount(account, origin) }) {
                Result.failure(NotSignedInException())
            } else {
                Result.success(account)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Napier.w("Failed to get account info", error)
            Result.failure(error)
        }

    /**
     * Runs an authenticated call with the stored access token. If the server rejects the token,
     * the session is refreshed and the call retried once with the new token. Any other failure is
     * returned as is, so a call that isn't safe to repeat never runs twice. Without a session the
     * call never runs.
     */
    suspend fun <T> authorized(
        expected: OriginBoundSession? = null,
        retryAnyFailure: Boolean = false,
        expectedAuthorization: SessionInstallationGuard? = null,
        call: suspend (accessToken: String) -> Result<T>,
    ): Result<T> {
        val captured = sessionStorage.captureAuthenticationSnapshot()
        if (captured == null && sessionStorage.supportsGuardedAuthentication) return Result.failure(NotSignedInException())
        val binding = if (captured != null) captured.session else sessionStorage.getOriginBoundSession()
        val authorization = expectedAuthorization ?: captured?.guard
        if (authorization != null &&
            !sessionStorage.isAuthenticationGuardCurrent(authorization)
        ) {
            return Result.failure(NotSignedInException())
        }
        if (expected != null &&
            (
                binding?.origin != expected.origin ||
                    binding.session.accountId != expected.session.accountId ||
                    binding.session.refreshToken != expected.session.refreshToken
            )
        ) {
            return Result.failure(NotSignedInException())
        }
        if (binding == null && sessionStorage.supportsGuardedAuthentication) return Result.failure(NotSignedInException())
        val session = binding?.session ?: sessionStorage.getSession() ?: return Result.failure(NotSignedInException())
        val origin = binding?.origin ?: configRepository.getCurrentBackendUrl()

        suspend fun stillCurrent(): Boolean {
            if (authorization != null && !sessionStorage.isAuthenticationGuardCurrent(authorization)) return false
            val current = sessionStorage.getOriginBoundSession()
            val retained = current?.session ?: sessionStorage.getSession()
            return configRepository.getCurrentBackendUrl() == origin &&
                (current == null || current.origin == origin) &&
                retained?.accountId == session.accountId &&
                retained.refreshToken == session.refreshToken
        }
        val result = call(session.accessToken)
        if (result.isSuccess ||
            (!retryAnyFailure && !result.exceptionOrNull().isRejected(REJECTED_ACCESS_TOKEN_CODES)) ||
            !stillCurrent()
        ) {
            return result
        }
        if (refreshAuthentication(binding).isFailure || !stillCurrent()) return result
        val refreshedBinding = sessionStorage.getOriginBoundSession()
        val refreshed = refreshedBinding?.session ?: sessionStorage.getSession() ?: return result
        if (refreshed.accountId != session.accountId ||
            refreshed.refreshToken != session.refreshToken ||
            (refreshedBinding != null && refreshedBinding.origin != origin) ||
            configRepository.getCurrentBackendUrl() != origin
        ) {
            return result
        }
        return call(refreshed.accessToken)
    }

    suspend fun listPasskeys(): Result<List<PasskeyInfo>> = authorized { apiClient.listPasskeys(it) }

    suspend fun deletePasskey(credentialId: String): Result<Unit> {
        val binding = sessionStorage.getOriginBoundSession()
        val origin = binding?.origin ?: configRepository.getCurrentBackendUrl()
        val api = apiForOrigin(origin)
        return authorized(binding, retryAnyFailure = true) { api.deletePasskey(it, credentialId) }
    }

    /** Refreshes once on failure while retaining the captured account and server. */
    suspend fun <T> withFreshAccessToken(block: suspend (accessToken: String) -> Result<T>): Result<T> =
        authorized(retryAnyFailure = true, call = block)

    private fun Throwable?.isRejected(codes: Set<String>): Boolean = this is PasskeyApiException && errorCode in codes

    private companion object {
        val REJECTED_ACCESS_TOKEN_CODES = setOf(PasskeyApiErrorCodes.INVALID_TOKEN, PasskeyApiErrorCodes.UNAUTHORIZED)
        val REJECTED_REFRESH_TOKEN_CODES =
            setOf(PasskeyApiErrorCodes.INVALID_REFRESH_TOKEN, PasskeyApiErrorCodes.REFRESH_TOKEN_REVOKED)
    }
}
