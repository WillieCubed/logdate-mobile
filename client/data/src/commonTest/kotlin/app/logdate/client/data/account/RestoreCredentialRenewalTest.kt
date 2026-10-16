package app.logdate.client.data.account

import app.logdate.client.datastore.UserSession
import app.logdate.client.device.PlatformAccountManager
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.shared.config.PinnedLogDateConfigRepository
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.PasskeyRegistrationOptions
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestoreCredentialRenewalTest {
    private val fixture = DefaultPasskeyAccountRepositoryTest()
    private val api = fixture.FakePasskeyApiClient()
    private val storage = RenewalStorage()
    private val origin = "https://cloud.logdate.app"
    private val config = PinnedLogDateConfigRepository(origin, null)
    private val platform = DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager()
    private val provider = DefaultPasskeyAccountRepositoryTest.FakeRestoreCredentialManager()

    @Test
    fun `successful renewal survives a new store instance and clears only its origin`() =
        runTest {
            val session = session()
            val renewal =
                RestoreCredentialRenewal(storage) { pinned ->
                    assertEquals(origin, pinned.getCurrentBackendUrl())
                    api
                }
            renewal.mark(origin, session.accountId)
            renewal.mark("https://other.example", session.accountId)
            renewal.renew(config, session, provider, platform) { true }
            assertFalse(RestoreCredentialRenewal(storage).isPending(origin, session.accountId))
            assertTrue(RestoreCredentialRenewal(storage).isPending("https://other.example", session.accountId))
        }

    @Test
    fun `a server switch while fetching account info cannot create a replacement key`() =
        runTest {
            var current = true
            var created = false
            val switchingApi =
                object : PasskeyApiClientContract by api {
                    override suspend fun getAccountInfo(accessToken: String): Result<LogDateAccount> {
                        current = false
                        return api.getAccountInfo(accessToken)
                    }
                }
            val credentials =
                object : RestoreCredentialManager by provider {
                    override suspend fun createRestoreKey(options: PasskeyRegistrationOptions): Result<String> {
                        created = true
                        return provider.createRestoreKey(options)
                    }
                }
            val session = session()
            val renewal = RestoreCredentialRenewal(storage) { switchingApi }
            renewal.mark(origin, session.accountId)
            assertFailsWith<IllegalStateException> { renewal.renew(config, session, credentials, platform) { current } }
            assertFalse(created)
            assertTrue(renewal.isPending(origin, session.accountId))
        }

    @Test
    fun `a server switch during platform account repair stops registration`() =
        runTest {
            var current = true
            var began = false
            val recordingApi =
                object : PasskeyApiClientContract by api {
                    override suspend fun beginRestoreKeyRegistration(accessToken: String): Result<PasskeyRegistrationOptions> {
                        began = true
                        return api.beginRestoreKeyRegistration(accessToken)
                    }
                }
            val switchingPlatform =
                object : PlatformAccountManager by platform {
                    override suspend fun updateTokens(
                        username: String,
                        backendUrl: String,
                        accessToken: String,
                        refreshToken: String,
                    ): Result<Unit> {
                        assertEquals(origin, backendUrl)
                        current = false
                        return Result.success(Unit)
                    }
                }
            val session = session()
            val renewal = RestoreCredentialRenewal(storage) { recordingApi }
            renewal.mark(origin, session.accountId)
            assertFailsWith<IllegalStateException> { renewal.renew(config, session, provider, switchingPlatform) { current } }
            assertFalse(began)
            assertTrue(renewal.isPending(origin, session.accountId))
        }

    @Test
    fun `provider failure keeps renewal pending for a later startup`() =
        runTest {
            val credentials =
                object : RestoreCredentialManager by provider {
                    override suspend fun createRestoreKey(options: PasskeyRegistrationOptions): Result<String> =
                        Result.failure(IllegalStateException("offline"))
                }
            val session = session()
            val renewal = RestoreCredentialRenewal(storage) { api }
            renewal.mark(origin, session.accountId)
            assertFailsWith<IllegalStateException> { renewal.renew(config, session, credentials, platform) { true } }
            assertTrue(renewal.isPending(origin, session.accountId))
        }

    @Test
    fun `a descriptor from a previous server cannot redirect renewal tokens`() =
        runTest {
            var contacted = false
            val renewal =
                RestoreCredentialRenewal(storage) {
                    contacted = true
                    api
                }
            val session = session()
            renewal.mark(origin, session.accountId)
            val stale =
                app.logdate.shared.model.ServerDescriptor(
                    serverOrigin = "https://previous.example",
                    apiBaseUrl = "https://previous.example/api/v1",
                    deploymentKind = app.logdate.shared.model.DeploymentKind.SELF_HOSTED,
                    displayName = "Previous",
                )
            assertFailsWith<IllegalArgumentException> {
                renewal.renew(PinnedLogDateConfigRepository(origin, stale), session, provider, platform) { true }
            }
            assertFalse(contacted)
            assertTrue(renewal.isPending(origin, session.accountId))
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

internal class RenewalStorage : SecureStorage {
    private val values = mutableMapOf<String, String>()

    override suspend fun getString(key: String): String? = values[key]

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }

    override suspend fun clear() = values.clear()

    override fun observeString(key: String) = flowOf(values[key])

    override fun observeAll() = flowOf(values.toMap())

    override suspend fun encrypt(data: ByteArray) = data

    override suspend fun decrypt(data: ByteArray) = data
}
