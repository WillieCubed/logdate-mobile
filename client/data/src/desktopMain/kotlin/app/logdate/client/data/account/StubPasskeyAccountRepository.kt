package app.logdate.client.data.account

import app.logdate.client.repository.account.AccountCreationRequest
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * Stub implementation of PasskeyAccountRepository for desktop.
 * This implementation simulates passkey functionality for desktop
 * where actual passkey support may not be available.
 */
class StubPasskeyAccountRepository : PasskeyAccountRepository {
    private val _currentAccount = MutableStateFlow<LogDateAccount?>(null)
    override val currentAccount: StateFlow<LogDateAccount?> = _currentAccount
    
    private val _isAuthenticated = MutableStateFlow(false)
    override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated
    
    init {
        // Initialize with a stub account for testing
        _currentAccount.value = LogDateAccount(
            username = "desktop_user",
            displayName = "Desktop User",
            passkeyCredentialIds = listOf("stub_passkey_id")
        )
        _isAuthenticated.value = true
    }
    
    override suspend fun createAccountWithPasskey(request: AccountCreationRequest): Result<LogDateAccount> {
        val newAccount = LogDateAccount(
            username = request.username,
            displayName = request.displayName,
            bio = request.bio,
            passkeyCredentialIds = listOf("new_passkey_id_${Random.nextInt(1000)}")
        )
        
        _currentAccount.value = newAccount
        _isAuthenticated.value = true
        return Result.success(newAccount)
    }
    
    override suspend fun authenticateWithPasskey(username: String?): Result<LogDateAccount> {
        val account = _currentAccount.value ?: return Result.failure(Exception("No account available"))
        _isAuthenticated.value = true
        return Result.success(account)
    }
    
    override suspend fun checkUsernameAvailability(username: String): Result<Boolean> {
        // Simulate username availability check
        // Return true if username is not "taken" or "admin"
        return Result.success(username != "taken" && username != "admin")
    }
    
    override suspend fun signOut(): Result<Unit> {
        _isAuthenticated.value = false
        return Result.success(Unit)
    }
    
    override suspend fun getCurrentAccount(): LogDateAccount? {
        return _currentAccount.value
    }
    
    override suspend fun getAccountInfo(): Result<LogDateAccount> {
        val account = _currentAccount.value ?: return Result.failure(Exception("No account available"))
        return Result.success(account)
    }
    
    override suspend fun refreshAuthentication(): Result<Unit> {
        // Simulate successful authentication refresh
        return Result.success(Unit)
    }
    
    override suspend fun deletePasskey(credentialId: String): Result<Unit> {
        val account = _currentAccount.value ?: return Result.failure(Exception("No account available"))
        
        // Filter out the credential
        val updatedCredentialIds = account.passkeyCredentialIds.filter { it != credentialId }
        
        // Only update if we actually removed a credential
        if (updatedCredentialIds.size != account.passkeyCredentialIds.size) {
            val updatedAccount = account.copy(
                passkeyCredentialIds = updatedCredentialIds,
                updatedAt = Clock.System.now()
            )
            _currentAccount.value = updatedAccount
        }
        
        return Result.success(Unit)
    }
}