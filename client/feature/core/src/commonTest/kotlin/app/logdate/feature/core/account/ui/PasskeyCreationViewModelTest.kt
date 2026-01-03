package app.logdate.feature.core.account.ui

import app.logdate.client.domain.account.AccountSetupData
import app.logdate.client.domain.account.CreatePasskeyAccountUseCase
import app.logdate.client.domain.account.CreateRemoteAccountUseCase
import app.logdate.client.domain.account.GetAccountSetupDataUseCase
import app.logdate.feature.core.account.ui.fakes.FakePasskeyAccountRepository
import app.logdate.feature.core.account.ui.fakes.InMemoryKeyValueStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class PasskeyCreationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var passkeyAccountRepository: FakePasskeyAccountRepository
    private lateinit var keyValueStorage: InMemoryKeyValueStorage
    private lateinit var getAccountSetupDataUseCase: GetAccountSetupDataUseCase
    private lateinit var viewModel: PasskeyCreationViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        passkeyAccountRepository = FakePasskeyAccountRepository()
        keyValueStorage = InMemoryKeyValueStorage()
        getAccountSetupDataUseCase = GetAccountSetupDataUseCase(keyValueStorage)

        viewModel = PasskeyCreationViewModel(
            createPasskeyAccountUseCase = CreatePasskeyAccountUseCase(passkeyAccountRepository),
            createRemoteAccountUseCase = CreateRemoteAccountUseCase(passkeyAccountRepository),
            getAccountSetupDataUseCase = getAccountSetupDataUseCase
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `createPasskey uses account setup data and completes account creation`() = testScope.runTest {
        val accountData = AccountSetupData(
            username = "testuser",
            displayName = "Test User",
            email = "test@logdate.app"
        )
        getAccountSetupDataUseCase(
            action = GetAccountSetupDataUseCase.Action.Save,
            data = accountData
        )

        viewModel.createPasskey()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.passkeyCreated)
        assertTrue(state.accountCreated)
        assertTrue(state.navigateToNextScreen)
        assertFalse(state.isCreatingPasskey)
        assertFalse(state.isCreatingAccount)
        assertNull(state.errorMessage)

        val clearedData = getAccountSetupDataUseCase()
        assertEquals("", clearedData.username)
        assertEquals("", clearedData.displayName)
        assertNull(clearedData.email)
    }

    @Test
    fun `createPasskey shows error when account setup data is missing`() = testScope.runTest {
        getAccountSetupDataUseCase(
            action = GetAccountSetupDataUseCase.Action.Save,
            data = AccountSetupData(username = "", displayName = "")
        )

        viewModel.createPasskey()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.passkeyCreated)
        assertFalse(state.accountCreated)
        assertFalse(state.navigateToNextScreen)
        assertEquals(
            "Username and display name are required. Please go back and complete those steps.",
            state.errorMessage
        )
    }

    @Test
    fun `createPasskeyAndAccount rejects empty input`() = testScope.runTest {
        viewModel.createPasskeyAndAccount("", "")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.passkeyCreated)
        assertFalse(state.accountCreated)
        assertEquals(
            "Username and display name are required to create a passkey",
            state.errorMessage
        )
    }

}
