package app.logdate.client.sync.metadata

import app.logdate.client.sync.InMemoryKeyValueStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncDeadLetterStoreTest {
    @Test
    fun `observing after restart loads saved failures before reporting an empty list`() =
        runTest {
            val storage = InMemoryKeyValueStorage()
            val record =
                SyncDeadLetterRecord(
                    id = "NOTE:1",
                    entityType = "NOTE",
                    entityId = "1",
                    operation = "CREATE",
                    retryCount = 3,
                    lastError = "Service Unavailable",
                    failedAt = 1L,
                )
            KeyValueSyncDeadLetterStore(storage).add(record)

            assertEquals(listOf(record), KeyValueSyncDeadLetterStore(storage).observe().first())
        }
}
