package app.logdate.client.domain.account

import app.logdate.client.domain.fakes.FakeJournalRepository
import app.logdate.client.domain.streak.FakeNotesRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.QueuedUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlin.uuid.Uuid

class EnqueueAllLocalDataUseCaseTest {
    private val journal = Journal(title = "Trips")
    private val note =
        JournalNote.Text(
            uid = Uuid.random(),
            content = "Hello",
            creationTimestamp = Instant.DISTANT_PAST,
            lastUpdated = Instant.DISTANT_PAST,
        )
    private val link = journal.id to note.uid
    private val draft = EditorDraft()

    @Test
    fun `everything on the device is queued including drafts`() =
        runTest {
            val sync = RecordingSyncMetadataService()

            val counts = useCase(sync).invoke().getOrThrow()

            assertEquals(EnqueueAllLocalDataUseCase.Counts(journals = 1, notes = 1, associations = 1, drafts = 1), counts)
            assertEquals(
                listOf(
                    Triple(journal.id.toString(), EntityType.JOURNAL, PendingOperation.CREATE),
                    Triple(note.uid.toString(), EntityType.NOTE, PendingOperation.CREATE),
                    Triple(AssociationPendingKey(link.first, link.second).toPendingId(), EntityType.ASSOCIATION, PendingOperation.CREATE),
                    Triple(draft.id.toString(), EntityType.DRAFT, PendingOperation.UPDATE),
                ),
                sync.enqueued,
            )
        }

    @Test
    fun `a first sign-in backfills drafts too`() =
        runTest {
            val sync = RecordingSyncMetadataService()
            val tracker = InMemoryTracker()
            val backfill = BackfillLocalDataUseCase(useCase(sync), tracker)

            val result = assertIs<BackfillLocalDataUseCase.Result.Success>(backfill("account-1"))

            assertEquals(1, result.draftCount)
            assertIs<BackfillLocalDataUseCase.Result.AlreadyBackfilled>(backfill("account-1"))
        }

    private fun useCase(sync: SyncMetadataService) =
        EnqueueAllLocalDataUseCase(
            journalRepository = FakeJournalRepository(initialJournals = listOf(journal), drafts = listOf(draft)),
            journalNotesRepository = NotesWithLink(),
            syncMetadataService = sync,
        )

    private inner class NotesWithLink : JournalNotesRepository by FakeNotesRepository() {
        override val allNotesObserved: Flow<List<JournalNote>> = flowOf(listOf(note))

        override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = listOf(link)
    }

    private class InMemoryTracker : BackfilledAccountTracker {
        private val ids = mutableSetOf<String>()

        override suspend fun getBackfilledAccountIds(): Set<String> = ids

        override suspend fun markAccountBackfilled(accountId: String) {
            ids += accountId
        }
    }

    private class RecordingSyncMetadataService : SyncMetadataService {
        val enqueued = mutableListOf<Triple<String, EntityType, PendingOperation>>()

        override suspend fun enqueuePending(
            entityId: String,
            entityType: EntityType,
            operation: PendingOperation,
        ) {
            enqueued += Triple(entityId, entityType, operation)
        }

        override suspend fun getPendingUploads(entityType: EntityType): List<PendingUpload> = emptyList()

        override suspend fun markAsSynced(
            entityId: String,
            entityType: EntityType,
            syncedAt: Instant,
            version: Long,
        ) {}

        override suspend fun getLastSyncTime(entityType: EntityType): Instant? = null

        override suspend fun updateLastSyncTime(
            entityType: EntityType,
            syncedAt: Instant,
        ) {}

        override suspend fun resetSyncStatus(
            entityId: String,
            entityType: EntityType,
        ) {}

        override suspend fun getPendingCount(): Int = enqueued.size

        override fun observePendingCount(): Flow<Int> = emptyFlow()

        override fun observePendingUploads(): Flow<List<QueuedUpload>> = emptyFlow()

        override suspend fun incrementRetryCount(
            entityId: String,
            entityType: EntityType,
        ) {}

        override suspend fun clearPending() {}

        override suspend fun resetAllCursors() {}
    }
}
