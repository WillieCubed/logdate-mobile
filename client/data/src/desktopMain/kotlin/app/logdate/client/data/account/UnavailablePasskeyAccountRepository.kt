package app.logdate.client.data.account

import app.logdate.client.repository.account.AccountCreationRequest
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop account repository for builds without a platform WebAuthn authenticator.
 *
 * It never fabricates a signed-in account. Callers receive explicit failures so the UI can
 * show account features as unavailable instead of proceeding with synthetic credentials.
 */
class UnavailablePasskeyAccountRepository : PasskeyAccountRepository {
    private val currentAccountState = MutableStateFlow<LogDateAccount?>(null)
    private val authenticatedState = MutableStateFlow(false)

    override val currentAccount: StateFlow<LogDateAccount?> = currentAccountState.asStateFlow()
    override val isAuthenticated: StateFlow<Boolean> = authenticatedState.asStateFlow()

    override suspend fun createAccountWithPasskey(request: AccountCreationRequest): Result<LogDateAccount> = unavailable()

    override suspend fun authenticateWithPasskey(username: String?): Result<LogDateAccount> = unavailable()

    override suspend fun checkUsernameAvailability(username: String): Result<Boolean> =
        Result.failure(UnsupportedOperationException(UNAVAILABLE_MESSAGE))

    override suspend fun signOut(): Result<Unit> = Result.success(Unit)

    override suspend fun getCurrentAccount(): LogDateAccount? = null

    override suspend fun getAccountInfo(): Result<LogDateAccount> = unavailable()

    override suspend fun refreshAuthentication(): Result<Unit> = Result.failure(UnsupportedOperationException(UNAVAILABLE_MESSAGE))

    override suspend fun createRestoreKey(): Result<Unit> = Result.failure(UnsupportedOperationException(UNAVAILABLE_MESSAGE))

    override suspend fun signInWithRestoreKey(): Result<LogDateAccount> = unavailable()

    override suspend fun deleteRestoreKey(): Result<Unit> = Result.failure(UnsupportedOperationException(UNAVAILABLE_MESSAGE))

    override suspend fun deletePasskey(credentialId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException(UNAVAILABLE_MESSAGE))

    private fun <T> unavailable(): Result<T> = Result.failure(UnsupportedOperationException(UNAVAILABLE_MESSAGE))

    private companion object {
        const val UNAVAILABLE_MESSAGE = "Passkey account management is unavailable on this desktop build."
    }
}
