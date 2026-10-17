package app.logdate.client.data.account

import app.logdate.client.datastore.NativeRestorePolicy
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.storage.SecureSessionStorage
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.networking.PasskeyApiErrorCodes
import app.logdate.client.networking.PasskeyApiException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountRefreshContinuityTest {
    @Test
    fun `guarded requests never fall back after snapshot capture fails`() =
        runTest {
            val source = DefaultPasskeyAccountRepositoryTest()
            val config = DefaultPasskeyAccountRepositoryTest.FakeConfigRepository()
            val vault = SecureSessionStorage(RenewalStorage(), config, backgroundScope)
            vault.saveExplicitSession(UserSession("access", "refresh", "owner"))
            val storage =
                object : SessionStorage by vault {
                    override suspend fun captureAuthenticationSnapshot(): app.logdate.client.datastore.SessionAuthenticationSnapshot? = null
                }
            val coordinator =
                AccountSessionRefreshCoordinator(
                    source.FakePasskeyApiClient(),
                    storage,
                    DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager(),
                    config,
                    PasskeyAccountSessionState(),
                    { Result.success(Unit) },
                )
            var calls = 0
            assertFalse(
                coordinator
                    .authorized {
                        calls++
                        Result.success(Unit)
                    }.isSuccess,
            )
            assertEquals(0, calls)
            assertFalse(coordinator.refreshAuthentication().isSuccess)
        }

    @Test
    fun `new explicit login wins pending sign out even when tokens are identical`() =
        runTest {
            val source = DefaultPasskeyAccountRepositoryTest()
            val config = DefaultPasskeyAccountRepositoryTest.FakeConfigRepository()
            val settings = mutableMapOf<String, String>()
            val policy = NativeRestorePolicy({ settings[it] }, { key, value -> settings[key] = value })
            val vault = SecureSessionStorage(RenewalStorage(), config, backgroundScope, nativeRestorePolicy = policy)
            val session = UserSession("same-access", "same-refresh", "owner")
            vault.saveExplicitSession(session)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var clears = 0
            val coordinator =
                AccountSessionRefreshCoordinator(
                    source.FakePasskeyApiClient(),
                    vault,
                    DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager(),
                    config,
                    PasskeyAccountSessionState(),
                    {
                        clears++
                        Result.success(Unit)
                    },
                    prepareRestoreKeyClear = { _, _ ->
                        entered.complete(Unit)
                        release.await()
                    },
                )
            val signingOut = async { coordinator.signOut() }
            entered.await()
            vault.saveExplicitSession(session)
            release.complete(Unit)
            signingOut.await().getOrThrow()
            assertEquals(session, vault.getSession())
            assertTrue(policy.canRestore(config.getCurrentBackendUrl()))
            assertEquals(0, clears)
        }

    @Test
    fun `rejected refresh preserves a later identical-token explicit login`() =
        runTest {
            val source = DefaultPasskeyAccountRepositoryTest()
            val config = DefaultPasskeyAccountRepositoryTest.FakeConfigRepository()
            val settings = mutableMapOf<String, String>()
            val policy = NativeRestorePolicy({ settings[it] }, { key, value -> settings[key] = value })
            val vault = SecureSessionStorage(RenewalStorage(), config, backgroundScope, nativeRestorePolicy = policy)
            val session = UserSession("same-access", "same-refresh", "owner")
            vault.saveExplicitSession(session)
            val response = CompletableDeferred<Result<String>>()
            val delegate = source.FakePasskeyApiClient()
            val api =
                object : PasskeyApiClientContract by delegate {
                    override suspend fun refreshToken(refreshToken: String): Result<String> = response.await()
                }
            val coordinator =
                AccountSessionRefreshCoordinator(
                    api,
                    vault,
                    DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager(),
                    config,
                    PasskeyAccountSessionState(),
                    { Result.success(Unit) },
                )
            val refreshing = async { coordinator.refreshAuthentication() }
            runCurrent()
            vault.saveExplicitSession(session)
            response.complete(Result.failure(PasskeyApiException(PasskeyApiErrorCodes.INVALID_REFRESH_TOKEN, "Rejected")))
            assertFalse(refreshing.await().isSuccess)
            assertEquals(session, vault.getSession())
            assertTrue(policy.canRestore(config.getCurrentBackendUrl()))
        }

    @Test
    fun `successful refresh preserves a later identical-token explicit login`() =
        runTest {
            val source = DefaultPasskeyAccountRepositoryTest()
            val config = DefaultPasskeyAccountRepositoryTest.FakeConfigRepository()
            val settings = mutableMapOf<String, String>()
            val policy = NativeRestorePolicy({ settings[it] }, { key, value -> settings[key] = value })
            val vault = SecureSessionStorage(RenewalStorage(), config, backgroundScope, nativeRestorePolicy = policy)
            val session = UserSession("same-access", "same-refresh", "owner")
            vault.saveExplicitSession(session)
            val response = CompletableDeferred<Result<String>>()
            val delegate = source.FakePasskeyApiClient()
            val api =
                object : PasskeyApiClientContract by delegate {
                    override suspend fun refreshToken(refreshToken: String): Result<String> = response.await()
                }
            val coordinator =
                AccountSessionRefreshCoordinator(
                    api,
                    vault,
                    DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager(),
                    config,
                    PasskeyAccountSessionState(),
                    { Result.success(Unit) },
                )
            val refreshing = async { coordinator.refreshAuthentication() }
            runCurrent()
            vault.saveExplicitSession(session)
            response.complete(Result.success("stale-refreshed-access"))
            assertFalse(refreshing.await().isSuccess)
            assertEquals(session, vault.getSession())
            assertTrue(policy.canRestore(config.getCurrentBackendUrl()))
        }

    @Test
    fun `successful authenticated mutations are never retried`() =
        runTest {
            val source = DefaultPasskeyAccountRepositoryTest()
            val config = DefaultPasskeyAccountRepositoryTest.FakeConfigRepository()
            val vault = SecureSessionStorage(RenewalStorage(), config, backgroundScope)
            val session = UserSession("original-access", "original-refresh", "owner")
            vault.saveExplicitSession(session)
            val coordinator =
                AccountSessionRefreshCoordinator(
                    source.FakePasskeyApiClient(),
                    vault,
                    DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager(),
                    config,
                    PasskeyAccountSessionState(),
                    { Result.success(Unit) },
                )
            var mutations = 0
            coordinator
                .authorized(retryAnyFailure = true) {
                    mutations++
                    Result.success(Unit)
                }.getOrThrow()
            assertEquals(1, mutations)
            assertEquals(session, vault.getSession())
        }

    @Test
    fun `rejected old deletion cannot retry with a newer accounts credentials`() =
        runTest {
            val source = DefaultPasskeyAccountRepositoryTest()
            val delegate = source.FakePasskeyApiClient()
            val config = DefaultPasskeyAccountRepositoryTest.FakeConfigRepository()
            val vault = SecureSessionStorage(RenewalStorage(), config, backgroundScope)
            val old = UserSession("old-access", "old-refresh", "old-owner")
            vault.saveExplicitSession(old)
            val response = CompletableDeferred<Result<Unit>>()
            val deleted = mutableListOf<String>()
            val api =
                object : PasskeyApiClientContract by delegate {
                    override suspend fun deleteAccount(accessToken: String): Result<Unit> {
                        deleted += accessToken
                        return response.await()
                    }
                }
            val coordinator =
                AccountSessionRefreshCoordinator(
                    api,
                    vault,
                    DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager(),
                    config,
                    PasskeyAccountSessionState(),
                    { Result.success(Unit) },
                )
            val deleting = async { coordinator.deleteAccount() }
            runCurrent()
            val newer = UserSession("new-access", "new-refresh", "new-owner")
            vault.saveExplicitSession(newer)
            response.complete(Result.failure(PasskeyApiException(PasskeyApiErrorCodes.INVALID_TOKEN, "Expired")))
            assertFalse(deleting.await().isSuccess)
            assertEquals(listOf("old-access"), deleted)
            assertEquals(newer, vault.getSession())
        }

    @Test
    fun `an old logout response cannot clear a newer explicit login`() =
        runTest {
            val source = DefaultPasskeyAccountRepositoryTest()
            val delegate = source.FakePasskeyApiClient()
            val config = DefaultPasskeyAccountRepositoryTest.FakeConfigRepository()
            val vault = SecureSessionStorage(RenewalStorage(), config, backgroundScope)
            val old = UserSession("old-access", "old-refresh", "owner")
            val newer = UserSession("new-access", "new-refresh", "owner")
            vault.saveExplicitSession(old)
            val response = CompletableDeferred<Result<Unit>>()
            val api =
                object : PasskeyApiClientContract by delegate {
                    override suspend fun logout(refreshToken: String): Result<Unit> = response.await()
                }
            var cleared = false
            val coordinator =
                AccountSessionRefreshCoordinator(
                    api,
                    vault,
                    DefaultPasskeyAccountRepositoryTest.FakePlatformAccountManager(),
                    config,
                    PasskeyAccountSessionState(),
                    {
                        cleared = true
                        Result.success(Unit)
                    },
                )
            val loggingOut = async { coordinator.signOut() }
            runCurrent()
            vault.saveExplicitSession(newer)
            response.complete(Result.success(Unit))
            loggingOut.await().getOrThrow()
            assertEquals(newer, vault.getSession())
            assertEquals(true, cleared)
        }

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
