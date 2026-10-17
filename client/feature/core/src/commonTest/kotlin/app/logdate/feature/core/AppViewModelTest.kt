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
import app.logdate.client.text.UiText
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.PasskeyInfo
import app.logdate.shared.model.user.AppSecurityLevel
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

/**
 * Unit tests for [AppViewModel].
 *
 * Tests global application state management, including session restoration,
 * network availability monitoring, and onboarding state initialization.
 */
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
    fun `resuming the app retries pending account recovery without another restore callback`() =
        runTest {
            val repository = FakePasskeyAccountRepository()
            val viewModel =
                AppViewModel(
                    FakeUserStateRepository(UserData(isOnboarded = true)),
                    FakeBiometricGatekeeper(),
                    FakeNetworkAvailabilityMonitor(true),
                    FakeSessionStorage(),
                    TryRestoreSignInUseCase(repository),
                )
            viewModel.tryRestoreSignInAfterCloudRestore(recoverMissingContent = false)
            advanceUntilIdle()
            viewModel.onAppForegrounded()
            advanceUntilIdle()
            assertEquals(2, repository.restoreSignInAttempts)
        }

    @Test
    fun `reconnecting retries a pending account recovery without recreating the activity`() =
        runTest {
            val network = FakeNetworkAvailabilityMonitor(true)
            val repository = FakePasskeyAccountRepository()
            val viewModel =
                AppViewModel(
                    FakeUserStateRepository(UserData(isOnboarded = true)),
                    FakeBiometricGatekeeper(),
                    network,
                    FakeSessionStorage(),
                    TryRestoreSignInUseCase(repository),
                )
            viewModel.tryRestoreSignInAfterCloudRestore(recoverMissingContent = false)
            advanceUntilIdle()
            network.setConnected(false)
            advanceUntilIdle()
            network.setConnected(true)
            advanceUntilIdle()
            assertEquals(2, repository.restoreSignInAttempts)
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
    fun `missing content recovery resumes when account access already exists`() =
        runTest {
            var imports = 0
            var resolved = false
            val repository = FakePasskeyAccountRepository()
            val viewModel =
                AppViewModel(
                    FakeUserStateRepository(UserData(isOnboarded = true)),
                    FakeBiometricGatekeeper(),
                    FakeNetworkAvailabilityMonitor(true),
                    FakeSessionStorage(hasValidSession = true),
                    TryRestoreSignInUseCase(repository),
                    onCloudRestoreSignIn = { imports++ },
                )

            viewModel.tryRestoreSignInAfterCloudRestore(recoverMissingContent = true, onAccountResolved = { resolved = true })
            advanceUntilIdle()

            assertEquals(true, resolved)
            assertEquals(1, imports)
            assertEquals(0, repository.restoreSignInAttempts)
        }

    @Test
    fun `app starts locked when biometric security is already enabled`() =
        runTest {
            val viewModel =
                AppViewModel(
                    userStateRepository =
                        FakeUserStateRepository(
                            UserData(isOnboarded = true, securityLevel = AppSecurityLevel.BIOMETRIC),
                        ),
                    biometricGatekeeper = FakeBiometricGatekeeper(),
                    networkMonitor = FakeNetworkAvailabilityMonitor(isAvailable = true),
                    sessionStorage = FakeSessionStorage(),
                    tryRestoreSignInUseCase = TryRestoreSignInUseCase(FakePasskeyAccountRepository()),
                )

            advanceUntilIdle()

            val state = assertIs<GlobalAppUiLoadedState>(viewModel.uiState.value)
            assertEquals(true, state.requiresUnlock)
        }

    @Test
    fun `enabling biometric mid-session does not lock the app`() =
        runTest {
            val userRepo =
                FakeUserStateRepository(
                    UserData(isOnboarded = true, securityLevel = AppSecurityLevel.NONE),
                )
            val viewModel =
                AppViewModel(
                    userStateRepository = userRepo,
                    biometricGatekeeper = FakeBiometricGatekeeper(),
                    networkMonitor = FakeNetworkAvailabilityMonitor(isAvailable = true),
                    sessionStorage = FakeSessionStorage(),
                    tryRestoreSignInUseCase = TryRestoreSignInUseCase(FakePasskeyAccountRepository()),
                )
            advanceUntilIdle()

            userRepo.setUserData(
                UserData(isOnboarded = true, securityLevel = AppSecurityLevel.BIOMETRIC),
            )
            advanceUntilIdle()

            val state = assertIs<GlobalAppUiLoadedState>(viewModel.uiState.value)
            assertEquals(false, state.requiresUnlock)
        }

    @Test
    fun `disabling biometric unlocks the app`() =
        runTest {
            val userRepo =
                FakeUserStateRepository(
                    UserData(isOnboarded = true, securityLevel = AppSecurityLevel.BIOMETRIC),
                )
            val viewModel =
                AppViewModel(
                    userStateRepository = userRepo,
                    biometricGatekeeper = FakeBiometricGatekeeper(),
                    networkMonitor = FakeNetworkAvailabilityMonitor(isAvailable = true),
                    sessionStorage = FakeSessionStorage(),
                    tryRestoreSignInUseCase = TryRestoreSignInUseCase(FakePasskeyAccountRepository()),
                )
            advanceUntilIdle()

            userRepo.setUserData(
                UserData(isOnboarded = true, securityLevel = AppSecurityLevel.NONE),
            )
            advanceUntilIdle()

            val state = assertIs<GlobalAppUiLoadedState>(viewModel.uiState.value)
            assertEquals(false, state.requiresUnlock)
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

    @Test
    fun `credential-only restore can recover an account before onboarding data arrives`() =
        runTest {
            val repository =
                FakePasskeyAccountRepository(Result.success(LogDateAccount(username = "fixture", displayName = "Fixture account")))
            var resolved = false
            val viewModel =
                AppViewModel(
                    FakeUserStateRepository(UserData(isOnboarded = false)),
                    FakeBiometricGatekeeper(),
                    FakeNetworkAvailabilityMonitor(true),
                    FakeSessionStorage(),
                    TryRestoreSignInUseCase(repository),
                )

            viewModel.tryRestoreSignInAfterCloudRestore(onAccountResolved = { resolved = true })
            advanceUntilIdle()

            assertEquals(1, repository.restoreSignInAttempts)
            assertEquals(true, resolved)
        }

    @Test
    fun `an absent credential stays pending for delayed native delivery`() =
        runTest {
            val repository = FakePasskeyAccountRepository()
            var resolved = false
            val viewModel =
                AppViewModel(
                    FakeUserStateRepository(UserData(isOnboarded = false)),
                    FakeBiometricGatekeeper(),
                    FakeNetworkAvailabilityMonitor(true),
                    FakeSessionStorage(),
                    TryRestoreSignInUseCase(repository),
                )

            viewModel.tryRestoreSignInAfterCloudRestore(onAccountResolved = { resolved = true })
            advanceUntilIdle()
            viewModel.tryRestoreSignInAfterCloudRestore(onAccountResolved = { resolved = true })
            advanceUntilIdle()

            assertEquals(false, resolved)
            assertEquals(2, repository.restoreSignInAttempts)
        }

    @Test
    fun `an account recovery error remains retryable in the same application process`() =
        runTest {
            val repository = FakePasskeyAccountRepository(Result.failure(IllegalStateException("Server unavailable")))
            var resolved = false
            val viewModel =
                AppViewModel(
                    FakeUserStateRepository(UserData(isOnboarded = true)),
                    FakeBiometricGatekeeper(),
                    FakeNetworkAvailabilityMonitor(true),
                    FakeSessionStorage(),
                    TryRestoreSignInUseCase(repository),
                )

            viewModel.tryRestoreSignInAfterCloudRestore(onAccountResolved = { resolved = true })
            advanceUntilIdle()
            viewModel.tryRestoreSignInAfterCloudRestore(onAccountResolved = { resolved = true })
            advanceUntilIdle()

            assertEquals(2, repository.restoreSignInAttempts)
            assertEquals(false, resolved)
        }

    private class FakeUserStateRepository(
        initialValue: UserData,
    ) : UserStateRepository {
        private val _userData = MutableStateFlow(initialValue)
        override val userData: StateFlow<UserData> = _userData

        fun setUserData(value: UserData) {
            _userData.value = value
        }

        override suspend fun setBirthday(birthday: Instant) {}

        override suspend fun setIsOnboardingComplete(isComplete: Boolean) {}

        override suspend fun setBiometricEnabled(isEnabled: Boolean) {
            val level = if (isEnabled) AppSecurityLevel.BIOMETRIC else AppSecurityLevel.NONE
            _userData.value = _userData.value.copy(securityLevel = level)
        }

        override suspend fun addFavoriteNote(vararg noteId: String) {}
    }

    private class FakeBiometricGatekeeper : BiometricGatekeeper {
        override val authState: StateFlow<AppAuthState> = MutableStateFlow(AppAuthState.NO_PROMPT_NEEDED)

        override fun authenticate(
            title: UiText,
            subtitle: UiText,
            cancelLabel: UiText,
            requireConfirmation: Boolean,
            requestEnrollmentIfNecessary: Boolean,
            description: UiText?,
            onResult: (AppAuthState) -> Unit,
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

        fun setConnected(connected: Boolean) {
            networkState.value =
                if (connected) NetworkState.Connected(Clock.System.now()) else NetworkState.NotConnected(Clock.System.now())
        }

        override fun isNetworkAvailable(): Boolean = networkState.value is NetworkState.Connected

        override fun observeNetwork(): SharedFlow<NetworkState> = networkState
    }

    private class FakeSessionStorage(
        private val session: UserSession? = null,
        private val hasValidSession: Boolean = false,
    ) : SessionStorage {
        override fun getSession(): UserSession? = session

        override fun getSessionFlow(): StateFlow<UserSession?> = MutableStateFlow(session)

        override suspend fun hasValidSession(): Boolean = hasValidSession

        override suspend fun saveSession(session: UserSession) {}

        override suspend fun clearSession() {}
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

        override suspend fun authenticateWithPasskey(
            username: String?,
            adoptLocalData: Boolean,
        ): Result<LogDateAccount> = Result.failure(NotImplementedError())

        override suspend fun checkUsernameAvailability(username: String): Result<Boolean> = Result.success(true)

        override suspend fun listPasskeys(): Result<List<PasskeyInfo>> = Result.success(emptyList())

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
