package app.logdate.feature.core.account.ui

import app.logdate.client.domain.account.model.AuthenticationResult
import app.logdate.client.domain.account.model.CloudAccount
import app.logdate.client.domain.account.model.AccountCredentials
import app.logdate.client.domain.account.passkey.PasskeyErrorCode
import app.logdate.client.domain.account.passkey.PasskeyException
import app.logdate.client.domain.account.passkey.PasskeyManager
import app.logdate.client.domain.account.passkey.PasskeyRegistrationResult
import app.logdate.client.domain.account.passkey.RegistrationOptions
import app.logdate.client.domain.account.repository.BeginAccountCreationResult
import app.logdate.client.domain.account.repository.CloudAccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class PasskeyCreationViewModelTest {
    
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    
    private lateinit var mockRepository: MockCloudAccountRepository
    private lateinit var mockPasskeyManager: MockPasskeyManager
    private lateinit var accountSetupState: AccountSetupState
    private lateinit var viewModel: PasskeyCreationViewModel
    
    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockRepository = MockCloudAccountRepository()
        mockPasskeyManager = MockPasskeyManager()
        accountSetupState = AccountSetupState().apply {
            username = "testuser"
            displayName = "Test User"
        }
        viewModel = PasskeyCreationViewModel(
            cloudAccountRepository = mockRepository,
            passkeyManager = mockPasskeyManager,
            accountSetupState = accountSetupState
        )
    }
    
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `when createPasskey succeeds, account is created`() = testScope.runTest {
        // Arrange
        mockRepository.beginAccountCreationResult = BeginAccountCreationResult(
            sessionToken = "session_token",
            challenge = "challenge",
            rpId = "logdate.app",
            rpName = "LogDate Cloud",
            userId = "user_id",
            username = "testuser",
            displayName = "Test User",
            timeout = 30000L
        )
        
        mockPasskeyManager.registrationResult = PasskeyRegistrationResult(
            credentialId = "credential_id",
            clientDataJSON = "client_data",
            attestationObject = "attestation_object"
        )
        
        val account = CloudAccount(
            id = "acc_123",
            username = "testuser",
            displayName = "Test User",
            userId = Uuid.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8"),
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            passkeyCredentialIds = listOf("credential_id")
        )
        
        val credentials = AccountCredentials(
            accessToken = "access_token",
            refreshToken = "refresh_token",
            expiresIn = 3600L
        )
        
        mockRepository.completeAccountCreationResult = AuthenticationResult.Success(account, credentials)
        
        // Act
        viewModel.createPasskey()
        advanceUntilIdle()
        
        // Assert
        val state = viewModel.uiState.first()
        assertFalse(state.isCreatingPasskey)
        assertFalse(state.isCreatingAccount)
        assertTrue(state.isAccountCreated)
        assertNull(state.errorMessage)
        assertEquals("acc_123", accountSetupState.accountId)
    }
    
    @Test
    fun `when beginAccountCreation fails, shows error`() = testScope.runTest {
        // Arrange
        mockRepository.beginAccountCreationResult = null
        
        // Act
        viewModel.createPasskey()
        advanceUntilIdle()
        
        // Assert
        val state = viewModel.uiState.first()
        assertFalse(state.isCreatingPasskey)
        assertFalse(state.isCreatingAccount)
        assertFalse(state.isAccountCreated)
        assertNotNull(state.errorMessage)
        assertEquals("Failed to initiate account creation", state.errorMessage)
    }
    
    @Test
    fun `when createPasskey fails, shows error`() = testScope.runTest {
        // Arrange
        mockRepository.beginAccountCreationResult = BeginAccountCreationResult(
            sessionToken = "session_token",
            challenge = "challenge",
            rpId = "logdate.app",
            rpName = "LogDate Cloud",
            userId = "user_id",
            username = "testuser",
            displayName = "Test User",
            timeout = 30000L
        )
        
        mockPasskeyManager.shouldThrowException = true
        mockPasskeyManager.exception = PasskeyException(
            PasskeyErrorCode.USER_CANCELLED,
            "User cancelled passkey creation"
        )
        
        // Act
        viewModel.createPasskey()
        advanceUntilIdle()
        
        // Assert
        val state = viewModel.uiState.first()
        assertFalse(state.isCreatingPasskey)
        assertFalse(state.isCreatingAccount)
        assertFalse(state.isAccountCreated)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Error:"))
    }
    
    @Test
    fun `when completeAccountCreation fails, shows error`() = testScope.runTest {
        // Arrange
        mockRepository.beginAccountCreationResult = BeginAccountCreationResult(
            sessionToken = "session_token",
            challenge = "challenge",
            rpId = "logdate.app",
            rpName = "LogDate Cloud",
            userId = "user_id",
            username = "testuser",
            displayName = "Test User",
            timeout = 30000L
        )
        
        mockPasskeyManager.registrationResult = PasskeyRegistrationResult(
            credentialId = "credential_id",
            clientDataJSON = "client_data",
            attestationObject = "attestation_object"
        )
        
        mockRepository.completeAccountCreationResult = AuthenticationResult.Error(
            errorCode = "INVALID_CREDENTIAL",
            message = "The provided credential is invalid"
        )
        
        // Act
        viewModel.createPasskey()
        advanceUntilIdle()
        
        // Assert
        val state = viewModel.uiState.first()
        assertFalse(state.isCreatingPasskey)
        assertFalse(state.isCreatingAccount)
        assertFalse(state.isAccountCreated)
        assertNotNull(state.errorMessage)
        assertEquals("Failed to complete account creation", state.errorMessage)
    }
    
    @Test
    fun `errorMessageShown clears error message`() = testScope.runTest {
        // Arrange
        mockRepository.beginAccountCreationResult = null
        
        viewModel.createPasskey()
        advanceUntilIdle()
        
        var state = viewModel.uiState.first()
        assertNotNull(state.errorMessage)
        
        // Act
        viewModel.errorMessageShown()
        
        // Assert
        state = viewModel.uiState.first()
        assertNull(state.errorMessage)
    }
    
    private class MockCloudAccountRepository : CloudAccountRepository {
        var beginAccountCreationResult: BeginAccountCreationResult? = null
        var completeAccountCreationResult: AuthenticationResult? = null
        
        override suspend fun beginAccountCreation(username: String, displayName: String, deviceInfo: app.logdate.client.domain.account.model.DeviceInfo?): Result<BeginAccountCreationResult> {
            return if (beginAccountCreationResult != null) {
                Result.success(beginAccountCreationResult!!)
            } else {
                Result.failure(Exception("Failed to begin account creation"))
            }
        }
        
        override suspend fun completeAccountCreation(sessionToken: String, credentialId: String, clientDataJSON: String, attestationObject: String): Result<AuthenticationResult> {
            return if (completeAccountCreationResult != null) {
                Result.success(completeAccountCreationResult!!)
            } else {
                Result.failure(Exception("Failed to complete account creation"))
            }
        }
        
        override suspend fun isUsernameAvailable(username: String) = Result.success(true)
        override suspend fun getCurrentAccount() = null
        override fun observeCurrentAccount() = kotlinx.coroutines.flow.flowOf<CloudAccount?>(null)
        override suspend fun refreshAccessToken(refreshToken: String) = Result.success("new_token")
        override suspend fun signOut() = Result.success(true)
        override suspend fun getPasskeyCredentials() = Result.success(emptyList<app.logdate.client.domain.account.model.PasskeyCredential>())
        override suspend fun associateUserIdentity(userId: Uuid, accountId: String) = Result.success(true)
    }
    
    private class MockPasskeyManager : PasskeyManager {
        var registrationResult: PasskeyRegistrationResult? = null
        var shouldThrowException: Boolean = false
        var exception: PasskeyException? = null
        
        override suspend fun createPasskey(options: RegistrationOptions): Result<PasskeyRegistrationResult> {
            if (shouldThrowException && exception != null) {
                return Result.failure(exception!!)
            }
            
            return if (registrationResult != null) {
                Result.success(registrationResult!!)
            } else {
                Result.failure(PasskeyException(
                    PasskeyErrorCode.UNKNOWN_ERROR, 
                    "Failed to create passkey"
                ))
            }
        }
        
        override suspend fun getPasskey(options: app.logdate.client.domain.account.passkey.AuthenticationOptions) =
            Result.failure<app.logdate.client.domain.account.passkey.PasskeyAuthenticationResult>(NotImplementedError())
            
        override fun isPasskeySupported() = true
    }
}