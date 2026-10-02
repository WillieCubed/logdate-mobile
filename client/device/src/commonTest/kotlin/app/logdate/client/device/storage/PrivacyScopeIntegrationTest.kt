package app.logdate.client.device.storage

import app.logdate.client.datastore.UserSession
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.config.PrivacyEpochStorage
import app.logdate.shared.config.PrivacyScopeEpoch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrivacyScopeIntegrationTest {
    private class EpochStorage : PrivacyEpochStorage {
        var stored: String? = null
        var fail = false

        override suspend fun read() = stored

        override suspend fun write(epoch: String) {
            check(!fail)
            stored = epoch
        }
    }

    @Test
    fun `backend changes rotate durable consent before observers can conflate them`() =
        runTest {
            val storage = EpochStorage()
            val epoch = PrivacyScopeEpoch(storage)
            epoch.initialize()
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://a.invalid", privacyEpoch = epoch)
            val grant = epoch.value.value
            config.updateBackendUrl("https://b.invalid")
            config.updateBackendUrl("https://a.invalid")
            assertNotEquals(grant, epoch.value.value)
            val changed = epoch.value.value
            config.updateBackendUrl("https://a.invalid/")
            assertEquals(changed, epoch.value.value)
            storage.fail = true
            assertTrue(runCatching { config.updateBackendUrl("https://b.invalid") }.isFailure)
            assertEquals("https://a.invalid", config.getCurrentBackendUrl())
            assertNull(epoch.value.value)
        }

    @Test
    fun `ownership publication awaits durable revocation and restoring the same owner preserves it`() =
        runTest {
            val epochStore = EpochStorage()
            val epoch = PrivacyScopeEpoch(epochStore)
            epoch.initialize()
            val backing = FakeSecureStorage()
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://a.invalid", privacyEpoch = epoch)
            val sessions = SecureSessionStorage(backing, config, backgroundScope, privacyEpoch = epoch)
            val first = UserSession("a", "r", "owner-a")
            sessions.saveSession(first)
            val granted = epoch.value.value
            sessions.saveSession(first.copy(accessToken = "refreshed"))
            assertEquals(granted, epoch.value.value)
            val restored = SecureSessionStorage(backing, config, backgroundScope, privacyEpoch = epoch)
            assertTrue(restored.hasValidSession())
            assertEquals(granted, epoch.value.value)
            epochStore.fail = true
            assertTrue(runCatching { sessions.saveSession(UserSession("b", "r", "owner-b")) }.isFailure)
            assertEquals("owner-a", sessions.getSession()?.accountId)
            assertNull(epoch.value.value)
            epochStore.fail = false
            sessions.saveSession(UserSession("b", "r", "owner-b"))
            sessions.saveSession(first)
            assertNotEquals(granted, epoch.value.value)
            val beforeSignOut = epoch.value.value
            sessions.clearSession()
            assertNull(sessions.getSession())
            assertNotEquals(beforeSignOut, epoch.value.value)
        }

    @Test
    fun `interrupted credential writes cannot restore credentials under a different owner`() =
        runTest {
            val backing = FakeSecureStorage()
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://a.invalid")
            var fail = false
            val storage =
                object : SecureStorage by backing {
                    override suspend fun putString(
                        key: String,
                        value: String,
                    ) {
                        if (fail && key.startsWith("session_account_id")) error("storage unavailable")
                        backing.putString(key, value)
                    }
                }
            val first = UserSession("a-access", "a-refresh", "owner-a")
            val second = UserSession("b-access", "b-refresh", "owner-b")
            val sessions = SecureSessionStorage(storage, config, backgroundScope)
            sessions.saveSession(first)
            fail = true
            runCatching { sessions.saveSession(second) }
            val restored = SecureSessionStorage(backing, config, backgroundScope)
            restored.hasValidSession()
            assertTrue(restored.getSession() in listOf(null, first, second), "Credentials and owner must be one atomic record")
        }
}
