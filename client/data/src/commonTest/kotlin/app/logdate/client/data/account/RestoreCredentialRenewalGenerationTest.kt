package app.logdate.client.data.account

import app.logdate.client.datastore.UserSession
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.shared.config.PinnedLogDateConfigRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreCredentialRenewalGenerationTest {
    private val api = DefaultPasskeyAccountRepositoryTest().FakePasskeyApiClient()
    private val origin = "https://cloud.logdate.app"
    private val config = PinnedLogDateConfigRepository(origin, null)
    private val provider = DefaultPasskeyAccountRepositoryTest.FakeRestoreCredentialManager()
    private val platform = DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager()

    @Test
    fun `a newer same-owner obligation survives an older registration completion`() =
        runTest {
            val storage = RenewalStorage()
            lateinit var renewal: RestoreCredentialRenewal
            val session = session()
            val remarkingApi =
                object : PasskeyApiClientContract by api {
                    override suspend fun completeRestoreKeyRegistration(
                        accessToken: String,
                        credentialJson: String,
                        challenge: String,
                    ): Result<Unit> {
                        val result = api.completeRestoreKeyRegistration(accessToken, credentialJson, challenge)
                        renewal.mark(origin, session.accountId)
                        return result
                    }
                }
            renewal = RestoreCredentialRenewal(storage) { remarkingApi }
            renewal.mark(origin, session.accountId)
            renewal.renew(config, session, provider, platform) { true }
            assertTrue(RestoreCredentialRenewal(storage).isPending(origin, session.accountId))
        }

    @Test
    fun `a newer mark survives a suspended resolved write`() =
        runTest {
            val delegate = RenewalStorage()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val storage =
                object : SecureStorage by delegate {
                    override suspend fun putString(
                        key: String,
                        value: String,
                    ) {
                        if (value == "resolved") {
                            entered.complete(Unit)
                            release.await()
                        }
                        delegate.putString(key, value)
                    }
                }
            val renewal = RestoreCredentialRenewal(storage) { api }
            val session = session()
            renewal.mark(origin, session.accountId)
            val older = launch { renewal.renew(config, session, provider, platform) { true } }
            entered.await()
            val newer = launch { renewal.mark(origin, session.accountId) }
            runCurrent()
            release.complete(Unit)
            older.join()
            newer.join()
            assertTrue(RestoreCredentialRenewal(delegate).isPending(origin, session.accountId))
        }

    @Test
    fun `legacy pending markers remain renewable`() =
        runTest {
            val storage = RenewalStorage()
            val session = session()
            val key = "restore_key_renewal_${kotlin.io.encoding.Base64.UrlSafe.encode(origin.encodeToByteArray())}"
            storage.putString(key, session.accountId)
            val renewal = RestoreCredentialRenewal(storage) { api }
            assertTrue(renewal.isPending(origin, session.accountId))
            renewal.renew(config, session, provider, platform) { true }
            assertFalse(renewal.isPending(origin, session.accountId))
        }

    private suspend fun session() =
        UserSession(
            "access",
            "refresh",
            api
                .getAccountInfo("access")
                .getOrThrow()
                .id
                .toString(),
        )
}
