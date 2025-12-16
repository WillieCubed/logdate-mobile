package app.logdate.client.domain.account

import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeletePasskeyUseCaseTest {

    @Test
    fun `invoke returns Success when repository deletion succeeds`() = runTest {
        // Arrange
        val credentialId = "test-credential-id"
        val request = DeletePasskeyUseCase.DeletePasskeyRequest(credentialId)
        
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
                kotlin.Result.failure(NotImplementedError())
            override suspend fun getCurrentAccount(): LogDateAccount? = null
            override suspend fun getAccountInfo(): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun refreshAuthentication(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun deletePasskey(credentialId: String): kotlin.Result<Unit> = 
                kotlin.Result.success(Unit)
        }
        val useCase = DeletePasskeyUseCase(mockRepository)

        // Act
        val result = useCase(request)

        // Assert
        assertTrue(result is DeletePasskeyUseCase.DeletePasskeyResult.Success)
    }

    @Test
    fun `invoke returns Error when repository deletion fails`() = runTest {
        // Arrange
        val credentialId = "test-credential-id"
        val request = DeletePasskeyUseCase.DeletePasskeyRequest(credentialId)
        val errorMessage = "Network error"
        val exception = Exception(errorMessage)

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
                kotlin.Result.failure(NotImplementedError())
            override suspend fun getCurrentAccount(): LogDateAccount? = null
            override suspend fun getAccountInfo(): kotlin.Result<LogDateAccount> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun refreshAuthentication(): kotlin.Result<Unit> = 
                kotlin.Result.failure(NotImplementedError())
            override suspend fun deletePasskey(credentialId: String): kotlin.Result<Unit> = 
                kotlin.Result.failure(exception)
        }
        val useCase = DeletePasskeyUseCase(mockRepository)

        // Act
        val result = useCase(request)

        // Assert
        assertTrue(result is DeletePasskeyUseCase.DeletePasskeyResult.Error)
        val errorResult = result as DeletePasskeyUseCase.DeletePasskeyResult.Error
        assertEquals(errorMessage, errorResult.message)
        assertEquals(exception, errorResult.cause)
    }
}