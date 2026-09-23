package app.logdate.client.device.storage

import app.logdate.client.datastore.UserSession
import app.logdate.shared.config.DefaultLogDateConfigRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SecureSessionStorageVaultTest {
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

            assertEquals(true, storage.hasValidSession())
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
