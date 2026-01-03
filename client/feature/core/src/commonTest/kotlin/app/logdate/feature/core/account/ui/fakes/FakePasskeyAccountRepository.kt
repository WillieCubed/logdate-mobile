package app.logdate.feature.core.account.ui.fakes

import app.logdate.client.repository.account.AccountCreationRequest
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlin.uuid.Uuid

class FakePasskeyAccountRepository : PasskeyAccountRepository {
    private val defaultAccount = LogDateAccount(
        id = Uuid.random(),
        username = "testuser",
        displayName = "Test User",
        passkeyCredentialIds = emptyList(),
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )

    override val currentAccount: StateFlow<LogDateAccount?> = MutableStateFlow(null).asStateFlow()
    override val isAuthenticated: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    var usernameAvailability: Result<Boolean> = Result.success(true)
    var accountCreation: Result<LogDateAccount> = Result.success(defaultAccount)

    override suspend fun createAccountWithPasskey(request: AccountCreationRequest): Result<LogDateAccount> {
        return accountCreation
    }

    override suspend fun authenticateWithPasskey(username: String?): Result<LogDateAccount> {
        return Result.success(defaultAccount)
    }

    override suspend fun checkUsernameAvailability(username: String): Result<Boolean> {
        return usernameAvailability
    }

    override suspend fun signOut(): Result<Unit> = Result.success(Unit)

    override suspend fun getCurrentAccount(): LogDateAccount? = null

    override suspend fun getAccountInfo(): Result<LogDateAccount> = Result.success(defaultAccount)

    override suspend fun refreshAuthentication(): Result<Unit> = Result.success(Unit)

    override suspend fun deletePasskey(credentialId: String): Result<Unit> = Result.success(Unit)
}
