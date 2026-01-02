package app.logdate.client.data.account

import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.repository.account.AccountRepository
import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Default implementation of AccountRepository using PasskeyApiClient.
 */
class DefaultAccountRepository(
    private val passkeyApiClient: PasskeyApiClientContract,
    private val tokenProvider: () -> String? // TODO: Replace with proper token management
) : AccountRepository {
    
    private val _currentAccount = MutableStateFlow<LogDateAccount?>(null)
    override val currentAccount: Flow<LogDateAccount?> = _currentAccount.asStateFlow()
    
    override suspend fun updateProfile(
        displayName: String?,
        username: String?
    ): Result<LogDateAccount> {
        val token = tokenProvider() ?: return Result.failure(
            IllegalStateException("No authentication token available")
        )
        
        return passkeyApiClient.updateAccountProfile(
            accessToken = token,
            displayName = displayName,
            username = username
        ).onSuccess { updatedAccount ->
            _currentAccount.value = updatedAccount
        }
    }
    
    override suspend fun refreshAccount(): Result<LogDateAccount> {
        val token = tokenProvider() ?: return Result.failure(
            IllegalStateException("No authentication token available")
        )
        
        return passkeyApiClient.getAccountInfo(token).onSuccess { account ->
            _currentAccount.value = account
        }
    }
    
    override suspend fun checkUsernameAvailability(username: String): Result<Boolean> {
        return passkeyApiClient.checkUsernameAvailability(username).map { data ->
            data.available
        }
    }
}
