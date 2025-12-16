package app.logdate.client.domain.account

import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignOutUseCaseTest {
    
    @Test
    fun `invoke should return success when repository signout succeeds`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun signOut(): kotlin.Result<Unit> = 
                kotlin.Result.success(Unit)
            override suspend fun getCurrentAccount(): LogDateAccount? = null
            override suspend fun getAccountInfo(): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun refreshAuthentication(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun deletePasskey(credentialId: String): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
        }
        val useCase = SignOutUseCase(mockRepository)
        
        // When
        val result = useCase()
        
        // Then
        assertTrue(result is SignOutUseCase.Result.Success)
    }
    
    @Test
    fun `invoke should return error when repository signout fails`() = runTest {
        // Given
        val errorMessage = "Failed to clear session"
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun signOut(): kotlin.Result<Unit> = 
                kotlin.Result.failure(Exception(errorMessage))
            override suspend fun getCurrentAccount(): LogDateAccount? = null
            override suspend fun getAccountInfo(): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun refreshAuthentication(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun deletePasskey(credentialId: String): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
        }
        val useCase = SignOutUseCase(mockRepository)
        
        // When
        val result = useCase()
        
        // Then
        assertTrue(result is SignOutUseCase.Result.Error)
        assertEquals(errorMessage, result.message)
    }
    
    @Test
    fun `invoke should return error when repository throws exception`() = runTest {
        // Given
        val errorMessage = "Unexpected error"
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun signOut(): kotlin.Result<Unit> {
                throw RuntimeException(errorMessage)
            }
            override suspend fun getCurrentAccount(): LogDateAccount? = null
            override suspend fun getAccountInfo(): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun refreshAuthentication(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun deletePasskey(credentialId: String): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
        }
        val useCase = SignOutUseCase(mockRepository)
        
        // When
        val result = useCase()
        
        // Then
        assertTrue(result is SignOutUseCase.Result.Error)
        assertEquals(errorMessage, result.message)
    }
    
    @Test
    fun `invoke should handle null exception message gracefully`() = runTest {
        // Given
        val mockRepository = object : PasskeyAccountRepository {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow(null)
            override val isAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
            override suspend fun createAccountWithPasskey(request: app.logdate.client.repository.account.AccountCreationRequest): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun authenticateWithPasskey(username: String?): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun checkUsernameAvailability(username: String): kotlin.Result<Boolean> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun signOut(): kotlin.Result<Unit> = 
                kotlin.Result.failure(Exception(null as String?))
            override suspend fun getCurrentAccount(): LogDateAccount? = null
            override suspend fun getAccountInfo(): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun refreshAuthentication(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun deletePasskey(credentialId: String): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
        }
        val useCase = SignOutUseCase(mockRepository)
        
        // When
        val result = useCase()
        
        // Then
        assertTrue(result is SignOutUseCase.Result.Error)
        assertEquals("Unknown error during sign out", result.message)
    }
}