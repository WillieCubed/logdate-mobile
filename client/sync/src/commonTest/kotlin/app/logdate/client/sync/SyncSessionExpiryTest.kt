package app.logdate.client.sync

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.test.fakeAccountRepository
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.shared.model.CloudAccountRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SyncSessionExpiryTest {
    @Test
    fun `a revoked refresh token removes the session that settings considers signed in`() =
        runTest {
            val storage = fakeSessionStorage()
            val rejected = CloudApiException("REFRESH_TOKEN_REVOKED", "Session revoked", 401)
            val refresher = refresher(storage) { Result.failure(rejected) }

            val result = refresher.withFreshToken<Unit>({ expiredAccessToken() }, "download")

            assertSame(rejected, result.exceptionOrNull())
            assertNull(storage.getSession())
            assertFalse(refresher.isAuthenticated())
        }

    @Test
    fun `an invalid refresh token removes the unusable session`() =
        runTest {
            val storage = fakeSessionStorage()
            val rejected = CloudApiException("INVALID_REFRESH_TOKEN", "Session expired", 401)
            val refresher = refresher(storage) { Result.failure(rejected) }

            refresher.withFreshToken<Unit>({ expiredAccessToken() }, "download")

            assertNull(storage.getSession())
        }

    @Test
    fun `a network failure during refresh is reported without signing out`() =
        runTest {
            val storage = fakeSessionStorage()
            val session = storage.getSession()
            val network = CloudApiException("NETWORK_ERROR", "Could not connect")
            val refresher = refresher(storage) { Result.failure(network) }

            val result = refresher.withFreshToken<Unit>({ expiredAccessToken() }, "download")

            assertSame(network, result.exceptionOrNull())
            assertEquals(session, storage.getSession())
            assertTrue(refresher.isAuthenticated())
        }

    @Test
    fun `an unavailable refresh server is reported without signing out`() =
        runTest {
            val storage = fakeSessionStorage()
            val session = storage.getSession()
            val unavailable = CloudApiException("SERVER_ERROR", "Try later", 503)
            val refresher = refresher(storage) { Result.failure(unavailable) }

            val result = refresher.withFreshToken<Unit>({ expiredAccessToken() }, "download")

            assertSame(unavailable, result.exceptionOrNull())
            assertEquals(session, storage.getSession())
        }

    @Test
    fun `a delayed rejection cannot clear a newly signed in session`() =
        runTest {
            val storage = fakeSessionStorage()
            val newSession = storage.getSession()!!.copy(accessToken = "new-login", refreshToken = "new-refresh")
            val refresher =
                refresher(storage) {
                    storage.saveSession(newSession)
                    Result.failure(CloudApiException("REFRESH_TOKEN_REVOKED", "Old session revoked", 401))
                }

            refresher.withFreshToken<Unit>({ expiredAccessToken() }, "download")

            assertEquals(newSession, storage.getSession())
        }

    @Test
    fun `a delayed refresh cannot resurrect a signed out session`() =
        runTest {
            val storage = fakeSessionStorage()
            val refresher =
                refresher(storage) {
                    storage.clearSession()
                    Result.success("refreshed-old-token")
                }

            var calls = 0
            refresher.withFreshToken<Unit>({
                calls++
                expiredAccessToken()
            }, "download")

            assertNull(storage.getSession())
            assertEquals(1, calls)
        }

    private fun refresher(
        storage: SessionStorage,
        refresh: suspend () -> Result<String>,
    ): SyncTokenRefresher =
        SyncTokenRefresher(
            storage,
            object : CloudAccountRepository by fakeAccountRepository() {
                override suspend fun refreshAccessToken(refreshToken: String): Result<String> = refresh()
            },
        )

    private fun expiredAccessToken(): Result<Unit> = Result.failure(CloudApiException("EXPIRED", "Access token expired", 401))
}
