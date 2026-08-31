package app.logdate.client.sync

import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.test.fakeJournalNotesRepository
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Entries can exist before a device has ever synced -- written offline, or restored from a backup.
 * Nothing enqueues those after the fact, so without a first-sync sweep they stay on the device for
 * ever while sync keeps reporting success.
 */
class FirstSyncBackfillTest {
    @Test
    fun `entries written before the first sync are queued for upload`() =
        runTest {
            val notesRepository = fakeJournalNotesRepository("restored one", "restored two")
            val metadata = fakeSyncMetadataService()
            val syncManager =
                testDefaultSyncManager(
                    journalNotesRepository = notesRepository,
                    syncMetadataService = metadata,
                )

            assertEquals(
                0,
                metadata.getPendingUploads(EntityType.NOTE).size,
                "nothing should be queued before the first sync runs",
            )

            val result = syncManager.fullSync()

            assertEquals(
                2,
                result.uploadedItems,
                "both entries should have been picked up and uploaded by the first sync",
            )
            assertEquals(
                0,
                metadata.getPendingCount(),
                "and nothing should still be waiting afterwards",
            )
        }
}
