package app.logdate.client.data.account

import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.storage.SecureSessionStorage
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.shared.model.CompleteAuthenticationData
import app.logdate.shared.model.CompleteAuthenticationRequest
import app.logdate.shared.model.PasskeyAuthenticationOptions
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
class ForegroundRestoreContinuityTest {
    @Test
    fun `foreground restore retries a failed replacement without another login`() =
        runTest {
            val fixture = Fixture(this)
            var attempts = 0
            fixture.create = {
                attempts++
                if (attempts == 1) Result.failure(IllegalStateException("temporary outage")) else Result.success("credential")
            }
            val repository = fixture.repository()
            runCurrent()
            assertTrue(repository.signInWithRestoreKey().isSuccess)
            runCurrent()
            assertEquals(1, attempts)
            assertTrue(fixture.renewal.isPending(fixture.origin, fixture.owner))
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(2, attempts)
            assertFalse(fixture.renewal.isPending(fixture.origin, fixture.owner))
            assertEquals(fixture.owner, fixture.sessions.getSession()?.accountId)
        }

    @Test
    fun `server switch during retrieval never completes against the new server`() =
        runTest {
            val fixture = Fixture(this)
            fixture.retrieve = { fixture.config.updateBackendUrl("https://other.example") }
            val repository = fixture.repository()
            runCurrent()
            assertTrue(repository.signInWithRestoreKey().isFailure)
            assertEquals(0, fixture.completeCalls)
            assertNull(fixture.sessions.getSession())
            assertNull(fixture.sessions.read("https://other.example"))
        }

    @Test
    fun `server switch during owner validation cannot install recovered tokens`() =
        runTest {
            val fixture = Fixture(this)
            fixture.validateOwner = { fixture.config.updateBackendUrl("https://other.example") }
            val repository = fixture.repository()
            runCurrent()
            assertTrue(repository.signInWithRestoreKey().isFailure)
            assertNull(fixture.sessions.getSession())
            assertNull(fixture.sessions.read("https://other.example"))
        }

    @Test
    fun `restored foreground session creates its missing platform account`() =
        runTest {
            val fixture = Fixture(this)
            fixture.platform.updateTokensResponse = Result.failure(IllegalStateException("account absent"))
            val repository = fixture.repository()
            runCurrent()
            assertTrue(repository.signInWithRestoreKey().isSuccess)
            runCurrent()
            assertEquals(1, fixture.platform.addAccountCalls)
        }

    @Test
    fun `unchanged restored session still wakes replacement renewal`() =
        runTest {
            val fixture = Fixture(this)
            val auth = fixture.delegate.completeAuthenticationResponse.getOrThrow()
            fixture.sessions.saveSession(UserSession(auth.tokens.accessToken, auth.tokens.refreshToken, fixture.owner))
            var attempts = 0
            fixture.create = {
                attempts++
                Result.success("credential")
            }
            val repository = fixture.repository()
            runCurrent()
            assertEquals(0, attempts)
            assertTrue(repository.signInWithRestoreKey().isSuccess)
            runCurrent()
            assertEquals(1, attempts)
            assertFalse(fixture.renewal.isPending(fixture.origin, fixture.owner))
        }

    @Test
    fun `cancellation after durable marker never publishes an authenticated session`() =
        runTest {
            val fixture = Fixture(this)
            fixture.afterMark = { currentCoroutineContext().cancel() }
            val repository = fixture.repository()
            runCurrent()
            val signIn = backgroundScope.async { repository.signInWithRestoreKey() }
            runCurrent()
            assertTrue(signIn.isCancelled)
            assertTrue(fixture.renewal.isPending(fixture.origin, fixture.owner))
            assertNull(fixture.sessions.getSession())
            assertNull(repository.currentAccount.value)
        }

    @Test
    fun `cancellation after secure write retains the renewal obligation`() =
        runTest {
            val fixture = Fixture(this)
            fixture.afterSessionWrite = { currentCoroutineContext().cancel() }
            fixture.create = { Result.failure(IllegalStateException("provider unavailable")) }
            val repository = fixture.repository()
            runCurrent()
            val signIn = backgroundScope.async { repository.signInWithRestoreKey() }
            runCurrent()
            assertTrue(signIn.isCancelled)
            assertTrue(fixture.renewal.isPending(fixture.origin, fixture.owner))
            assertNull(repository.currentAccount.value)
            assertEquals(fixture.owner, fixture.sessions.read(fixture.origin)?.accountId)
            fixture.afterSessionWrite = {}
            fixture.create = { Result.success("credential") }
            assertTrue(fixture.sessions.hasValidSession())
            advanceTimeBy(30_000)
            runCurrent()
            assertFalse(fixture.renewal.isPending(fixture.origin, fixture.owner))
        }

    private class Fixture(
        val scope: TestScope,
    ) {
        val defaults = DefaultPasskeyAccountRepositoryTest()
        val delegate = defaults.FakePasskeyApiClient()
        val owner =
            delegate.completeAuthenticationResponse
                .getOrThrow()
                .account.id
                .toString()
        val config = DefaultPasskeyAccountRepositoryTest.FakeConfigRepository()
        val origin = config.getCurrentBackendUrl()
        var afterSessionWrite: suspend () -> Unit = {}
        var afterMark: suspend () -> Unit = {}
        val backing = RenewalStorage()
        val sessions =
            SecureSessionStorage(
                object : SecureStorage by backing {
                    override suspend fun putString(
                        key: String,
                        value: String,
                    ) {
                        backing.putString(key, value)
                        afterSessionWrite()
                    }
                },
                config,
                scope.backgroundScope,
            )
        val platform = DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager()
        var completeCalls = 0
        var retrieve: suspend () -> Unit = {}
        var validateOwner: suspend () -> Unit = {}
        var create: suspend () -> Result<String> = { Result.success("credential") }
        val api =
            object : PasskeyApiClientContract by delegate {
                override suspend fun completeRestoreSignIn(request: CompleteAuthenticationRequest): Result<CompleteAuthenticationData> {
                    completeCalls++
                    return delegate.completeRestoreSignIn(request)
                }
            }
        val pending = RenewalStorage()
        val renewal =
            RestoreCredentialRenewal(
                object : SecureStorage by pending {
                    override suspend fun putString(
                        key: String,
                        value: String,
                    ) {
                        pending.putString(key, value)
                        if (value.substringBefore('\n') == owner) afterMark()
                    }
                },
            ) { api }
        val credentials =
            object : RestoreCredentialManager by DefaultPasskeyAccountRepositoryTest.FakeRestoreCredentialManager() {
                override suspend fun createRestoreKey(options: PasskeyRegistrationOptions) = create()

                override suspend fun getRestoreCredential(options: PasskeyAuthenticationOptions): Result<String> {
                    retrieve()
                    return DefaultPasskeyAccountRepositoryTest.FakePasskeyManager().authenticateWithPasskeyResponse
                }
            }

        fun repository() =
            DefaultPasskeyAccountRepository(
                apiClient = api,
                passkeyManager = DefaultPasskeyAccountRepositoryTest.FakePasskeyManager(),
                restoreCredentialManager = credentials,
                sessionStorage = sessions,
                platformAccountManager = platform,
                configRepository = config,
                canonicalOwnerProvider =
                    object : CanonicalOwnerProvider {
                        override suspend fun getCanonicalOwnerId(): String {
                            validateOwner()
                            return owner
                        }

                        override suspend fun hasBoundOwner() = true
                    },
                hasLocalData = { true },
                repositoryScope = scope.backgroundScope,
                restoreCredentialRenewal = renewal,
            )
    }
}
