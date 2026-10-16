package app.logdate.client.data.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.GoogleSignInManager
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.shared.model.CompleteAuthenticationData
import app.logdate.shared.model.PasskeyRegistrationOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreCredentialEnrollmentTest {
    @Test
    fun `already signed in installation enrolls and retries without a previous marker`() =
        runTest {
            val fixture = Fixture()
            fixture.sessions.saveSession(fixture.session)
            fixture.failures = 1
            fixture.repository(this)
            runCurrent()
            assertEquals(1, fixture.attempts)
            assertTrue(fixture.renewal.isPending(fixture.origin, fixture.owner))
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(2, fixture.attempts)
            assertFalse(fixture.renewal.isPending(fixture.origin, fixture.owner))
            fixture.repository(this)
            runCurrent()
            assertEquals(2, fixture.attempts, "resolved enrollment must not rotate on every launch")
        }

    @Test
    fun `explicit passkey sign in persists enrollment before session and recovers after restart`() =
        runTest {
            val fixture = Fixture()
            fixture.renewal.mark(fixture.origin, fixture.owner)
            fixture.renewal.renew(fixture.config, fixture.session, fixture.provider, fixture.platform) { true }
            var markedBeforeSession = false
            val sessions =
                object : SessionStorage by fixture.sessions {
                    override suspend fun saveSession(session: UserSession) {
                        markedBeforeSession = fixture.renewal.isPending(fixture.origin, fixture.owner)
                        fixture.sessions.saveSession(session)
                    }
                }
            val repository = fixture.repository(this, sessions)
            assertTrue(repository.authenticateWithPasskey(null, false).isSuccess)
            assertTrue(markedBeforeSession)
            runCurrent()
            assertFalse(fixture.renewal.isPending(fixture.origin, fixture.owner))
        }

    @Test
    fun `secondary server account cannot initialize enrollment`() =
        runTest {
            val fixture = Fixture()
            fixture.sessions.saveSession(fixture.session)
            fixture.repository(this, createsRestoreKey = false)
            runCurrent()
            assertEquals(0, fixture.attempts)
            assertFalse(fixture.renewal.isPending(fixture.origin, fixture.owner))
        }

    @Test
    fun `explicit Google sign in marks enrollment before publishing the session`() =
        runTest {
            val fixture = Fixture()
            var marked = false
            val sessions =
                object : SessionStorage by fixture.sessions {
                    override suspend fun saveSession(session: UserSession) {
                        marked = fixture.renewal.isPending(fixture.origin, fixture.owner)
                        fixture.sessions.saveSession(session)
                    }
                }
            assertTrue(fixture.repository(this, sessions).signInWithGoogle().isSuccess)
            assertTrue(marked)
            runCurrent()
            assertEquals(1, fixture.attempts)
        }

    @Test
    fun `startup storage failure retries without terminating the session observer`() =
        runTest {
            val backing = RenewalStorage()
            var reads = 0
            val storage =
                object : SecureStorage by backing {
                    override suspend fun getString(key: String): String? {
                        if (reads++ == 0) error("storage unavailable")
                        return backing.getString(key)
                    }
                }
            val fixture = Fixture(storage)
            fixture.sessions.saveSession(fixture.session)
            fixture.repository(this)
            runCurrent()
            assertEquals(0, fixture.attempts)
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(1, fixture.attempts)
        }

    @Test
    fun `server switch during enrollment marking cannot publish foreign tokens`() =
        runTest {
            val backing = RenewalStorage()
            var onWrite: suspend () -> Unit = {}
            val storage =
                object : SecureStorage by backing {
                    override suspend fun putString(
                        key: String,
                        value: String,
                    ) {
                        backing.putString(key, value)
                        onWrite()
                    }
                }
            val fixture = Fixture(storage)
            onWrite = { fixture.config.updateBackendUrl("https://other.example") }
            assertFalse(fixture.repository(this).authenticateWithPasskey(null, false).isSuccess)
            assertEquals(null, fixture.sessions.getSession())
            assertTrue(fixture.renewal.isPending(fixture.origin, fixture.owner))
            assertFalse(fixture.renewal.isPending("https://other.example", fixture.owner))
        }

    @Test
    fun `cancellation immediately after enrollment persistence cannot install a session`() =
        runTest {
            val backing = RenewalStorage()
            val storage =
                object : SecureStorage by backing {
                    override suspend fun putString(
                        key: String,
                        value: String,
                    ) {
                        backing.putString(key, value)
                        currentCoroutineContext().cancel()
                    }
                }
            val fixture = Fixture(storage)
            val repository = fixture.repository(this)
            val signIn = async { repository.authenticateWithPasskey(null, false) }
            runCurrent()
            assertTrue(signIn.isCancelled)
            assertNull(fixture.sessions.getSession())
            assertTrue(fixture.renewal.isPending(fixture.origin, fixture.owner))
        }

    @Test
    fun `unchanged tokens wake enrollment even when later platform bookkeeping throws`() =
        runTest {
            val fixture = Fixture()
            fixture.renewal.mark(fixture.origin, fixture.owner)
            fixture.renewal.renew(fixture.config, fixture.session, fixture.provider, fixture.platform) { true }
            fixture.sessions.saveSession(fixture.session)
            var updates = 0
            val platform =
                object : PlatformAccountManager by fixture.platform {
                    override suspend fun updateTokens(
                        username: String,
                        backendUrl: String,
                        accessToken: String,
                        refreshToken: String,
                    ): Result<Unit> {
                        if (updates++ == 0) error("platform unavailable")
                        return Result.success(Unit)
                    }
                }
            val repository = fixture.repository(this, platformManager = platform)
            runCurrent()
            assertEquals(1, fixture.attempts)
            assertFalse(repository.authenticateWithPasskey(null, false).isSuccess)
            runCurrent()
            assertEquals(2, fixture.attempts)
            assertFalse(fixture.renewal.isPending(fixture.origin, fixture.owner))
        }

    private class Fixture(
        storage: SecureStorage = RenewalStorage(),
    ) {
        private val source = DefaultPasskeyAccountRepositoryTest()
        private val delegate = source.FakePasskeyApiClient()
        val api =
            object : PasskeyApiClientContract by delegate {
                override suspend fun signInWithGoogle(
                    idToken: String,
                    nonce: String?,
                ): Result<CompleteAuthenticationData> = delegate.completeAuthenticationResponse
            }
        val config = DefaultPasskeyAccountRepositoryTest.FakeConfigRepository()
        val sessions = DefaultPasskeyAccountRepositoryTest.FakeSessionStorage()
        val platform = DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager()
        val origin = config.getCurrentBackendUrl()
        val owner =
            delegate.completeAuthenticationResponse
                .getOrThrow()
                .account.id
                .toString()
        val session =
            delegate.completeAuthenticationResponse.getOrThrow().let {
                UserSession(
                    it.tokens.accessToken,
                    it.tokens.refreshToken,
                    owner,
                )
            }
        val renewal = RestoreCredentialRenewal(storage) { api }
        var attempts = 0
        var failures = 0
        val provider =
            object : RestoreCredentialManager by DefaultPasskeyAccountRepositoryTest.FakeRestoreCredentialManager() {
                override suspend fun createRestoreKey(options: PasskeyRegistrationOptions): Result<String> {
                    attempts++
                    return if (attempts <= failures) Result.failure(IllegalStateException("provider unavailable")) else Result.success("{}")
                }
            }

        fun repository(
            scope: TestScope,
            sessionStorage: SessionStorage = sessions,
            createsRestoreKey: Boolean = true,
            platformManager: PlatformAccountManager = platform,
        ) = DefaultPasskeyAccountRepository(
            apiClient = api,
            passkeyManager = DefaultPasskeyAccountRepositoryTest.FakePasskeyManager(),
            restoreCredentialManager = provider,
            sessionStorage = sessionStorage,
            platformAccountManager = platformManager,
            configRepository = config,
            canonicalOwnerProvider =
                object : CanonicalOwnerProvider {
                    override suspend fun getCanonicalOwnerId() = owner

                    override suspend fun hasBoundOwner() = true
                },
            hasLocalData = { true },
            repositoryScope = scope.backgroundScope,
            createsRestoreKey = createsRestoreKey,
            restoreCredentialRenewal = renewal,
            googleSignInManager =
                object : GoogleSignInManager {
                    override suspend fun getGoogleIdToken(
                        serverClientId: String,
                        nonce: String?,
                    ) = Result.success("synthetic-google-token")
                },
        )
    }
}
