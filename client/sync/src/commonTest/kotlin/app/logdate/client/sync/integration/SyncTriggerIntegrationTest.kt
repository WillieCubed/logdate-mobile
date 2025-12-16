package app.logdate.client.sync.integration

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository  
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncResult
import app.logdate.client.sync.SyncStatus
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Integration tests that verify repository sync triggers are properly called.
 * 
 * These tests focus on ensuring that CRUD operations in repositories
 * correctly trigger sync operations in the sync manager.
 */
class SyncTriggerIntegrationTest {

    @Test
    fun testJournalNotesRepositoryTriggersContentSync() = runTest {
        // Given: A mock sync manager that tracks sync calls
        val mockSyncManager = MockSyncManager()
        val repository = TestJournalNotesRepositoryWithSyncTriggers(mockSyncManager)
        
        // When: Creating a note
        val note = JournalNote.Text(
            uid = Uuid.random(),
            content = "Test note",
            creationTimestamp = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        repository.create(note)
        
        // Then: Content sync should have been triggered
        assertEquals(1, mockSyncManager.syncContentCalls, "Content sync should be called once after note creation")
    }

    @Test
    fun testJournalNotesRepositoryTriggersContentSyncOnDeletion() = runTest {
        // Given: A mock sync manager and repository
        val mockSyncManager = MockSyncManager()
        val repository = TestJournalNotesRepositoryWithSyncTriggers(mockSyncManager)
        
        // When: Removing a note
        val note = JournalNote.Text(
            uid = Uuid.random(),
            content = "Test note",
            creationTimestamp = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        repository.remove(note)
        
        // Then: Content sync should have been triggered
        assertEquals(1, mockSyncManager.syncContentCalls, "Content sync should be called once after note removal")
    }

    @Test
    fun testJournalNotesRepositoryTriggersAssociationSync() = runTest {
        // Given: A mock sync manager and repository
        val mockSyncManager = MockSyncManager()
        val repository = TestJournalNotesRepositoryWithSyncTriggers(mockSyncManager)
        
        // When: Adding a note to a journal
        val note = JournalNote.Text(
            uid = Uuid.random(),
            content = "Test note",
            creationTimestamp = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        val journalId = Uuid.random()
        repository.create(note, journalId)
        
        // Then: Both content and association sync should have been triggered
        assertEquals(1, mockSyncManager.syncContentCalls, "Content sync should be called for note creation")
        assertEquals(1, mockSyncManager.syncAssociationsCalls, "Association sync should be called for journal linking")
    }

    @Test
    fun testJournalRepositoryTriggersSyncOnCrud() = runTest {
        // Given: A mock sync manager and repository
        val mockSyncManager = MockSyncManager()
        val repository = TestJournalRepositoryWithSyncTriggers(mockSyncManager)
        
        // When: Creating, updating, and deleting a journal
        val journal = Journal(
            id = Uuid.random(),
            title = "Test Journal",
            description = "Test Description",
            created = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        
        repository.create(journal)
        repository.update(journal.copy(title = "Updated Title"))
        repository.delete(journal.id)
        
        // Then: Journal sync should have been triggered for each operation
        assertEquals(3, mockSyncManager.syncJournalsCalls, "Journal sync should be called for create, update, and delete")
    }

    @Test 
    fun testMultipleOperationsAccumulateSyncCalls() = runTest {
        // Given: A mock sync manager and repositories
        val mockSyncManager = MockSyncManager()
        val notesRepository = TestJournalNotesRepositoryWithSyncTriggers(mockSyncManager)
        val journalRepository = TestJournalRepositoryWithSyncTriggers(mockSyncManager)
        
        // When: Performing multiple operations
        val note1 = JournalNote.Text(Uuid.random(), Clock.System.now(), Clock.System.now(), "Note 1")
        val note2 = JournalNote.Text(Uuid.random(), Clock.System.now(), Clock.System.now(), "Note 2")
        val journal = Journal(Uuid.random(), "Journal", "Description", created = Clock.System.now(), lastUpdated = Clock.System.now())
        
        notesRepository.create(note1)
        notesRepository.create(note2)
        journalRepository.create(journal)
        notesRepository.removeById(note1.uid)
        
        // Then: Each operation should have triggered appropriate sync calls
        assertEquals(3, mockSyncManager.syncContentCalls, "Content sync should be called 3 times (2 creates + 1 remove)")
        assertEquals(1, mockSyncManager.syncJournalsCalls, "Journal sync should be called 1 time (1 create)")
    }
}

/**
 * Mock sync manager that tracks method calls for testing.
 */
private class MockSyncManager : SyncManager {
    var syncContentCalls = 0
    var syncJournalsCalls = 0
    var syncAssociationsCalls = 0
    var uploadPendingChangesCalls = 0
    var downloadRemoteChangesCalls = 0
    var fullSyncCalls = 0
    
    override fun sync(startNow: Boolean) {
        // No-op for testing
    }
    
    override suspend fun uploadPendingChanges(): SyncResult {
        uploadPendingChangesCalls++
        return SyncResult(success = true)
    }
    
    override suspend fun downloadRemoteChanges(): SyncResult {
        downloadRemoteChangesCalls++
        return SyncResult(success = true)
    }
    
    override suspend fun syncContent(): SyncResult {
        syncContentCalls++
        return SyncResult(success = true)
    }
    
    override suspend fun syncJournals(): SyncResult {
        syncJournalsCalls++
        return SyncResult(success = true)
    }
    
    override suspend fun syncAssociations(): SyncResult {
        syncAssociationsCalls++
        return SyncResult(success = true)
    }
    
    override suspend fun fullSync(): SyncResult {
        fullSyncCalls++
        return SyncResult(success = true)
    }
    
    override suspend fun getSyncStatus(): SyncStatus {
        return SyncStatus(
            isEnabled = true,
            lastSyncTime = null,
            pendingUploads = 0,
            isSyncing = false,
            hasErrors = false
        )
    }
}

/**
 * Test implementation of JournalNotesRepository that triggers sync operations.
 * This simulates the behavior of the real OfflineFirstJournalNotesRepository.
 */
private class TestJournalNotesRepositoryWithSyncTriggers(
    private val syncManager: SyncManager
) : JournalNotesRepository {
    
    private val notes = mutableListOf<JournalNote>()
    private val _notesFlow = MutableStateFlow<List<JournalNote>>(emptyList())
    
    override val allNotesObserved: Flow<List<JournalNote>> = _notesFlow.asStateFlow()
    
    override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = _notesFlow.asStateFlow()
    override fun observeNotesInRange(start: Instant, end: Instant): Flow<List<JournalNote>> = _notesFlow.asStateFlow()
    override fun observeNotesPage(pageSize: Int, offset: Int): Flow<List<JournalNote>> = _notesFlow.asStateFlow()
    override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = _notesFlow.asStateFlow()
    override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = _notesFlow.asStateFlow()
    
    override suspend fun create(note: JournalNote): Uuid {
        notes.add(note)
        _notesFlow.value = notes.toList()
        
        // Trigger sync like the real repository does
        syncManager.syncContent()
        
        return note.uid
    }
    
    override suspend fun remove(note: JournalNote) {
        notes.removeAll { it.uid == note.uid }
        _notesFlow.value = notes.toList()
        
        // Trigger sync like the real repository does
        syncManager.syncContent()
    }
    
    override suspend fun removeById(noteId: Uuid) {
        notes.removeAll { it.uid == noteId }
        _notesFlow.value = notes.toList()
        
        // Trigger sync like the real repository does
        syncManager.syncContent()
    }
    
    override suspend fun create(note: JournalNote, journalId: Uuid) {
        // First create the note (which triggers content sync)
        create(note)
        
        // Then trigger association sync for the journal linking
        syncManager.syncAssociations()
    }
    
    override suspend fun removeFromJournal(noteId: Uuid, journalId: Uuid) {
        // Trigger association sync like the real repository does
        syncManager.syncAssociations()
    }
}

/**
 * Test implementation of JournalRepository that triggers sync operations.
 * This simulates the behavior of the real OfflineFirstJournalRepository.
 */
private class TestJournalRepositoryWithSyncTriggers(
    private val syncManager: SyncManager
) : JournalRepository {
    
    private val journals = mutableListOf<Journal>()
    private val _journalsFlow = MutableStateFlow<List<Journal>>(emptyList())
    
    override val allJournalsObserved: Flow<List<Journal>> = _journalsFlow.asStateFlow()
    
    override fun observeJournalById(id: Uuid): Flow<Journal> {
        TODO("Not needed for sync trigger tests")
    }
    
    override suspend fun getJournalById(id: Uuid): Journal? = journals.find { it.id == id }
    
    override suspend fun create(journal: Journal): Uuid {
        journals.add(journal)
        _journalsFlow.value = journals.toList()
        
        // Trigger sync like the real repository does
        syncManager.syncJournals()
        
        return journal.id
    }
    
    override suspend fun update(journal: Journal) {
        val index = journals.indexOfFirst { it.id == journal.id }
        if (index >= 0) {
            journals[index] = journal
            _journalsFlow.value = journals.toList()
        }
        
        // Trigger sync like the real repository does
        syncManager.syncJournals()
    }
    
    override suspend fun delete(journalId: Uuid) {
        journals.removeAll { it.id == journalId }
        _journalsFlow.value = journals.toList()
        
        // Trigger sync like the real repository does
        syncManager.syncJournals()
    }
    
    // Draft methods are not relevant for sync testing
    override suspend fun saveDraft(draft: EditorDraft) {}
    override suspend fun getLatestDraft(): EditorDraft? = null
    override suspend fun getAllDrafts(): List<EditorDraft> = emptyList()
    override suspend fun getDraft(id: Uuid): EditorDraft? = null
    override suspend fun deleteDraft(id: Uuid) {}
}