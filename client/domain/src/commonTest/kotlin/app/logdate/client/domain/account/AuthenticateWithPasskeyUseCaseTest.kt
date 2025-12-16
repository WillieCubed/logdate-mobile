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
class AuthenticateWithPasskeyUseCaseTest {
    
    private val mockAccount = LogDateAccount(
        id = Uuid.random(),
        username = "testuser",
        displayName = "Test User",
        bio = "Test bio",
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )
    
    @Test
    fun `invoke with valid credentials should return success`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.success(mockAccount)
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.failure(NotImplementedError())
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
        val useCase = AuthenticateWithPasskeyUseCase(mockRepository)
        
        // When
        val result = useCase("testuser")
        
        // Then
        assertTrue(result is AuthenticateWithPasskeyUseCase.Result.Success)
        assertEquals(mockAccount, result.account)
    }
    
    @Test
    fun `invoke without username should work for usernameless authentication`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.success(mockAccount)
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.failure(NotImplementedError())
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
        val useCase = AuthenticateWithPasskeyUseCase(mockRepository)
        
        // When
        val result = useCase()
        
        // Then
        assertTrue(result is AuthenticateWithPasskeyUseCase.Result.Success)
        assertEquals(mockAccount, result.account)
    }
    
    @Test
    fun `invoke with repository failure should return error`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(Exception("USER_CANCELLED"))
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.failure(NotImplementedError())
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
        val useCase = AuthenticateWithPasskeyUseCase(mockRepository)
        
        // When
        val result = useCase("testuser")
        
        // Then
        assertTrue(result is AuthenticateWithPasskeyUseCase.Result.Error)
        assertEquals(AuthenticateWithPasskeyUseCase.AuthenticationError.PasskeyCancelled, result.error)
    }
    
    @Test
    fun `invoke with not supported error should map correctly`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(Exception("NOT_SUPPORTED"))
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.failure(NotImplementedError())
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
        val useCase = AuthenticateWithPasskeyUseCase(mockRepository)
        
        // When
        val result = useCase("testuser")
        
        // Then
        assertTrue(result is AuthenticateWithPasskeyUseCase.Result.Error)
        assertEquals(AuthenticateWithPasskeyUseCase.AuthenticationError.PasskeyNotSupported, result.error)
    }
    
    @Test
    fun `invoke with network error should map correctly`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(Exception("NETWORK_ERROR: Connection failed"))
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.failure(NotImplementedError())
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
        val useCase = AuthenticateWithPasskeyUseCase(mockRepository)
        
        // When
        val result = useCase("testuser")
        
        // Then
        assertTrue(result is AuthenticateWithPasskeyUseCase.Result.Error)
        assertEquals(AuthenticateWithPasskeyUseCase.AuthenticationError.NetworkError, result.error)
    }
    
    @Test
    fun `invoke with unexpected exception should return unknown error`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> {
                throw RuntimeException("Unexpected error")
            }
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.failure(NotImplementedError())
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
        val useCase = AuthenticateWithPasskeyUseCase(mockRepository)
        
        // When
        val result = useCase("testuser")
        
        // Then
        assertTrue(result is AuthenticateWithPasskeyUseCase.Result.Error)
        assertTrue(result.error is AuthenticateWithPasskeyUseCase.AuthenticationError.Unknown)
        assertEquals("Unexpected error", (result.error as AuthenticateWithPasskeyUseCase.AuthenticationError.Unknown).message)
    }
}