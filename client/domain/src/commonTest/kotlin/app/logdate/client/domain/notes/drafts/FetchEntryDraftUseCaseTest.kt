package app.logdate.client.domain.notes.drafts

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

class FetchEntryDraftUseCaseTest {

    private lateinit var mockRepository: MockEntryDraftRepository
    private lateinit var useCase: FetchEntryDraftUseCase

    @BeforeTest
    fun setUp() {
        mockRepository = MockEntryDraftRepository()
        useCase = FetchEntryDraftUseCase(entryDraftRepository = mockRepository)
    }

    @Test
    fun `invoke should return success result when draft exists`() = runTest {
        // Given
        val draftId = Uuid.random()
        val testDraft = createTestDraft(draftId)
        mockRepository.existingDrafts[draftId] = testDraft
        
        // When
        val result = useCase(draftId).first()
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(testDraft, result.getOrNull())
        assertEquals(1, mockRepository.getDraftCalls.size)
        assertEquals(draftId, mockRepository.getDraftCalls.first())
    }

    @Test
    fun `invoke should return failure result when draft does not exist`() = runTest {
        // Given
        val draftId = Uuid.random()
        // No draft added to repository
        
        // When
        val result = useCase(draftId).first()
        
        // Then
        assertTrue(result.isFailure)
        assertEquals(1, mockRepository.getDraftCalls.size)
        assertEquals(draftId, mockRepository.getDraftCalls.first())
    }

    @Test
    fun `invoke should handle multiple fetch requests`() = runTest {
        // Given
        val draftId1 = Uuid.random()
        val draftId2 = Uuid.random()
        val draft1 = createTestDraft(draftId1)
        val draft2 = createTestDraft(draftId2)
        
        mockRepository.existingDrafts[draftId1] = draft1
        mockRepository.existingDrafts[draftId2] = draft2
        
        // When
        val result1 = useCase(draftId1).first()
        val result2 = useCase(draftId2).first()
        
        // Then
        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)
        assertEquals(draft1, result1.getOrNull())
        assertEquals(draft2, result2.getOrNull())
        assertEquals(2, mockRepository.getDraftCalls.size)
    }

    @Test
    fun `invoke should return different results for existing vs non-existing drafts`() = runTest {
        // Given
        val existingDraftId = Uuid.random()
        val nonExistingDraftId = Uuid.random()
        val existingDraft = createTestDraft(existingDraftId)
        
        mockRepository.existingDrafts[existingDraftId] = existingDraft
        
        // When
        val existingResult = useCase(existingDraftId).first()
        val nonExistingResult = useCase(nonExistingDraftId).first()
        
        // Then
        assertTrue(existingResult.isSuccess)
        assertFalse(nonExistingResult.isSuccess)
        assertEquals(existingDraft, existingResult.getOrNull())
    }

    private fun createTestDraft(id: Uuid) = EntryDraft(
        id = id,
        notes = listOf(
            JournalNote.Text(
                uid = Uuid.random(),
                content = "Test draft content",
                creationTimestamp = Clock.System.now(),
                lastUpdated = Clock.System.now()
            )
        ),
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )

    private class MockEntryDraftRepository : EntryDraftRepository {
        val existingDrafts = mutableMapOf<Uuid, EntryDraft>()
        val getDraftCalls = mutableListOf<Uuid>()

        override fun getDraft(uid: Uuid): Flow<Result<EntryDraft>> {
            getDraftCalls.add(uid)
            val draft = existingDrafts[uid]
            return if (draft != null) {
                flowOf(Result.success(draft))
            } else {
                flowOf(Result.failure(NoSuchElementException("Draft not found")))
            }
        }

        override fun getDrafts(): Flow<List<EntryDraft>> = flowOf(emptyList())
        override suspend fun createDraft(notes: List<JournalNote>): Uuid = Uuid.random()
        override suspend fun updateDraft(uid: Uuid, notes: List<JournalNote>): Uuid = uid
        override suspend fun deleteDraft(uid: Uuid) = Unit
    }
}