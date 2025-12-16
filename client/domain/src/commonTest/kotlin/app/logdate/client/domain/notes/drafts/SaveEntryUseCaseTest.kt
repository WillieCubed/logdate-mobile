package app.logdate.client.domain.notes.drafts

import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SaveEntryUseCaseTest {

    private lateinit var mockDraftRepository: MockEntryDraftRepository
    private lateinit var mockAddNoteUseCase: MockAddNoteUseCase
    private lateinit var useCase: SaveEntryUseCase

    @BeforeTest
    fun setUp() {
        mockDraftRepository = MockEntryDraftRepository()
        mockAddNoteUseCase = MockAddNoteUseCase()
        useCase = SaveEntryUseCase(
            draftRepository = mockDraftRepository,
            addNotes = mockAddNoteUseCase
        )
    }

    @Test
    fun `invoke should add notes from draft and return empty list`() = runTest {
        // Given
        val testNotes = listOf(
            createTestNote("First note"),
            createTestNote("Second note"),
            createTestNote("Third note")
        )
        val testDraft = createTestDraft(notes = testNotes)
        
        // When
        val result = useCase(testDraft)
        
        // Then
        assertTrue(result.isEmpty())
        assertEquals(1, mockAddNoteUseCase.addNoteCalls.size)
        assertEquals(testNotes, mockAddNoteUseCase.addNoteCalls.first())
    }

    @Test
    fun `invoke should handle draft with single note`() = runTest {
        // Given
        val singleNote = createTestNote("Single note")
        val testDraft = createTestDraft(notes = listOf(singleNote))
        
        // When
        val result = useCase(testDraft)
        
        // Then
        assertTrue(result.isEmpty())
        assertEquals(1, mockAddNoteUseCase.addNoteCalls.size)
        assertEquals(listOf(singleNote), mockAddNoteUseCase.addNoteCalls.first())
    }

    @Test
    fun `invoke should handle draft with empty notes`() = runTest {
        // Given
        val testDraft = createTestDraft(notes = emptyList())
        
        // When
        val result = useCase(testDraft)
        
        // Then
        assertTrue(result.isEmpty())
        assertEquals(1, mockAddNoteUseCase.addNoteCalls.size)
        assertEquals(emptyList(), mockAddNoteUseCase.addNoteCalls.first())
    }

    @Test
    fun `invoke should handle multiple save operations`() = runTest {
        // Given
        val draft1 = createTestDraft(notes = listOf(createTestNote("Draft 1")))
        val draft2 = createTestDraft(notes = listOf(createTestNote("Draft 2")))
        
        // When
        val result1 = useCase(draft1)
        val result2 = useCase(draft2)
        
        // Then
        assertTrue(result1.isEmpty())
        assertTrue(result2.isEmpty())
        assertEquals(2, mockAddNoteUseCase.addNoteCalls.size)
    }

    @Test
    fun `invoke should handle AddNoteUseCase errors gracefully`() = runTest {
        // Given
        val testDraft = createTestDraft(notes = listOf(createTestNote("Test note")))
        mockAddNoteUseCase.shouldThrowException = true
        
        // When/Then
        try {
            useCase(testDraft)
            kotlin.test.fail("Expected exception was not thrown")
        } catch (e: Exception) {
            assertEquals("AddNote failed", e.message)
        }
    }

    private fun createTestNote(content: String) = JournalNote.Text(
        uid = Uuid.random(),
        content = content,
        creationTimestamp = Clock.System.now(),
        lastUpdated = Clock.System.now()
    )

    private fun createTestDraft(notes: List<JournalNote>) = EntryDraft(
        id = Uuid.random(),
        notes = notes,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )

    private class MockEntryDraftRepository : EntryDraftRepository {
        override fun getDrafts(): Flow<List<EntryDraft>> = flowOf(emptyList())
        override fun getDraft(uid: Uuid): Flow<Result<EntryDraft>> = flowOf(Result.failure(NoSuchElementException()))
        override suspend fun createDraft(notes: List<JournalNote>): Uuid = Uuid.random()
        override suspend fun updateDraft(uid: Uuid, notes: List<JournalNote>): Uuid = uid
        override suspend fun deleteDraft(uid: Uuid) = Unit
    }

    private class MockAddNoteUseCase : AddNoteUseCase {
        val addNoteCalls = mutableListOf<List<JournalNote>>()
        var shouldThrowException = false

        override suspend fun invoke(notes: List<JournalNote>, attachments: List<String>) {
            addNoteCalls.add(notes)
            if (shouldThrowException) {
                throw Exception("AddNote failed")
            }
        }

        override suspend fun invoke(vararg notes: JournalNote, attachments: List<String>) {
            addNoteCalls.add(notes.toList())
            if (shouldThrowException) {
                throw Exception("AddNote failed")
            }
        }
    }
}