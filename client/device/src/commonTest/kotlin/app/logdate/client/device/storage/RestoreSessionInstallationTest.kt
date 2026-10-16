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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RestoreSessionInstallationTest {
    private val origin = "https://first.example"
    private val recovered = UserSession("recovered", "refresh", "owner")

    @Test
    fun `installation guard loads retained credentials before permitting restore`() =
        runTest {
            val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
            val backing = FakeSecureStorage()
            SecureSessionStorage(backing, config, backgroundScope).write(origin, recovered)
            val vault = SecureSessionStorage(backing, config, backgroundScope)
            val guard = assertNotNull(vault.captureInstallationGuard())
            assertEquals(recovered, vault.getSession())
            assertTrue(vault.installSessionIfCurrent(guard, recovered.copy(accessToken = "renewed")))
        }

    @Test
    fun `sign out invalidates an outstanding guard even when session was already absent`() =
        runTest {
            val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
            val vault = SecureSessionStorage(FakeSecureStorage(), config, backgroundScope)
            val guard = assertNotNull(vault.captureInstallationGuard())
            vault.clearSession()
            assertFalse(vault.installSessionIfCurrent(guard, recovered))
            assertNull(vault.read(origin))
        }

    @Test
    fun `a later sign in cannot be overwritten by an older restore`() =
        runTest {
            val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
            val vault = SecureSessionStorage(FakeSecureStorage(), config, backgroundScope)
            val guard = assertNotNull(vault.captureInstallationGuard())
            val later = recovered.copy(accessToken = "later")
            vault.saveSession(later)
            assertFalse(vault.installSessionIfCurrent(guard, recovered))
            assertEquals(later, vault.read(origin))
        }

    @Test
    fun `switch during secure persistence cannot put restored tokens in another vault`() =
        runTest {
            val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
            val backing = FakeSecureStorage()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val delayed =
                object : SecureStorage by backing {
                    override suspend fun putString(
                        key: String,
                        value: String,
                    ) {
                        entered.complete(Unit)
                        release.await()
                        backing.putString(key, value)
                    }
                }
            val vault = SecureSessionStorage(delayed, config, backgroundScope)
            val guard = assertNotNull(vault.captureInstallationGuard())
            val installation = async { vault.installSessionIfCurrent(guard, recovered) }
            entered.await()
            config.updateBackendUrl("https://second.example")
            release.complete(Unit)
            assertFalse(installation.await())
            runCurrent()
            assertNull(vault.getSession())
            assertNull(vault.read("https://second.example"))
            // The completed write is retained only under the origin that issued it.
            assertEquals(recovered, vault.read(origin))
        }
}
