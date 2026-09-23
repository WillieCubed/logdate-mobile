package app.logdate.client.data.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.GoogleSignInManager
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.AccountTokens
import app.logdate.shared.model.LogDateAccount
import io.github.aakira.napier.Napier

/**
 * Creates and authenticates LogDate Cloud accounts using Google Sign-In, exchanging a Google ID
 * token for LogDate session tokens.
 */
internal class GoogleSignInCoordinator(
    private val apiClient: PasskeyApiClientContract,
    private val sessionStorage: SessionStorage,
    private val platformAccountManager: PlatformAccountManager,
    private val configRepository: LogDateConfigRepository,
    private val canonicalOwnerProvider: CanonicalOwnerProvider,
    private val bindingGuard: CanonicalOwnerBindingGuard,
    private val googleSignInManager: GoogleSignInManager,
    private val serverClientId: String,
    private val sessionState: PasskeyAccountSessionState,
    private val createRestoreKey: suspend () -> Result<Unit>,
) {
    suspend fun signUpWithGoogle(
        username: String?,
        displayName: String?,
    ): Result<LogDateAccount> {
        return try {
            bindingGuard.requireCanonicalOwnerBinding()
            val canonicalOwnerId = canonicalOwnerProvider.getCanonicalOwnerId()
            val tokenResult = googleSignInManager.getGoogleIdToken(serverClientId)
            if (tokenResult.isFailure) {
                return Result.failure(tokenResult.exceptionOrNull()!!)
            }

            val createResult =
                apiClient.signUpWithGoogle(
                    idToken = tokenResult.getOrThrow(),
                    username = username,
                    displayName = displayName,
                    requestedOwnerId = canonicalOwnerId,
                )
            if (createResult.isFailure) {
                return Result.failure(createResult.exceptionOrNull()!!)
            }

            val data = createResult.getOrThrow()
            if (data.account.id.toString() != canonicalOwnerId) {
                return Result.failure(CanonicalOwnerMismatchException())
            }
            persistAuthenticatedSession(data.account, data.tokens, isNewAccount = true)

            createRestoreKey().onFailure { error ->
                Napier.w("Google account created but restore key registration failed — user has no recovery path", error)
            }

            Napier.i("Account created with Google for user: ${data.account.username}")
            Result.success(data.account)
        } catch (e: Exception) {
            Napier.w("Failed to sign up with Google", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(): Result<LogDateAccount> {
        return try {
            bindingGuard.requireCanonicalOwnerBinding()
            val tokenResult = googleSignInManager.getGoogleIdToken(serverClientId)
            if (tokenResult.isFailure) {
                return Result.failure(tokenResult.exceptionOrNull()!!)
            }

            val authResult = apiClient.signInWithGoogle(idToken = tokenResult.getOrThrow())
            if (authResult.isFailure) {
                return Result.failure(authResult.exceptionOrNull()!!)
            }

            val data = authResult.getOrThrow()
            if (!bindingGuard.belongsToCanonicalOwner(data.account)) {
                return Result.failure(CanonicalOwnerMismatchException())
            }
            persistAuthenticatedSession(data.account, data.tokens, isNewAccount = false)

            Napier.i("Authentication with Google successful for user: ${data.account.username}")
            Result.success(data.account)
        } catch (e: Exception) {
            Napier.w("Failed to sign in with Google", e)
            Result.failure(e)
        }
    }

    /**
     * Persists tokens + account after a successful authentication and updates the platform account
     * manager. [isNewAccount] selects between registering a new platform account and refreshing the
     * tokens of an existing one. Platform-account-manager failures are non-fatal.
     */
    private suspend fun persistAuthenticatedSession(
        account: LogDateAccount,
        tokens: AccountTokens,
        isNewAccount: Boolean,
    ) {
        sessionStorage.saveSession(
            UserSession(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                accountId = account.id.toString(),
            ),
        )

        val backendUrl = configRepository.getCurrentBackendUrl()
        val platformResult =
            if (isNewAccount) {
                platformAccountManager.addAccount(
                    account = account,
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    backendUrl = backendUrl,
                )
            } else {
                platformAccountManager.updateTokens(
                    username = account.username,
                    backendUrl = backendUrl,
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                )
            }
        if (platformResult.isFailure) {
            Napier.w("Failed to update platform account manager after Google auth", platformResult.exceptionOrNull())
        }

        sessionState.markAuthenticated(account)
    }
}
