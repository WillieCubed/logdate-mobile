package app.logdate.client.sync.diagnostics

import app.logdate.shared.config.PrivacyEpochStorage
import app.logdate.shared.config.PrivacyScopeEpoch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrivacyScopeEpochTest {
    private class Storage : PrivacyEpochStorage {
        var stored: String? = null
        var fail = false

        override suspend fun read() = stored

        override suspend fun write(epoch: String) {
            check(!fail)
            stored = epoch
        }
    }

    @Test
    fun `fast return to previous account still invalidates prior consent after restart`() =
        runTest {
            val storage = Storage()
            val epoch = PrivacyScopeEpoch(storage)
            epoch.initialize()
            val granted = assertNotNull(epoch.value.value)
            var account = "a"
            epoch.transition(true) { account = "b" }
            epoch.transition(true) { account = "a" }
            assertEquals("a", account)
            val restored = PrivacyScopeEpoch(storage)
            restored.initialize()
            assertNotEquals(granted, assertNotNull(restored.value.value))
            val unchanged = restored.value.value
            restored.transition(false) { Unit }
            assertEquals(unchanged, restored.value.value)
        }

    @Test
    fun `failed epoch write prevents publication and denies sends until durable retry`() =
        runTest {
            val storage = Storage()
            val epoch = PrivacyScopeEpoch(storage)
            epoch.initialize()
            val before = epoch.value.value
            storage.fail = true
            var published = false
            assertTrue(runCatching { epoch.transition(true) { published = true } }.isFailure)
            assertTrue(!published)
            assertNull(epoch.value.value)
            storage.fail = false
            epoch.transition(true) { published = true }
            assertTrue(published)
            assertNotEquals(before, assertNotNull(epoch.value.value))
            assertEquals(storage.stored, epoch.value.value)
        }
}
