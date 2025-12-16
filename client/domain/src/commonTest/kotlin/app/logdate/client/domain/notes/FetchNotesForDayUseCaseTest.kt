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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class FetchNotesForDayUseCaseTest {

    private lateinit var mockRepository: MockJournalNotesRepository
    private lateinit var useCase: FetchNotesForDayUseCase

    @BeforeTest
    fun setUp() {
        mockRepository = MockJournalNotesRepository()
        useCase = FetchNotesForDayUseCase(repository = mockRepository)
    }

    @Test
    fun `invoke should return notes for specified day`() = runTest {
        // Given
        val testDate = LocalDate(2024, 1, 15)
        val testNotes = listOf(
            createTestNote("Morning note"),
            createTestNote("Evening note")
        )
        mockRepository.notesForRange = testNotes
        
        // When
        val result = useCase(testDate).first()
        
        // Then
        assertEquals(testNotes, result)
        assertEquals(1, mockRepository.observeCallCount)
    }

    @Test
    fun `invoke should return empty list when no notes exist for day`() = runTest {
        // Given
        val testDate = LocalDate(2024, 1, 15)
        mockRepository.notesForRange = emptyList()
        
        // When
        val result = useCase(testDate).first()
        
        // Then
        assertTrue(result.isEmpty())
        assertEquals(1, mockRepository.observeCallCount)
    }

    @Test
    fun `invoke should call repository with correct time range for given date`() = runTest {
        // Given
        val testDate = LocalDate(2024, 1, 15)
        mockRepository.notesForRange = emptyList()
        val expectedStart = testDate.atStartOfDayIn(TimeZone.currentSystemDefault())
        
        // When
        useCase(testDate).first()
        
        // Then
        assertEquals(1, mockRepository.observeCallCount)
        assertEquals(expectedStart, mockRepository.lastStartTime)
        
        // Verify the time range covers exactly 24 hours
        val timeDifference = mockRepository.lastEndTime!! - mockRepository.lastStartTime!!
        assertEquals(24 * 60 * 60 * 1000, timeDifference.inWholeMilliseconds) // 24 hours in milliseconds
    }

    @Test
    fun `invoke should handle different dates correctly`() = runTest {
        // Given
        val date1 = LocalDate(2024, 1, 1)
        val date2 = LocalDate(2024, 12, 31)
        
        val notes1 = listOf(createTestNote("New Year note"))
        val notes2 = listOf(createTestNote("Year end note"))
        
        // When
        mockRepository.notesForRange = notes1
        val result1 = useCase(date1).first()
        
        mockRepository.notesForRange = notes2
        val result2 = useCase(date2).first()
        
        // Then
        assertEquals(notes1, result1)
        assertEquals(notes2, result2)
        assertEquals(2, mockRepository.observeCallCount)
    }

    @Test
    fun `invoke should return single note correctly`() = runTest {
        // Given
        val testDate = LocalDate(2024, 6, 15)
        val singleNote = createTestNote("Single note for the day")
        mockRepository.notesForRange = listOf(singleNote)
        
        // When
        val result = useCase(testDate).first()
        
        // Then
        assertEquals(1, result.size)
        assertEquals(singleNote, result.first())
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