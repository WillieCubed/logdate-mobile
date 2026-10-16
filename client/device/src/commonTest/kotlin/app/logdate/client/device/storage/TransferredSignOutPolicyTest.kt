package app.logdate.client.device.storage

import app.logdate.client.datastore.NativeRestorePolicy
import app.logdate.client.datastore.UserSession
import app.logdate.shared.config.DefaultLogDateConfigRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransferredSignOutPolicyTest {
    private val origin = "https://restore.example"
    private val session = UserSession("access", "refresh", "owner")

    @Test
    fun `rejected pinned login cannot hide a newer connected login`() =
        runTest {
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://other.example")
            val source = SecureSessionStorage(FakeSecureStorage(), config, backgroundScope)
            source.write(origin, session)
            val pinned = source.openSessionStorage(origin, backgroundScope)
            val guard = assertNotNull(pinned.captureAuthenticationGuard())
            config.updateBackendUrl(origin)
            source.hasValidSession()
            val newer = session.copy(accessToken = "newer-access", refreshToken = "newer-refresh")
            source.saveExplicitSession(newer)
            assertFalse(pinned.installExplicitSessionIfCurrent(guard, session))
            assertEquals(newer, source.getSession())
            assertEquals(newer, source.read(origin))
        }

    @Test
    fun `failed or cancelled tombstone write removes cached authentication`() =
        runTest {
            for (cancelled in listOf(false, true)) {
                val settings = mutableMapOf<String, String>()
                val policy = NativeRestorePolicy({ settings[it] }, { key, value -> settings[key] = value })
                val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
                val backing = FakeSecureStorage()
                val failing =
                    object : SecureStorage by backing {
                        override suspend fun putString(
                            key: String,
                            value: String,
                        ) {
                            if (value == "null") {
                                if (cancelled) throw kotlinx.coroutines.CancellationException("interrupted sign out")
                                error("secure storage unavailable")
                            }
                            backing.putString(key, value)
                        }
                    }
                val source = SecureSessionStorage(failing, config, backgroundScope, nativeRestorePolicy = policy)
                source.saveExplicitSession(session)
                assertTrue(runCatching { source.clearSession() }.isFailure)
                assertNull(source.getSession())
                assertFalse(source.hasValidSession())
                assertFalse(policy.canRestore(origin))
                assertNull(SecureSessionStorage(backing, config, backgroundScope, nativeRestorePolicy = policy).read(origin))
            }
        }

    @Test
    fun `pinned sign out updates the connected vault when its server becomes active`() =
        runTest {
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://other.example")
            val source = SecureSessionStorage(FakeSecureStorage(), config, backgroundScope)
            source.write(origin, session)
            val pinned = source.openSessionStorage(origin, backgroundScope)
            config.updateBackendUrl(origin)
            source.hasValidSession()
            assertNotNull(source.getSession())
            pinned.clearSession()
            assertNull(source.getSession())
            assertFalse(source.hasValidSession())
        }

    @Test
    fun `a pinned server refresh cannot undo sign out through another vault`() =
        runTest {
            val settings = mutableMapOf<String, String>()
            val policy = NativeRestorePolicy({ settings[it] }, { key, value -> settings[key] = value })
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://other.example")
            val source = SecureSessionStorage(FakeSecureStorage(), config, backgroundScope, nativeRestorePolicy = policy)
            source.write(origin, session)
            val pinned = assertNotNull(source.openSessionStorage(origin, backgroundScope))
            assertTrue(pinned.supportsGuardedAuthentication)
            val binding = assertNotNull(pinned.getOriginBoundSession())
            val login = assertNotNull(pinned.captureAuthenticationGuard())
            source.clear(origin)
            assertFalse(pinned.replaceSessionIfCurrent(binding, session.copy(accessToken = "obsolete")))
            assertFalse(pinned.installExplicitSessionIfCurrent(login, session))
            assertFalse(policy.canRestore(origin))
            assertNull(source.read(origin))
        }

    @Test
    fun `signed out backup cannot recover account into a fresh secure store`() =
        runTest {
            val settings = mutableMapOf<String, String>()
            val policy = NativeRestorePolicy({ settings[it] }, { key, value -> settings[key] = value })
            val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
            val source = SecureSessionStorage(FakeSecureStorage(), config, backgroundScope, nativeRestorePolicy = policy)
            source.saveSession(session)
            source.clearSession()
            val transferred = settings.toMutableMap()
            val restoredPolicy = NativeRestorePolicy({ transferred[it] }, { key, value -> transferred[key] = value })
            val target = SecureSessionStorage(FakeSecureStorage(), config, backgroundScope, nativeRestorePolicy = restoredPolicy)
            assertNull(target.captureInstallationGuard())
            assertNull(target.getSession())
        }

    @Test
    fun `backup policy revoked after capture prevents installation`() =
        runTest {
            val settings = mutableMapOf<String, String>()
            val policy = NativeRestorePolicy({ settings[it] }, { key, value -> settings[key] = value })
            val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
            val target = SecureSessionStorage(FakeSecureStorage(), config, backgroundScope, nativeRestorePolicy = policy)
            val guard = assertNotNull(target.captureInstallationGuard())
            policy.setAllowed(origin, false)
            assertFalse(target.installSessionIfCurrent(guard, session))
            assertNull(target.getSession())
        }

    @Test
    fun `explicit authentication reenables backed authorization after durable installation`() =
        runTest {
            val settings = mutableMapOf<String, String>()
            val policy = NativeRestorePolicy({ settings[it] }, { key, value -> settings[key] = value })
            val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
            val target = SecureSessionStorage(FakeSecureStorage(), config, backgroundScope, nativeRestorePolicy = policy)
            target.clearSession()
            assertNull(target.captureInstallationGuard())
            val guard = assertNotNull(target.captureAuthenticationGuard())
            assertTrue(target.installExplicitSessionIfCurrent(guard, session))
            assertTrue(policy.canRestore(origin))
            assertNotNull(target.captureInstallationGuard())
        }

    @Test
    fun `a later repeated sign out wins over an earlier explicit authentication`() =
        runTest {
            val settings = mutableMapOf<String, String>()
            val policy = NativeRestorePolicy({ settings[it] }, { key, value -> settings[key] = value })
            val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
            val backing = FakeSecureStorage()
            val target = SecureSessionStorage(backing, config, backgroundScope, nativeRestorePolicy = policy)
            target.clearSession()
            val guard = assertNotNull(target.captureAuthenticationGuard())
            SecureSessionStorage(backing, config, backgroundScope, nativeRestorePolicy = policy).clearSession()
            assertFalse(target.installExplicitSessionIfCurrent(guard, session))
            assertFalse(policy.canRestore(origin))
        }

    @Test
    fun `failed authorization write keeps committed tokens inaccessible after restart and remains repairable`() =
        runTest {
            val settings = mutableMapOf<String, String>()
            var failAllow = true
            val policy =
                NativeRestorePolicy({ settings[it] }, { key, value ->
                    if (failAllow && value.startsWith("allowed:")) error("preferences unavailable")
                    settings[key] = value
                })
            val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
            val backing = FakeSecureStorage()
            val source = SecureSessionStorage(backing, config, backgroundScope, nativeRestorePolicy = policy)
            source.clearSession()
            val guard = assertNotNull(source.captureAuthenticationGuard())
            assertFailsWith<IllegalStateException> { source.installExplicitSessionIfCurrent(guard, session) }
            val restarted = SecureSessionStorage(backing, config, backgroundScope, nativeRestorePolicy = policy)
            assertNull(restarted.read(origin))
            assertNull(restarted.captureInstallationGuard())
            failAllow = false
            val retry = assertNotNull(restarted.captureAuthenticationGuard())
            assertTrue(restarted.installExplicitSessionIfCurrent(retry, session))
        }

    @Test
    fun `a stale vault cannot refresh after another vault signs out`() =
        runTest {
            val settings = mutableMapOf<String, String>()
            val policy = NativeRestorePolicy({ settings[it] }, { key, value -> settings[key] = value })
            val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
            val backing = FakeSecureStorage()
            val source = SecureSessionStorage(backing, config, backgroundScope, nativeRestorePolicy = policy)
            source.saveExplicitSession(session)
            val binding = assertNotNull(source.getOriginBoundSession())
            SecureSessionStorage(backing, config, backgroundScope, nativeRestorePolicy = policy).clearSession()
            assertFalse(source.replaceSessionIfCurrent(binding, session.copy(accessToken = "obsolete")))
            assertNull(SecureSessionStorage(backing, config, backgroundScope, nativeRestorePolicy = policy).read(origin))
        }
}
