package app.logdate.client.device.storage

import app.logdate.client.datastore.UserSession
import app.logdate.shared.config.DefaultLogDateConfigRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SecureSessionStorageVaultTest {
    @Test
    fun `startup loader can run immediately during construction`() =
        runTest {
            val immediate =
                kotlinx.coroutines.CoroutineScope(
                    backgroundScope.coroutineContext + kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler),
                )
            val storage = SecureSessionStorage(FakeSecureStorage(), config(serverA), immediate)
            assertFalse(storage.hasValidSession())
            storage.saveSession(sessionA)
            assertEquals(sessionA, storage.getSession())
        }

    @Test
    fun `a delayed old-server load cannot publish after the destination changes`() =
        runTest {
            val backing = FakeSecureStorage()
            val selected = config(serverA)
            SecureSessionStorage(backing, selected, backgroundScope).write(serverA, sessionA)
            val release = CompletableDeferred<Unit>()
            val delayed =
                object : SecureStorage by backing {
                    override suspend fun getString(key: String): String? {
                        val value = backing.getString(key)
                        if (value?.contains(sessionA.accessToken) == true) release.await()
                        return value
                    }
                }
            val storage = SecureSessionStorage(delayed, selected, backgroundScope)
            val loading = async { storage.hasValidSession() }
            runCurrent()
            selected.updateBackendUrl(serverB)
            val writing = async { storage.write(serverB, sessionB) }
            runCurrent()
            release.complete(Unit)
            writing.await()
            assertFalse(loading.await())
            assertEquals(sessionB, storage.getSession())
        }

    private val serverA = "https://cloud.logdate.app"
    private val serverB = "https://journal.example.com"
    private val sessionA = UserSession(accessToken = "access-a", refreshToken = "refresh-a", accountId = "owner")
    private val sessionB = UserSession(accessToken = "access-b", refreshToken = "refresh-b", accountId = "owner")

    @Test
    fun `a session written for another server is read back for that server only`() =
        runTest {
            val storage = SecureSessionStorage(FakeSecureStorage(), config(serverA), backgroundScope)

            storage.write(serverB, sessionB)

            assertEquals(sessionB, storage.read(serverB))
            assertNull(storage.read(serverA))
        }

    @Test
    fun `the current server's session is found once the app switches to it`() =
        runTest {
            val config = config(serverA)
            val storage = SecureSessionStorage(FakeSecureStorage(), config, backgroundScope)
            storage.write(serverA, sessionA)
            storage.write(serverB, sessionB)

            config.updateBackendUrl(serverB)

            // The collector has not run yet: the old token must already be unavailable.
            assertNull(storage.getSession())
            assertEquals(true, storage.hasValidSession())
            assertEquals(sessionB, storage.getSession())
            assertEquals(sessionB, storage.read(config.getCurrentBackendUrl()))
        }

    @Test
    fun `clearing another server's session leaves the current one alone`() =
        runTest {
            val storage = SecureSessionStorage(FakeSecureStorage(), config(serverA), backgroundScope)
            storage.write(serverA, sessionA)
            storage.write(serverB, sessionB)

            storage.clear(serverB)

            assertNull(storage.read(serverB))
            assertEquals(sessionA, storage.read(serverA))
        }

    private fun config(origin: String) = DefaultLogDateConfigRepository(initialBackendUrl = origin)
}
