package app.logdate.feature.core.settings.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.storage.SecureStorage
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
import kotlinx.coroutines.flow.map
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
import kotlin.test.assertTrue
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

    @Test
    fun `reveal recovery phrase loads secure storage phrase after authentication`() =
        runTest {
            val gatekeeper = ScriptedBiometricGatekeeper(resultToReport = AppAuthState.AUTHENTICATED)
            val identityKeyManager = buildIdentityKeyManager()
            val phrase = identityKeyManager.setupNewIdentity()
            val viewModel =
                buildViewModel(
                    gatekeeper = gatekeeper,
                    userStateRepository = FakeUserStateRepository(),
                    identityKeyManager = identityKeyManager,
                )

            viewModel.revealRecoveryPhrase()
            advanceUntilIdle()

            val state = viewModel.recoveryPhraseRevealState.value
            assertTrue(state is RecoveryPhraseRevealState.Revealed)
            assertEquals(phrase.words, state.words)
        }

    @Test
    fun `reveal recovery phrase refuses when authentication is not satisfied`() =
        runTest {
            val gatekeeper = ScriptedBiometricGatekeeper(resultToReport = AppAuthState.REQUIRE_PROMPT)
            val identityKeyManager = buildIdentityKeyManager()
            identityKeyManager.setupNewIdentity()
            val viewModel =
                buildViewModel(
                    gatekeeper = gatekeeper,
                    userStateRepository = FakeUserStateRepository(),
                    identityKeyManager = identityKeyManager,
                )

            viewModel.revealRecoveryPhrase()
            advanceUntilIdle()

            assertTrue(viewModel.recoveryPhraseRevealState.value is RecoveryPhraseRevealState.Error)
        }

    private fun buildViewModel(
        gatekeeper: BiometricGatekeeper,
        userStateRepository: UserStateRepository,
        identityKeyManager: IdentityKeyManager = buildIdentityKeyManager(),
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
            identityKeyManager = identityKeyManager,
            supportsSystemSearchVisibilityToggle = false,
        )
    }

    private fun buildIdentityKeyManager(): IdentityKeyManager =
        IdentityKeyManager(
            secureStorage = InMemorySecureStorage(),
            cryptoManager = FakeCryptoManager(),
        )

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

    private class InMemorySecureStorage : SecureStorage {
        private val storage = MutableStateFlow<Map<String, String>>(emptyMap())

        override suspend fun getString(key: String): String? = storage.value[key]

        override suspend fun putString(
            key: String,
            value: String,
        ) {
            storage.value = storage.value + (key to value)
        }

        override suspend fun remove(key: String) {
            storage.value = storage.value - key
        }

        override suspend fun clear() {
            storage.value = emptyMap()
        }

        override fun observeString(key: String): Flow<String?> = storage.map { values -> values[key] }

        override fun observeAll(): Flow<Map<String, String>> = storage

        override suspend fun encrypt(data: ByteArray): ByteArray = data

        override suspend fun decrypt(data: ByteArray): ByteArray? = data
    }

    private class FakeCryptoManager : CryptoManager {
        override suspend fun generateRecoveryPhrase(): List<String> = (1..12).map { "word$it" }

        override suspend fun deriveMasterKey(phrase: List<String>): ByteArray =
            ByteArray(32) { index -> phrase.joinToString(" ").encodeToByteArray()[index % phrase.joinToString(" ").length] }

        override fun validateRecoveryPhrase(phrase: List<String>): Boolean = phrase.size == 12 && phrase.all { it.isNotBlank() }

        override fun encryptSink(
            sink: okio.Sink,
            key: ByteArray,
            iv: ByteArray,
        ): okio.Sink = sink

        override fun decryptSource(
            source: okio.Source,
            key: ByteArray,
            iv: ByteArray,
        ): okio.Source = source

        override fun generateRandomBytes(size: Int): ByteArray = ByteArray(size) { it.toByte() }

        override fun hmacSha256(
            key: ByteArray,
            data: ByteArray,
        ): ByteArray = ByteArray(32)

        override fun aesGcmEncrypt(
            key: ByteArray,
            iv: ByteArray,
            aad: ByteArray,
            plaintext: ByteArray,
        ): ByteArray = plaintext

        override fun aesGcmDecrypt(
            key: ByteArray,
            iv: ByteArray,
            aad: ByteArray,
            ciphertext: ByteArray,
        ): ByteArray = ciphertext
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
