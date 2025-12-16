package app.logdate.client.domain.account

import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CheckUsernameAvailabilityUseCaseTest {
    
    @Test
    fun `invoke with available username should return success true`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.success(true)
            override suspend fun signOut(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun getCurrentAccount(): LogDateAccount? = null
            override suspend fun getAccountInfo(): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun refreshAuthentication(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun deletePasskey(credentialId: String): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
        }
        val useCase = CheckUsernameAvailabilityUseCase(mockRepository)
        
        // When
        val result = useCase("validuser", debounceMs = 0L)
        
        // Then
        assertTrue(result is CheckUsernameAvailabilityUseCase.Result.Success)
        assertEquals(true, result.isAvailable)
    }
    
    @Test
    fun `invoke with taken username should return success false`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.success(false)
            override suspend fun signOut(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun getCurrentAccount(): LogDateAccount? = null
            override suspend fun getAccountInfo(): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun refreshAuthentication(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun deletePasskey(credentialId: String): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
        }
        val useCase = CheckUsernameAvailabilityUseCase(mockRepository)
        
        // When
        val result = useCase("takenuser", debounceMs = 0L)
        
        // Then
        assertTrue(result is CheckUsernameAvailabilityUseCase.Result.Success)
        assertEquals(false, result.isAvailable)
    }
    
    @Test
    fun `invoke with blank username should return invalid username error`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.success(true)
            override suspend fun signOut(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun getCurrentAccount(): LogDateAccount? = null
            override suspend fun getAccountInfo(): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun refreshAuthentication(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun deletePasskey(credentialId: String): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
        }
        val useCase = CheckUsernameAvailabilityUseCase(mockRepository)
        
        // When
        val result = useCase("", debounceMs = 0L)
        
        // Then
        assertTrue(result is CheckUsernameAvailabilityUseCase.Result.Error)
        assertEquals(CheckUsernameAvailabilityUseCase.AvailabilityCheckError.InvalidUsername, result.error)
    }
    
    @Test
    fun `invoke with too short username should return invalid username error`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.success(true)
            override suspend fun signOut(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun getCurrentAccount(): LogDateAccount? = null
            override suspend fun getAccountInfo(): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun refreshAuthentication(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun deletePasskey(credentialId: String): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
        }
        val useCase = CheckUsernameAvailabilityUseCase(mockRepository)
        
        // When
        val result = useCase("ab", debounceMs = 0L)
        
        // Then
        assertTrue(result is CheckUsernameAvailabilityUseCase.Result.Error)
        assertEquals(CheckUsernameAvailabilityUseCase.AvailabilityCheckError.InvalidUsername, result.error)
    }
    
    @Test
    fun `invoke with too long username should return invalid username error`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.success(true)
            override suspend fun signOut(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun getCurrentAccount(): LogDateAccount? = null
            override suspend fun getAccountInfo(): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun refreshAuthentication(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun deletePasskey(credentialId: String): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
        }
        val useCase = CheckUsernameAvailabilityUseCase(mockRepository)
        
        // When
        val result = useCase("a".repeat(51), debounceMs = 0L)
        
        // Then
        assertTrue(result is CheckUsernameAvailabilityUseCase.Result.Error)
        assertEquals(CheckUsernameAvailabilityUseCase.AvailabilityCheckError.InvalidUsername, result.error)
    }
    
    @Test
    fun `invoke with invalid characters should return invalid username error`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.success(true)
            override suspend fun signOut(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun getCurrentAccount(): LogDateAccount? = null
            override suspend fun getAccountInfo(): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun refreshAuthentication(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun deletePasskey(credentialId: String): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
        }
        val useCase = CheckUsernameAvailabilityUseCase(mockRepository)
        
        // When
        val result = useCase("user@name", debounceMs = 0L)
        
        // Then
        assertTrue(result is CheckUsernameAvailabilityUseCase.Result.Error)
        assertEquals(CheckUsernameAvailabilityUseCase.AvailabilityCheckError.InvalidUsername, result.error)
    }
    
    @Test
    fun `invoke with network error should return network error`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.failure(Exception("Network error"))
            override suspend fun signOut(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun getCurrentAccount(): LogDateAccount? = null
            override suspend fun getAccountInfo(): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun refreshAuthentication(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun deletePasskey(credentialId: String): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
        }
        val useCase = CheckUsernameAvailabilityUseCase(mockRepository)
        
        // When
        val result = useCase("validuser", debounceMs = 0L)
        
        // Then
        assertTrue(result is CheckUsernameAvailabilityUseCase.Result.Error)
        assertEquals(CheckUsernameAvailabilityUseCase.AvailabilityCheckError.NetworkError, result.error)
    }
}