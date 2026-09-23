package app.logdate.client.data.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.RestoreCredentialError
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.AccountTokens
import app.logdate.shared.model.CompleteAuthenticationRequest
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.PasskeyAuthenticationOptions
import app.logdate.shared.model.ServerCapability
import io.github.aakira.napier.Napier

/**
 * Registers, clears, and signs in with restore credentials backed by the device's encrypted
 * cloud backup, so a reinstall can recover this installation's LogDate Cloud identity without a
 * fresh passkey ceremony.
 */
internal class RestoreCredentialCoordinator(
    private val apiClient: PasskeyApiClientContract,
    private val restoreCredentialManager: RestoreCredentialManager,
    private val sessionStorage: SessionStorage,
    private val platformAccountManager: PlatformAccountManager,
    private val configRepository: LogDateConfigRepository,
    private val bindingGuard: CanonicalOwnerBindingGuard,
    private val credentialCodec: PasskeyCredentialCodec,
    private val sessionState: PasskeyAccountSessionState,
    private val sessionRefreshCoordinator: AccountSessionRefreshCoordinator,
) {
    suspend fun createRestoreKey(): Result<Unit> {
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
            val beginResult = apiClient.beginRestoreSignIn()
            if (beginResult.isFailure) {
                return Result.failure(beginResult.exceptionOrNull()!!)
            }
            val beginData = beginResult.getOrThrow()

            val authOptions =
                PasskeyAuthenticationOptions(
                    challenge = beginData.challenge,
                    rpId = beginData.rpId,
                    allowCredentials = beginData.allowCredentials.map { it.id },
                    timeout = beginData.timeout,
                )

            val credentialResult = restoreCredentialManager.getRestoreCredential(authOptions)
            if (credentialResult.isFailure) {
                return Result.failure(credentialResult.exceptionOrNull()!!)
            }

            val assertion = credentialCodec.parseAssertionResponse(credentialResult.getOrThrow())
            val completeRequest =
                CompleteAuthenticationRequest(
                    credential = assertion,
                    challenge = beginData.challenge,
                )

            val completeResult = apiClient.completeRestoreSignIn(completeRequest)
            if (completeResult.isFailure) {
                return Result.failure(completeResult.exceptionOrNull()!!)
            }

            val completeData = completeResult.getOrThrow()
            if (!bindingGuard.belongsToCanonicalOwner(completeData.account)) {
                return Result.failure(CanonicalOwnerMismatchException())
            }
            persistSession(
                accessToken = completeData.tokens.accessToken,
                refreshToken = completeData.tokens.refreshToken,
                accountId = completeData.account.id.toString(),
            )

            updatePlatformAccountTokens(completeData.account, completeData.tokens)

            sessionState.markAuthenticated(completeData.account)

            // Re-register a restore credential for this device so future restores work.
            // The old credential was consumed server-side; this issues a fresh one.
            createRestoreKey().onFailure { error ->
                Napier.w("Restore sign-in succeeded but re-registering restore key failed — next restore won't work", error)
            }

            Napier.i("Restore sign-in successful for user: ${completeData.account.username}")
            Result.success(completeData.account)
        } catch (e: Exception) {
            Napier.i("Restore sign-in failed: ${e.message ?: "unknown error"}")
            Result.failure(e)
        }
    }

    private suspend fun updatePlatformAccountTokens(
        account: LogDateAccount,
        tokens: AccountTokens,
    ) {
        platformAccountManager
            .updateTokens(
                username = account.username,
                backendUrl = configRepository.getCurrentBackendUrl(),
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
            ).onFailure { Napier.w("Failed to update platform account tokens after restore sign-in", it) }
    }

    private suspend fun persistSession(
        accessToken: String,
        refreshToken: String,
        accountId: String,
    ) {
        sessionStorage.saveSession(
            UserSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                accountId = accountId,
            ),
        )
    }
}
