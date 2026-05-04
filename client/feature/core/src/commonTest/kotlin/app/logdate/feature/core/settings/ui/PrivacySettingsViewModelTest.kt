package app.logdate.feature.core.settings.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.domain.account.CreatePasskeyUseCase
import app.logdate.client.domain.account.DeletePasskeyUseCase
import app.logdate.client.domain.account.GetCurrentAccountUseCase
import app.logdate.client.repository.account.AccountCreationRequest
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.feature.core.AppAuthState
import app.logdate.feature.core.BiometricGatekeeper
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.user.AppSecurityLevel
import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacySettingsViewModelTest {
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
    fun `enabling biometric persists only when authentication succeeds`() =
        runTest {
            val gatekeeper = ScriptedBiometricGatekeeper(resultToReport = AppAuthState.AUTHENTICATED)
            val userRepo = FakeUserStateRepository()
            val viewModel = buildViewModel(gatekeeper = gatekeeper, userStateRepository = userRepo)

            viewModel.setBiometricEnabled(true)
            advanceUntilIdle()

            assertEquals(1, gatekeeper.authenticateInvocations)
            assertEquals(AppSecurityLevel.BIOMETRIC, userRepo.lastSecurityLevelSet)
        }

    @Test
    fun `enabling biometric does not persist when authentication is cancelled`() =
        runTest {
            val gatekeeper = ScriptedBiometricGatekeeper(resultToReport = AppAuthState.REQUIRE_PROMPT)
            val userRepo = FakeUserStateRepository()
            val viewModel = buildViewModel(gatekeeper = gatekeeper, userStateRepository = userRepo)

            viewModel.setBiometricEnabled(true)
            advanceUntilIdle()

            assertEquals(1, gatekeeper.authenticateInvocations)
            assertNull(userRepo.lastSecurityLevelSet)
        }

    @Test
    fun `disabling biometric persists immediately and skips the gatekeeper`() =
        runTest {
            val gatekeeper = ScriptedBiometricGatekeeper(resultToReport = AppAuthState.AUTHENTICATED)
            val userRepo =
                FakeUserStateRepository(
                    initialUserData = UserData(securityLevel = AppSecurityLevel.BIOMETRIC),
                )
            val viewModel = buildViewModel(gatekeeper = gatekeeper, userStateRepository = userRepo)

            viewModel.setBiometricEnabled(false)
            advanceUntilIdle()

            assertEquals(0, gatekeeper.authenticateInvocations)
            assertEquals(AppSecurityLevel.NONE, userRepo.lastSecurityLevelSet)
        }

    private fun buildViewModel(
        gatekeeper: BiometricGatekeeper,
        userStateRepository: UserStateRepository,
    ): PrivacySettingsViewModel {
        val passkeyRepository = FakePasskeyAccountRepository()
        return PrivacySettingsViewModel(
            preferencesDataSource = LogdatePreferencesDataSource(InMemoryPreferencesDataStore()),
            userStateRepository = userStateRepository,
            sessionStorage = FakeSessionStorage(),
            getCurrentAccountUseCase = GetCurrentAccountUseCase(passkeyRepository),
            createPasskeyUseCase = CreatePasskeyUseCase(passkeyRepository),
            deletePasskeyUseCase = DeletePasskeyUseCase(passkeyRepository),
            biometricGatekeeper = gatekeeper,
            supportsSystemSearchVisibilityToggle = false,
        )
    }

    private class ScriptedBiometricGatekeeper(
        private val resultToReport: AppAuthState,
    ) : BiometricGatekeeper {
        private val _authState = MutableStateFlow(AppAuthState.NO_PROMPT_NEEDED)
        override val authState: StateFlow<AppAuthState> = _authState
        var authenticateInvocations: Int = 0
            private set

        override fun authenticate(
            title: String,
            subtitle: String,
            cancelLabel: String,
            requireConfirmation: Boolean,
            requestEnrollmentIfNecessary: Boolean,
            description: String?,
            onResult: (AppAuthState) -> Unit,
        ) {
            authenticateInvocations += 1
            _authState.value = resultToReport
            onResult(resultToReport)
        }

        override fun requestEnrollment() {}
    }

    private class FakeUserStateRepository(
        initialUserData: UserData = UserData(),
    ) : UserStateRepository {
        private val _userData = MutableStateFlow(initialUserData)
        override val userData: StateFlow<UserData> = _userData
        var lastSecurityLevelSet: AppSecurityLevel? = null
            private set

        override suspend fun setBirthday(birthday: Instant) {}

        override suspend fun setIsOnboardingComplete(isComplete: Boolean) {}

        override suspend fun setBiometricEnabled(isEnabled: Boolean) {
            val level = if (isEnabled) AppSecurityLevel.BIOMETRIC else AppSecurityLevel.NONE
            lastSecurityLevelSet = level
            _userData.update { it.copy(securityLevel = level) }
        }

        override suspend fun addFavoriteNote(vararg noteId: String) {}
    }

    private class FakeSessionStorage : SessionStorage {
        override fun getSession(): UserSession? = null

        override fun getSessionFlow(): StateFlow<UserSession?> = MutableStateFlow(null)

        override suspend fun hasValidSession(): Boolean = false

        override fun saveSession(session: UserSession) {}

        override fun clearSession() {}
    }

    private class FakePasskeyAccountRepository : PasskeyAccountRepository {
        override val currentAccount: StateFlow<LogDateAccount?> = MutableStateFlow(null)
        override val isAuthenticated: StateFlow<Boolean> = MutableStateFlow(false)

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

        override suspend fun signInWithRestoreKey(): Result<LogDateAccount> = Result.failure(NotImplementedError())

        override suspend fun deleteRestoreKey(): Result<Unit> = Result.success(Unit)
    }
}

private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock {
            val updated = transform(state.value)
            state.value = updated
            updated
        }
}
