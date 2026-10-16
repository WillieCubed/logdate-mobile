package app.logdate.client.data.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.RestoreCredentialError
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.config.PinnedLogDateConfigRepository
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.ServerCapability
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Registers, clears, and signs in with restore credentials backed by the device's encrypted
 * cloud backup, so a reinstall can recover this installation's LogDate Cloud identity without a
 * fresh passkey ceremony.
 */
internal class RestoreCredentialCoordinator(
    private val apiClient: PasskeyApiClientContract,
    private val restoreCredentialManager: RestoreCredentialManager,
    private val sessionStorage: SessionStorage,
    private val configRepository: LogDateConfigRepository,
    private val bindingGuard: CanonicalOwnerBindingGuard,
    private val sessionState: PasskeyAccountSessionState,
    private val sessionRefreshCoordinator: AccountSessionRefreshCoordinator,
    private val registrationMutex: Mutex = Mutex(),
    private val renewal: RestoreCredentialRenewal? = null,
    private val onSessionInstalled: () -> Unit = {},
) {
    suspend fun createRestoreKey(): Result<Unit> = registrationMutex.withLock { registerRestoreKey() }

    private suspend fun registerRestoreKey(): Result<Unit> {
        if (sessionStorage.getSession() == null) {
            return Result.failure(Exception("No active session"))
        }
        return try {
            val optionsResult =
                sessionRefreshCoordinator.withFreshAccessToken { accessToken ->
                    apiClient.beginRestoreKeyRegistration(accessToken)
                }
            if (optionsResult.isFailure) {
                val cause = optionsResult.exceptionOrNull()
                Napier.w("Restore key registration failed: server rejected /auth/restore/register/begin", cause)
                return Result.failure(cause ?: Exception("Restore key begin failed"))
            }
            val options = optionsResult.getOrThrow()

            val credentialResult = restoreCredentialManager.createRestoreKey(options)
            if (credentialResult.isFailure) {
                val error = credentialResult.exceptionOrNull()
                if (error is RestoreCredentialError.BackupUnavailable) {
                    // True silent-success: this device doesn't support cloud restore credentials at
                    // all (e.g. iOS without iCloud Keychain, Android without backup configured).
                    // The user is not at risk of losing their account because there was nothing
                    // to register in the first place.
                    Napier.i("Restore key skipped — device does not support E2EE backup")
                    return Result.success(Unit)
                }
                // Anything else is a real failure: device supports restore credentials but the OS
                // refused to mint one (user cancellation, biometric setup missing, transient
                // platform error). Propagate so the caller can prompt the user to retry instead
                // of leaving the account without a recovery path.
                Napier.w("Restore key registration failed: device refused credential creation", error)
                return Result.failure(error ?: Exception("Restore credential creation failed"))
            }

            val completeResult =
                sessionRefreshCoordinator.withFreshAccessToken { accessToken ->
                    apiClient.completeRestoreKeyRegistration(
                        accessToken = accessToken,
                        credentialJson = credentialResult.getOrThrow(),
                        challenge = options.challenge,
                    )
                }
            if (completeResult.isFailure) {
                val cause = completeResult.exceptionOrNull()
                Napier.w("Restore key registration failed: server rejected /auth/restore/register/complete", cause)
                return Result.failure(cause ?: Exception("Restore key complete failed"))
            }
            Napier.i("Restore key registered successfully")
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Napier.w("Restore key creation encountered an unexpected error", e)
            Result.failure(e)
        }
    }

    suspend fun deleteRestoreKey(): Result<Unit> =
        restoreCredentialManager
            .clearRestoreCredential()
            .onFailure { Napier.w("Failed to clear restore credential (non-fatal)", it) }
            .let { Result.success(Unit) }

    suspend fun signInWithRestoreKey(): Result<LogDateAccount> {
        if (!bindingGuard.currentServerSupportsCanonicalOwnerBinding()) {
            return Result.failure(UnsupportedCanonicalOwnerBindingException())
        }
        val serverDescriptor = configRepository.getCurrentServerDescriptor()
        if (serverDescriptor != null && !serverDescriptor.hasCapability(ServerCapability.AUTH_PASSKEY)) {
            return Result.failure(Exception("Server does not support passkey authentication"))
        }

        return try {
            val pendingRenewal = renewal ?: return Result.failure(IllegalStateException("Native restore is unavailable"))
            val guard =
                sessionStorage.captureInstallationGuard()
                    ?: return Result.failure(IllegalStateException("Account recovery is not ready"))
            val initialSession = sessionStorage.getSession()
            val pinned = PinnedLogDateConfigRepository(guard.origin, serverDescriptor, configRepository.apiVersion.value)
            val isCurrent = {
                configRepository.getCurrentBackendUrl() == guard.origin &&
                    configRepository.getCurrentServerDescriptor() == serverDescriptor &&
                    configRepository.apiVersion.value == pinned.apiVersion.value &&
                    sessionStorage.getSession() == initialSession
            }
            val pinnedApi =
                pendingRenewal.apiFor(pinned)
                    ?: return Result.failure(IllegalStateException("Native restore is unavailable"))
            val completeData = RestoreCredentialExchange(pinnedApi, restoreCredentialManager, isCurrent)().getOrThrow()
            if (!bindingGuard.belongsToCanonicalOwner(completeData.account)) {
                return Result.failure(CanonicalOwnerMismatchException())
            }
            currentCoroutineContext().ensureActive()
            check(isCurrent()) { "Restore configuration changed" }
            val session =
                UserSession(
                    completeData.tokens.accessToken,
                    completeData.tokens.refreshToken,
                    completeData.account.id.toString(),
                )
            // The consumed credential's replacement must survive even an interrupted vault write.
            pendingRenewal.mark(guard.origin, session.accountId)
            currentCoroutineContext().ensureActive()
            check(isCurrent()) { "Restore configuration changed" }
            check(sessionStorage.installSessionIfCurrent(guard, session)) { "Restore session changed" }
            currentCoroutineContext().ensureActive()
            check(configRepository.getCurrentBackendUrl() == guard.origin && sessionStorage.getSession() == session)
            onSessionInstalled()
            sessionState.markAuthenticated(completeData.account)
            currentCoroutineContext().ensureActive()
            Napier.i("Restore sign-in succeeded; replacement credential will register automatically")
            Result.success(completeData.account)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Napier.i("Restore sign-in remains pending")
            Result.failure(e)
        }
    }
}
