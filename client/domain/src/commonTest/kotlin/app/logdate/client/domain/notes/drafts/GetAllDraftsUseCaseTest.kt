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
import kotlin.uuid.Uuid

class GetAllDraftsUseCaseTest {

    private lateinit var mockRepository: MockEntryDraftRepository
    private lateinit var useCase: GetAllDraftsUseCase

    @BeforeTest
    fun setUp() {
        mockRepository = MockEntryDraftRepository()
        useCase = GetAllDraftsUseCase(entryDraftRepository = mockRepository)
    }

    @Test
    fun `invoke should return all drafts from repository`() = runTest {
        // Given
        val draft1 = createTestDraft()
        val draft2 = createTestDraft()
        val draft3 = createTestDraft()
        val allDrafts = listOf(draft1, draft2, draft3)
        
        mockRepository.drafts = allDrafts
        
        // When
        val result = useCase().first()
        
        // Then
        assertEquals(allDrafts, result)
        assertEquals(3, result.size)
    }

    @Test
    fun `invoke should return empty list when no drafts exist`() = runTest {
        // Given
        mockRepository.drafts = emptyList()
        
        // When
        val result = useCase().first()
        
        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `invoke should return single draft when only one exists`() = runTest {
        // Given
        val singleDraft = createTestDraft()
        mockRepository.drafts = listOf(singleDraft)
        
        // When
        val result = useCase().first()
        
        // Then
        assertEquals(1, result.size)
        assertEquals(singleDraft, result.first())
    }

    @Test
    fun `invoke should handle large number of drafts`() = runTest {
        // Given
        val manyDrafts = (1..50).map { createTestDraft() }
        mockRepository.drafts = manyDrafts
        
        // When
        val result = useCase().first()
        
        // Then
        assertEquals(50, result.size)
        assertEquals(manyDrafts, result)
    }

    @Test
    fun `invoke should preserve draft order from repository`() = runTest {
        // Given
        val draft1 = createTestDraft(content = "First draft")
        val draft2 = createTestDraft(content = "Second draft")
        val draft3 = createTestDraft(content = "Third draft")
        val orderedDrafts = listOf(draft1, draft2, draft3)
        
        mockRepository.drafts = orderedDrafts
        
        // When
        val result = useCase().first()
        
        // Then
        assertEquals(orderedDrafts, result)
        assertEquals("First draft", (result[0].notes.first() as JournalNote.Text).content)
        assertEquals("Second draft", (result[1].notes.first() as JournalNote.Text).content)
        assertEquals("Third draft", (result[2].notes.first() as JournalNote.Text).content)
    }

    private fun createTestDraft(content: String = "Test draft content") = EntryDraft(
        id = Uuid.random(),
        notes = listOf(
            JournalNote.Text(
                uid = Uuid.random(),
                content = content,
                creationTimestamp = Clock.System.now(),
                lastUpdated = Clock.System.now()
            )
        ),
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )

    private class MockEntryDraftRepository : EntryDraftRepository {
        var drafts = emptyList<EntryDraft>()

        override fun getDrafts(): Flow<List<EntryDraft>> = flowOf(drafts)

        override fun getDraft(uid: Uuid): Flow<Result<EntryDraft>> = flowOf(Result.failure(NoSuchElementException()))
        override suspend fun createDraft(notes: List<JournalNote>): Uuid = Uuid.random()
        override suspend fun updateDraft(uid: Uuid, notes: List<JournalNote>): Uuid = uid
        override suspend fun deleteDraft(uid: Uuid) = Unit
    }
}