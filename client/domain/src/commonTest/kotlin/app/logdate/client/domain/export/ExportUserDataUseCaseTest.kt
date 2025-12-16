package app.logdate.client.domain.export

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ExportUserDataUseCaseTest {

    private lateinit var mockJournalRepository: MockJournalRepository
    private lateinit var mockNotesRepository: MockJournalNotesRepository
    private lateinit var useCase: ExportUserDataUseCase

    @BeforeTest
    fun setUp() {
        mockJournalRepository = MockJournalRepository()
        mockNotesRepository = MockJournalNotesRepository()
        
        useCase = ExportUserDataUseCase(
            journalRepository = mockJournalRepository,
            journalNotesRepository = mockNotesRepository
        )
    }

    @Test
    fun `exportUserData emits expected progress updates`() = runTest {
        // When
        val progressUpdates = useCase.exportUserData().toList()
        
        // Then
        assertEquals(6, progressUpdates.size, "Should emit 6 progress updates")
        assertTrue(progressUpdates[0] is ExportProgress.Starting, "First emission should be Starting")
        
        assertTrue(progressUpdates[1] is ExportProgress.InProgress, "Second emission should be InProgress")
        assertEquals(0.1f, (progressUpdates[1] as ExportProgress.InProgress).progress)
        
        assertTrue(progressUpdates[2] is ExportProgress.InProgress, "Third emission should be InProgress")
        assertEquals(0.3f, (progressUpdates[2] as ExportProgress.InProgress).progress)
        
        assertTrue(progressUpdates[3] is ExportProgress.InProgress, "Fourth emission should be InProgress")
        assertEquals(0.5f, (progressUpdates[3] as ExportProgress.InProgress).progress)
        
        assertTrue(progressUpdates[4] is ExportProgress.InProgress, "Fifth emission should be InProgress")
        assertEquals(0.7f, (progressUpdates[4] as ExportProgress.InProgress).progress)
        
        assertTrue(progressUpdates[5] is ExportProgress.Completed, "Final emission should be Completed")
    }

    @Test
    fun `exportUserData produces valid JSON with test data`() = runTest {
        // Given
        val testJournalId = Uuid.random()
        val testJournal = Journal(
            id = testJournalId.toString(),
            name = "Test Journal",
            description = "Journal description",
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now(),
            color = 0xFF0000FF.toInt(),
            isArchived = false
        )
        
        val testNote = JournalNote.TextNote(
            id = Uuid.random().toString(),
            journalId = testJournalId.toString(),
            createdAt = Clock.System.now(),
            lastModifiedAt = Clock.System.now(),
            content = "Test note content",
            tags = emptyList(),
            isTrashed = false
        )
        
        val testDraft = EditorDraft(
            id = Uuid.random().toString(),
            journalId = testJournalId.toString(),
            createdAt = Clock.System.now(),
            lastModifiedAt = Clock.System.now(),
            content = "Draft content"
        )
        
        mockJournalRepository.testJournals = listOf(testJournal)
        mockJournalRepository.testDrafts = listOf(testDraft)
        mockNotesRepository.testNotes = listOf(testNote)
        
        // When
        val progressUpdates = useCase.exportUserData().toList()
        val jsonData = (progressUpdates.last() as ExportProgress.Completed).jsonData
        
        // Then - Verify JSON content includes our test data
        assertTrue(jsonData.contains("Test Journal"), "JSON should contain journal name")
        assertTrue(jsonData.contains("Test note content"), "JSON should contain note content")
        assertTrue(jsonData.contains("Draft content"), "JSON should contain draft content")
        
        // Verify JSON is valid
        val parser = Json { ignoreUnknownKeys = true }
        val parsed = parser.decodeFromString<Map<String, Any>>(jsonData)
        
        // Verify structure of the JSON
        assertTrue(parsed.containsKey("exportTimestamp"), "JSON should include export timestamp")
        assertTrue(parsed.containsKey("journals"), "JSON should include journals")
        assertTrue(parsed.containsKey("notes"), "JSON should include notes")
        assertTrue(parsed.containsKey("drafts"), "JSON should include drafts")
    }

    @Test
    fun `exportUserData handles empty data gracefully`() = runTest {
        // Given
        mockJournalRepository.testJournals = emptyList()
        mockJournalRepository.testDrafts = emptyList()
        mockNotesRepository.testNotes = emptyList()
        
        // When
        val progressUpdates = useCase.exportUserData().toList()
        val jsonData = (progressUpdates.last() as ExportProgress.Completed).jsonData
        
        // Then
        val parser = Json { ignoreUnknownKeys = true }
        val parsed = parser.decodeFromString<Map<String, Any>>(jsonData)
        
        // Verify structure with empty data
        assertTrue(parsed.containsKey("exportTimestamp"), "JSON should include export timestamp")
        assertTrue(parsed.containsKey("journals"), "JSON should include empty journals array")
        assertTrue(parsed.containsKey("notes"), "JSON should include empty notes array")
        assertTrue(parsed.containsKey("drafts"), "JSON should include empty drafts array")
    }

    @Test
    fun `exportUserData handles repository errors`() = runTest {
        // Given
        mockJournalRepository.shouldThrowException = true
        
        // When
        val progressUpdates = useCase.exportUserData().toList()
        
        // Then
        assertTrue(progressUpdates.last() is ExportProgress.Failed, "Final emission should be Failed")
        assertEquals("Test exception", (progressUpdates.last() as ExportProgress.Failed).errorMessage)
    }

    private class MockJournalRepository : JournalRepository {
        var testJournals = emptyList<Journal>()
        var testDrafts = emptyList<EditorDraft>()
        var shouldThrowException = false
        
        override val allJournalsObserved: Flow<List<Journal>> = flowOf(testJournals)
        
        override suspend fun getAllDrafts(): List<EditorDraft> {
            if (shouldThrowException) {
                throw RuntimeException("Test exception")
            }
            return testDrafts
        }
        
        // Stub implementations for other required methods
        override suspend fun getJournalById(id: String): Journal? = null
        override suspend fun createJournal(journal: Journal): String = ""
        override suspend fun updateJournal(journal: Journal): Boolean = true
        override suspend fun deleteJournal(journalId: String): Boolean = true
        override suspend fun archiveJournal(journalId: String): Boolean = true
        override suspend fun unarchiveJournal(journalId: String): Boolean = true
        override fun observeJournalById(id: String): Flow<Journal?> = flowOf(null)
    }
    
    private class MockJournalNotesRepository : JournalNotesRepository {
        var testNotes = emptyList<JournalNote>()
        
        override val allNotesObserved: Flow<List<JournalNote>> = flowOf(testNotes)
        
        // Stub implementations for other required methods
        override suspend fun create(note: JournalNote): Uuid = Uuid.random()
        override suspend fun removeById(noteId: Uuid) {}
        override suspend fun remove(note: JournalNote) {}
        override suspend fun create(note: JournalNote, journalId: Uuid): Uuid = Uuid.random()
        override suspend fun removeFromJournal(noteId: Uuid, journalId: Uuid) {}
        override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())
        override fun observeNotesInRange(start: Instant, end: Instant): Flow<List<JournalNote>> = flowOf(emptyList())
        override fun observeNotesPage(pageSize: Int, offset: Int): Flow<List<JournalNote>> = flowOf(emptyList())
        override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = flowOf(emptyList())
        override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = flowOf(emptyList())
    }
}