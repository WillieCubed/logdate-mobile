package app.logdate.feature.core

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.domain.account.TryRestoreSignInUseCase
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.networking.NetworkState
import app.logdate.client.permissions.RestoreCredentialError
import app.logdate.client.repository.account.AccountCreationRequest
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ui state becomes loaded with stable initial online network state`() =
        runTest {
            val viewModel =
                AppViewModel(
                    userStateRepository = FakeUserStateRepository(UserData(isOnboarded = true)),
                    biometricGatekeeper = FakeBiometricGatekeeper(),
                    networkMonitor = FakeNetworkAvailabilityMonitor(isAvailable = true),
                    sessionStorage = FakeSessionStorage(),
                    tryRestoreSignInUseCase = TryRestoreSignInUseCase(FakePasskeyAccountRepository()),
                )

            advanceUntilIdle()

            val state = assertIs<GlobalAppUiLoadedState>(viewModel.uiState.value)
            assertEquals(true, state.isOnboarded)
            assertEquals(true, state.isOnline)
        }

    @Test
    fun `ui state becomes loaded with stable initial offline network state`() =
        runTest {
            val viewModel =
                AppViewModel(
                    userStateRepository = FakeUserStateRepository(UserData(isOnboarded = false)),
                    biometricGatekeeper = FakeBiometricGatekeeper(),
                    networkMonitor = FakeNetworkAvailabilityMonitor(isAvailable = false),
                    sessionStorage = FakeSessionStorage(),
                    tryRestoreSignInUseCase = TryRestoreSignInUseCase(FakePasskeyAccountRepository()),
                )

            advanceUntilIdle()

            val state = assertIs<GlobalAppUiLoadedState>(viewModel.uiState.value)
            assertEquals(false, state.isOnboarded)
            assertEquals(false, state.isOnline)
        }

    @Test
    fun `does not attempt restore sign-in during init`() =
        runTest {
            val passkeyAccountRepository = FakePasskeyAccountRepository()
            AppViewModel(
                userStateRepository = FakeUserStateRepository(UserData(isOnboarded = true)),
                biometricGatekeeper = FakeBiometricGatekeeper(),
                networkMonitor = FakeNetworkAvailabilityMonitor(isAvailable = true),
                sessionStorage = FakeSessionStorage(),
                tryRestoreSignInUseCase = TryRestoreSignInUseCase(passkeyAccountRepository),
            )

            advanceUntilIdle()

            assertEquals(0, passkeyAccountRepository.restoreSignInAttempts)
        }

    @Test
    fun `cloud restore sign-in skips when a valid session exists`() =
        runTest {
            val passkeyAccountRepository = FakePasskeyAccountRepository()
            val viewModel =
                AppViewModel(
                    userStateRepository = FakeUserStateRepository(UserData(isOnboarded = true)),
                    biometricGatekeeper = FakeBiometricGatekeeper(),
                    networkMonitor = FakeNetworkAvailabilityMonitor(isAvailable = true),
                    sessionStorage = FakeSessionStorage(hasValidSession = true),
                    tryRestoreSignInUseCase = TryRestoreSignInUseCase(passkeyAccountRepository),
                )

            viewModel.tryRestoreSignInAfterCloudRestore()
            advanceUntilIdle()

            assertEquals(0, passkeyAccountRepository.restoreSignInAttempts)
        }

    @Test
    fun `cloud restore sign-in attempts once when launched repeatedly`() =
        runTest {
            val passkeyAccountRepository = FakePasskeyAccountRepository()
            val viewModel =
                AppViewModel(
                    userStateRepository = FakeUserStateRepository(UserData(isOnboarded = true)),
                    biometricGatekeeper = FakeBiometricGatekeeper(),
                    networkMonitor = FakeNetworkAvailabilityMonitor(isAvailable = true),
                    sessionStorage = FakeSessionStorage(hasValidSession = false),
                    tryRestoreSignInUseCase = TryRestoreSignInUseCase(passkeyAccountRepository),
                )

            viewModel.tryRestoreSignInAfterCloudRestore()
            viewModel.tryRestoreSignInAfterCloudRestore()
            advanceUntilIdle()

            assertEquals(1, passkeyAccountRepository.restoreSignInAttempts)
        }

    private class FakeUserStateRepository(
        initialValue: UserData,
    ) : UserStateRepository {
        override val userData: StateFlow<UserData> = MutableStateFlow(initialValue)

        override suspend fun setBirthday(birthday: Instant) {}

        override suspend fun setIsOnboardingComplete(isComplete: Boolean) {}

        override suspend fun setBiometricEnabled(isEnabled: Boolean) {}

        override suspend fun addFavoriteNote(vararg noteId: String) {}
    }

    private class FakeBiometricGatekeeper : BiometricGatekeeper {
        override val authState: StateFlow<AppAuthState> = MutableStateFlow(AppAuthState.NO_PROMPT_NEEDED)

        override fun authenticate(
            title: String,
            subtitle: String,
            cancelLabel: String,
            requireConfirmation: Boolean,
            requestEnrollmentIfNecessary: Boolean,
            description: String?,
        ) {
        }

        override fun requestEnrollment() {
        }
    }

    private class FakeNetworkAvailabilityMonitor(
        private val isAvailable: Boolean,
    ) : NetworkAvailabilityMonitor {
        private val networkState =
            MutableStateFlow<NetworkState>(
                if (isAvailable) {
                    NetworkState.Connected(Clock.System.now())
                } else {
                    NetworkState.NotConnected(Clock.System.now())
                },
            )

        override fun isNetworkAvailable(): Boolean = isAvailable

        override fun observeNetwork(): SharedFlow<NetworkState> = networkState
    }

    private class FakeSessionStorage(
        private val session: UserSession? = null,
        private val hasValidSession: Boolean = false,
    ) : SessionStorage {
        override fun getSession(): UserSession? = session

        override fun getSessionFlow(): StateFlow<UserSession?> = MutableStateFlow(session)

        override suspend fun hasValidSession(): Boolean = hasValidSession

        override fun saveSession(session: UserSession) {}

        override fun clearSession() {}
    }

    private class FakePasskeyAccountRepository(
        private val signInWithRestoreKeyResult: Result<LogDateAccount> = Result.failure(RestoreCredentialError.NoCredential()),
    ) : PasskeyAccountRepository {
        override val currentAccount: StateFlow<LogDateAccount?> = MutableStateFlow(null)
        override val isAuthenticated: StateFlow<Boolean> = MutableStateFlow(false)
        var restoreSignInAttempts: Int = 0
            private set

        override suspend fun createAccountWithPasskey(request: AccountCreationRequest): Result<LogDateAccount> =
            Result.failure(NotImplementedError())

        override suspend fun authenticateWithPasskey(username: String?): Result<LogDateAccount> = Result.failure(NotImplementedError())

        override suspend fun checkUsernameAvailability(username: String): Result<Boolean> = Result.success(true)

        override suspend fun signOut(): Result<Unit> = Result.success(Unit)

        override suspend fun getCurrentAccount(): LogDateAccount? = null

        override suspend fun getAccountInfo(): Result<LogDateAccount> = Result.failure(NotImplementedError())

        override suspend fun refreshAuthentication(): Result<Unit> = Result.success(Unit)

        override suspend fun deletePasskey(credentialId: String): Result<Unit> = Result.success(Unit)

        override suspend fun createRestoreKey(): Result<Unit> = Result.success(Unit)

        override suspend fun signInWithRestoreKey(): Result<LogDateAccount> {
            restoreSignInAttempts += 1
            return signInWithRestoreKeyResult
        }

        override suspend fun deleteRestoreKey(): Result<Unit> = Result.success(Unit)
    }
}
