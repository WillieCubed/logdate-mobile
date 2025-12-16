package app.logdate.client.domain.account

import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class GetCurrentAccountUseCaseTest {
    
    private val mockAccount = LogDateAccount(
        id = Uuid.random(),
        username = "testuser",
        displayName = "Test User",
        bio = "Test bio",
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )
    
    @Test
    fun `invoke with GetCurrentAccount request should return current account flow`() = runTest {
        // Given
        val accountFlow = MutableStateFlow(mockAccount)
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = accountFlow
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(true)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun signOut(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun getCurrentAccount(): LogDateAccount? = mockAccount
            override suspend fun getAccountInfo(): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun refreshAuthentication(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun deletePasskey(credentialId: String): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
        }
        val useCase = GetCurrentAccountUseCase(mockRepository)
        
        // When
        val result = useCase(GetCurrentAccountUseCase.AccountRequest.GetCurrentAccount)
        
        // Then
        assertTrue(result is GetCurrentAccountUseCase.AccountResult.CurrentAccount)
        assertEquals(mockAccount, result.account.first())
    }
    
    @Test
    fun `invoke with GetAuthenticationStatus request should return authentication status flow`() = runTest {
        // Given
        val authFlow = MutableStateFlow(true)
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(mockAccount)
            override val isAuthenticated = authFlow
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun signOut(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun getCurrentAccount(): LogDateAccount? = mockAccount
            override suspend fun getAccountInfo(): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun refreshAuthentication(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun deletePasskey(credentialId: String): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
        }
        val useCase = GetCurrentAccountUseCase(mockRepository)
        
        // When
        val result = useCase(GetCurrentAccountUseCase.AccountRequest.GetAuthenticationStatus)
        
        // Then
        assertTrue(result is GetCurrentAccountUseCase.AccountResult.AuthenticationStatus)
        assertEquals(true, result.isAuthenticated.first())
    }
    
    @Test
    fun `invoke with GetAccountState request should return authenticated state when account exists`() = runTest {
        // Given
        val accountFlow = MutableStateFlow(mockAccount)
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = accountFlow
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(true)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun signOut(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun getCurrentAccount(): LogDateAccount? = mockAccount
            override suspend fun getAccountInfo(): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun refreshAuthentication(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun deletePasskey(credentialId: String): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
        }
        val useCase = GetCurrentAccountUseCase(mockRepository)
        
        // When
        val result = useCase(GetCurrentAccountUseCase.AccountRequest.GetAccountState)
        
        // Then
        assertTrue(result is GetCurrentAccountUseCase.AccountResult.AccountState)
        val state = result.state.first()
        assertTrue(state is GetCurrentAccountUseCase.AccountState.Authenticated)
        assertEquals(mockAccount, state.account)
    }
    
    @Test
    fun `invoke with GetAccountState request should return not authenticated state when account is null`() = runTest {
        // Given
        val accountFlow = MutableStateFlow<LogDateAccount?>(null)
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = accountFlow
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
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
        val useCase = GetCurrentAccountUseCase(mockRepository)
        
        // When
        val result = useCase(GetCurrentAccountUseCase.AccountRequest.GetAccountState)
        
        // Then
        assertTrue(result is GetCurrentAccountUseCase.AccountResult.AccountState)
        val state = result.state.first()
        assertTrue(state is GetCurrentAccountUseCase.AccountState.NotAuthenticated)
    }
    
    @Test
    fun `invoke with RefreshAccountInfo request should return success result`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(mockAccount)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(true)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun signOut(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun getCurrentAccount(): LogDateAccount? = mockAccount
            override suspend fun getAccountInfo(): kotlin.Result<LogDateAccount> = 
                kotlin.Result.success(mockAccount)
            override suspend fun refreshAuthentication(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun deletePasskey(credentialId: String): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
        }
        val useCase = GetCurrentAccountUseCase(mockRepository)
        
        // When
        val result = useCase(GetCurrentAccountUseCase.AccountRequest.RefreshAccountInfo)
        
        // Then
        assertTrue(result is GetCurrentAccountUseCase.AccountResult.RefreshResult)
        assertTrue(result.result.isSuccess)
        assertEquals(mockAccount, result.result.getOrNull())
    }
    
    @Test
    fun `invoke with RefreshAccountInfo request should return failure result when repository fails`() = runTest {
        // Given
        val expectedException = Exception("Network error")
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(mockAccount)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(true)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun signOut(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun getCurrentAccount(): LogDateAccount? = mockAccount
            override suspend fun getAccountInfo(): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(expectedException)
            override suspend fun refreshAuthentication(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun deletePasskey(credentialId: String): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
        }
        val useCase = GetCurrentAccountUseCase(mockRepository)
        
        // When
        val result = useCase(GetCurrentAccountUseCase.AccountRequest.RefreshAccountInfo)
        
        // Then
        assertTrue(result is GetCurrentAccountUseCase.AccountResult.RefreshResult)
        assertTrue(result.result.isFailure)
        assertEquals(expectedException, result.result.exceptionOrNull())
    }
}