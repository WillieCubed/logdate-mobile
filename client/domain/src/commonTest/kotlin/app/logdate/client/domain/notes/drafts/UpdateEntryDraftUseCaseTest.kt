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

class UpdateEntryDraftUseCaseTest {

    private lateinit var mockRepository: MockEntryDraftRepository
    private lateinit var useCase: UpdateEntryDraftUseCase

    @BeforeTest
    fun setUp() {
        mockRepository = MockEntryDraftRepository()
        useCase = UpdateEntryDraftUseCase(entryDraftRepository = mockRepository)
    }

    @Test
    fun `invoke with notes list should update draft and return draft ID`() = runTest {
        // Given
        val draftId = Uuid.random()
        val updatedNotes = listOf(
            createTestNote("Updated note 1"),
            createTestNote("Updated note 2")
        )
        mockRepository.returnDraftId = draftId
        
        // When
        val result = useCase(draftId, updatedNotes)
        
        // Then
        assertEquals(draftId, result)
        assertEquals(1, mockRepository.updateCalls.size)
        val updateCall = mockRepository.updateCalls.first()
        assertEquals(draftId, updateCall.first)
        assertEquals(updatedNotes, updateCall.second)
    }

    @Test
    fun `invoke with EntryDraft should update draft and return draft ID`() = runTest {
        // Given
        val draftId = Uuid.random()
        val entryDraft = createTestDraft()
        mockRepository.returnDraftId = draftId
        
        // When
        val result = useCase(draftId, entryDraft)
        
        // Then
        assertEquals(draftId, result)
        assertEquals(1, mockRepository.updateCalls.size)
        val updateCall = mockRepository.updateCalls.first()
        assertEquals(draftId, updateCall.first)
        assertEquals(entryDraft.notes, updateCall.second)
    }

    @Test
    fun `invoke with overwrite true should update draft normally`() = runTest {
        // Given
        val draftId = Uuid.random()
        val updatedNotes = listOf(createTestNote("Overwritten content"))
        mockRepository.returnDraftId = draftId
        
        // When
        val result = useCase(draftId, updatedNotes, overwrite = true)
        
        // Then
        assertEquals(draftId, result)
        assertEquals(1, mockRepository.updateCalls.size)
    }

    @Test
    fun `invoke with overwrite false should still update draft`() = runTest {
        // Given
        val draftId = Uuid.random()
        val updatedNotes = listOf(createTestNote("Non-overwrite content"))
        mockRepository.returnDraftId = draftId
        
        // When
        val result = useCase(draftId, updatedNotes, overwrite = false)
        
        // Then
        assertEquals(draftId, result)
        assertEquals(1, mockRepository.updateCalls.size)
        // Note: The current implementation ignores the overwrite flag and always updates
    }

    @Test
    fun `invoke should handle multiple update operations`() = runTest {
        // Given
        val draftId1 = Uuid.random()
        val draftId2 = Uuid.random()
        val notes1 = listOf(createTestNote("First update"))
        val notes2 = listOf(createTestNote("Second update"))
        
        // When
        mockRepository.returnDraftId = draftId1
        val result1 = useCase(draftId1, notes1)
        
        mockRepository.returnDraftId = draftId2
        val result2 = useCase(draftId2, notes2)
        
        // Then
        assertEquals(draftId1, result1)
        assertEquals(draftId2, result2)
        assertEquals(2, mockRepository.updateCalls.size)
    }

    @Test
    fun `invoke should handle empty notes list`() = runTest {
        // Given
        val draftId = Uuid.random()
        val emptyNotes = emptyList<JournalNote>()
        mockRepository.returnDraftId = draftId
        
        // When
        val result = useCase(draftId, emptyNotes)
        
        // Then
        assertEquals(draftId, result)
        assertEquals(1, mockRepository.updateCalls.size)
        assertEquals(emptyNotes, mockRepository.updateCalls.first().second)
    }

    private fun createTestNote(content: String) = JournalNote.Text(
        uid = Uuid.random(),
        content = content,
        creationTimestamp = Clock.System.now(),
        lastUpdated = Clock.System.now()
    )

    private fun createTestDraft() = EntryDraft(
        id = Uuid.random(),
        notes = listOf(createTestNote("Test draft content")),
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )

    private class MockEntryDraftRepository : EntryDraftRepository {
        val updateCalls = mutableListOf<Pair<Uuid, List<JournalNote>>>()
        var returnDraftId = Uuid.random()

        override suspend fun updateDraft(uid: Uuid, notes: List<JournalNote>): Uuid {
            updateCalls.add(Pair(uid, notes))
            return returnDraftId
        }

        override fun getDrafts(): Flow<List<EntryDraft>> = flowOf(emptyList())
        override fun getDraft(uid: Uuid): Flow<Result<EntryDraft>> = flowOf(Result.failure(NoSuchElementException()))
        override suspend fun createDraft(notes: List<JournalNote>): Uuid = Uuid.random()
        override suspend fun deleteDraft(uid: Uuid) = Unit
    }
}