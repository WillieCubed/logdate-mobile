package app.logdate.client.domain.notes

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class RemoveNoteUseCaseTest {

    private lateinit var mockRepository: MockJournalNotesRepository
    private lateinit var useCase: RemoveNoteUseCase

    @BeforeTest
    fun setUp() {
        mockRepository = MockJournalNotesRepository()
        useCase = RemoveNoteUseCase(notesRepository = mockRepository)
    }

    @Test
    fun `invoke should remove note by id`() = runTest {
        // Given
        val noteId = Uuid.random()
        
        // When
        useCase(noteId)
        
        // Then
        assertEquals(1, mockRepository.removedIds.size)
        assertEquals(noteId, mockRepository.removedIds.first())
    }

    @Test
    fun `invoke should handle multiple removals`() = runTest {
        // Given
        val noteId1 = Uuid.random()
        val noteId2 = Uuid.random()
        val noteId3 = Uuid.random()
        
        // When
        useCase(noteId1)
        useCase(noteId2)
        useCase(noteId3)
        
        // Then
        assertEquals(3, mockRepository.removedIds.size)
        assertTrue(mockRepository.removedIds.contains(noteId1))
        assertTrue(mockRepository.removedIds.contains(noteId2))
        assertTrue(mockRepository.removedIds.contains(noteId3))
    }

    @Test
    fun `invoke should handle repository errors`() = runTest {
        // Given
        val noteId = Uuid.random()
        mockRepository.shouldThrowException = true
        
        // When/Then
        try {
            useCase(noteId)
            // Should fail if exception is not thrown
            kotlin.test.fail("Expected exception was not thrown")
        } catch (e: Exception) {
            assertEquals("Repository error", e.message)
        }
    }

    private class MockJournalNotesRepository : JournalNotesRepository {
        val removedIds = mutableListOf<Uuid>()
        var shouldThrowException = false

        override suspend fun removeById(noteId: Uuid) {
            if (shouldThrowException) {
                throw Exception("Repository error")
            }
            removedIds.add(noteId)
        }

        override val allNotesObserved = kotlinx.coroutines.flow.flowOf(emptyList<JournalNote>())
        override fun observeNotesInJournal(journalId: Uuid) = kotlinx.coroutines.flow.flowOf(emptyList<JournalNote>())
        override fun observeNotesInRange(start: kotlinx.datetime.Instant, end: kotlinx.datetime.Instant) =
            kotlinx.coroutines.flow.flowOf(emptyList<JournalNote>())
        override fun observeNotesPage(pageSize: Int, offset: Int) = kotlinx.coroutines.flow.flowOf(emptyList<JournalNote>())
        override fun observeNotesStream(pageSize: Int) = kotlinx.coroutines.flow.flowOf(emptyList<JournalNote>())
        override fun observeRecentNotes(limit: Int) = kotlinx.coroutines.flow.flowOf(emptyList<JournalNote>())
        override suspend fun create(note: JournalNote): Uuid = note.uid
        override suspend fun remove(note: JournalNote) = Unit
        override suspend fun create(note: JournalNote, journalId: Uuid) = Unit
        override suspend fun removeFromJournal(noteId: Uuid, journalId: Uuid) = Unit
    }
}
