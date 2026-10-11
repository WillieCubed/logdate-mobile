package app.logdate.client.data.account

import androidx.room.Room
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.entities.sync.PendingUploadEntity
import app.logdate.client.database.entities.sync.SyncCursorEntity
import app.logdate.client.database.getRoomDatabase
import app.logdate.client.sync.peer.link.OwnerScope
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests [OwnerAdoptionMover] against a real database: a linked device's queue and cursors follow it to its account. */
class OwnerAdoptionMoverTest {
    private val database =
        getRoomDatabase(Room.databaseBuilder<LogDateDatabase>(Files.createTempFile("owner-adoption-mover", ".db").toString()))
    private val dao = database.syncMetadataDao()
    private val mover = OwnerAdoptionMover(dao, database.historyOwnerAdoptionDao())

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `queued uploads and cursors move to the linked account and server`() =
        runBlocking {
            dao.insertPending(pending(LOCAL, DEFAULT, "note-1", "op-1"))
            dao.upsertCursor(SyncCursorEntity(LOCAL, DEFAULT, "NOTE", 50))

            val leftBehind = mover.move(OwnerScope(LOCAL, DEFAULT), OwnerScope(ACCOUNT, LINKED), "watch-device")

            assertEquals(0, leftBehind)
            assertEquals(listOf("op-1"), dao.getPendingForOrigin(ACCOUNT, LINKED).map { it.operationId })
            assertTrue(dao.getPendingForOrigin(LOCAL, DEFAULT).isEmpty())
            assertEquals(50, dao.getCursor(ACCOUNT, LINKED, "NOTE")?.lastSyncTimestamp)
        }

    @Test
    fun `a record the account already queues stays behind and is counted`() =
        runBlocking {
            dao.insertPending(pending(LOCAL, DEFAULT, "note-1", "op-local"))
            dao.insertPending(pending(ACCOUNT, LINKED, "note-1", "op-account"))

            val leftBehind = mover.move(OwnerScope(LOCAL, DEFAULT), OwnerScope(ACCOUNT, LINKED), "watch-device")

            assertEquals(1, leftBehind)
            assertEquals(listOf("op-local"), dao.getPendingForOrigin(LOCAL, DEFAULT).map { it.operationId })
            assertEquals(listOf("op-account"), dao.getPendingForOrigin(ACCOUNT, LINKED).map { it.operationId })
        }

    @Test
    fun `moving a second time changes nothing`() =
        runBlocking {
            dao.insertPending(pending(LOCAL, DEFAULT, "note-1", "op-1"))
            mover.move(OwnerScope(LOCAL, DEFAULT), OwnerScope(ACCOUNT, LINKED), "watch-device")

            assertEquals(0, mover.move(OwnerScope(LOCAL, DEFAULT), OwnerScope(ACCOUNT, LINKED), "watch-device"))
            assertEquals(listOf("op-1"), dao.getPendingForOrigin(ACCOUNT, LINKED).map { it.operationId })
        }

    private fun pending(
        owner: String,
        origin: String,
        id: String,
        operationId: String,
    ) = PendingUploadEntity(
        ownerId = owner,
        serverOrigin = origin,
        entityType = "NOTE",
        entityId = id,
        operation = "CREATE",
        createdAt = 1,
        operationId = operationId,
    )

    private companion object {
        const val LOCAL = "local-owner"
        const val ACCOUNT = "account-owner"
        const val DEFAULT = "https://default.test"
        const val LINKED = "https://linked.test"
    }
}
