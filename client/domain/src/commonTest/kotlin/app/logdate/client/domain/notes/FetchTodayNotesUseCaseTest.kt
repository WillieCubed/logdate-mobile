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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class FetchTodayNotesUseCaseTest {

    private lateinit var mockRepository: MockJournalNotesRepository
    private lateinit var useCase: FetchTodayNotesUseCase

    @BeforeTest
    fun setUp() {
        mockRepository = MockJournalNotesRepository()
        useCase = FetchTodayNotesUseCase(repository = mockRepository)
    }

    @Test
    fun `invoke should return notes for today with default buffer`() = runTest {
        // Given
        val testNotes = listOf(
            createTestNote("Morning note"),
            createTestNote("Afternoon note")
        )
        mockRepository.notesForRange = testNotes
        
        // When
        val result = useCase().first()
        
        // Then
        assertEquals(testNotes, result)
        assertEquals(1, mockRepository.observeCallCount)
    }

    @Test
    fun `invoke should return empty list when no notes exist for today`() = runTest {
        // Given
        mockRepository.notesForRange = emptyList()
        
        // When
        val result = useCase().first()
        
        // Then
        assertTrue(result.isEmpty())
        assertEquals(1, mockRepository.observeCallCount)
    }

    @Test
    fun `invoke should use default 4-hour buffer`() = runTest {
        // Given
        mockRepository.notesForRange = emptyList()
        
        // When
        useCase().first()
        
        // Then
        assertEquals(1, mockRepository.observeCallCount)
        // Verify the buffer is applied (start time should be 4 hours before start of day)
        val timeDifference = mockRepository.lastEndTime!! - mockRepository.lastStartTime!!
        assertEquals(28 * 60 * 60 * 1000, timeDifference.inWholeMilliseconds) // 28 hours (24 + 4 buffer)
    }

    @Test
    fun `invoke should use custom buffer when provided`() = runTest {
        // Given
        val customBuffer = 2.hours
        mockRepository.notesForRange = emptyList()
        
        // When
        useCase(buffer = customBuffer).first()
        
        // Then
        assertEquals(1, mockRepository.observeCallCount)
        // Verify the custom buffer is applied
        val timeDifference = mockRepository.lastEndTime!! - mockRepository.lastStartTime!!
        assertEquals(26 * 60 * 60 * 1000, timeDifference.inWholeMilliseconds) // 26 hours (24 + 2 buffer)
    }

    @Test
    fun `invoke should handle zero buffer`() = runTest {
        // Given
        val noBuffer = 0.hours
        mockRepository.notesForRange = emptyList()
        
        // When
        useCase(buffer = noBuffer).first()
        
        // Then
        assertEquals(1, mockRepository.observeCallCount)
        // Verify no buffer is applied
        val timeDifference = mockRepository.lastEndTime!! - mockRepository.lastStartTime!!
        assertEquals(24 * 60 * 60 * 1000, timeDifference.inWholeMilliseconds) // Exactly 24 hours
    }

    @Test
    fun `invoke should return large list of notes correctly`() = runTest {
        // Given
        val manyNotes = (1..100).map { 
            createTestNote("Note number $it")
        }
        mockRepository.notesForRange = manyNotes
        
        // When
        val result = useCase().first()
        
        // Then
        assertEquals(100, result.size)
        assertEquals(manyNotes, result)
    }

    @Test
    fun `invoke should include notes from previous day with buffer`() = runTest {
        // Given
        val notesIncludingPreviousDay = listOf(
            createTestNote("Late night note from yesterday"),
            createTestNote("Early morning note from today"),
            createTestNote("Regular note from today")
        )
        mockRepository.notesForRange = notesIncludingPreviousDay
        
        // When
        val result = useCase(buffer = 6.hours).first()
        
        // Then
        assertEquals(notesIncludingPreviousDay, result)
        // Verify 6-hour buffer is applied
        val timeDifference = mockRepository.lastEndTime!! - mockRepository.lastStartTime!!
        assertEquals(30 * 60 * 60 * 1000, timeDifference.inWholeMilliseconds) // 30 hours (24 + 6 buffer)
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