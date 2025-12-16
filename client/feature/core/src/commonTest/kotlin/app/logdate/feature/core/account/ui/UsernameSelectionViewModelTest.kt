package app.logdate.feature.core.account.ui

import app.logdate.client.domain.account.CheckUsernameAvailabilityUseCase
import app.logdate.client.domain.account.CreatePasskeyAccountUseCase
import app.logdate.client.domain.account.CreateRemoteAccountUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UsernameSelectionViewModelTest {
    
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    
    private lateinit var mockCheckUsernameUseCase: MockCheckUsernameAvailabilityUseCase
    private lateinit var mockCreatePasskeyAccountUseCase: MockCreatePasskeyAccountUseCase
    private lateinit var mockCreateRemoteAccountUseCase: MockCreateRemoteAccountUseCase
    private lateinit var viewModel: UsernameSelectionViewModel
    
    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockCheckUsernameUseCase = MockCheckUsernameAvailabilityUseCase()
        mockCreatePasskeyAccountUseCase = MockCreatePasskeyAccountUseCase()
        mockCreateRemoteAccountUseCase = MockCreateRemoteAccountUseCase()
        viewModel = UsernameSelectionViewModel(
            mockCheckUsernameUseCase,
            mockCreatePasskeyAccountUseCase,
            mockCreateRemoteAccountUseCase,
            testDispatcher
        )
    }
    
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `when username is blank, validation fails`() = testScope.runTest {
        // Arrange
        val username = ""
        
        // Act
        viewModel.updateUsername(username)
        
        // Assert
        val state = viewModel.uiState.first()
        assertEquals(username, state.username)
        assertNotNull(state.usernameError)
        assertEquals("Username cannot be empty", state.usernameError)
        assertFalse(viewModel.canProceed())
    }
    
    @Test
    fun `when username is too short, validation fails`() = testScope.runTest {
        // Arrange
        val username = "ab"
        
        // Act
        viewModel.updateUsername(username)
        
        // Assert
        val state = viewModel.uiState.first()
        assertEquals(username, state.username)
        assertNotNull(state.usernameError)
        assertEquals("Username must be at least 3 characters", state.usernameError)
        assertFalse(viewModel.canProceed())
    }
    
    @Test
    fun `when username is too long, validation fails`() = testScope.runTest {
        // Arrange
        val username = "a".repeat(31)
        
        // Act
        viewModel.updateUsername(username)
        
        // Assert
        val state = viewModel.uiState.first()
        assertEquals(username, state.username)
        assertNotNull(state.usernameError)
        assertEquals("Username must be at most 30 characters", state.usernameError)
        assertFalse(viewModel.canProceed())
    }
    
    @Test
    fun `when username contains invalid characters, validation fails`() = testScope.runTest {
        // Arrange
        val username = "user@name"
        
        // Act
        viewModel.updateUsername(username)
        
        // Assert
        val state = viewModel.uiState.first()
        assertEquals(username, state.username)
        assertNotNull(state.usernameError)
        assertEquals("Username can only contain letters, numbers, and underscores", state.usernameError)
        assertFalse(viewModel.canProceed())
    }
    
    @Test
    fun `when username is valid but taken, availability check fails`() = testScope.runTest {
        // Arrange
        mockCheckUsernameUseCase.isAvailable = false
        val username = "takenusername"
        
        // Act
        viewModel.updateUsername(username)
        
        // Delay to allow the debounce to complete
        advanceTimeBy(600)
        advanceUntilIdle()
        
        // Assert
        val state = viewModel.uiState.first()
        assertEquals(username, state.username)
        assertEquals(UsernameAvailability.TAKEN, state.availability)
        assertNotNull(state.usernameError)
        assertEquals("Username is already taken", state.usernameError)
        assertFalse(viewModel.canProceed())
    }
    
    @Test
    fun `when username is valid and available, can proceed`() = testScope.runTest {
        // Arrange
        mockCheckUsernameUseCase.isAvailable = true
        val username = "validuser123"
        
        // Act
        viewModel.updateUsername(username)
        
        // Delay to allow the debounce to complete
        advanceTimeBy(600)
        advanceUntilIdle()
        
        // Assert
        val state = viewModel.uiState.first()
        assertEquals(username, state.username)
        assertEquals(UsernameAvailability.AVAILABLE, state.availability)
        assertNull(state.usernameError)
        assertTrue(viewModel.canProceed())
    }
    
    @Test
    fun `when proceeding with valid username, navigates to next screen`() = testScope.runTest {
        // Arrange
        mockCheckUsernameUseCase.isAvailable = true
        val username = "validuser123"
        viewModel.updateUsername(username)
        advanceTimeBy(600)
        advanceUntilIdle()
        
        // Act
        viewModel.proceedWithUsername()
        
        // Assert
        val state = viewModel.uiState.first()
        assertTrue(state.navigateToNextScreen)
    }
    
    @Test
    fun `resetNavigation clears navigation flag`() = testScope.runTest {
        // Arrange
        mockCheckUsernameUseCase.isAvailable = true
        val username = "validuser123"
        viewModel.updateUsername(username)
        advanceTimeBy(600)
        advanceUntilIdle()
        viewModel.proceedWithUsername()
        
        // Act
        viewModel.resetNavigation()
        
        // Assert
        val state = viewModel.uiState.first()
        assertFalse(state.navigateToNextScreen)
    }
    
    private class MockCheckUsernameAvailabilityUseCase : CheckUsernameAvailabilityUseCase {
        var isAvailable: Boolean = true
        
        override suspend fun invoke(username: String): Result<Boolean> {
            return Result.success(isAvailable)
        }
    }
    
    private class MockCreatePasskeyAccountUseCase : CreatePasskeyAccountUseCase {
        override suspend fun invoke(username: String, displayName: String): Result<String> {
            return Result.failure(NotImplementedError())
        }
    }
    
    private class MockCreateRemoteAccountUseCase : CreateRemoteAccountUseCase {
        override suspend fun invoke(username: String, displayName: String): Result<String> {
            return Result.failure(NotImplementedError())
        }
    }
}