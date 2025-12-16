package app.logdate.client.domain.notes.drafts

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class DeleteEntryDraftUseCaseTest {

    private lateinit var mockRepository: MockEntryDraftRepository
    private lateinit var useCase: DeleteEntryDraftUseCase

    @BeforeTest
    fun setUp() {
        mockRepository = MockEntryDraftRepository()
        useCase = DeleteEntryDraftUseCase(entryDraftRepository = mockRepository)
    }

    @Test
    fun `invoke should delete draft by ID`() = runTest {
        // Given
        val draftId = Uuid.random()
        
        // When
        useCase(draftId)
        
        // Then
        assertEquals(1, mockRepository.deletedDraftIds.size)
        assertEquals(draftId, mockRepository.deletedDraftIds.first())
    }

    @Test
    fun `invoke should handle multiple deletions`() = runTest {
        // Given
        val draftId1 = Uuid.random()
        val draftId2 = Uuid.random()
        val draftId3 = Uuid.random()
        
        // When
        useCase(draftId1)
        useCase(draftId2)
        useCase(draftId3)
        
        // Then
        assertEquals(3, mockRepository.deletedDraftIds.size)
        assertTrue(mockRepository.deletedDraftIds.contains(draftId1))
        assertTrue(mockRepository.deletedDraftIds.contains(draftId2))
        assertTrue(mockRepository.deletedDraftIds.contains(draftId3))
    }

    @Test
    fun `invoke should handle repository errors gracefully`() = runTest {
        // Given
        val draftId = Uuid.random()
        mockRepository.shouldThrowException = true
        
        // When/Then
        try {
            useCase(draftId)
            kotlin.test.fail("Expected exception was not thrown")
        } catch (e: Exception) {
            assertEquals("Repository error", e.message)
        }
    }

    private class MockEntryDraftRepository : EntryDraftRepository {
        val deletedDraftIds = mutableListOf<Uuid>()
        var shouldThrowException = false

        override suspend fun deleteDraft(uid: Uuid) {
            if (shouldThrowException) {
                throw Exception("Repository error")
            }
            deletedDraftIds.add(uid)
        }

        override fun getDrafts(): Flow<List<EntryDraft>> = flowOf(emptyList())
        override fun getDraft(uid: Uuid): Flow<Result<EntryDraft>> = flowOf(Result.failure(NoSuchElementException()))
        override suspend fun createDraft(notes: List<JournalNote>): Uuid = Uuid.random()
        override suspend fun updateDraft(uid: Uuid, notes: List<JournalNote>): Uuid = uid
    }
}