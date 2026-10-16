package app.logdate.client.data.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.storage.SecureSessionStorage
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.networking.PasskeyApiException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountRefreshContinuityTest {
    @Test
    fun `refresh uses credentials from one origin bound snapshot`() =
        runTest {
            val source = DefaultPasskeyAccountRepositoryTest()
            val delegate = source.FakePasskeyApiClient()
            val config = DefaultPasskeyAccountRepositoryTest.FakeConfigRepository()
            val vault = SecureSessionStorage(RenewalStorage(), config, backgroundScope)
            val current = UserSession("current-access", "current-refresh", "owner")
            vault.saveExplicitSession(current)
            var reads = 0
            val storage =
                object : SessionStorage by vault {
                    override fun getSession(): UserSession? {
                        reads++
                        return if (reads == 1) UserSession("old-access", "old-refresh", "owner") else vault.getSession()
                    }
                }
            var refreshedToken: String? = null
            val api =
                object : PasskeyApiClientContract by delegate {
                    override suspend fun refreshToken(refreshToken: String): Result<String> {
                        refreshedToken = refreshToken
                        return Result.success("renewed-access")
                    }
                }
            val coordinator =
                AccountSessionRefreshCoordinator(
                    api,
                    storage,
                    DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager(),
                    config,
                    PasskeyAccountSessionState(),
                    { Result.success(Unit) },
                )
            coordinator.refreshAuthentication()
            assertEquals("current-refresh", refreshedToken)
            assertEquals(current.copy(accessToken = "renewed-access"), vault.read(config.getCurrentBackendUrl()))
        }

    @Test
    fun `a completed old refresh cannot replace a newer explicit sign in`() =
        runTest {
            for (rejected in listOf(false, true)) {
                val source = DefaultPasskeyAccountRepositoryTest()
                val delegate = source.FakePasskeyApiClient()
                val config = DefaultPasskeyAccountRepositoryTest.FakeConfigRepository()
                val vault = SecureSessionStorage(RenewalStorage(), config, backgroundScope)
                val old = UserSession("old-access", "old-refresh", "owner")
                val newer = UserSession("new-access", "new-refresh", "owner")
                vault.saveExplicitSession(old)
                val response = CompletableDeferred<Result<String>>()
                val api =
                    object : PasskeyApiClientContract by delegate {
                        override suspend fun refreshToken(refreshToken: String): Result<String> = response.await()
                    }
                var providerClears = 0
                val coordinator =
                    AccountSessionRefreshCoordinator(
                        api,
                        vault,
                        DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager(),
                        config,
                        PasskeyAccountSessionState(),
                        {
                            providerClears++
                            Result.success(Unit)
                        },
                    )
                val refreshing = async { coordinator.refreshAuthentication() }
                runCurrent()
                vault.saveExplicitSession(newer)
                response.complete(
                    if (rejected) {
                        Result.failure(
                            PasskeyApiException("INVALID_REFRESH_TOKEN", "rejected"),
                        )
                    } else {
                        Result.success("obsolete-access")
                    },
                )
                assertFalse(refreshing.await().isSuccess)
                assertEquals(newer, vault.read(config.getCurrentBackendUrl()))
                assertEquals(0, providerClears)
            }
        }
}
