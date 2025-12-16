package app.logdate.client.domain.notes.drafts

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
import kotlin.uuid.Uuid

class CreateEntryDraftUseCaseTest {

    private lateinit var mockRepository: MockEntryDraftRepository
    private lateinit var useCase: CreateEntryDraftUseCase

    @BeforeTest
    fun setUp() {
        mockRepository = MockEntryDraftRepository()
        useCase = CreateEntryDraftUseCase(entryDraftRepository = mockRepository)
    }

    @Test
    fun `invoke with list of notes should create draft and return draft ID`() = runTest {
        // Given
        val testNotes = listOf(
            createTestNote("First note"),
            createTestNote("Second note"),
            createTestNote("Third note")
        )
        val expectedDraftId = Uuid.random()
        mockRepository.nextDraftId = expectedDraftId
        
        // When
        val result = useCase(testNotes)
        
        // Then
        assertEquals(expectedDraftId, result)
        assertEquals(1, mockRepository.createdDrafts.size)
        assertEquals(testNotes, mockRepository.createdDrafts.first())
    }

    @Test
    fun `invoke with single note should create draft and return draft ID`() = runTest {
        // Given
        val testNote = createTestNote("Single note")
        val expectedDraftId = Uuid.random()
        mockRepository.nextDraftId = expectedDraftId
        
        // When
        val result = useCase(testNote)
        
        // Then
        assertEquals(expectedDraftId, result)
        assertEquals(1, mockRepository.createdDrafts.size)
        assertEquals(listOf(testNote), mockRepository.createdDrafts.first())
    }

    @Test
    fun `invoke with empty list should create empty draft`() = runTest {
        // Given
        val emptyNotes = emptyList<JournalNote>()
        val expectedDraftId = Uuid.random()
        mockRepository.nextDraftId = expectedDraftId
        
        // When
        val result = useCase(emptyNotes)
        
        // Then
        assertEquals(expectedDraftId, result)
        assertEquals(1, mockRepository.createdDrafts.size)
        assertEquals(emptyNotes, mockRepository.createdDrafts.first())
    }

    @Test
    fun `invoke should handle multiple draft creations`() = runTest {
        // Given
        val note1 = createTestNote("Note 1")
        val note2 = createTestNote("Note 2")
        val draftId1 = Uuid.random()
        val draftId2 = Uuid.random()
        
        // When
        mockRepository.nextDraftId = draftId1
        val result1 = useCase(note1)
        
        mockRepository.nextDraftId = draftId2
        val result2 = useCase(note2)
        
        // Then
        assertEquals(draftId1, result1)
        assertEquals(draftId2, result2)
        assertEquals(2, mockRepository.createdDrafts.size)
    }

    private fun createTestNote(content: String) = JournalNote.Text(
        uid = Uuid.random(),
        content = content,
        creationTimestamp = Clock.System.now(),
        lastUpdated = Clock.System.now()
    )

    private class MockEntryDraftRepository : EntryDraftRepository {
        val createdDrafts = mutableListOf<List<JournalNote>>()
        var nextDraftId = Uuid.random()

        override suspend fun createDraft(notes: List<JournalNote>): Uuid {
            createdDrafts.add(notes)
            return nextDraftId
        }

        override fun getDrafts(): Flow<List<EntryDraft>> = flowOf(emptyList())
        override fun getDraft(uid: Uuid): Flow<Result<EntryDraft>> = flowOf(Result.failure(NoSuchElementException()))
        override suspend fun updateDraft(uid: Uuid, notes: List<JournalNote>): Uuid = uid
        override suspend fun deleteDraft(uid: Uuid) = Unit
    }
}