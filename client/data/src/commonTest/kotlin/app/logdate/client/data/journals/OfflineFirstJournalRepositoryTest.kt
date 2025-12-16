package app.logdate.client.data.journals

import app.logdate.client.data.fakes.FakeDraftRepository
import app.logdate.client.data.fakes.FakeJournalDao
import app.logdate.client.data.fakes.FakeRemoteJournalDataSource
import app.logdate.client.database.entities.JournalEntity
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Unit tests for [OfflineFirstJournalRepository].
 * 
 * These tests validate the repository's implementation of the [JournalRepository] interface,
 * focusing on CRUD operations for journals and draft management functionality. The tests use
 * fake implementations of dependencies to isolate the repository's behavior.
 *
 * Test cases cover:
 * - Observing journals (empty state, after creation)
 * - Retrieving journals by ID
 * - Creating and deleting journals
 * - Managing editor drafts (save, get, delete)
 * - Error handling when trying to perform draft operations without a draft repository
 */

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstJournalRepositoryTest {
    
    private lateinit var journalDao: FakeJournalDao
    private lateinit var remoteDataSource: FakeRemoteJournalDataSource
    private lateinit var draftRepository: FakeDraftRepository
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var repository: OfflineFirstJournalRepository

    @BeforeTest
    fun setup() {
        journalDao = FakeJournalDao()
        remoteDataSource = FakeRemoteJournalDataSource()
        draftRepository = FakeDraftRepository()
        testDispatcher = UnconfinedTestDispatcher()
        
        repository = OfflineFirstJournalRepository(
            journalDao = journalDao,
            remoteDataSource = remoteDataSource,
            draftRepository = draftRepository,
            dispatcher = testDispatcher,
            externalScope = CoroutineScope(testDispatcher)
        )
    }

    @AfterTest
    fun tearDown() {
        journalDao.clear()
        remoteDataSource.clear()
        draftRepository.clear()
    }

    /**
     * Verifies that the repository initially emits an empty list of journals when no journals exist.
     * 
     * Expected behavior: The flow should emit an empty list when first collected.
     */
    @Test
    fun allJournalsObserved_emitsEmptyListInitially() = runTest(testDispatcher) {
        val journals = repository.allJournalsObserved.first()
        assertTrue(journals.isEmpty())
    }

    /**
     * Tests that the repository correctly emits journals after they are created in the database.
     * 
     * Expected behavior: After creating a journal in the DAO, the repository's flow should emit
     * a list containing the created journal with all properties correctly mapped to the model.
     */
    @Test
    fun allJournalsObserved_emitsJournalsAfterCreation() = runTest(testDispatcher) {
        val journal = createTestJournal()
        journalDao.create(journal.toEntity())
        
        val journals = repository.allJournalsObserved.first()
        assertEquals(1, journals.size)
        assertEquals(journal.title, journals.first().title)
    }

    /**
     * Tests that the repository can observe a specific journal by its ID.
     * 
     * Expected behavior: When observing a journal by ID, the flow should emit the correct journal
     * with all properties matching the original journal in the database.
     */
    @Test
    fun observeJournalById_emitsCorrectJournal() = runTest(testDispatcher) {
        val journal = createTestJournal()
        journalDao.create(journal.toEntity())
        
        val observedJournal = repository.observeJournalById(journal.id).first()
        assertEquals(journal.title, observedJournal.title)
        assertEquals(journal.id, observedJournal.id)
    }

    /**
     * Verifies that attempting to observe a non-existent journal by ID throws an exception.
     * 
     * Expected behavior: The repository should throw a [NoSuchElementException] when trying to
     * observe a journal with an ID that doesn't exist in the database.
     */
    @Test
    fun observeJournalById_throwsWhenJournalNotFound() = runTest(testDispatcher) {
        val nonExistentId = Uuid.random()
        
        assertFailsWith<NoSuchElementException> {
            repository.observeJournalById(nonExistentId).first()
        }
    }

    /**
     * Tests the repository's journal creation functionality.
     * 
     * Expected behavior: 
     * 1. The create method should return a valid UUID
     * 2. The journal should be stored in the database and be retrievable
     * 3. The journals observable flow should be updated with the new journal
     */
    @Test
    fun create_successfullyCreatesJournal() = runTest(testDispatcher) {
        val journal = createTestJournal()
        
        val createdId = repository.create(journal)
        // No need to advance with UnconfinedTestDispatcher
        
        assertNotNull(createdId)
        val journals = repository.allJournalsObserved.first()
        assertEquals(1, journals.size)
    }

    /**
     * Tests the repository's journal deletion functionality.
     * 
     * Expected behavior: 
     * 1. After deleting a journal, it should no longer be present in the database
     * 2. The journals observable flow should be updated to no longer include the deleted journal
     */
    @Test
    fun delete_successfullyDeletesJournal() = runTest(testDispatcher) {
        val journal = createTestJournal()
        journalDao.create(journal.toEntity())
        
        repository.delete(journal.id)
        // No need to advance with UnconfinedTestDispatcher
        
        val journals = repository.allJournalsObserved.first()
        assertTrue(journals.isEmpty())
    }

    /**
     * Tests that the repository correctly delegates draft saving to the draft repository.
     * 
     * Expected behavior: When saveDraft is called, the repository should pass the draft to the
     * draftRepository, which should store it correctly and make it retrievable by ID.
     */
    @Test
    fun saveDraft_delegatesToDraftRepository() = runTest(testDispatcher) {
        val draft = createTestDraft()
        
        repository.saveDraft(draft)
        
        val savedDraft = draftRepository.getDraft(draft.id)
        assertNotNull(savedDraft)
        assertEquals(draft.id, savedDraft.id)
    }

    /**
     * Tests error handling when trying to save a draft without a draft repository.
     * 
     * Expected behavior: When draftRepository is null and saveDraft is called, the repository
     * should throw an IllegalStateException to indicate that the operation cannot be performed.
     */
    @Test
    fun saveDraft_throwsWhenDraftRepositoryNull() = runTest(testDispatcher) {
        val repositoryWithoutDrafts = OfflineFirstJournalRepository(
            journalDao = journalDao,
            remoteDataSource = remoteDataSource,
            draftRepository = FakeDraftRepository(),
            dispatcher = testDispatcher,
            externalScope = CoroutineScope(testDispatcher)
        )
        
        val draft = createTestDraft()
        
        assertFailsWith<IllegalStateException> {
            repositoryWithoutDrafts.saveDraft(draft)
        }
    }

    /**
     * Tests that the repository correctly retrieves the latest modified draft.
     * 
     * Expected behavior: When multiple drafts exist, getLatestDraft should return the draft with
     * the most recent lastModifiedAt timestamp.
     */
    @Test
    fun getLatestDraft_returnsCorrectDraft() = runTest(testDispatcher) {
        val draft1 = createTestDraft()
        val draft2 = createTestDraft().copy(
            lastModifiedAt = Clock.System.now()
        )
        
        draftRepository.saveDraft(draft1)
        draftRepository.saveDraft(draft2)
        
        val latestDraft = repository.getLatestDraft()
        assertEquals(draft2.id, latestDraft?.id)
    }

    /**
     * Tests that getLatestDraft returns null when no drafts exist.
     * 
     * Expected behavior: When no drafts have been saved, getLatestDraft should return null.
     */
    @Test
    fun getLatestDraft_returnsNullWhenNoDrafts() = runTest(testDispatcher) {
        val latestDraft = repository.getLatestDraft()
        assertNull(latestDraft)
    }

    /**
     * Tests that the repository correctly retrieves all saved drafts.
     * 
     * Expected behavior: getAllDrafts should return all drafts that have been saved,
     * matching the count and content of drafts in the draft repository.
     */
    @Test
    fun getAllDrafts_returnsAllDrafts() = runTest(testDispatcher) {
        val draft1 = createTestDraft()
        val draft2 = createTestDraft()
        
        draftRepository.saveDraft(draft1)
        draftRepository.saveDraft(draft2)
        
        val allDrafts = repository.getAllDrafts()
        assertEquals(2, allDrafts.size)
    }

    /**
     * Tests that the repository correctly retrieves a specific draft by ID.
     * 
     * Expected behavior: When a draft exists with the given ID, getDraft should return
     * that draft with all properties matching the original draft.
     */
    @Test
    fun getDraft_returnsCorrectDraft() = runTest(testDispatcher) {
        val draft = createTestDraft()
        draftRepository.saveDraft(draft)
        
        val retrievedDraft = repository.getDraft(draft.id)
        assertNotNull(retrievedDraft)
        assertEquals(draft.id, retrievedDraft.id)
    }

    /**
     * Tests that getDraft returns null when no draft exists with the given ID.
     * 
     * Expected behavior: When no draft exists with the requested ID, getDraft should
     * return null rather than throwing an exception.
     */
    @Test
    fun getDraft_returnsNullWhenNotFound() = runTest(testDispatcher) {
        val nonExistentId = Uuid.random()
        
        val retrievedDraft = repository.getDraft(nonExistentId)
        assertNull(retrievedDraft)
    }

    /**
     * Tests that the repository correctly deletes a draft by ID.
     * 
     * Expected behavior: After deleting a draft, it should no longer be retrievable by ID
     * from the draft repository.
     */
    @Test
    fun deleteDraft_successfullyDeletesDraft() = runTest(testDispatcher) {
        val draft = createTestDraft()
        draftRepository.saveDraft(draft)
        
        repository.deleteDraft(draft.id)
        
        val retrievedDraft = draftRepository.getDraft(draft.id)
        assertNull(retrievedDraft)
    }

    /**
     * Tests error handling when trying to delete a draft without a draft repository.
     * 
     * Expected behavior: When draftRepository is null and deleteDraft is called, the repository
     * should throw an IllegalStateException to indicate that the operation cannot be performed.
     */
    @Test
    fun deleteDraft_throwsWhenDraftRepositoryNull() = runTest(testDispatcher) {
        val repositoryWithoutDrafts = OfflineFirstJournalRepository(
            journalDao = journalDao,
            remoteDataSource = remoteDataSource,
            draftRepository = FakeDraftRepository(),
            dispatcher = testDispatcher,
            externalScope = CoroutineScope(testDispatcher)
        )
        
        assertFailsWith<IllegalStateException> {
            repositoryWithoutDrafts.deleteDraft(Uuid.random())
        }
    }

    /**
     * Creates a test journal with random UUID and test data.
     * Used as a helper method for tests that need journal instances.
     */
    private fun createTestJournal() = Journal(
        id = Uuid.random(),
        title = "Test Journal",
        description = "Test Description",
        created = Clock.System.now(),
        lastUpdated = Clock.System.now()
    )

    /**
     * Creates a test draft with random UUID and empty content.
     * Used as a helper method for tests that need draft instances.
     */
    private fun createTestDraft() = EditorDraft(
        id = Uuid.random(),
        blocks = emptyList(),
        selectedJournalIds = emptyList(),
        createdAt = Clock.System.now(),
        lastModifiedAt = Clock.System.now()
    )
}