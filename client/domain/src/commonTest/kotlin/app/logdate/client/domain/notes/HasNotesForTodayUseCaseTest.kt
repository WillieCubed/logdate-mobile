package app.logdate.client.domain.notes

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class HasNotesForTodayUseCaseTest {

    private lateinit var mockRepository: MockJournalNotesRepository
    private lateinit var useCase: HasNotesForTodayUseCase

    @BeforeTest
    fun setUp() {
        mockRepository = MockJournalNotesRepository()
        useCase = HasNotesForTodayUseCase(repository = mockRepository)
    }

    @Test
    fun `invoke should return true when notes exist for today`() = runTest {
        // Given
        val testNotes = listOf(
            createTestNote(),
            createTestNote(content = "Another note")
        )
        mockRepository.notesForRange = testNotes
        
        // When
        val result = useCase().first()
        
        // Then
        assertTrue(result)
        assertEquals(1, mockRepository.observeCallCount)
    }

    @Test
    fun `invoke should return false when no notes exist for today`() = runTest {
        // Given
        mockRepository.notesForRange = emptyList()
        
        // When
        val result = useCase().first()
        
        // Then
        assertFalse(result)
        assertEquals(1, mockRepository.observeCallCount)
    }

    @Test
    fun `invoke should return true when single note exists for today`() = runTest {
        // Given
        val testNote = createTestNote()
        mockRepository.notesForRange = listOf(testNote)
        
        // When
        val result = useCase().first()
        
        // Then
        assertTrue(result)
        assertEquals(1, mockRepository.observeCallCount)
    }

    @Test
    fun `invoke should call repository with correct time range`() = runTest {
        // Given
        mockRepository.notesForRange = emptyList()
        
        // When
        useCase().first()
        
        // Then
        assertEquals(1, mockRepository.observeCallCount)
        // Verify the time range covers exactly 24 hours from start of day
        val capturedStart = mockRepository.lastStartTime!!
        val capturedEnd = mockRepository.lastEndTime!!
        val timeDifference = capturedEnd - capturedStart
        assertEquals(24 * 60 * 60 * 1000, timeDifference.inWholeMilliseconds) // 24 hours in milliseconds
    }

    private fun createTestNote(
        content: String = "Test note content",
        journalId: Uuid = Uuid.random()
    ) = JournalNote.TextNote(
        id = Uuid.random(),
        content = content,
        journalId = journalId,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )

    private class MockJournalNotesRepository : JournalNotesRepository {
        var notesForRange = emptyList<JournalNote>()
        var observeCallCount = 0
        var lastStartTime: Instant? = null
        var lastEndTime: Instant? = null

        override suspend fun observeNotesInRange(start: Instant, end: Instant): Flow<List<JournalNote>> {
            observeCallCount++
            lastStartTime = start
            lastEndTime = end
            return flowOf(notesForRange)
        }

        override suspend fun create(note: JournalNote) = Unit
        override suspend fun removeById(id: Uuid) = Unit
        override suspend fun getByJournalId(journalId: Uuid) = emptyList<JournalNote>()
        override suspend fun observeNotesByJournal(journalId: Uuid) = flowOf(emptyList<JournalNote>())
        override suspend fun getNotesForDay(date: LocalDate) = emptyList<JournalNote>()
        override suspend fun getAll() = emptyList<JournalNote>()
    }
}