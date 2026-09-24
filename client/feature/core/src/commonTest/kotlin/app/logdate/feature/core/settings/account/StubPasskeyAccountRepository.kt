package app.logdate.feature.core.settings.account

import app.logdate.client.repository.account.AccountCreationRequest
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.PasskeyInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A signed-in account repository that answers every call with a harmless default. Tests override
 * only the calls they exercise, so a method added to [PasskeyAccountRepository] needs one default
 * here rather than one in every test.
 */
internal open class StubPasskeyAccountRepository(
    account: LogDateAccount? = null,
    isAuthenticated: Boolean = true,
) : PasskeyAccountRepository {
    override val currentAccount: StateFlow<LogDateAccount?> = MutableStateFlow(account)
    override val isAuthenticated: StateFlow<Boolean> = MutableStateFlow(isAuthenticated)

    override suspend fun createAccountWithPasskey(request: AccountCreationRequest): Result<LogDateAccount> =
        Result.failure(NotImplementedError())

    override suspend fun authenticateWithPasskey(
        username: String?,
        adoptLocalData: Boolean,
    ): Result<LogDateAccount> = Result.failure(NotImplementedError())

    override suspend fun checkUsernameAvailability(username: String): Result<Boolean> = Result.success(true)

    override suspend fun signOut(): Result<Unit> = Result.success(Unit)

    override suspend fun getCurrentAccount(): LogDateAccount? = currentAccount.value

    override suspend fun getAccountInfo(): Result<LogDateAccount> = Result.failure(NotImplementedError())

    override suspend fun refreshAuthentication(): Result<Unit> = Result.success(Unit)

    override suspend fun listPasskeys(): Result<List<PasskeyInfo>> = Result.success(emptyList())

    override suspend fun deletePasskey(credentialId: String): Result<Unit> = Result.success(Unit)

    override suspend fun createRestoreKey(): Result<Unit> = Result.success(Unit)

    override suspend fun signInWithRestoreKey(): Result<LogDateAccount> = Result.failure(NotImplementedError())

    override suspend fun deleteRestoreKey(): Result<Unit> = Result.success(Unit)
}
