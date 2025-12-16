package app.logdate.client.data.notes.drafts

import app.logdate.client.data.fakes.FakeLocalEntryDraftStore
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
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
 * Unit tests for [OfflineFirstEntryDraftRepository].
 * 
 * These tests validate the repository's implementation of [EntryDraftRepository],
 * focusing on operations with entry drafts including creation, updating, and deletion.
 * The tests use fake implementations of dependencies to isolate the repository's behavior.
 *
 * Test cases cover:
 * - Retrieving drafts (empty state, after creation)
 * - Creating new drafts
 * - Updating existing drafts
 * - Retrieving specific drafts by ID
 * - Deleting drafts
 * - Error handling for operations on non-existent drafts
 */

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstEntryDraftRepositoryTest {
    
    private lateinit var draftStore: FakeLocalEntryDraftStore
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var repository: OfflineFirstEntryDraftRepository

    @BeforeTest
    fun setup() {
        draftStore = FakeLocalEntryDraftStore()
        testDispatcher = UnconfinedTestDispatcher()
        
        repository = OfflineFirstEntryDraftRepository(
            draftStore = draftStore,
            coroutineScope = CoroutineScope(testDispatcher)
        )
    }

    @AfterTest
    fun tearDown() {
        draftStore.clear()
    }

    /**
     * Verifies that the repository initially emits an empty list of drafts when no drafts exist.
     * 
     * Expected behavior: The flow should emit an empty list when first collected.
     */
    @Test
    fun getDrafts_emitsEmptyListInitially() = runTest(testDispatcher) {
        val drafts = repository.getDrafts().first()
        assertTrue(drafts.isEmpty())
    }

    /**
     * Tests that the repository correctly emits drafts after they are created.
     * 
     * Expected behavior: After creating a draft, the repository's flow should emit
     * a list containing the created draft with the same notes that were provided.
     */
    @Test
    fun getDrafts_emitsDraftsAfterCreation() = runTest(testDispatcher) {
        val notes = listOf(createTestTextNote())
        
        repository.createDraft(notes)
        
        val drafts = repository.getDrafts().first()
        assertEquals(1, drafts.size)
        assertEquals(notes, drafts.first().notes)
    }

    /**
     * Tests retrieving a specific draft by ID when it exists.
     * 
     * Expected behavior: When a draft with the given ID exists, getDraft should
     * return a success Result containing the correct draft with matching ID and notes.
     */
    @Test
    fun getDraft_returnsSuccessForExistingDraft() = runTest(testDispatcher) {
        val notes = listOf(createTestTextNote())
        val draftId = repository.createDraft(notes)
        
        val result = repository.getDraft(draftId).first()
        assertTrue(result.isSuccess)
        assertEquals(draftId, result.getOrNull()?.id)
    }

    /**
     * Tests error handling when retrieving a non-existent draft by ID.
     * 
     * Expected behavior: When no draft exists with the given ID, getDraft should
     * return a failure Result containing a NoSuchElementException.
     */
    @Test
    fun getDraft_returnsFailureForNonExistentDraft() = runTest(testDispatcher) {
        val nonExistentId = Uuid.random()
        
        val result = repository.getDraft(nonExistentId).first()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }

    /**
     * Tests creating a new draft with multiple notes.
     * 
     * Expected behavior: 
     * 1. The createDraft method should return a valid UUID
     * 2. The draft should be stored and be retrievable from the repository
     * 3. The draft should contain all the notes that were provided
     */
    @Test
    fun createDraft_returnsValidUuid() = runTest(testDispatcher) {
        val notes = listOf(createTestTextNote(), createTestImageNote())
        
        val draftId = repository.createDraft(notes)
        
        assertNotNull(draftId)
        val drafts = repository.getDrafts().first()
        assertEquals(1, drafts.size)
        assertEquals(draftId, drafts.first().id)
        assertEquals(2, drafts.first().notes.size)
    }

    /**
     * Tests that created drafts are persisted to the underlying store.
     * 
     * Expected behavior: When a draft is created, it should be saved in the draft store
     * and be directly retrievable with matching notes and ID.
     */
    @Test
    fun createDraft_persistsToStore() = runTest(testDispatcher) {
        val notes = listOf(createTestTextNote())
        
        val draftId = repository.createDraft(notes)
        
        val storedDraft = draftStore.getDraft(draftId)
        assertNotNull(storedDraft)
        assertEquals(notes, storedDraft.notes)
    }

    /**
     * Tests updating an existing draft with new notes.
     * 
     * Expected behavior: 
     * 1. The updateDraft method should return the same UUID as the original draft
     * 2. The draft should be updated with the new notes
     * 3. The updated draft's updatedAt timestamp should be newer than its createdAt timestamp
     */
    @Test
    fun updateDraft_modifiesExistingDraft() = runTest(testDispatcher) {
        val initialNotes = listOf(createTestTextNote())
        val draftId = repository.createDraft(initialNotes)
        
        val updatedNotes = listOf(createTestTextNote(), createTestImageNote())
        val returnedId = repository.updateDraft(draftId, updatedNotes)
        
        assertEquals(draftId, returnedId)
        
        val result = repository.getDraft(draftId).first()
        assertTrue(result.isSuccess)
        val updatedDraft = result.getOrNull()!!
        assertEquals(2, updatedDraft.notes.size)
        assertTrue(updatedDraft.updatedAt > updatedDraft.createdAt)
    }

    /**
     * Tests error handling when updating a non-existent draft.
     * 
     * Expected behavior: When attempting to update a draft that doesn't exist,
     * the repository should throw an IllegalArgumentException.
     */
    @Test
    fun updateDraft_throwsForNonExistentDraft() = runTest(testDispatcher) {
        val nonExistentId = Uuid.random()
        val notes = listOf(createTestTextNote())
        
        assertFailsWith<IllegalArgumentException> {
            repository.updateDraft(nonExistentId, notes)
        }
    }

    /**
     * Tests that updates to drafts are persisted to the underlying store.
     * 
     * Expected behavior: When a draft is updated, the changes should be saved to the draft store
     * and be directly retrievable with the updated notes.
     */
    @Test
    fun updateDraft_persistsChangesToStore() = runTest(testDispatcher) {
        val initialNotes = listOf(createTestTextNote())
        val draftId = repository.createDraft(initialNotes)
        
        val updatedNotes = listOf(createTestImageNote())
        repository.updateDraft(draftId, updatedNotes)
        
        val storedDraft = draftStore.getDraft(draftId)
        assertNotNull(storedDraft)
        assertEquals(1, storedDraft.notes.size)
        assertTrue(storedDraft.notes.first() is JournalNote.Image)
    }

    @Test
    fun deleteDraft_removesFromRepository() = runTest(testDispatcher) {
        val notes = listOf(createTestTextNote())
        val draftId = repository.createDraft(notes)
        
        repository.deleteDraft(draftId)
        
        val drafts = repository.getDrafts().first()
        assertTrue(drafts.isEmpty())
        
        val result = repository.getDraft(draftId).first()
        assertTrue(result.isFailure)
    }

    @Test
    fun deleteDraft_removesFromStore() = runTest(testDispatcher) {
        val notes = listOf(createTestTextNote())
        val draftId = repository.createDraft(notes)
        
        repository.deleteDraft(draftId)
        
        val storedDraft = draftStore.getDraft(draftId)
        assertNull(storedDraft)
    }

    @Test
    fun deleteDraft_handlesNonExistentDraft() = runTest(testDispatcher) {
        val nonExistentId = Uuid.random()
        
        // Should not throw an exception
        repository.deleteDraft(nonExistentId)
        
        val drafts = repository.getDrafts().first()
        assertTrue(drafts.isEmpty())
    }

    @Test
    fun initialization_loadsExistingDrafts() = runTest(testDispatcher) {
        // Pre-populate the store
        val existingDraft = EntryDraft(
            id = Uuid.random(),
            notes = listOf(createTestTextNote()),
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        draftStore.saveDraft(existingDraft)
        
        // Create a new repository instance to test initialization
        val newRepository = OfflineFirstEntryDraftRepository(
            draftStore = draftStore,
            coroutineScope = CoroutineScope(testDispatcher)
        )
        
        val drafts = newRepository.getDrafts().first()
        assertEquals(1, drafts.size)
        assertEquals(existingDraft.id, drafts.first().id)
    }

    @Test
    fun createDraft_withEmptyNotes_works() = runTest(testDispatcher) {
        val emptyNotes = emptyList<JournalNote>()
        
        val draftId = repository.createDraft(emptyNotes)
        
        assertNotNull(draftId)
        val drafts = repository.getDrafts().first()
        assertEquals(1, drafts.size)
        assertTrue(drafts.first().notes.isEmpty())
    }

    @Test
    fun multipleOperations_maintainConsistency() = runTest(testDispatcher) {
        // Create multiple drafts
        val draft1Id = repository.createDraft(listOf(createTestTextNote()))
        val draft2Id = repository.createDraft(listOf(createTestImageNote()))
        val draft3Id = repository.createDraft(listOf(createTestTextNote(), createTestImageNote()))
        
        // Verify all drafts exist
        val allDrafts = repository.getDrafts().first()
        assertEquals(3, allDrafts.size)
        
        // Update one draft
        repository.updateDraft(draft2Id, listOf(createTestTextNote()))
        
        // Delete one draft
        repository.deleteDraft(draft1Id)
        
        // Verify final state
        val finalDrafts = repository.getDrafts().first()
        assertEquals(2, finalDrafts.size)
        
        val remainingIds = finalDrafts.map { it.id }.toSet()
        assertTrue(draft2Id in remainingIds)
        assertTrue(draft3Id in remainingIds)
        assertTrue(draft1Id !in remainingIds)
    }

    /**
     * Creates a test text note with random UUID and test content.
     * Used as a helper method for tests that need text note instances.
     */
    private fun createTestTextNote() = JournalNote.Text(
        uid = Uuid.random(),
        content = "Test text content",
        creationTimestamp = Clock.System.now(),
        lastUpdated = Clock.System.now()
    )

    /**
     * Creates a test image note with random UUID and test media reference.
     * Used as a helper method for tests that need image note instances.
     */
    private fun createTestImageNote() = JournalNote.Image(
        uid = Uuid.random(),
        mediaRef = "test-image.jpg",
        creationTimestamp = Clock.System.now(),
        lastUpdated = Clock.System.now()
    )
}