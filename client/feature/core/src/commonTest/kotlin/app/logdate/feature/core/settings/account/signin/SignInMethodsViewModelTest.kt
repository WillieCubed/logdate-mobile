package app.logdate.feature.core.settings.account.signin

import app.logdate.client.networking.PasskeyApiErrorCodes
import app.logdate.client.networking.PasskeyApiException
import app.logdate.client.permissions.PasskeyErrorCodes
import app.logdate.client.permissions.PasskeyException
import app.logdate.client.permissions.PasskeyManager
import app.logdate.client.repository.account.LinkedSignInProvider
import app.logdate.client.repository.account.NotSignedInException
import app.logdate.feature.core.settings.account.StubPasskeyAccountRepository
import app.logdate.shared.model.PasskeyAuthenticationOptions
import app.logdate.shared.model.PasskeyCapabilities
import app.logdate.shared.model.PasskeyInfo
import app.logdate.shared.model.PasskeyRegistrationOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class SignInMethodsViewModelTest {
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
    fun `passkeys are listed by device with the days they were added and last used`() =
        runTest {
            val repository =
                FakeAccountRepository(
                    passkeys =
                        listOf(
                            passkey(
                                credentialId = "cred-pixel",
                                nickname = "Pixel 9",
                                createdAt = "2026-09-03T10:00:00Z",
                                lastUsedAt = "2026-09-22T18:00:00Z",
                            ),
                        ),
                )

            val state = assertIs<SignInMethodsUiState.Loaded>(viewModel(repository).state.value)

            val row = state.passkeys.single()
            assertEquals("cred-pixel", row.credentialId)
            assertEquals("Pixel 9", row.deviceName)
            assertEquals(LocalDate(2026, 9, 3), row.addedOn)
            assertEquals(LocalDate(2026, 9, 22), row.lastUsedOn)
        }

    @Test
    fun `a passkey still carrying the server's default name is shown as unnamed`() =
        runTest {
            val repository = FakeAccountRepository(passkeys = listOf(passkey(nickname = "LogDate")))

            val state = assertIs<SignInMethodsUiState.Loaded>(viewModel(repository, defaultPasskeyName = "LogDate").state.value)

            assertNull(state.passkeys.single().deviceName)
        }

    @Test
    fun `a linked Google account is listed with its address`() =
        runTest {
            val repository =
                FakeAccountRepository(
                    passkeys = listOf(passkey()),
                    linkedProviders =
                        listOf(
                            LinkedSignInProvider(
                                kind = LinkedSignInProvider.Kind.GOOGLE,
                                email = "alice@example.com",
                                linkedAt = Instant.parse("2026-09-01T10:00:00Z"),
                                lastSignInAt = Instant.parse("2026-09-20T10:00:00Z"),
                            ),
                        ),
                )

            val state = assertIs<SignInMethodsUiState.Loaded>(viewModel(repository).state.value)

            val google = state.linkedProviders.single()
            assertEquals(LinkedSignInProvider.Kind.GOOGLE, google.kind)
            assertEquals("alice@example.com", google.email)
            assertEquals(LocalDate(2026, 9, 20), google.lastSignInOn)
        }

    @Test
    fun `the only way to sign in cannot be removed`() =
        runTest {
            val repository = FakeAccountRepository(passkeys = listOf(passkey()))

            val state = assertIs<SignInMethodsUiState.Loaded>(viewModel(repository).state.value)

            assertFalse(state.passkeys.single().canRemove)
        }

    @Test
    fun `a passkey can be removed when a linked account still signs in`() =
        runTest {
            val repository =
                FakeAccountRepository(
                    passkeys = listOf(passkey()),
                    linkedProviders = listOf(googleProvider()),
                )

            val state = assertIs<SignInMethodsUiState.Loaded>(viewModel(repository).state.value)

            assertTrue(state.passkeys.single().canRemove)
        }

    @Test
    fun `adding a passkey announces it and refreshes the list`() =
        runTest {
            val repository = FakeAccountRepository(passkeys = listOf(passkey(credentialId = "cred-a")))
            val viewModel = viewModel(repository)
            val events = mutableListOf<SignInMethodsEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }

            repository.passkeys = repository.passkeys + passkey(credentialId = "cred-b", nickname = "Pixel 9")
            repository.addPasskeyResult = Result.success(passkey(credentialId = "cred-b", nickname = "Pixel 9"))
            viewModel.addPasskey()

            assertEquals(listOf<SignInMethodsEvent>(SignInMethodsEvent.PasskeyAdded(deviceName = "Pixel 9")), events)
            val state = assertIs<SignInMethodsUiState.Loaded>(viewModel.state.value)
            assertEquals(listOf("cred-a", "cred-b"), state.passkeys.map { it.credentialId })
            assertFalse(state.isAddingPasskey)
        }

    @Test
    fun `cancelling the passkey prompt says nothing`() =
        runTest {
            val repository =
                FakeAccountRepository(
                    passkeys = listOf(passkey()),
                    addPasskeyResult = Result.failure(PasskeyException("Cancelled", PasskeyErrorCodes.USER_CANCELLED)),
                )
            val viewModel = viewModel(repository)
            val events = mutableListOf<SignInMethodsEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }

            viewModel.addPasskey()

            assertTrue(events.isEmpty())
            assertFalse(assertIs<SignInMethodsUiState.Loaded>(viewModel.state.value).isAddingPasskey)
        }

    @Test
    fun `a passkey this device already holds is reported as such`() =
        runTest {
            val repository =
                FakeAccountRepository(
                    passkeys = listOf(passkey()),
                    addPasskeyResult = Result.failure(PasskeyException("Exists", PasskeyErrorCodes.INVALID_STATE)),
                )
            val viewModel = viewModel(repository)
            val events = mutableListOf<SignInMethodsEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }

            viewModel.addPasskey()

            assertEquals(
                listOf<SignInMethodsEvent>(SignInMethodsEvent.AddPasskeyFailed(AddPasskeyFailure.ALREADY_ON_THIS_DEVICE)),
                events,
            )
        }

    @Test
    fun `adding a passkey is not offered where the platform has no passkeys`() =
        runTest {
            val repository = FakeAccountRepository(passkeys = listOf(passkey()))

            val state =
                assertIs<SignInMethodsUiState.Loaded>(
                    viewModel(repository, passkeyManager = FakePasskeyManager(supported = false)).state.value,
                )

            assertFalse(state.canAddPasskey)
        }

    @Test
    fun `removing a passkey announces it and refreshes the list`() =
        runTest {
            val repository =
                FakeAccountRepository(passkeys = listOf(passkey(credentialId = "cred-a"), passkey(credentialId = "cred-b")))
            val viewModel = viewModel(repository)
            val events = mutableListOf<SignInMethodsEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }

            viewModel.removePasskey("cred-a")

            assertEquals(listOf("cred-a"), repository.deletedCredentialIds)
            assertEquals(listOf<SignInMethodsEvent>(SignInMethodsEvent.PasskeyRemoved), events)
            val state = assertIs<SignInMethodsUiState.Loaded>(viewModel.state.value)
            assertEquals(listOf("cred-b"), state.passkeys.map { it.credentialId })
            assertNull(state.removingCredentialId)
        }

    @Test
    fun `the server refusing to remove the last sign-in method is reported as such`() =
        runTest {
            val repository =
                FakeAccountRepository(
                    passkeys = listOf(passkey(credentialId = "cred-a"), passkey(credentialId = "cred-b")),
                    deleteResult = Result.failure(PasskeyApiException(PasskeyApiErrorCodes.LAST_SIGNIN_FACTOR, "Last")),
                )
            val viewModel = viewModel(repository)
            val events = mutableListOf<SignInMethodsEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }

            viewModel.removePasskey("cred-a")

            assertEquals(
                listOf<SignInMethodsEvent>(SignInMethodsEvent.RemovePasskeyFailed(RemovePasskeyFailure.LAST_SIGN_IN_METHOD)),
                events,
            )
        }

    @Test
    fun `adding a passkey is not offered for a server this platform can't create passkeys for`() =
        runTest {
            val repository = FakeAccountRepository(passkeys = listOf(passkey()))

            val state =
                assertIs<SignInMethodsUiState.Loaded>(
                    viewModel(
                        repository,
                        connectedRpId = "journal.example.com",
                        passkeysWorkWith = { it.endsWith("logdate.app") },
                    ).state.value,
                )

            assertFalse(state.canAddPasskey)
        }

    @Test
    fun `passkeys still show when linked accounts can't be loaded`() =
        runTest {
            val repository =
                FakeAccountRepository(
                    passkeys = listOf(passkey()),
                    providersError = PasskeyApiException(PasskeyApiErrorCodes.SERVER_ERROR, "Not found"),
                )

            val state = assertIs<SignInMethodsUiState.Loaded>(viewModel(repository).state.value)

            assertEquals(1, state.passkeys.size)
            assertTrue(state.linkedProviders.isEmpty())
        }

    @Test
    fun `an unreachable server is reported as offline and can be retried`() =
        runTest {
            val repository =
                FakeAccountRepository(
                    passkeys = listOf(passkey()),
                    listError = PasskeyApiException(PasskeyApiErrorCodes.NETWORK_ERROR, "Offline"),
                )
            val viewModel = viewModel(repository)

            assertEquals(SignInMethodsUiState.Failed(SignInMethodsLoadError.OFFLINE), viewModel.state.value)

            repository.listError = null
            viewModel.refresh()

            assertIs<SignInMethodsUiState.Loaded>(viewModel.state.value)
        }

    @Test
    fun `without a session the screen says so`() =
        runTest {
            val repository = FakeAccountRepository(listError = NotSignedInException())

            assertEquals(
                SignInMethodsUiState.Failed(SignInMethodsLoadError.NOT_SIGNED_IN),
                viewModel(repository).state.value,
            )
        }

    private fun viewModel(
        repository: FakeAccountRepository,
        passkeyManager: PasskeyManager = FakePasskeyManager(),
        defaultPasskeyName: String? = "LogDate",
        connectedRpId: String? = null,
        passkeysWorkWith: (String) -> Boolean = { true },
    ) = SignInMethodsViewModel(
        accountRepository = repository,
        passkeyManager = passkeyManager,
        defaultPasskeyName = { defaultPasskeyName },
        connectedRpId = { connectedRpId },
        passkeysWorkWith = passkeysWorkWith,
        timeZone = TimeZone.UTC,
    )

    private fun passkey(
        credentialId: String = "cred-${Uuid.random()}",
        nickname: String? = "Pixel 9",
        createdAt: String = "2026-09-01T10:00:00Z",
        lastUsedAt: String? = null,
    ) = PasskeyInfo(
        id = Uuid.random(),
        credentialId = credentialId,
        nickname = nickname,
        deviceType = "platform",
        createdAt = Instant.parse(createdAt),
        lastUsedAt = lastUsedAt?.let { Instant.parse(it) },
        isActive = true,
    )

    private fun googleProvider() =
        LinkedSignInProvider(
            kind = LinkedSignInProvider.Kind.GOOGLE,
            email = "alice@example.com",
            linkedAt = Instant.parse("2026-09-01T10:00:00Z"),
            lastSignInAt = null,
        )

    private class FakeAccountRepository(
        var passkeys: List<PasskeyInfo> = emptyList(),
        var linkedProviders: List<LinkedSignInProvider> = emptyList(),
        var listError: Throwable? = null,
        var providersError: Throwable? = null,
        var addPasskeyResult: Result<PasskeyInfo> = Result.failure(NotImplementedError()),
        var deleteResult: Result<Unit> = Result.success(Unit),
    ) : StubPasskeyAccountRepository() {
        val deletedCredentialIds = mutableListOf<String>()

        override suspend fun listPasskeys(): Result<List<PasskeyInfo>> = listError?.let { Result.failure(it) } ?: Result.success(passkeys)

        override suspend fun listLinkedSignInProviders(): Result<List<LinkedSignInProvider>> =
            (listError ?: providersError)?.let { Result.failure(it) } ?: Result.success(linkedProviders)

        override suspend fun addPasskey(): Result<PasskeyInfo> = addPasskeyResult

        override suspend fun deletePasskey(credentialId: String): Result<Unit> {
            deletedCredentialIds += credentialId
            return deleteResult.onSuccess { passkeys = passkeys.filterNot { it.credentialId == credentialId } }
        }
    }

    private class FakePasskeyManager(
        private val supported: Boolean = true,
    ) : PasskeyManager {
        override suspend fun getCapabilities(): PasskeyCapabilities =
            PasskeyCapabilities(
                isSupported = supported,
                isPlatformAuthenticatorAvailable = supported,
                supportedAlgorithms = listOf("ES256"),
            )

        override suspend fun isPlatformAuthenticatorAvailable(): Boolean = supported

        override suspend fun registerPasskey(options: PasskeyRegistrationOptions): Result<String> = Result.failure(NotImplementedError())

        override suspend fun authenticateWithPasskey(options: PasskeyAuthenticationOptions): Result<String> =
            Result.failure(NotImplementedError())

        override fun getAvailabilityStatus(): Flow<PasskeyCapabilities> = emptyFlow()
    }
}
