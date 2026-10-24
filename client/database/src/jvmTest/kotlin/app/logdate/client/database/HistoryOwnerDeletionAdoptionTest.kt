package app.logdate.client.database

import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.logdate.client.database.entities.HistoryRecordEntity
import app.logdate.client.database.entities.sync.DownloadInboxEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HistoryOwnerDeletionAdoptionTest {
    @Test
    fun `consented adoption moves only valid typed deletions on the exact origin`() =
        runTest {
            withDatabase { db ->
                val inbox = db.downloadInboxDao()
                for (type in TYPES) inbox.put(marker(type = type))
                val outside =
                    listOf(
                        marker(origin = "other"),
                        marker(owner = "foreign"),
                        marker(type = "ARCHIVE_NOTE"),
                        marker(type = "LOCAL_DELETE_FUTURE"),
                        marker(id = "pending").copy(state = "PENDING"),
                    )
                for (row in outside) inbox.put(row)
                db.historyOwnerAdoptionDao().adopt("old", "new", "origin", "device") { it }
                for (type in TYPES) {
                    assertNull(inbox.get("old", "origin", type, "entity"))
                    assertEquals(marker(owner = "new", type = type), inbox.get("new", "origin", type, "entity"))
                }
                for (row in outside) assertEquals(row, inbox.get(row.ownerId, row.serverOrigin, row.entityType, row.entityId))
            }
        }

    @Test
    fun `destination collision preserves deletion and retries respect explicit recreation`() =
        runTest {
            withDatabase { db ->
                val inbox = db.downloadInboxDao()
                val target = marker(owner = "new")
                inbox.put(marker())
                inbox.put(target)
                val dao = db.historyOwnerAdoptionDao()
                dao.adopt("old", "new", "origin", "device") { it }
                assertEquals(target, inbox.get("new", "origin", TYPES.first(), "entity"))
                assertNull(inbox.get("old", "origin", TYPES.first(), "entity"))
                db.useWriterConnection { connection ->
                    connection.usePrepared("DELETE FROM sync_download_inbox WHERE ownerId = 'new'") { it.step() }
                }
                dao.adopt("old", "new", "origin", "device") { it }
                assertNull(inbox.get("new", "origin", TYPES.first(), "entity"))
            }
        }

    @Test
    fun `failure after receipt migration rolls back receipts and history together then retries`() =
        runTest {
            withDatabase { db ->
                val inbox = db.downloadInboxDao()
                inbox.put(marker())
                db.historyRecordDao().upsert(
                    HistoryRecordEntity("old", "origin", "history", "place", "private", "device", 1, 0, false, true),
                )
                val dao = db.historyOwnerAdoptionDao()
                assertFailsWith<IllegalStateException> {
                    dao.adopt("old", "new", "origin", "device") { error("fault after migration") }
                }
                assertEquals(marker(), inbox.get("old", "origin", TYPES.first(), "entity"))
                assertNull(inbox.get("new", "origin", TYPES.first(), "entity"))
                assertNotNull(db.historyRecordDao().record("old", "origin", "history"))
                dao.adopt("old", "new", "origin", "device") { it }
                assertNull(inbox.get("old", "origin", TYPES.first(), "entity"))
                assertNotNull(inbox.get("new", "origin", TYPES.first(), "entity"))
            }
        }

    @Test
    fun `malformed destination collision refuses adoption without losing original intent`() =
        runTest {
            withDatabase { db ->
                val inbox = db.downloadInboxDao()
                inbox.put(marker())
                val target = marker(owner = "new").copy(state = "PENDING")
                inbox.put(target)
                assertFailsWith<IllegalStateException> {
                    db.historyOwnerAdoptionDao().adopt("old", "new", "origin", "device") { it }
                }
                assertEquals(marker(), inbox.get("old", "origin", TYPES.first(), "entity"))
                assertEquals(target, inbox.get("new", "origin", TYPES.first(), "entity"))
            }
        }

    @Test
    fun `active malformed source deletion refuses binding instead of losing its shield`() =
        runTest {
            val malformed =
                listOf(
                    marker().copy(operationId = "wire-operation"),
                    marker().copy(version = 1),
                    marker().copy(payload = "unclassified"),
                    marker().copy(deleted = false),
                    marker().copy(attempts = 1),
                    marker().copy(nextAttemptAt = 1),
                    marker().copy(reason = "UNKNOWN"),
                )
            for (source in malformed) {
                withDatabase { db ->
                    val inbox = db.downloadInboxDao()
                    inbox.put(source)
                    assertFailsWith<IllegalStateException> {
                        db.historyOwnerAdoptionDao().adopt("old", "new", "origin", "device") { it }
                    }
                    assertEquals(source, inbox.get("old", "origin", TYPES.first(), "entity"))
                    assertNull(inbox.get("new", "origin", TYPES.first(), "entity"))
                }
            }
        }

    private suspend fun withDatabase(block: suspend (LogDateDatabase) -> Unit) {
        val database = Room.inMemoryDatabaseBuilder<LogDateDatabase>().setDriver(BundledSQLiteDriver()).build()
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun marker(
        owner: String = "old",
        origin: String = "origin",
        type: String = TYPES.first(),
        id: String = "entity",
    ) = DownloadInboxEntity(owner, origin, type, id, 0, true, "", "local-delete-v1", "APPLIED", 0, 0, "NONE")

    private companion object {
        val TYPES = listOf("LOCAL_DELETE_NOTE", "LOCAL_DELETE_JOURNAL", "LOCAL_DELETE_ASSOCIATION", "LOCAL_DELETE_TRANSCRIPTION")
    }
}
