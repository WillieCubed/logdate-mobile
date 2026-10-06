package app.logdate.client.data.journals

import app.logdate.client.data.fakes.FakeJournalContentDao
import app.logdate.client.data.fakes.FakeJournalRepository
import app.logdate.client.data.fakes.FakeSyncMetadataService
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstJournalContentRepositoryTest {
    private lateinit var journalContentDao: FakeJournalContentDao
    private lateinit var syncMetadataService: FakeSyncMetadataService
    private lateinit var dispatcher: TestDispatcher
    private lateinit var repository: OfflineFirstJournalContentRepository

    @BeforeTest
    fun setUp() {
        journalContentDao = FakeJournalContentDao()
        syncMetadataService = FakeSyncMetadataService()
        dispatcher = UnconfinedTestDispatcher()
        repository =
            OfflineFirstJournalContentRepository(
                journalContentDao = journalContentDao,
                journalRepository = FakeJournalRepository(),
                journalNotesRepository = EmptyJournalNotesRepository,
                syncMetadataService = syncMetadataService,
                dispatcher = dispatcher,
            )
    }

    @Test
    fun `add contents deduplicates requests, preserves existing links, and queues only new associations`() =
        runTest(dispatcher) {
            val journalId = Uuid.random()
            val existingId = Uuid.random()
            val newId = Uuid.random()
            journalContentDao.addContentToJournal(
                app.logdate.client.database.entities.journals
                    .JournalContentEntityLink(journalId, existingId),
            )

            val firstAdded = repository.addContentsToJournal(listOf(existingId, newId, newId), journalId)
            val secondAdded = repository.addContentsToJournal(listOf(existingId, newId), journalId)

            assertEquals(1, firstAdded)
            assertEquals(0, secondAdded)
            assertEquals(
                setOf(existingId, newId),
                journalContentDao.getAllLinks().map { it.contentId }.toSet(),
            )
            assertEquals(
                listOf(
                    AssociationPendingKey(journalId, newId).toPendingId() to PendingOperation.CREATE,
                ),
                syncMetadataService
                    .getPendingUploads(EntityType.ASSOCIATION)
                    .map { it.entityId to it.operation },
            )
        }

    @Test
    fun `add contents rolls back all links and pending associations when queuing fails`() =
        runTest(dispatcher) {
            val journalId = Uuid.random()
            val firstContentId = Uuid.random()
            val secondContentId = Uuid.random()
            val failingSyncMetadata = FailingSyncMetadataService(failAfterSuccessfulEnqueues = 1)
            val rollbackRepository =
                OfflineFirstJournalContentRepository(
                    journalContentDao = journalContentDao,
                    journalRepository = FakeJournalRepository(),
                    journalNotesRepository = EmptyJournalNotesRepository,
                    syncMetadataService = failingSyncMetadata,
                    transactionManager = RollbackTransactionManager(journalContentDao, failingSyncMetadata),
                    dispatcher = dispatcher,
                )

            assertFailsWith<IllegalStateException> {
                rollbackRepository.addContentsToJournal(listOf(firstContentId, secondContentId), journalId)
            }

            assertEquals(emptyList(), journalContentDao.getAllLinks())
            assertEquals(emptyList(), failingSyncMetadata.getPendingUploads(EntityType.ASSOCIATION))
        }

    private object EmptyJournalNotesRepository : JournalNotesRepository {
        override val allNotesObserved: Flow<List<JournalNote>> = emptyFlow()

        override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = emptyFlow()

        override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = emptyList()

        override fun observeNotesInRange(
            start: kotlin.time.Instant,
            end: kotlin.time.Instant,
        ): Flow<List<JournalNote>> = emptyFlow()

        override fun observeNotesPage(
            pageSize: Int,
            offset: Int,
        ): Flow<List<JournalNote>> = emptyFlow()

        override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = emptyFlow()

        override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = emptyFlow()

        override suspend fun getNoteById(noteId: Uuid): JournalNote? = null

        override suspend fun create(note: JournalNote): Uuid = note.uid

        override suspend fun remove(note: JournalNote) = Unit

        override suspend fun removeById(noteId: Uuid) = Unit

        override suspend fun create(
            note: JournalNote,
            journalId: Uuid,
        ) = Unit

        override suspend fun removeFromJournal(
            noteId: Uuid,
            journalId: Uuid,
        ) = Unit
    }

    private class FailingSyncMetadataService(
        private var failAfterSuccessfulEnqueues: Int,
    ) : FakeSyncMetadataService() {
        override suspend fun enqueuePending(
            entityId: String,
            entityType: EntityType,
            operation: PendingOperation,
        ) {
            check(failAfterSuccessfulEnqueues-- > 0) { "Pending metadata failed" }
            super.enqueuePending(entityId, entityType, operation)
        }
    }

    private class RollbackTransactionManager(
        private val journalContentDao: FakeJournalContentDao,
        private val syncMetadataService: FakeSyncMetadataService,
    ) : SyncTransactionManager {
        override suspend fun <T> withTransaction(block: suspend () -> T): T {
            val links = journalContentDao.snapshot()
            val pending = syncMetadataService.snapshot()
            return try {
                block()
            } catch (error: Throwable) {
                journalContentDao.restore(links)
                syncMetadataService.restore(pending)
                throw error
            }
        }
    }
}
