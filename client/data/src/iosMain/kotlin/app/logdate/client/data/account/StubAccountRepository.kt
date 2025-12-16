package app.logdate.client.data.account

import app.logdate.client.repository.account.AccountRepository
import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Stub implementation of AccountRepository for iOS platform without cloud account.
 */
class StubAccountRepository : AccountRepository {
    
    override val currentAccount: Flow<LogDateAccount?> = flowOf(null)
    
    override suspend fun updateProfile(
        displayName: String?,
        username: String?
    ): Result<LogDateAccount> {
        return Result.failure(
            UnsupportedOperationException("No cloud account available on iOS")
        )
    }
    
    override suspend fun refreshAccount(): Result<LogDateAccount> {
        return Result.failure(
            UnsupportedOperationException("No cloud account available on iOS")
        )
    }
    
    override suspend fun checkUsernameAvailability(username: String): Result<Boolean> {
        return Result.failure(
            UnsupportedOperationException("No cloud account available on iOS")
        )
    }
}