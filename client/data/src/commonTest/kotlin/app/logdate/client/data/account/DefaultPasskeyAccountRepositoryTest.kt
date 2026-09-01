package app.logdate.client.data.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.PlatformAccountInfo
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.device.TokenPair
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.PasskeyManager
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.client.repository.account.AccountCreationRequest
import app.logdate.client.repository.account.LocalDataAdoptionRequiredException
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.AccountTokens
import app.logdate.shared.model.BeginAccountCreationData
import app.logdate.shared.model.BeginAccountCreationRequest
import app.logdate.shared.model.BeginAuthenticationData
import app.logdate.shared.model.BeginAuthenticationRequest
import app.logdate.shared.model.CompleteAccountCreationData
import app.logdate.shared.model.CompleteAccountCreationRequest
import app.logdate.shared.model.CompleteAuthenticationData
import app.logdate.shared.model.CompleteAuthenticationRequest
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.EntitlementResponse
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.PasskeyAllowCredential
import app.logdate.shared.model.PasskeyAuthenticationOptions
import app.logdate.shared.model.PasskeyCapabilities
import app.logdate.shared.model.PasskeyRegistrationOptions
import app.logdate.shared.model.PasskeyUser
import app.logdate.shared.model.PublicKeyCredentialParameter
import app.logdate.shared.model.ServerCapability
import app.logdate.shared.model.ServerDescriptor
import app.logdate.shared.model.UsernameAvailabilityData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Comprehensive tests for [DefaultPasskeyAccountRepository] covering the full lifecycle of
 * passkey-based account management.
 *
 * This includes verifying account creation and authentication flows, session persistence,
 * automatic token refreshing, and integration with platform-level account managers and
 * passkey providers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPasskeyAccountRepositoryTest {
    private val testAccount =
        LogDateAccount(
            id = kotlin.uuid.Uuid.random(), // Use random() instead of private constructor
            username = "testuser",
            displayName = "Test User",
            bio = "Test bio",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        )

    private val testTokens =
        AccountTokens(
            accessToken = "access_token_123",
            refreshToken = "refresh_token_123",
        )

    private val testSession =
        UserSession(
            accessToken = testTokens.accessToken,
            refreshToken = testTokens.refreshToken,
            accountId = testAccount.id.toString(),
        )

    /**
     * Builds a repository whose background work runs on the test scheduler.
     *
     * The repository restores session state from `init`, so a scope backed by a real dispatcher
     * lets that work race the assertions -- the test then passes or fails depending on which
     * thread wins. Defaulting to [TestScope.backgroundScope] makes the ordering the test's to
     * control, and stops the work outliving the test.
     */
    private fun TestScope.createRepository(
        apiClient: FakePasskeyApiClient = FakePasskeyApiClient(),
        passkeyManager: FakePasskeyManager = FakePasskeyManager(),
        restoreCredentialManager: FakeRestoreCredentialManager = FakeRestoreCredentialManager(),
        sessionStorage: FakeSessionStorage = FakeSessionStorage(),
        platformAccountManager: FakePlatformAccountManager = FakePlatformAccountManager(),
        configRepository: FakeConfigRepository = FakeConfigRepository(),
        canonicalOwnerProvider: CanonicalOwnerProvider = FakeCanonicalOwnerProvider(testAccount.id.toString()),
        hasLocalData: suspend () -> Boolean = { false },
        repositoryScope: CoroutineScope = backgroundScope,
    ): DefaultPasskeyAccountRepository =
        DefaultPasskeyAccountRepository(
            apiClient = apiClient,
            passkeyManager = passkeyManager,
            restoreCredentialManager = restoreCredentialManager,
            sessionStorage = sessionStorage,
            platformAccountManager = platformAccountManager,
            configRepository = configRepository,
            canonicalOwnerProvider = canonicalOwnerProvider,
            hasLocalData = hasLocalData,
            repositoryScope = repositoryScope,
            json = Json { ignoreUnknownKeys = true },
        )

    /**
     * Tests that the repository correctly detects an existing session on initialization
     * and sets the authenticated state to true.
     */
    @Test
    fun `initialization with existing session sets authenticated state`() =
        runTest {
            val sessionStorage =
                FakeSessionStorage().apply {
                    saveSession(testSession)
                }

            val repository = createRepository(sessionStorage = sessionStorage, repositoryScope = repositoryObserverScope())

            advanceUntilIdle()

            assertTrue(repository.isAuthenticated.value)
        }

    /**
     * The repository observes its session for as long as its scope lives. Handing it the TestScope
     * meant runTest waited on collectors that never finish; handing it a real dispatcher made the
     * assertions race the observer. This scope runs eagerly and dies with the test.
     */
    private fun TestScope.repositoryObserverScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler)).also { scope ->
            backgroundScope.coroutineContext[Job]?.invokeOnCompletion { scope.cancel() }
        }

    /**
     * Tests that the repository starts in an unauthenticated state when no session exists.
     */
    @Test
    fun `initialization without session sets unauthenticated state`() =
        runTest {
            val repository = createRepository()

            assertFalse(repository.isAuthenticated.value)
            assertNull(repository.currentAccount.value)
        }

    @Test
    fun `initialization quarantines a session for a different local owner`() =
        runTest {
            val sessionStorage = FakeSessionStorage().apply { saveSession(testSession) }

            val repository =
                createRepository(
                    sessionStorage = sessionStorage,
                    canonicalOwnerProvider = FakeCanonicalOwnerProvider(Uuid.random().toString()),
                    repositoryScope = repositoryObserverScope(),
                )

            advanceUntilIdle()

            assertFalse(repository.isAuthenticated.value)
            assertNull(sessionStorage.getSession())
        }

    /**
     * Tests that username availability check returns available when the username is free.
     */
    @Test
    fun `check username availability returns available when username is free`() =
        runTest {
            val apiClient =
                FakePasskeyApiClient().apply {
                    usernameAvailabilityResponse =
                        Result.success(
                            UsernameAvailabilityData(
                                username = "newuser",
                                available = true,
                            ),
                        )
                }

            val repository = createRepository(apiClient = apiClient)
            val result = repository.checkUsernameAvailability("newuser")

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow())
        }

    /**
     * Tests that username availability check returns unavailable when the username is taken.
     */
    @Test
    fun `check username availability returns unavailable when username is taken`() =
        runTest {
            val apiClient =
                FakePasskeyApiClient().apply {
                    usernameAvailabilityResponse =
                        Result.success(
                            UsernameAvailabilityData(
                                username = "existinguser",
                                available = false,
                            ),
                        )
                }

            val repository = createRepository(apiClient = apiClient)
            val result = repository.checkUsernameAvailability("existinguser")

            assertTrue(result.isSuccess)
            assertFalse(result.getOrThrow())
        }

    /**
     * Tests that username availability check properly handles API errors.
     */
    @Test
    fun `check username availability handles api error`() =
        runTest {
            val apiClient =
                FakePasskeyApiClient().apply {
                    usernameAvailabilityResponse = Result.failure(Exception("Network error"))
                }

            val repository = createRepository(apiClient = apiClient)
            val result = repository.checkUsernameAvailability("testuser")

            assertTrue(result.isFailure)
        }

    /**
     * Tests the full account creation flow with passkeys.
     */
    @Test
    fun `create account with passkey succeeds with valid flow`() =
        runTest {
            val sessionStorage = FakeSessionStorage()
            val platformAccountManager = FakePlatformAccountManager()
            val configRepository = FakeConfigRepository()

            val repository =
                createRepository(
                    sessionStorage = sessionStorage,
                    platformAccountManager = platformAccountManager,
                    configRepository = configRepository,
                    repositoryScope = repositoryObserverScope(),
                )

            val request =
                AccountCreationRequest(
                    username = "newuser",
                    displayName = "New User",
                    bio = "New user bio",
                )

            val result = repository.createAccountWithPasskey(request)

            assertTrue(result.isSuccess)
            assertEquals(testAccount, result.getOrThrow())
            assertTrue(repository.isAuthenticated.value)
            assertEquals(testAccount, repository.currentAccount.value)

            // Verify session was stored
            val storedSession = sessionStorage.getSession()
            assertNotNull(storedSession)
            assertEquals(testTokens.accessToken, storedSession.accessToken)
            assertEquals(testTokens.refreshToken, storedSession.refreshToken)
            assertEquals(testAccount.id.toString(), storedSession.accountId)
        }

    @Test
    fun `create account with passkey does not persist a different cloud owner`() =
        runTest {
            val sessionStorage = FakeSessionStorage()
            val repository =
                createRepository(
                    sessionStorage = sessionStorage,
                    canonicalOwnerProvider = FakeCanonicalOwnerProvider(Uuid.random().toString()),
                )

            val result = repository.createAccountWithPasskey(AccountCreationRequest("newuser", "New User"))

            assertTrue(result.isFailure)
            assertNull(sessionStorage.getSession())
            assertFalse(repository.isAuthenticated.value)
        }

    /**
     * Tests that account creation fails when the API cannot begin account creation.
     */
    @Test
    fun `create account with passkey handles begin account creation failure`() =
        runTest {
            val apiClient =
                FakePasskeyApiClient().apply {
                    beginAccountCreationResponse = Result.failure(Exception("Username already exists"))
                }

            val repository = createRepository(apiClient = apiClient)

            val request =
                AccountCreationRequest(
                    username = "existinguser",
                    displayName = "User",
                    bio = "Bio",
                )

            val result = repository.createAccountWithPasskey(request)

            assertTrue(result.isFailure)
            assertFalse(repository.isAuthenticated.value)
            assertNull(repository.currentAccount.value)
        }

    /**
     * Tests that account creation fails when passkey registration fails.
     */
    @Test
    fun `create account with passkey handles passkey registration failure`() =
        runTest {
            val passkeyManager =
                FakePasskeyManager().apply {
                    registerPasskeyResponse = Result.failure(Exception("User cancelled"))
                }

            val repository = createRepository(passkeyManager = passkeyManager)

            val request =
                AccountCreationRequest(
                    username = "newuser",
                    displayName = "User",
                    bio = "Bio",
                )

            val result = repository.createAccountWithPasskey(request)

            assertTrue(result.isFailure)
            assertFalse(repository.isAuthenticated.value)
        }

    /**
     * Tests that account creation fails when the API cannot complete account creation.
     */
    @Test
    fun `create account with passkey handles complete account creation failure`() =
        runTest {
            val apiClient =
                FakePasskeyApiClient().apply {
                    completeAccountCreationResponse = Result.failure(Exception("Invalid credential"))
                }

            val repository = createRepository(apiClient = apiClient)

            val request =
                AccountCreationRequest(
                    username = "newuser",
                    displayName = "User",
                    bio = "Bio",
                )

            val result = repository.createAccountWithPasskey(request)

            assertTrue(result.isFailure)
            assertFalse(repository.isAuthenticated.value)
        }

    /**
     * Tests the full authentication flow with passkeys.
     */
    @Test
    fun `authenticate with passkey succeeds with valid flow`() =
        runTest {
            val sessionStorage = FakeSessionStorage()
            val platformAccountManager = FakePlatformAccountManager()

            val repository =
                createRepository(
                    sessionStorage = sessionStorage,
                    platformAccountManager = platformAccountManager,
                )

            val result = repository.authenticateWithPasskey("testuser")

            assertTrue(result.isSuccess)
            assertEquals(testAccount, result.getOrThrow())
            assertTrue(repository.isAuthenticated.value)
            assertEquals(testAccount, repository.currentAccount.value)

            // Verify session was stored
            val storedSession = sessionStorage.getSession()
            assertNotNull(storedSession)
            assertEquals(testTokens.accessToken, storedSession.accessToken)
            assertEquals(testTokens.refreshToken, storedSession.refreshToken)
            assertEquals(testAccount.id.toString(), storedSession.accountId)
        }

    @Test
    fun `authenticate with passkey does not replace the installation owner`() =
        runTest {
            val sessionStorage = FakeSessionStorage()
            val repository =
                createRepository(
                    sessionStorage = sessionStorage,
                    canonicalOwnerProvider = FakeCanonicalOwnerProvider(Uuid.random().toString()),
                )

            val result = repository.authenticateWithPasskey("testuser")

            assertTrue(result.isFailure)
            assertNull(sessionStorage.getSession())
            assertFalse(repository.isAuthenticated.value)
        }

    @Test
    fun `authenticate with passkey binds an unused installation to the authenticated owner`() =
        runTest {
            val sessionStorage = FakeSessionStorage()
            val repository =
                createRepository(
                    sessionStorage = sessionStorage,
                    canonicalOwnerProvider = FreshInstallationCanonicalOwnerProvider(),
                )

            val result = repository.authenticateWithPasskey("testuser")

            assertTrue(result.isSuccess)
            assertEquals(testAccount.id.toString(), sessionStorage.getSession()?.accountId)
        }

    @Test
    fun `authenticateWithPasskey asks before adopting an installation that already has entries`() =
        runTest {
            val sessionStorage = FakeSessionStorage()
            val repository =
                createRepository(
                    sessionStorage = sessionStorage,
                    canonicalOwnerProvider = FreshInstallationCanonicalOwnerProvider(),
                    hasLocalData = { true },
                )

            val result = repository.authenticateWithPasskey("testuser")

            assertTrue(result.isFailure)
            assertTrue(
                result.exceptionOrNull() is LocalDataAdoptionRequiredException,
                "the caller has to be able to tell this apart from a refusal so it can ask",
            )
            assertNull(sessionStorage.getSession())
        }

    @Test
    fun `authenticateWithPasskey adopts an installation with entries once the user agrees`() =
        runTest {
            val sessionStorage = FakeSessionStorage()
            val repository =
                createRepository(
                    sessionStorage = sessionStorage,
                    canonicalOwnerProvider = FreshInstallationCanonicalOwnerProvider(),
                    hasLocalData = { true },
                )

            val result = repository.authenticateWithPasskey("testuser", adoptLocalData = true)

            assertTrue(result.isSuccess)
            assertEquals(testAccount.id.toString(), sessionStorage.getSession()?.accountId)
        }

    @Test
    fun `authenticateWithPasskey still refuses an installation bound to a different account`() =
        runTest {
            val sessionStorage = FakeSessionStorage()
            val repository =
                createRepository(
                    sessionStorage = sessionStorage,
                    canonicalOwnerProvider = FakeCanonicalOwnerProvider(Uuid.random().toString()),
                    hasLocalData = { true },
                )

            val result = repository.authenticateWithPasskey("testuser", adoptLocalData = true)

            assertTrue(result.isFailure, "consent must not override an existing account binding")
            assertNull(sessionStorage.getSession())
        }

    @Test
    fun `authenticateWithPasskey refuses an alternate server without canonical owner binding`() =
        runTest {
            val configRepository =
                FakeConfigRepository().apply {
                    updateBackendUrl("https://alternate.logdate.test")
                    updateServerDescriptor(
                        ServerDescriptor(
                            serverOrigin = "https://alternate.logdate.test",
                            apiBaseUrl = "https://alternate.logdate.test/api/v1",
                            deploymentKind = DeploymentKind.SELF_HOSTED,
                            displayName = "Alternate LogDate",
                            capabilities = listOf(ServerCapability.AUTH_PASSKEY),
                        ),
                    )
                }
            val apiClient = FakePasskeyApiClient()
            val repository = createRepository(apiClient = apiClient, configRepository = configRepository)

            val result = repository.authenticateWithPasskey("testuser")

            assertTrue(result.isFailure)
            assertEquals(0, apiClient.beginAuthenticationCalls)
        }

    @Test
    fun `sign in with restore key does not replace the installation owner`() =
        runTest {
            val sessionStorage = FakeSessionStorage()
            val repository =
                createRepository(
                    sessionStorage = sessionStorage,
                    canonicalOwnerProvider = FakeCanonicalOwnerProvider(Uuid.random().toString()),
                )

            val result = repository.signInWithRestoreKey()

            assertTrue(result.isFailure)
            assertNull(sessionStorage.getSession())
        }

    /**
     * Tests that authentication fails when the API cannot begin authentication.
     */
    @Test
    fun `authenticate with passkey handles begin authentication failure`() =
        runTest {
            val apiClient =
                FakePasskeyApiClient().apply {
                    beginAuthenticationResponse = Result.failure(Exception("User not found"))
                }

            val repository = createRepository(apiClient = apiClient)
            val result = repository.authenticateWithPasskey("nonexistentuser")

            assertTrue(result.isFailure)
            assertFalse(repository.isAuthenticated.value)
        }

    /**
     * Tests that authentication fails when passkey authentication fails.
     */
    @Test
    fun `authenticate with passkey handles passkey authentication failure`() =
        runTest {
            val passkeyManager =
                FakePasskeyManager().apply {
                    authenticateWithPasskeyResponse = Result.failure(Exception("Authentication failed"))
                }

            val repository = createRepository(passkeyManager = passkeyManager)
            val result = repository.authenticateWithPasskey("testuser")

            assertTrue(result.isFailure)
            assertFalse(repository.isAuthenticated.value)
        }

    /**
     * Tests that signing out clears the session and resets the authentication state.
     */
    @Test
    fun `sign out clears session and resets state`() =
        runTest {
            val sessionStorage =
                FakeSessionStorage().apply {
                    saveSession(testSession)
                }
            val platformAccountManager = FakePlatformAccountManager()

            val repository =
                createRepository(
                    sessionStorage = sessionStorage,
                    platformAccountManager = platformAccountManager,
                )

            // Set up authenticated state
            repository.createAccountWithPasskey(
                AccountCreationRequest("user", "User", "Bio"),
            )

            val result = repository.signOut()

            assertTrue(result.isSuccess)
            assertFalse(repository.isAuthenticated.value)
            assertNull(repository.currentAccount.value)
            assertNull(sessionStorage.getSession())
            assertEquals(1, platformAccountManager.removeAccountCalls)
        }

    @Test
    fun `sign out revokes the remote refresh token before clearing local credentials`() =
        runTest {
            val sessionStorage = FakeSessionStorage().apply { saveSession(testSession) }
            val apiClient = FakePasskeyApiClient()
            val repository = createRepository(apiClient = apiClient, sessionStorage = sessionStorage)

            repository.createAccountWithPasskey(AccountCreationRequest("user", "User", "Bio"))

            val result = repository.signOut()

            assertTrue(result.isSuccess)
            assertEquals(testTokens.refreshToken, apiClient.loggedOutRefreshToken)
            assertNull(sessionStorage.getSession())
        }

    /**
     * Tests that token refresh successfully updates the session with a new access token.
     */
    @Test
    fun `refresh authentication succeeds with valid refresh token`() =
        runTest {
            val sessionStorage =
                FakeSessionStorage().apply {
                    saveSession(testSession)
                }
            val apiClient =
                FakePasskeyApiClient().apply {
                    refreshTokenResponse = Result.success("new_access_token")
                }

            val repository =
                createRepository(
                    sessionStorage = sessionStorage,
                    apiClient = apiClient,
                )

            val result = repository.refreshAuthentication()

            assertTrue(result.isSuccess)

            // Verify session was updated with new access token
            val updatedSession = sessionStorage.getSession()
            assertNotNull(updatedSession)
            assertEquals("new_access_token", updatedSession.accessToken)
            assertEquals(testTokens.refreshToken, updatedSession.refreshToken)
        }

    /**
     * Tests that token refresh failure clears the session and resets the authentication state.
     */
    @Test
    fun `refresh authentication clears session on failure`() =
        runTest {
            val sessionStorage =
                FakeSessionStorage().apply {
                    saveSession(testSession)
                }
            val apiClient =
                FakePasskeyApiClient().apply {
                    refreshTokenResponse = Result.failure(Exception("Invalid refresh token"))
                }

            val repository =
                createRepository(
                    sessionStorage = sessionStorage,
                    apiClient = apiClient,
                )

            val result = repository.refreshAuthentication()

            assertTrue(result.isFailure)
            assertNull(sessionStorage.getSession())
            assertFalse(repository.isAuthenticated.value)
        }

    /**
     * Tests that getAccountInfo returns account info when the API call succeeds.
     */
    @Test
    fun `get account info succeeds with valid session`() =
        runTest {
            val sessionStorage =
                FakeSessionStorage().apply {
                    saveSession(testSession)
                }
            val apiClient =
                FakePasskeyApiClient().apply {
                    getAccountInfoResponse = Result.success(testAccount)
                }

            val repository =
                createRepository(
                    sessionStorage = sessionStorage,
                    apiClient = apiClient,
                )

            val result = repository.getAccountInfo()

            assertTrue(result.isSuccess)
            assertEquals(testAccount, result.getOrThrow())
            assertEquals(testAccount, repository.currentAccount.value)
        }

    /**
     * Tests that getAccountInfo retries after token refresh when the initial call fails.
     */
    @Test
    fun `get account info retries after token refresh on authentication failure`() =
        runTest {
            val sessionStorage =
                FakeSessionStorage().apply {
                    saveSession(testSession)
                }
            val apiClient =
                FakePasskeyApiClient().apply {
                    // First call fails, second succeeds after refresh
                    refreshTokenResponse = Result.success("new_access_token")

                    // Override to simulate retry behavior
                    getAccountInfoResponses =
                        listOf(
                            Result.failure(Exception("Unauthorized")),
                            Result.success(testAccount),
                        )
                }

            val repository =
                createRepository(
                    sessionStorage = sessionStorage,
                    apiClient = apiClient,
                )

            val result = repository.getAccountInfo()

            assertTrue(result.isSuccess)
            assertEquals(testAccount, result.getOrThrow())
        }

    /**
     * Tests that deleting a passkey succeeds when the API call succeeds.
     */
    @Test
    fun `delete passkey succeeds with valid session`() =
        runTest {
            val sessionStorage =
                FakeSessionStorage().apply {
                    saveSession(testSession)
                }
            val apiClient =
                FakePasskeyApiClient().apply {
                    deletePasskeyResponse = Result.success(Unit)
                }

            val repository =
                createRepository(
                    sessionStorage = sessionStorage,
                    apiClient = apiClient,
                )

            val result = repository.deletePasskey("credential123")

            assertTrue(result.isSuccess)
        }

    /**
     * Tests that deletePasskey retries after token refresh when the initial call fails.
     */
    @Test
    fun `delete passkey retries after token refresh on authentication failure`() =
        runTest {
            val sessionStorage =
                FakeSessionStorage().apply {
                    saveSession(testSession)
                }
            val apiClient =
                FakePasskeyApiClient().apply {
                    refreshTokenResponse = Result.success("new_access_token")
                    deletePasskeyResponses =
                        listOf(
                            Result.failure(Exception("Unauthorized")),
                            Result.success(Unit),
                        )
                }

            val repository =
                createRepository(
                    sessionStorage = sessionStorage,
                    apiClient = apiClient,
                )

            val result = repository.deletePasskey("credential123")

            assertTrue(result.isSuccess)
        }

    /**
     * Tests that getCurrentAccount returns the current account state.
     */
    @Test
    fun `get current account returns current account state`() =
        runTest {
            val repository = createRepository()

            // Initially null
            assertNull(repository.getCurrentAccount())

            // After successful account creation
            repository.createAccountWithPasskey(
                AccountCreationRequest("user", "User", "Bio"),
            )

            assertEquals(testAccount, repository.getCurrentAccount())
        }

    // Fake implementations for testing

    /**
     * Fake implementation of PasskeyApiClient for testing.
     */
    inner class FakePasskeyApiClient : PasskeyApiClientContract {
        var usernameAvailabilityResponse: Result<UsernameAvailabilityData> =
            Result.success(
                UsernameAvailabilityData(
                    username = "test",
                    available = true,
                ),
            )

        var beginAccountCreationResponse: Result<BeginAccountCreationData> =
            Result.success(
                BeginAccountCreationData(
                    sessionToken = "session123",
                    registrationOptions =
                        PasskeyRegistrationOptions(
                            challenge = "challenge123",
                            rpId = "logdate.app",
                            rpName = "LogDate",
                            user =
                                PasskeyUser(
                                    id = "user123",
                                    name = "testuser",
                                    displayName = "Test User",
                                ),
                            pubKeyCredParams =
                                listOf(
                                    PublicKeyCredentialParameter(
                                        type = "public-key",
                                        alg = -7,
                                    ),
                                ),
                            timeout = 300000,
                        ),
                ),
            )

        var completeAccountCreationResponse: Result<CompleteAccountCreationData> =
            Result.success(
                CompleteAccountCreationData(
                    account = testAccount,
                    tokens = testTokens,
                ),
            )

        var beginAuthenticationResponse: Result<BeginAuthenticationData> =
            Result.success(
                BeginAuthenticationData(
                    challenge = "challenge123",
                    rpId = "logdate.app",
                    allowCredentials =
                        listOf(
                            PasskeyAllowCredential(id = "cred123", type = "public-key", transports = listOf("internal")),
                        ),
                    timeout = 300000,
                    userVerification = "preferred",
                ),
            )

        var completeAuthenticationResponse: Result<CompleteAuthenticationData> =
            Result.success(
                CompleteAuthenticationData(
                    account = testAccount,
                    tokens = testTokens,
                ),
            )

        var refreshTokenResponse: Result<String> = Result.success("new_access_token")
        var deletePasskeyResponse: Result<Unit> = Result.success(Unit)
        var deleteAccountResponse: Result<Unit> = Result.success(Unit)
        var logoutResponse: Result<Unit> = Result.success(Unit)
        var loggedOutRefreshToken: String? = null
        var getAccountInfoResponse: Result<LogDateAccount> = Result.success(testAccount)
        var getAccountInfoResponses: List<Result<LogDateAccount>>? = null
        var deletePasskeyResponses: List<Result<Unit>>? = null

        private var getAccountInfoCallCount = 0
        private var deletePasskeyCallCount = 0
        var beginAuthenticationCalls = 0

        override suspend fun checkUsernameAvailability(username: String): Result<UsernameAvailabilityData> = usernameAvailabilityResponse

        override suspend fun beginAccountCreation(request: BeginAccountCreationRequest): Result<BeginAccountCreationData> =
            beginAccountCreationResponse

        override suspend fun completeAccountCreation(request: CompleteAccountCreationRequest): Result<CompleteAccountCreationData> =
            completeAccountCreationResponse

        override suspend fun beginAuthentication(request: BeginAuthenticationRequest): Result<BeginAuthenticationData> {
            beginAuthenticationCalls++
            return beginAuthenticationResponse
        }

        override suspend fun completeAuthentication(request: CompleteAuthenticationRequest): Result<CompleteAuthenticationData> =
            completeAuthenticationResponse

        override suspend fun getAccountInfo(accessToken: String): Result<LogDateAccount> =
            getAccountInfoResponses?.let { responses ->
                responses[getAccountInfoCallCount++.coerceAtMost(responses.size - 1)]
            } ?: getAccountInfoResponse

        override suspend fun updateAccountProfile(
            accessToken: String,
            displayName: String?,
            username: String?,
            bio: String?,
        ): Result<LogDateAccount> =
            getAccountInfoResponse.map { account ->
                account.copy(
                    displayName = displayName ?: account.displayName,
                    username = username ?: account.username,
                    bio = bio ?: account.bio,
                )
            }

        override suspend fun refreshToken(refreshToken: String): Result<String> = refreshTokenResponse

        override suspend fun logout(refreshToken: String): Result<Unit> {
            loggedOutRefreshToken = refreshToken
            return logoutResponse
        }

        override suspend fun deletePasskey(
            accessToken: String,
            credentialId: String,
        ): Result<Unit> =
            deletePasskeyResponses?.let { responses ->
                responses[deletePasskeyCallCount++.coerceAtMost(responses.size - 1)]
            } ?: deletePasskeyResponse

        override suspend fun beginRestoreKeyRegistration(accessToken: String): Result<PasskeyRegistrationOptions> =
            Result.success(beginAccountCreationResponse.getOrThrow().registrationOptions)

        override suspend fun completeRestoreKeyRegistration(
            accessToken: String,
            credentialJson: String,
            challenge: String,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun beginRestoreSignIn(): Result<BeginAuthenticationData> = beginAuthenticationResponse

        override suspend fun completeRestoreSignIn(request: CompleteAuthenticationRequest): Result<CompleteAuthenticationData> =
            completeAuthenticationResponse

        override suspend fun deleteAccount(accessToken: String): Result<Unit> = deleteAccountResponse

        override suspend fun getEntitlement(accessToken: String): Result<EntitlementResponse> =
            Result.failure(NotImplementedError("getEntitlement not exercised by these tests"))
    }

    /**
     * Fake implementation of PasskeyManager for testing.
     */
    class FakePasskeyManager : PasskeyManager {
        var registerPasskeyResponse: Result<String> =
            Result.success(
                """
                {
                  "id": "credential123",
                  "rawId": "credential123",
                  "type": "public-key",
                  "response": {
                    "clientDataJSON": "client-data",
                    "attestationObject": "attestation-object"
                  }
                }
                """.trimIndent(),
            )
        var authenticateWithPasskeyResponse: Result<String> =
            Result.success(
                """
                {
                  "id": "credential123",
                  "rawId": "credential123",
                  "type": "public-key",
                  "response": {
                    "clientDataJSON": "client-data",
                    "authenticatorData": "auth-data",
                    "signature": "signature",
                    "userHandle": "user-handle"
                  }
                }
                """.trimIndent(),
            )

        override suspend fun getCapabilities(): PasskeyCapabilities =
            PasskeyCapabilities(
                isSupported = true,
                isPlatformAuthenticatorAvailable = true,
                supportedAlgorithms = listOf("ES256"),
            )

        override suspend fun isPlatformAuthenticatorAvailable(): Boolean = true

        override suspend fun registerPasskey(options: PasskeyRegistrationOptions): Result<String> = registerPasskeyResponse

        override suspend fun authenticateWithPasskey(options: PasskeyAuthenticationOptions): Result<String> =
            authenticateWithPasskeyResponse

        override fun getAvailabilityStatus(): Flow<PasskeyCapabilities> =
            flow {
                emit(
                    PasskeyCapabilities(
                        isSupported = true,
                        isPlatformAuthenticatorAvailable = true,
                        supportedAlgorithms = listOf("ES256"),
                    ),
                )
            }
    }

    class FakeRestoreCredentialManager : RestoreCredentialManager {
        override suspend fun createRestoreKey(options: PasskeyRegistrationOptions): Result<String> =
            Result.success("""{"credential":"restore"}""")

        override suspend fun getRestoreCredential(options: PasskeyAuthenticationOptions): Result<String> =
            Result.success("""{"credential":"restore"}""")

        override suspend fun clearRestoreCredential(): Result<Unit> = Result.success(Unit)
    }

    /** An installation an account has already claimed. */
    private class FakeCanonicalOwnerProvider(
        private val ownerId: String,
    ) : CanonicalOwnerProvider {
        override suspend fun getCanonicalOwnerId(): String = ownerId

        override suspend fun hasBoundOwner(): Boolean = true
    }

    /** An installation no account has claimed, whether or not it has been used offline. */
    private class FreshInstallationCanonicalOwnerProvider : CanonicalOwnerProvider {
        private var ownerId: String? = null

        override suspend fun getCanonicalOwnerId(): String = ownerId ?: "a8a3400a-9f3c-4fca-9a7a-7c8cbe5ca24e"

        override suspend fun hasBoundOwner(): Boolean = ownerId != null

        override suspend fun adoptRemoteOwnerIfUninitialized(remoteOwnerId: String): Boolean {
            if (ownerId != null) return ownerId == remoteOwnerId
            ownerId = remoteOwnerId
            return true
        }
    }

    class FakeSessionStorage : SessionStorage {
        private var session: UserSession? = null
        private val sessionFlow = MutableStateFlow<UserSession?>(null)

        override fun getSession(): UserSession? = session

        override fun getSessionFlow(): Flow<UserSession?> = sessionFlow.asStateFlow()

        override suspend fun hasValidSession(): Boolean = session != null

        override fun saveSession(session: UserSession) {
            this.session = session
            sessionFlow.value = session
        }

        override fun clearSession() {
            session = null
            sessionFlow.value = null
        }
    }

    /**
     * Fake implementation of PlatformAccountManager for testing.
     */
    class FakePlatformAccountManager : PlatformAccountManager {
        var addAccountResponse: Result<Unit> = Result.success(Unit)
        var updateTokensResponse: Result<Unit> = Result.success(Unit)
        var removeAccountCalls = 0

        override suspend fun addAccount(
            account: LogDateAccount,
            accessToken: String,
            refreshToken: String,
            backendUrl: String,
        ): Result<Unit> = addAccountResponse

        override suspend fun updateAccount(
            account: LogDateAccount,
            backendUrl: String,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun updateTokens(
            username: String,
            backendUrl: String,
            accessToken: String,
            refreshToken: String,
        ): Result<Unit> = updateTokensResponse

        override suspend fun removeAccount(
            username: String,
            backendUrl: String,
        ): Result<Unit> {
            removeAccountCalls++
            return Result.success(Unit)
        }

        override suspend fun getStoredAccounts(): Result<List<PlatformAccountInfo>> = Result.success(emptyList())

        override suspend fun getTokens(
            username: String,
            backendUrl: String,
        ): Result<TokenPair?> =
            Result.success(
                TokenPair(
                    accessToken = "access_token_123",
                    refreshToken = "refresh_token_123",
                ),
            )

        override suspend fun clearAllTokens(): Result<Unit> = Result.success(Unit)
    }

    /**
     * Fake implementation of LogDateConfigRepository for testing.
     */
    class FakeConfigRepository : LogDateConfigRepository {
        private val _backendUrl = MutableStateFlow(DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL)
        private val _apiVersion = MutableStateFlow("v1")
        private val _apiBaseUrl = MutableStateFlow("${DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL}/api/v1")
        private val _localServerAddress = MutableStateFlow("localhost:8765")
        private val _serverDescriptor = MutableStateFlow<app.logdate.shared.model.ServerDescriptor?>(null)

        override val backendUrl: StateFlow<String> = _backendUrl.asStateFlow()
        override val apiVersion: StateFlow<String> = _apiVersion.asStateFlow()
        override val apiBaseUrl: Flow<String> = _apiBaseUrl.asStateFlow()
        override val localServerAddress: StateFlow<String> = _localServerAddress.asStateFlow()
        override val serverDescriptor: StateFlow<app.logdate.shared.model.ServerDescriptor?> = _serverDescriptor.asStateFlow()

        override suspend fun updateBackendUrl(url: String) {
            _backendUrl.value = url
            _apiBaseUrl.value = "${url.trimEnd('/')}/api/${_apiVersion.value}"
        }

        override suspend fun updateApiVersion(version: String) {
            _apiVersion.value = version
            _apiBaseUrl.value = "${_backendUrl.value.trimEnd('/')}/api/$version"
        }

        override suspend fun updateLocalServerAddress(address: String) {
            _localServerAddress.value = address
        }

        override suspend fun updateServerDescriptor(descriptor: app.logdate.shared.model.ServerDescriptor?) {
            _serverDescriptor.value = descriptor
        }

        override suspend fun resetToDefaults() {
            _backendUrl.value = DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL
            _apiVersion.value = "v1"
            _apiBaseUrl.value = "${DefaultLogDateConfigRepository.DEFAULT_BACKEND_URL}/api/v1"
            _localServerAddress.value = "localhost:8765"
            _serverDescriptor.value = null
        }

        override fun getCurrentBackendUrl(): String = _backendUrl.value

        override fun getCurrentApiBaseUrl(): String = _apiBaseUrl.value

        override fun getCurrentServerDescriptor(): app.logdate.shared.model.ServerDescriptor? = _serverDescriptor.value
    }
}
