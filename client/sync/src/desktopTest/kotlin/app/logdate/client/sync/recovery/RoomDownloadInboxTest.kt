package app.logdate.client.sync.recovery

import androidx.room.Room
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.dao.sync.DownloadInboxDao
import app.logdate.client.database.entities.sync.DownloadCheckpointEntity
import app.logdate.client.database.entities.sync.PendingUploadEntity
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.sync.RoomSyncTransactionManager
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RoomDownloadInboxTest {
    @Test
    fun `repair insertion preserves scoped and legacy mutations and retry state`() =
        runTest {
            val path = Files.createTempFile("logdate-repair-test", ".db")
            Files.delete(path)

            fun open() = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(path.toString()))
            var database = open()
            try {
                val dao = database.syncMetadataDao()
                for (owner in listOf("owner", "")) {
                    val id = "deleted-$owner"
                    val deletion = PendingUploadEntity(owner, "server", "DRAFT", id, "DELETE", 10, 7)
                    dao.insertPending(deletion)
                    dao.insertRepairIfAbsent("owner", "server", "DRAFT", id, 20)
                    dao.insertRepairIfAbsent("owner", "server", "DRAFT", id, 30, operation = "CREATE")
                    assertEquals(deletion, dao.getPending(owner, "server", "DRAFT", id))
                    if (owner.isEmpty()) assertEquals(null, dao.getPending("owner", "server", "DRAFT", id))
                }
                dao.insertPending(PendingUploadEntity("", "other-server", "DRAFT", "recoverable", "DELETE", 10))
                dao.insertRepairIfAbsent("owner", "server", "DRAFT", "recoverable", 20, expectedServerVersion = 2L)
                assertEquals("UPDATE", dao.getPending("owner", "server", "DRAFT", "recoverable")?.operation)
                dao.insertRepairIfAbsent("owner", "server", "DRAFT", "recoverable", 30)
                assertEquals(20L, dao.getPending("owner", "server", "DRAFT", "recoverable")?.createdAt)
                dao.insertRepairIfAbsent("owner", "server", "NOTE", "new-legacy-entry", 40, operation = "CREATE")
                val initial = requireNotNull(dao.getPending("owner", "server", "NOTE", "new-legacy-entry"))
                kotlin.test.assertTrue(dao.bindCreateToServerVersion("owner", "server", "NOTE", initial.entityId, initial.operationId, 42))
                kotlin.test.assertFalse(dao.deletePendingIfCurrent("owner", "server", "NOTE", initial.entityId, initial.operationId))
                database.close()
                database = open()
                assertEquals("UPDATE", database.syncMetadataDao().getPending("owner", "server", "NOTE", "new-legacy-entry")?.operation)
                assertEquals(
                    42L,
                    database.syncMetadataDao().getPending("owner", "server", "NOTE", "new-legacy-entry")?.expectedServerVersion,
                )
                assertEquals("DELETE", database.syncMetadataDao().getPending("owner", "server", "DRAFT", "deleted-owner")?.operation)
                assertEquals(2L, database.syncMetadataDao().getPending("owner", "server", "DRAFT", "recoverable")?.expectedServerVersion)
            } finally {
                database.close()
                Files.deleteIfExists(path)
                Files.deleteIfExists(path.resolveSibling("${path.fileName}-wal"))
                Files.deleteIfExists(path.resolveSibling("${path.fileName}-shm"))
            }
        }

    @Test
    fun `wire page and cursor commit together and survive reopening the database`() =
        runTest {
            val path = Files.createTempFile("logdate-inbox-test", ".db")
            Files.delete(path)

            fun open() = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(path.toString()))
            var database = open()
            try {
                var fail = true
                val dao = database.downloadInboxDao()
                val faultingDao =
                    object : DownloadInboxDao by dao {
                        override suspend fun checkpoint(row: DownloadCheckpointEntity) {
                            if (fail) error("simulated interruption before checkpoint")
                            dao.checkpoint(row)
                        }
                    }
                val scope = { DownloadScope("disposable-owner", "disposable-server") }
                val inbox = DownloadInbox(faultingDao, RoomSyncTransactionManager(database), scope, { 1L })
                assertFailsWith<IllegalStateException> {
                    inbox.stage("JOURNAL", 10, listOf(WireDownload("record", 10, false, "encrypted-wire")))
                }
                assertEquals(0, inbox.count())
                assertEquals(0L, inbox.cursor("JOURNAL"))
                fail = false
                inbox.stage("JOURNAL", 10, listOf(WireDownload("record", 10, false, "encrypted-wire")))
                database.close()
                database = open()
                val restarted = DownloadInbox(database.downloadInboxDao(), RoomSyncTransactionManager(database), scope, { 1L })
                assertEquals(10L, restarted.cursor("JOURNAL"))
                assertEquals("encrypted-wire", restarted.pending("JOURNAL").single().payload)
            } finally {
                database.close()
                Files.deleteIfExists(path)
                Files.deleteIfExists(path.resolveSibling("${path.fileName}-wal"))
                Files.deleteIfExists(path.resolveSibling("${path.fileName}-shm"))
            }
        }
}
