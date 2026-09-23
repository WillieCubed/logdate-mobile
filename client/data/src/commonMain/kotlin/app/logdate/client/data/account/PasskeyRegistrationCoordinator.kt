package app.logdate.client.data.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.PasskeyManager
import app.logdate.client.repository.account.AccountCreationRequest
import app.logdate.client.repository.account.LocalDataAdoptionRequiredException
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.BeginAccountCreationRequest
import app.logdate.shared.model.BeginAuthenticationRequest
import app.logdate.shared.model.CompleteAccountCreationRequest
import app.logdate.shared.model.CompleteAuthenticationRequest
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.PasskeyAuthenticationOptions
import io.github.aakira.napier.Napier

/**
 * Creates LogDate Cloud accounts and authenticates existing ones using WebAuthn passkeys.
 *
 * Owns the two passkey ceremonies -- registering a brand new credential during account creation,
 * and asserting an existing one during sign-in -- and the session/platform-account bookkeeping
 * that follows each.
 */
internal class PasskeyRegistrationCoordinator(
    private val apiClient: PasskeyApiClientContract,
    private val passkeyManager: PasskeyManager,
    private val sessionStorage: SessionStorage,
    private val platformAccountManager: PlatformAccountManager,
    private val configRepository: LogDateConfigRepository,
    private val canonicalOwnerProvider: CanonicalOwnerProvider,
    private val bindingGuard: CanonicalOwnerBindingGuard,
    private val credentialCodec: PasskeyCredentialCodec,
    private val sessionState: PasskeyAccountSessionState,
    private val createRestoreKey: suspend () -> Result<Unit>,
    private val updateTokensOrRegisterPlatformAccount: suspend (
        account: LogDateAccount,
        accessToken: String,
        refreshToken: String,
        backendUrl: String,
    ) -> Unit,
    private val deviceName: () -> String?,
) {
    suspend fun createAccountWithPasskey(request: AccountCreationRequest): Result<LogDateAccount> {
        return try {
            bindingGuard.requireCanonicalOwnerBinding()
            // Step 1: Begin account creation
            val canonicalOwnerId = canonicalOwnerProvider.getCanonicalOwnerId()
            val beginRequest =
                BeginAccountCreationRequest(
                    username = request.username,
                    displayName = request.displayName,
                    bio = request.bio,
                    requestedOwnerId = canonicalOwnerId,
                )

            val beginResult = apiClient.beginAccountCreation(beginRequest)
            if (beginResult.isFailure) {
                return Result.failure(beginResult.exceptionOrNull()!!)
            }

            val beginData = beginResult.getOrThrow()

            // Step 2: Create passkey using platform authenticator
            val registrationResult = passkeyManager.registerPasskey(beginData.registrationOptions)
            if (registrationResult.isFailure) {
                return Result.failure(registrationResult.exceptionOrNull()!!)
            }

            val credentialJson = registrationResult.getOrThrow()
            val credential = credentialCodec.parseCredentialResponse(credentialJson)

            // Step 3: Complete account creation
            val completeRequest =
                CompleteAccountCreationRequest(
                    sessionToken = beginData.sessionToken,
                    credential = credential,
                    nickname = deviceName(),
                )

            val completeResult = apiClient.completeAccountCreation(completeRequest)
            if (completeResult.isFailure) {
                return Result.failure(completeResult.exceptionOrNull()!!)
            }

            val completeData = completeResult.getOrThrow()
            if (completeData.account.id.toString() != canonicalOwnerId) {
                return Result.failure(CanonicalOwnerMismatchException())
            }

            // Step 4: Store session and account data
            persistSession(
                accessToken = completeData.tokens.accessToken,
                refreshToken = completeData.tokens.refreshToken,
                accountId = completeData.account.id.toString(),
            )

            // Step 5: Add account to platform account manager
            registerPlatformAccount(
                account = completeData.account,
                accessToken = completeData.tokens.accessToken,
                refreshToken = completeData.tokens.refreshToken,
            )

            sessionState.markAuthenticated(completeData.account)

            // Attempt to create a restore key for seamless re-authentication after device restore.
            // Logged at WARN on failure so operators see the broken-recovery path; non-fatal
            // for account creation itself. UI surfaces should observe a future
            // restoreKeyStatus signal and prompt the user to retry from settings.
            createRestoreKey().onFailure { error ->
                Napier.w("Account created but restore key registration failed — user has no recovery path", error)
            }

            Napier.i("Account created successfully for user: ${completeData.account.username}")
            Result.success(completeData.account)
        } catch (e: Exception) {
            Napier.w("Failed to create account with passkey", e)
            Result.failure(e)
        }
    }

    suspend fun authenticateWithPasskey(
        username: String?,
        adoptLocalData: Boolean,
    ): Result<LogDateAccount> {
        return try {
            bindingGuard.requireCanonicalOwnerBinding()
            // Step 1: Begin authentication
            val beginRequest = BeginAuthenticationRequest(username = username)
            val beginResult = apiClient.beginAuthentication(beginRequest)

            if (beginResult.isFailure) {
                return Result.failure(beginResult.exceptionOrNull()!!)
            }

            val beginData = beginResult.getOrThrow()

            // Step 2: Convert to PasskeyAuthenticationOptions
            val authOptions =
                PasskeyAuthenticationOptions(
                    challenge = beginData.challenge,
                    rpId = beginData.rpId,
                    allowCredentials = beginData.allowCredentials.map { it.id },
                    timeout = beginData.timeout,
                )

            // Step 3: Authenticate with passkey
            val authResult = passkeyManager.authenticateWithPasskey(authOptions)
            if (authResult.isFailure) {
                return Result.failure(authResult.exceptionOrNull()!!)
            }

            val assertionJson = authResult.getOrThrow()
            val assertion = credentialCodec.parseAssertionResponse(assertionJson)

            // Step 4: Complete authentication
            val completeRequest =
                CompleteAuthenticationRequest(
                    credential = assertion,
                    challenge = beginData.challenge,
                )

            val completeResult = apiClient.completeAuthentication(completeRequest)
            if (completeResult.isFailure) {
                return Result.failure(completeResult.exceptionOrNull()!!)
            }

            val completeData = completeResult.getOrThrow()
            when (bindingGuard.ownerBindingFor(completeData.account, adoptLocalData)) {
                CanonicalOwnerBindingGuard.OwnerBinding.ALLOWED -> Unit
                CanonicalOwnerBindingGuard.OwnerBinding.NEEDS_LOCAL_DATA_CONSENT ->
                    return Result.failure(LocalDataAdoptionRequiredException())
                CanonicalOwnerBindingGuard.OwnerBinding.REFUSED ->
                    return Result.failure(CanonicalOwnerMismatchException())
            }

            // Step 5: Store session and account data
            persistSession(
                accessToken = completeData.tokens.accessToken,
                refreshToken = completeData.tokens.refreshToken,
                accountId = completeData.account.id.toString(),
            )

            // Step 6: Update tokens in platform account manager (or register it, if this
            // device signed in to an existing LogDate Cloud account without ever running
            // the account-creation flow locally). Don't fail the entire operation since
            // authentication was already successful.
            updateTokensOrRegisterPlatformAccount(
                completeData.account,
                completeData.tokens.accessToken,
                completeData.tokens.refreshToken,
                configRepository.getCurrentBackendUrl(),
            )

            sessionState.markAuthenticated(completeData.account)

            Napier.i("Authentication successful for user: ${completeData.account.username}")
            Result.success(completeData.account)
        } catch (e: Exception) {
            Napier.w("Failed to authenticate with passkey", e)
            Result.failure(e)
        }
    }

    private suspend fun registerPlatformAccount(
        account: LogDateAccount,
        accessToken: String,
        refreshToken: String,
    ) {
        val platformResult =
            platformAccountManager.addAccount(
                account = account,
                accessToken = accessToken,
                refreshToken = refreshToken,
                backendUrl = configRepository.getCurrentBackendUrl(),
            )

        if (platformResult.isFailure) {
            Napier.w("Failed to add account to platform account manager", platformResult.exceptionOrNull())
            // Don't fail the entire operation since the account was created successfully
        }
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
