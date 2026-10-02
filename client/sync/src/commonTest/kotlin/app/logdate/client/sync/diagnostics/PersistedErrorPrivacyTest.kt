package app.logdate.client.sync.diagnostics

import app.logdate.client.sync.InMemoryKeyValueStorage
import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.metadata.KeyValueLastSyncErrorStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PersistedErrorPrivacyTest {
    @Test
    fun `saving and migrating last error keeps category without retaining remote content`() =
        runTest {
            val storage = InMemoryKeyValueStorage()
            val store = KeyValueLastSyncErrorStore(storage)
            store.save(SyncError(SyncErrorType.SERVER_ERROR, "private-journal-and-path"))
            assertFalse(storage.values.toString().contains("private-"))
            storage.putString("sync_last_error_message", "legacy-private-secret")
            assertEquals(SyncErrorType.SERVER_ERROR, store.load()?.type)
            assertFalse(storage.values.toString().contains("private-"))
            assertFalse(store.load().toString().contains("private-"))
        }

    @Test
    fun `legacy dead letters lose private messages without losing retry work`() =
        runTest {
            val storage = InMemoryKeyValueStorage()
            storage.putString(
                "sync_dead_letter_queue",
                """[{"id":"job","entityType":"NOTE","entityId":"record","operation":"CREATE","retryCount":4,"lastError":"No such file private-path-marker","failedAt":42}]""",
            )
            val store =
                app.logdate.client.sync.metadata
                    .KeyValueSyncDeadLetterStore(storage)
            val record = store.list().single()
            assertEquals("record", record.entityId)
            assertEquals(4, record.retryCount)
            assertEquals(app.logdate.client.sync.metadata.SyncDeadLetterReason.MISSING_FILE, record.reason)
            assertFalse(storage.values.toString().contains("private-path-marker"))
            store.add(record.copy(lastError = "private-new-error"))
            assertFalse(storage.values.toString().contains("private-new-error"))
        }

    @Test
    fun `failed privacy rewrite preserves valid retry work and permits later mutation`() =
        runTest {
            val backing = InMemoryKeyValueStorage()
            backing.putString(
                "sync_dead_letter_queue",
                """[{"id":"job","entityType":"NOTE","entityId":"record","operation":"CREATE","retryCount":4,"lastError":"No such file private-path-marker","failedAt":42}]""",
            )
            var failWrites = true
            val storage =
                object : app.logdate.client.datastore.KeyValueStorage by backing {
                    override suspend fun putString(
                        key: String,
                        value: String,
                    ) {
                        if (failWrites) error("private-disk-error")
                        backing.putString(key, value)
                    }
                }
            val store =
                app.logdate.client.sync.metadata
                    .KeyValueSyncDeadLetterStore(storage)
            val record = store.list().single()
            assertEquals("MISSING_FILE", record.lastError)
            assertEquals(4, record.retryCount)
            failWrites = false
            store.add(record.copy(retryCount = 5))
            assertEquals(5, store.list().single().retryCount)
            assertFalse(backing.values.toString().contains("private-"))
        }
}
