package app.logdate.client.sync.metadata

import androidx.room.Room
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.entities.sync.PendingUploadEntity
import app.logdate.client.database.getRoomDatabase
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomPendingOperationTest {
    @Test
    fun `reopened outbox rejects old settlement while retaining other origins and newer deletion`() =
        runTest {
            val path = Files.createTempFile("logdate-operation-test", ".db")
            Files.delete(path)

            fun open() = getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(path.toString()))
            var database = open()
            try {
                val original = PendingUploadEntity("owner", "first", "NOTE", "note", "UPDATE", 10)
                val newer = PendingUploadEntity("owner", "first", "NOTE", "note", "DELETE", 11)
                val other = PendingUploadEntity("owner", "second", "NOTE", "note", "UPDATE", 12)
                database.syncMetadataDao().insertPending(original)
                database.close()
                database = open()
                val dao = database.syncMetadataDao()
                assertEquals(original.operationId, dao.getPending("owner", "first", "NOTE", "note")?.operationId)
                dao.insertPending(newer)
                dao.insertPending(other)
                assertFalse(dao.deletePendingIfCurrent("owner", "first", "NOTE", "note", original.operationId))
                assertEquals(newer, dao.getPending("owner", "first", "NOTE", "note"))
                assertTrue(dao.deletePendingIfCurrent("owner", "first", "NOTE", "note", newer.operationId))
                assertEquals(other, dao.getPending("owner", "second", "NOTE", "note"))
            } finally {
                database.close()
                Files.deleteIfExists(path)
                Files.deleteIfExists(path.resolveSibling("${path.fileName}-wal"))
                Files.deleteIfExists(path.resolveSibling("${path.fileName}-shm"))
            }
        }
}
