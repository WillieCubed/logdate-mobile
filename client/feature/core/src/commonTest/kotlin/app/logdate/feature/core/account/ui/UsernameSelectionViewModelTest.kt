package app.logdate.feature.core.account.ui

import app.logdate.client.domain.account.CheckUsernameAvailabilityUseCase
import app.logdate.client.domain.account.GetAccountSetupDataUseCase
import app.logdate.feature.core.account.ui.fakes.FakePasskeyAccountRepository
import app.logdate.feature.core.account.ui.fakes.InMemoryKeyValueStorage
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UsernameSelectionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var passkeyAccountRepository: FakePasskeyAccountRepository
    private lateinit var getAccountSetupDataUseCase: GetAccountSetupDataUseCase
    private lateinit var viewModel: AccountOnboardingViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        passkeyAccountRepository = FakePasskeyAccountRepository()
        getAccountSetupDataUseCase = GetAccountSetupDataUseCase(InMemoryKeyValueStorage())

        viewModel = AccountOnboardingViewModel(
            checkUsernameAvailabilityUseCase = CheckUsernameAvailabilityUseCase(passkeyAccountRepository),
            getAccountSetupDataUseCase = getAccountSetupDataUseCase
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when username is blank, validation fails`() = testScope.runTest {
        viewModel.onUsernameChanged("")
        viewModel.checkUsernameAvailability()

        val state = viewModel.uiState.first()
        assertEquals("", state.username)
        assertEquals("Username cannot be empty", state.usernameError)
        assertEquals(UsernameAvailability.UNKNOWN, state.usernameAvailability)
        assertFalse(state.canCheckUsernameAvailability)
    }

    @Test
    fun `when username has invalid characters, validation fails`() = testScope.runTest {
        viewModel.onUsernameChanged("user@name")
        viewModel.checkUsernameAvailability()

        val state = viewModel.uiState.first()
        assertEquals("user@name", state.username)
        assertEquals("Username can only contain letters, numbers, and underscores", state.usernameError)
        assertEquals(UsernameAvailability.UNKNOWN, state.usernameAvailability)
        assertTrue(state.canCheckUsernameAvailability)
    }

    @Test
    fun `when username is valid but taken, availability check fails`() = testScope.runTest {
        passkeyAccountRepository.usernameAvailability = Result.success(false)

        viewModel.onUsernameChanged("takenusername")
        viewModel.checkUsernameAvailability()

        advanceTimeBy(350)
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals("takenusername", state.username)
        assertEquals(UsernameAvailability.TAKEN, state.usernameAvailability)
        assertNull(state.usernameError)
        assertFalse(state.canContinueFromUsername)
    }

    @Test
    fun `when username is valid and available, can continue`() = testScope.runTest {
        passkeyAccountRepository.usernameAvailability = Result.success(true)

        viewModel.onUsernameChanged("validuser123")
        viewModel.checkUsernameAvailability()

        advanceTimeBy(350)
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals("validuser123", state.username)
        assertEquals(UsernameAvailability.AVAILABLE, state.usernameAvailability)
        assertNull(state.usernameError)
        assertTrue(state.canContinueFromUsername)
    }

    @Test
    fun `onUsernameContinue persists account setup data`() = testScope.runTest {
        viewModel.onUsernameChanged("validuser123")
        viewModel.onUsernameContinue()
        advanceUntilIdle()

        val saved = getAccountSetupDataUseCase()
        assertEquals("validuser123", saved.username)
    }
}
