package app.logdate.wear.sync

import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncMetadataService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests [WearDeadLetterMigration], which puts captures older versions dropped from the outbox back
 * in it.
 */
class WearDeadLetterMigrationTest {
    private val deadLetterStore = mockk<SyncDeadLetterStore>(relaxed = true)
    private val syncMetadataService = mockk<SyncMetadataService>(relaxed = true)
    private val migration = WearDeadLetterMigration(deadLetterStore, syncMetadataService)
    private val noteId = "550e8400-e29b-41d4-a716-446655440000"

    @Test
    fun `a capture an older version set aside is queued again`() =
        runTest {
            coEvery { deadLetterStore.list() } returns listOf(record(id = "NOTE_$noteId", entityType = "NOTE", entityId = noteId))

            assertEquals(1, migration.migrate())

            coVerify { syncMetadataService.enqueuePending(noteId, EntityType.NOTE, PendingOperation.CREATE) }
            coVerify { deadLetterStore.remove("NOTE_$noteId") }
        }

    @Test
    fun `a link an older version set aside is queued again under its own id`() =
        runTest {
            val pendingId = "770e8400-e29b-41d4-a716-446655440000::$noteId"
            coEvery { deadLetterStore.list() } returns
                listOf(record(id = "ASSOCIATION_$pendingId", entityType = "ASSOCIATION", entityId = pendingId, operation = "DELETE"))

            migration.migrate()

            coVerify { syncMetadataService.enqueuePending(pendingId, EntityType.ASSOCIATION, PendingOperation.DELETE) }
        }

    @Test
    fun `items this version set aside are left alone`() =
        runTest {
            coEvery { deadLetterStore.list() } returns
                listOf(
                    record(id = "NOTE:$noteId", entityType = "NOTE", entityId = noteId),
                    record(id = "operation-1", entityType = "NOTE", entityId = noteId),
                )

            assertEquals(0, migration.migrate())

            coVerify(exactly = 0) { syncMetadataService.enqueuePending(any(), any(), any()) }
            coVerify(exactly = 0) { deadLetterStore.remove(any()) }
        }

    @Test
    fun `a record of an unknown kind stays where it can be seen`() =
        runTest {
            coEvery { deadLetterStore.list() } returns listOf(record(id = "MOMENT_$noteId", entityType = "MOMENT", entityId = noteId))

            assertEquals(0, migration.migrate())

            coVerify(exactly = 0) { deadLetterStore.remove(any()) }
        }

    private fun record(
        id: String,
        entityType: String,
        entityId: String,
        operation: String = "CREATE",
    ) = SyncDeadLetterRecord(
        id = id,
        entityType = entityType,
        entityId = entityId,
        operation = operation,
        retryCount = 5,
        lastError = "Phone did not confirm ${entityType.lowercase()} $entityId",
        failedAt = 1_700_000_000_000,
    )
}
