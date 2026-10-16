package app.logdate.client.data.account

import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.shared.model.PasskeyRegistrationOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreCredentialRenewalLifecycleTest {
    @Test
    fun `signed in installation renews after a provider outage without reopening the app`() =
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
            val session =
                app.logdate.client.datastore
                    .UserSession("access", "refresh", owner)
            val sessions = DefaultPasskeyAccountRepositoryTest.FakeSessionStorage()
            sessions.saveSession(session)
            val renewal = RestoreCredentialRenewal(RenewalStorage()) { api }
            renewal.mark(config.getCurrentBackendUrl(), owner)
            var attempts = 0
            val delegate = DefaultPasskeyAccountRepositoryTest.FakeRestoreCredentialManager()
            val credentials =
                object : RestoreCredentialManager by delegate {
                    override suspend fun createRestoreKey(options: PasskeyRegistrationOptions): Result<String> {
                        attempts++
                        return if (attempts ==
                            1
                        ) {
                            Result.failure(IllegalStateException("provider unavailable"))
                        } else {
                            delegate.createRestoreKey(options)
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
            assertEquals(1, attempts)
            assertTrue(renewal.isPending(config.getCurrentBackendUrl(), owner))
            assertTrue(repository.isAuthenticated.value)
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(2, attempts)
            assertFalse(renewal.isPending(config.getCurrentBackendUrl(), owner))
            assertEquals(session, sessions.getSession())
        }
}
