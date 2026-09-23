package app.logdate.feature.core.settings.account.delete

import app.logdate.client.networking.PasskeyApiErrorCodes
import app.logdate.client.networking.PasskeyApiException
import app.logdate.client.repository.account.AccountCreationRequest
import app.logdate.client.repository.account.NotSignedInException
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.feature.core.settings.account.ConnectedServer
import app.logdate.feature.core.settings.account.ConnectedServerInfo
import app.logdate.feature.core.settings.account.ServerHealth
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.PasskeyInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the screen names the server the account is deleted from`() =
        runTest {
            assertEquals("LogDate Cloud", viewModel().state.value.serverName)
        }

    @Test
    fun `deleting keeps this device's journal unless asked to erase it`() =
        runTest {
            var erased = false
            val repository = FakeAccountRepository()
            val viewModel = viewModel(repository = repository, erase = { erased = true })

            viewModel.delete()

            assertTrue(repository.deleted)
            assertFalse(erased)
            assertEquals(DeleteAccountUiState.Phase.Deleted(erasedThisDevice = false), viewModel.state.value.phase)
        }

    @Test
    fun `deleting can also erase this device`() =
        runTest {
            var erased = false
            val viewModel = viewModel(erase = { erased = true })

            viewModel.setEraseThisDevice(true)
            viewModel.delete()

            assertTrue(erased)
            assertEquals(DeleteAccountUiState.Phase.Deleted(erasedThisDevice = true), viewModel.state.value.phase)
        }

    @Test
    fun `a refused deletion leaves this device untouched and says why`() =
        runTest {
            var erased = false
            val repository =
                FakeAccountRepository(
                    result = Result.failure(PasskeyApiException(PasskeyApiErrorCodes.DELETION_UNAVAILABLE, "Unavailable")),
                )
            val viewModel = viewModel(repository = repository, erase = { erased = true })

            viewModel.setEraseThisDevice(true)
            viewModel.delete()

            assertFalse(erased)
            assertEquals(DeleteAccountUiState.Phase.Failed(DeleteAccountFailure.UNAVAILABLE), viewModel.state.value.phase)
        }

    @Test
    fun `failures are told apart`() =
        runTest {
            val cases =
                mapOf(
                    PasskeyApiException(PasskeyApiErrorCodes.NETWORK_ERROR, "Offline") to DeleteAccountFailure.OFFLINE,
                    NotSignedInException() to DeleteAccountFailure.NOT_SIGNED_IN,
                    PasskeyApiException("DELETION_FAILED", "Failed") to DeleteAccountFailure.SERVER,
                )
            cases.forEach { (error, expected) ->
                val viewModel = viewModel(repository = FakeAccountRepository(result = Result.failure(error)))

                viewModel.delete()

                assertEquals(DeleteAccountUiState.Phase.Failed(expected), viewModel.state.value.phase)
            }
        }

    private fun viewModel(
        repository: FakeAccountRepository = FakeAccountRepository(),
        erase: suspend () -> Unit = {},
    ) = DeleteAccountViewModel(
        accountRepository = repository,
        connectedServer = FakeServer(),
        eraseThisDevice = erase,
    )

    private class FakeServer : ConnectedServer {
        override val info: Flow<ConnectedServerInfo> =
            MutableStateFlow(
                ConnectedServerInfo(
                    origin = "https://cloud.logdate.app",
                    displayName = "LogDate Cloud",
                    isLogDateCloud = true,
                    publishesIdentityChanges = false,
                ),
            )

        override suspend fun refresh(): ServerHealth = ServerHealth.Reachable(null)
    }

    private class FakeAccountRepository(
        private val result: Result<Unit> = Result.success(Unit),
    ) : PasskeyAccountRepository {
        var deleted = false
        override val currentAccount: StateFlow<LogDateAccount?> = MutableStateFlow(null)
        override val isAuthenticated: StateFlow<Boolean> = MutableStateFlow(true)

        override suspend fun deleteAccount(): Result<Unit> = result.onSuccess { deleted = true }

        override suspend fun createAccountWithPasskey(request: AccountCreationRequest): Result<LogDateAccount> =
            Result.failure(NotImplementedError())

        override suspend fun authenticateWithPasskey(
            username: String?,
            adoptLocalData: Boolean,
        ): Result<LogDateAccount> = Result.failure(NotImplementedError())

        override suspend fun checkUsernameAvailability(username: String): Result<Boolean> = Result.success(true)

        override suspend fun signOut(): Result<Unit> = Result.success(Unit)

        override suspend fun getCurrentAccount(): LogDateAccount? = null

        override suspend fun getAccountInfo(): Result<LogDateAccount> = Result.failure(NotImplementedError())

        override suspend fun refreshAuthentication(): Result<Unit> = Result.success(Unit)

        override suspend fun listPasskeys(): Result<List<PasskeyInfo>> = Result.success(emptyList())

        override suspend fun deletePasskey(credentialId: String): Result<Unit> = Result.success(Unit)

        override suspend fun createRestoreKey(): Result<Unit> = Result.success(Unit)

        override suspend fun signInWithRestoreKey(): Result<LogDateAccount> = Result.failure(NotImplementedError())

        override suspend fun deleteRestoreKey(): Result<Unit> = Result.success(Unit)
    }
}
