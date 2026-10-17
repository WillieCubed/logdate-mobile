package app.logdate.client.device.storage

import app.logdate.client.datastore.NativeRestorePolicy
import app.logdate.client.datastore.UserSession
import app.logdate.shared.config.DefaultLogDateConfigRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionAuthorizationLifetimeTest {
    @Test
    fun `retained authorization survives bounded guard cache eviction and access refresh`() =
        runTest {
            for (withPolicy in listOf(false, true)) {
                val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://first.example")
                val settings = mutableMapOf<String, String>()
                val policy = if (withPolicy) NativeRestorePolicy({ settings[it] }, { k, v -> settings[k] = v }) else null
                val vault = SecureSessionStorage(FakeSecureStorage(), config, backgroundScope, nativeRestorePolicy = policy)
                val session = UserSession("access", "refresh", "owner")
                vault.saveExplicitSession(session)
                val retained = assertNotNull(vault.captureAuthenticationSnapshot()).guard
                assertFalse(retained.toString().contains("access"), "Opaque guards must not render stored credentials")
                repeat(128) { assertNotNull(vault.captureAuthenticationSnapshot()) }
                assertTrue(vault.isAuthenticationGuardCurrent(retained))
                assertTrue(
                    vault.replaceSessionIfAuthorized(
                        assertNotNull(vault.getOriginBoundSession()),
                        session.copy(accessToken = "renewed"),
                        retained,
                    ),
                )
                assertTrue(vault.isAuthenticationGuardCurrent(retained))
                vault.saveExplicitSession(assertNotNull(vault.getSession()))
                assertFalse(vault.isAuthenticationGuardCurrent(retained))
            }
        }

    @Test
    fun `retained authorization cannot move to another vault or origin`() =
        runTest {
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://first.example")
            val backing = FakeSecureStorage()
            val vault = SecureSessionStorage(backing, config, backgroundScope)
            vault.saveExplicitSession(UserSession("access", "refresh", "owner"))
            val retained = assertNotNull(vault.captureAuthenticationSnapshot()).guard
            val otherVault = SecureSessionStorage(backing, config, backgroundScope)
            otherVault.captureAuthenticationSnapshot()
            assertFalse(otherVault.isAuthenticationGuardCurrent(retained))
            assertFalse(vault.isAuthenticationGuardCurrent(retained.copy(origin = "https://other.example")))
        }
}
