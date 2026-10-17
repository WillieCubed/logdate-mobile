package app.logdate.client.data.account

import app.logdate.client.datastore.SessionAuthenticationSnapshot
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.storage.SecureSessionStorage
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.shared.model.LogDateAccount
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
class RestoreRenewalAuthorizationTest {
    @Test
    fun `background renewal cannot adopt an identical-token later sign in`() = exercise(true, false)

    @Test
    fun `temporary authorization read failure retries enrollment automatically`() = exercise(false, true)

    private fun exercise(
        replaceSignIn: Boolean,
        failCapture: Boolean,
    ) = runTest {
        val source = DefaultPasskeyAccountRepositoryTest()
        val delegate = source.FakePasskeyApiClient()
        val account = delegate.getAccountInfo("access").getOrThrow()
        val config = DefaultPasskeyAccountRepositoryTest.FakeConfigRepository()
        val vault = SecureSessionStorage(RenewalStorage(), config, backgroundScope)
        val session = UserSession("access", "refresh", account.id.toString())
        vault.saveExplicitSession(session)
        var reads = 0
        val sessions =
            object : SessionStorage by vault {
                override suspend fun captureAuthenticationSnapshot(): SessionAuthenticationSnapshot? {
                    if (failCapture && reads++ == 0) error("Secure storage temporarily unavailable")
                    return vault.captureAuthenticationSnapshot()
                }
            }
        val api =
            object : PasskeyApiClientContract by delegate {
                override suspend fun getAccountInfo(accessToken: String): Result<LogDateAccount> {
                    // The explicit decision commits before its observer is notified.
                    if (replaceSignIn) vault.saveExplicitSession(session)
                    return Result.success(account)
                }
            }
        var providerWrites = 0
        val provider =
            object : RestoreCredentialManager by DefaultPasskeyAccountRepositoryTest.FakeRestoreCredentialManager() {
                override suspend fun createRestoreKey(options: PasskeyRegistrationOptions): Result<String> {
                    providerWrites++
                    return Result.success("{}")
                }
            }
        val renewal = RestoreCredentialRenewal(RenewalStorage()) { api }
        DefaultPasskeyAccountRepository(
            apiClient = api,
            passkeyManager = DefaultPasskeyAccountRepositoryTest.FakePasskeyManager(),
            restoreCredentialManager = provider,
            sessionStorage = sessions,
            platformAccountManager = DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager(),
            configRepository = config,
            canonicalOwnerProvider =
                object : CanonicalOwnerProvider {
                    override suspend fun getCanonicalOwnerId() = session.accountId

                    override suspend fun hasBoundOwner() = true
                },
            hasLocalData = { false },
            repositoryScope = backgroundScope,
            restoreCredentialRenewal = renewal,
        )
        runCurrent()
        assertEquals(0, providerWrites)
        if (replaceSignIn) {
            assertTrue(renewal.isPending(config.getCurrentBackendUrl(), session.accountId))
        } else {
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(1, providerWrites)
            assertFalse(renewal.isPending(config.getCurrentBackendUrl(), session.accountId))
        }
        assertEquals(session, vault.getSession())
    }
}
