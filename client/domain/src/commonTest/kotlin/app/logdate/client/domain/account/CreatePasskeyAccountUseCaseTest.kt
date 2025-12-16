package app.logdate.client.domain.account

import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class CreatePasskeyAccountUseCaseTest {
    
    private val mockAccount = LogDateAccount(
        id = Uuid.random(),
        username = "testuser",
        displayName = "Test User",
        bio = "Test bio",
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )
    
    @Test
    fun `invoke with valid data should create account successfully`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.success(mockAccount)
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
        val useCase = CreatePasskeyAccountUseCase(mockRepository)
        
        // When
        val result = useCase("testuser", "Test User", "Test bio")
        
        // Then
        assertTrue(result is CreatePasskeyAccountUseCase.Result.Success)
        assertEquals(mockAccount, result.account)
    }
    
    @Test
    fun `invoke with taken username should return username taken error`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.success(mockAccount)
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
        val useCase = CreatePasskeyAccountUseCase(mockRepository)
        
        // When
        val result = useCase("takenuser", "Test User", "Test bio")
        
        // Then
        assertTrue(result is CreatePasskeyAccountUseCase.Result.Error)
        assertEquals(CreatePasskeyAccountUseCase.CreateAccountError.UsernameTaken, result.error)
    }
    
    @Test
    fun `invoke with invalid username should return username invalid error`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.success(mockAccount)
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
        val useCase = CreatePasskeyAccountUseCase(mockRepository)
        
        // When
        val result = useCase("ab", "Test User", "Test bio")
        
        // Then
        assertTrue(result is CreatePasskeyAccountUseCase.Result.Error)
        assertEquals(CreatePasskeyAccountUseCase.CreateAccountError.UsernameInvalid, result.error)
    }
    
    @Test
    fun `invoke with username containing invalid characters should return username invalid error`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.success(mockAccount)
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
        val useCase = CreatePasskeyAccountUseCase(mockRepository)
        
        // When
        val result = useCase("user@name", "Test User", "Test bio")
        
        // Then
        assertTrue(result is CreatePasskeyAccountUseCase.Result.Error)
        assertEquals(CreatePasskeyAccountUseCase.CreateAccountError.UsernameInvalid, result.error)
    }
    
    @Test
    fun `invoke with invalid display name should return display name invalid error`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.success(mockAccount)
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
        val useCase = CreatePasskeyAccountUseCase(mockRepository)
        
        // When
        val result = useCase("validuser", "", "Test bio")
        
        // Then
        assertTrue(result is CreatePasskeyAccountUseCase.Result.Error)
        assertEquals(CreatePasskeyAccountUseCase.CreateAccountError.DisplayNameInvalid, result.error)
    }
    
    @Test
    fun `invoke with network error during availability check should return network error`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.success(mockAccount)
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
        val useCase = CreatePasskeyAccountUseCase(mockRepository)
        
        // When
        val result = useCase("validuser", "Test User", "Test bio")
        
        // Then
        assertTrue(result is CreatePasskeyAccountUseCase.Result.Error)
        assertEquals(CreatePasskeyAccountUseCase.CreateAccountError.NetworkError, result.error)
    }
    
    @Test
    fun `invoke with passkey cancelled error should map correctly`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(Exception("USER_CANCELLED"))
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
        val useCase = CreatePasskeyAccountUseCase(mockRepository)
        
        // When
        val result = useCase("validuser", "Test User", "Test bio")
        
        // Then
        assertTrue(result is CreatePasskeyAccountUseCase.Result.Error)
        assertEquals(CreatePasskeyAccountUseCase.CreateAccountError.PasskeyCancelled, result.error)
    }
    
    @Test
    fun `invoke with valid data and null bio should create account successfully`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.success(mockAccount)
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
        val useCase = CreatePasskeyAccountUseCase(mockRepository)
        
        // When
        val result = useCase("testuser", "Test User")
        
        // Then
        assertTrue(result is CreatePasskeyAccountUseCase.Result.Success)
        assertEquals(mockAccount, result.account)
    }
}