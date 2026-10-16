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
    fun `sign out prevents native recovery across restart until explicit sign in`() =
        runTest {
            val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
            val backing = FakeSecureStorage()
            SecureSessionStorage(backing, config, backgroundScope).clearSession()
            val restarted = SecureSessionStorage(backing, config, backgroundScope)
            assertNull(restarted.captureInstallationGuard())
            restarted.saveSession(recovered)
            assertNotNull(restarted.captureInstallationGuard())
        }

    @Test
    fun `unreadable signed out intent cannot authorize native recovery`() =
        runTest {
            val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
            val backing = FakeSecureStorage()
            val unreadable =
                object : SecureStorage by backing {
                    override suspend fun getString(key: String): String? = error("storage unavailable")
                }
            val vault = SecureSessionStorage(unreadable, config, backgroundScope)
            val result = runCatching { vault.captureInstallationGuard() }
            assertTrue(result.isFailure || result.getOrNull() == null)
        }

    @Test
    fun `background vault cannot overwrite sign out through another vault`() =
        runTest {
            val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
            val backing = FakeSecureStorage()
            val background = SecureSessionStorage(backing, config, backgroundScope)
            val foreground = SecureSessionStorage(backing, config, backgroundScope)
            val guard = assertNotNull(background.captureInstallationGuard())
            foreground.clearSession()
            assertFalse(background.installSessionIfCurrent(guard, recovered))
            assertNull(SecureSessionStorage(backing, config, backgroundScope).captureInstallationGuard())
        }

    @Test
    fun `background vault cannot overwrite later sign in through another vault`() =
        runTest {
            val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
            val backing = FakeSecureStorage()
            val background = SecureSessionStorage(backing, config, backgroundScope)
            val foreground = SecureSessionStorage(backing, config, backgroundScope)
            val guard = assertNotNull(background.captureInstallationGuard())
            val explicit = recovered.copy(accessToken = "explicit")
            foreground.saveSession(explicit)
            assertFalse(background.installSessionIfCurrent(guard, recovered))
            assertEquals(explicit, foreground.read(origin))
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
    fun `sign out waits for a suspended background write then wins durably`() =
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
            val background = SecureSessionStorage(delayed, config, backgroundScope)
            val foreground = SecureSessionStorage(backing, config, backgroundScope)
            val guard = assertNotNull(background.captureInstallationGuard())
            val installation = async { background.installSessionIfCurrent(guard, recovered) }
            entered.await()
            val signOut = async { foreground.clearSession() }
            runCurrent()
            assertFalse(signOut.isCompleted)
            release.complete(Unit)
            assertTrue(installation.await())
            signOut.await()
            assertNull(SecureSessionStorage(backing, config, backgroundScope).captureInstallationGuard())
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
