package app.logdate.client.data.account

import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.networking.PasskeyApiException
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.shared.model.PasskeyRegistrationOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreCredentialRegistrationConcurrencyTest {
    @Test
    fun `foreground registration cannot race a pending background replacement`() =
        runTest {
            val fixture = DefaultPasskeyAccountRepositoryTest()
            val api = fixture.FakePasskeyApiClient()
            val config = DefaultPasskeyAccountRepositoryTest.FakeConfigRepository()
            val owner =
                api
                    .getAccountInfo("access")
                    .getOrThrow()
                    .id
                    .toString()
            val sessions = DefaultPasskeyAccountRepositoryTest.FakeSessionStorage()
            sessions.saveSession(UserSession("access", "refresh", owner))
            val renewal = RestoreCredentialRenewal(RenewalStorage()) { api }
            renewal.mark(config.getCurrentBackendUrl(), owner)
            val release = CompletableDeferred<Unit>()
            var calls = 0
            var active = 0
            var maximumActive = 0
            val delegate = DefaultPasskeyAccountRepositoryTest.FakeRestoreCredentialManager()
            val credentials =
                object : RestoreCredentialManager by delegate {
                    override suspend fun createRestoreKey(options: PasskeyRegistrationOptions): Result<String> {
                        calls++
                        active++
                        maximumActive = maxOf(maximumActive, active)
                        try {
                            if (calls == 1) release.await()
                            return delegate.createRestoreKey(options)
                        } finally {
                            active--
                        }
                    }
                }
            val repository =
                DefaultPasskeyAccountRepository(
                    apiClient = api,
                    passkeyManager = DefaultPasskeyAccountRepositoryTest.FakePasskeyManager(),
                    restoreCredentialManager = credentials,
                    sessionStorage = sessions,
                    platformAccountManager = DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager(),
                    configRepository = config,
                    canonicalOwnerProvider =
                        object : CanonicalOwnerProvider {
                            override suspend fun getCanonicalOwnerId() = owner

                            override suspend fun hasBoundOwner() = true
                        },
                    hasLocalData = { true },
                    repositoryScope = backgroundScope,
                    restoreCredentialRenewal = renewal,
                )
            runCurrent()
            assertEquals(1, calls)
            val foreground = backgroundScope.async { repository.createRestoreKey() }
            runCurrent()
            release.complete(Unit)
            runCurrent()
            assertTrue(foreground.await().isSuccess)
            assertEquals(2, calls)
            assertEquals(1, maximumActive)
        }

    @Test
    fun `rejected refresh can clear credentials during registration without deadlocking`() =
        runTest {
            val fixture = DefaultPasskeyAccountRepositoryTest()
            val delegate = fixture.FakePasskeyApiClient()
            val owner =
                delegate
                    .getAccountInfo("access")
                    .getOrThrow()
                    .id
                    .toString()
            val sessions = DefaultPasskeyAccountRepositoryTest.FakeSessionStorage()
            sessions.saveSession(UserSession("expired", "revoked", owner))
            val api =
                object : PasskeyApiClientContract by delegate {
                    override suspend fun beginRestoreKeyRegistration(accessToken: String): Result<PasskeyRegistrationOptions> =
                        Result.failure(PasskeyApiException("INVALID_TOKEN", "expired"))

                    override suspend fun refreshToken(refreshToken: String): Result<String> =
                        Result.failure(PasskeyApiException("INVALID_REFRESH_TOKEN", "revoked"))
                }
            val repository =
                DefaultPasskeyAccountRepository(
                    apiClient = api,
                    passkeyManager = DefaultPasskeyAccountRepositoryTest.FakePasskeyManager(),
                    restoreCredentialManager = DefaultPasskeyAccountRepositoryTest.FakeRestoreCredentialManager(),
                    sessionStorage = sessions,
                    platformAccountManager = DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager(),
                    configRepository = DefaultPasskeyAccountRepositoryTest.FakeConfigRepository(),
                    canonicalOwnerProvider =
                        object : CanonicalOwnerProvider {
                            override suspend fun getCanonicalOwnerId() = owner

                            override suspend fun hasBoundOwner() = true
                        },
                    hasLocalData = { true },
                    repositoryScope = backgroundScope,
                )
            val result = withTimeout(1_000) { repository.createRestoreKey() }
            assertTrue(result.isFailure)
            assertNull(sessions.getSession())
        }
}
