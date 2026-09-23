package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.fakeFirstSyncEnqueueStore
import app.logdate.client.sync.test.fakeJournalNotesRepository
import app.logdate.client.sync.test.fakeJournalRepository
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testSyncUploader
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [SyncUploader.enqueueEverythingOnFirstSync] used to be gated only on the download cursor --
 * [app.logdate.client.sync.metadata.SyncMetadataService.getLastSyncTime] returning null. If
 * downloading keeps failing (a poison page, a network problem, an account issue), that cursor
 * never advances, and the whole local table -- every journal, every note -- would be re-scanned
 * and re-enqueued on every single full sync attempt, for ever. `FirstSyncEnqueueStore` decouples
 * "have we already swept this device once" from that cursor, so the sweep runs at most once ever
 * per entity type.
 */
class FirstSyncEnqueueOnceTest {
    /** Throws once from [allNotesObserved] the first time it's collected, then behaves normally. */
    private class FlakyOnceJournalNotesRepository(
        private val delegate: FakeJournalNotesRepository,
    ) : JournalNotesRepository by delegate {
        private var shouldFail = true

        override val allNotesObserved: Flow<List<JournalNote>> =
            flow {
                if (shouldFail) {
                    shouldFail = false
                    throw IllegalStateException("Simulated first-sync enqueue failure")
                }
                emitAll(delegate.allNotesObserved)
            }
    }

    @Test
    fun `first call queues every journal and note already on the device`() =
        runTest {
            val journalRepository = fakeJournalRepository().apply { create(Journal(title = "Existing journal")) }
            val notesRepository = fakeJournalNotesRepository("restored one", "restored two")
            val metadata = fakeSyncMetadataService()
            val firstSyncStore = fakeFirstSyncEnqueueStore()
            val uploader =
                testSyncUploader(
                    journalRepository = journalRepository,
                    journalNotesRepository = notesRepository,
                    syncMetadataService = metadata,
                    firstSyncEnqueueStore = firstSyncStore,
                )

            uploader.enqueueEverythingOnFirstSync()

            assertEquals(1, metadata.getPendingUploads(EntityType.JOURNAL).size, "the existing journal should be queued")
            assertEquals(2, metadata.getPendingUploads(EntityType.NOTE).size, "both existing notes should be queued")
            assertTrue(firstSyncStore.hasEnqueued(EntityType.JOURNAL), "the sweep should be recorded as done for journals")
            assertTrue(firstSyncStore.hasEnqueued(EntityType.NOTE), "the sweep should be recorded as done for notes")
        }

    @Test
    fun `a download cursor that never advances does not repeat the scan`() =
        runTest {
            val journalRepository = fakeJournalRepository().apply { create(Journal(title = "Existing journal")) }
            val notesRepository = fakeJournalNotesRepository("restored one", "restored two")
            val metadata = fakeSyncMetadataService()
            val firstSyncStore = fakeFirstSyncEnqueueStore()
            val uploader =
                testSyncUploader(
                    journalRepository = journalRepository,
                    journalNotesRepository = notesRepository,
                    syncMetadataService = metadata,
                    firstSyncEnqueueStore = firstSyncStore,
                )

            uploader.enqueueEverythingOnFirstSync()
            val callsAfterFirstRun = metadata.enqueuePendingCalls.size
            assertEquals(3, callsAfterFirstRun, "one journal and two notes should have been enqueued once")

            // The download cursor for JOURNAL/NOTE is never advanced here (getLastSyncTime stays
            // null throughout, exactly as it would if every download attempt kept failing).
            // Without the flag, this second call would re-scan and re-enqueue the whole table.
            uploader.enqueueEverythingOnFirstSync()
            uploader.enqueueEverythingOnFirstSync()

            assertEquals(
                callsAfterFirstRun,
                metadata.enqueuePendingCalls.size,
                "a still-null download cursor must not cause the full-table sweep to repeat",
            )
        }

    @Test
    fun `an enqueue that throws partway leaves that entity type unmarked for a later retry`() =
        runTest {
            val journalRepository = fakeJournalRepository().apply { create(Journal(title = "Existing journal")) }
            val flakyNotes =
                FlakyOnceJournalNotesRepository(
                    FakeJournalNotesRepository().apply {
                        addTestNote("a")
                        addTestNote("b")
                    },
                )
            val metadata = fakeSyncMetadataService()
            val firstSyncStore = fakeFirstSyncEnqueueStore()
            val uploader =
                testSyncUploader(
                    journalRepository = journalRepository,
                    journalNotesRepository = flakyNotes,
                    syncMetadataService = metadata,
                    firstSyncEnqueueStore = firstSyncStore,
                )

            // The journal sweep runs first and succeeds; the note sweep throws before it enqueues
            // anything. enqueueEverythingOnFirstSync swallows the failure (it must not fail sync).
            uploader.enqueueEverythingOnFirstSync()

            assertTrue(firstSyncStore.hasEnqueued(EntityType.JOURNAL), "the journal sweep completed and must be marked done")
            assertFalse(firstSyncStore.hasEnqueued(EntityType.NOTE), "the note sweep threw and must not be marked done")
            assertEquals(1, metadata.getPendingUploads(EntityType.JOURNAL).size, "the journal should still be queued")
            assertEquals(0, metadata.getPendingUploads(EntityType.NOTE).size, "nothing reached the queue before the throw")

            // A later sync attempt must still retry the note sweep -- the journal sweep must not
            // run a second time now that it's marked done.
            uploader.enqueueEverythingOnFirstSync()

            assertTrue(firstSyncStore.hasEnqueued(EntityType.NOTE), "the retry should have completed the note sweep")
            assertEquals(2, metadata.getPendingUploads(EntityType.NOTE).size, "both notes should be queued after the retry")
            assertEquals(
                1,
                metadata.enqueuePendingCalls.count { it.first == EntityType.JOURNAL },
                "the journal sweep must not repeat once it succeeded",
            )
        }
}
