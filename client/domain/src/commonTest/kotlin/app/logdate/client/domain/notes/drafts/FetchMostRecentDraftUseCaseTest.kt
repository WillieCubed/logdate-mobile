package app.logdate.client.domain.notes.drafts

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class FetchMostRecentDraftUseCaseTest {

    private lateinit var mockRepository: MockEntryDraftRepository
    private lateinit var useCase: FetchMostRecentDraftUseCase

    @BeforeTest
    fun setUp() {
        mockRepository = MockEntryDraftRepository()
        useCase = FetchMostRecentDraftUseCase(entryDraftRepository = mockRepository)
    }

    @Test
    fun `invoke should return most recent draft by updatedAt timestamp`() = runTest {
        // Given
        val now = Clock.System.now()
        val oldDraft = createTestDraft(updatedAt = now - 2.hours)
        val recentDraft = createTestDraft(updatedAt = now - 1.hours)
        val mostRecentDraft = createTestDraft(updatedAt = now)
        
        mockRepository.drafts = listOf(oldDraft, recentDraft, mostRecentDraft)
        
        // When
        val result = useCase().first()
        
        // Then
        assertEquals(mostRecentDraft, result)
    }

    @Test
    fun `invoke should return null when no drafts exist`() = runTest {
        // Given
        mockRepository.drafts = emptyList()
        
        // When
        val result = useCase().first()
        
        // Then
        assertNull(result)
    }

    @Test
    fun `invoke should return single draft when only one exists`() = runTest {
        // Given
        val singleDraft = createTestDraft()
        mockRepository.drafts = listOf(singleDraft)
        
        // When
        val result = useCase().first()
        
        // Then
        assertEquals(singleDraft, result)
    }

    @Test
    fun `invoke should handle multiple drafts with same timestamp`() = runTest {
        // Given
        val timestamp = Clock.System.now()
        val draft1 = createTestDraft(id = Uuid.random(), updatedAt = timestamp)
        val draft2 = createTestDraft(id = Uuid.random(), updatedAt = timestamp)
        
        mockRepository.drafts = listOf(draft1, draft2)
        
        // When
        val result = useCase().first()
        
        // Then
        // Should return one of them (implementation returns the first one found by maxByOrNull)
        assertEquals(draft1, result)
    }

    @Test
    fun `invoke should correctly sort drafts with different timestamps`() = runTest {
        // Given
        val now = Clock.System.now()
        val veryOldDraft = createTestDraft(updatedAt = now - 10.hours)
        val newerDraft = createTestDraft(updatedAt = now - 5.hours)
        val newestDraft = createTestDraft(updatedAt = now)
        
        // Add them in non-chronological order to test sorting
        mockRepository.drafts = listOf(newerDraft, veryOldDraft, newestDraft)
        
        // When
        val result = useCase().first()
        
        // Then
        assertEquals(newestDraft, result)
    }

    private fun createTestDraft(
        id: Uuid = Uuid.random(),
        updatedAt: Instant = Clock.System.now()
    ) = EntryDraft(
        id = id,
        notes = listOf(
            JournalNote.Text(
                uid = Uuid.random(),
                content = "Test draft content",
                creationTimestamp = updatedAt,
                lastUpdated = updatedAt
            )
        ),
        createdAt = updatedAt,
        updatedAt = updatedAt
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