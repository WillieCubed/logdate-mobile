package app.logdate.client.sync

import app.logdate.client.sync.metadata.UploadScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SyncUpgradeResumptionTest {
    private val storage = InMemoryKeyValueStorage()
    private val scope = UploadScope("account", "https://example.test")

    @Test
    fun `an update resumes once across process restarts and again for the next version`() =
        runTest {
            var releases = 0

            suspend fun resume(version: String) = SyncUpgradeResumption(storage).resume(version, scope, { true }) { releases++ }

            resume("55")
            resume("55")
            assertEquals(1, releases)
            resume("56")
            assertEquals(2, releases)
        }

    @Test
    fun `failed resumption is retried rather than marked complete`() =
        runTest {
            assertFailsWith<IllegalStateException> {
                SyncUpgradeResumption(storage).resume("56", scope, { true }) { error("Storage unavailable") }
            }
            var releases = 0
            SyncUpgradeResumption(storage).resume("56", scope, { true }) { releases++ }
            assertEquals(1, releases)
        }

    @Test
    fun `accounts and servers resume independently`() =
        runTest {
            var releases = 0
            for (selected in listOf(scope, scope.copy(ownerId = "another"), scope.copy(serverOrigin = "https://other.test"))) {
                SyncUpgradeResumption(storage).resume("56", selected, { true }) { releases++ }
            }
            assertEquals(3, releases)
        }

    @Test
    fun `changing accounts during resumption leaves the original scope unfinished`() =
        runTest {
            var current = true
            assertFailsWith<IllegalStateException> {
                SyncUpgradeResumption(storage).resume("56", scope, { current }) { current = false }
            }
            var releases = 0
            SyncUpgradeResumption(storage).resume("56", scope, { true }) { releases++ }
            assertEquals(1, releases)
        }
}
