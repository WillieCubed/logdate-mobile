package app.logdate.client.domain.journals

import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.uuid.Uuid

class GetCurrentUserJournalsUseCaseTest {

    private class MockJournalRepository : JournalRepository {
        var allJournalsObservedResult: Flow<List<Journal>> = flowOf(emptyList())

        override val allJournalsObserved: Flow<List<Journal>>
            get() = allJournalsObservedResult

        override fun observeJournalById(id: Uuid): Flow<Journal> = flowOf(
            Journal(id = id, title = "Test")
        )
        override suspend fun getJournalById(id: Uuid): Journal? = null
        override suspend fun create(journal: Journal): Uuid = journal.id
        override suspend fun update(journal: Journal) = Unit
        override suspend fun delete(journalId: Uuid) = Unit
        override suspend fun saveDraft(draft: EditorDraft) = Unit
        override suspend fun getLatestDraft(): EditorDraft? = null
        override suspend fun getAllDrafts(): List<EditorDraft> = emptyList()
        override suspend fun getDraft(id: Uuid): EditorDraft? = null
        override suspend fun deleteDraft(id: Uuid) = Unit
    }

    @Test
    fun `invoke should return flow of all journals from repository`() = runTest {
        // Given
        val mockRepository = MockJournalRepository()
        val expectedJournals = listOf(
            Journal(
                id = Uuid.random(),
                title = "My Daily Journal",
                created = Clock.System.now(),
                lastUpdated = Clock.System.now()
            ),
            Journal(
                id = Uuid.random(), 
                title = "Work Notes",
                created = Clock.System.now(),
                lastUpdated = Clock.System.now()
            ),
            Journal(
                id = Uuid.random(),
                title = "Travel Diary",
                created = Clock.System.now(),
                lastUpdated = Clock.System.now()
            )
        )
        mockRepository.allJournalsObservedResult = flowOf(expectedJournals)
        val useCase = GetCurrentUserJournalsUseCase(mockRepository)

        // When
        val result = useCase()

        // Then
        val emittedValues = result.toList()
        assertEquals(1, emittedValues.size)
        assertEquals(expectedJournals, emittedValues.first())
    }

    @Test
    fun `invoke should return empty flow when no journals exist`() = runTest {
        // Given
        val mockRepository = MockJournalRepository()
        mockRepository.allJournalsObservedResult = flowOf(emptyList())
        val useCase = GetCurrentUserJournalsUseCase(mockRepository)

        // When
        val result = useCase()

        // Then
        val emittedValues = result.toList()
        assertEquals(1, emittedValues.size)
        assertEquals(emptyList(), emittedValues.first())
    }

    @Test
    fun `invoke should return single journal when only one exists`() = runTest {
        // Given
        val mockRepository = MockJournalRepository()
        val singleJournal = Journal(
            id = Uuid.random(),
            title = "My Only Journal",
            created = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )
        mockRepository.allJournalsObservedResult = flowOf(listOf(singleJournal))
        val useCase = GetCurrentUserJournalsUseCase(mockRepository)

        // When
        val result = useCase()

        // Then
        val emittedValues = result.toList()
        assertEquals(1, emittedValues.size)
        assertEquals(listOf(singleJournal), emittedValues.first())
    }

    @Test
    fun `invoke should handle multiple emissions from repository`() = runTest {
        // Given
        val mockRepository = MockJournalRepository()
        val firstEmission = listOf(
            Journal(
                id = Uuid.random(),
                title = "First Journal",
                created = Clock.System.now(),
                lastUpdated = Clock.System.now()
            )
        )
        val secondEmission = listOf(
            Journal(
                id = Uuid.random(),
                title = "First Journal",
                created = Clock.System.now(),
                lastUpdated = Clock.System.now()
            ),
            Journal(
                id = Uuid.random(),
                title = "Second Journal",
                created = Clock.System.now(),
                lastUpdated = Clock.System.now()
            )
        )
        mockRepository.allJournalsObservedResult = flowOf(firstEmission, secondEmission)
        val useCase = GetCurrentUserJournalsUseCase(mockRepository)

        // When
        val result = useCase()

        // Then
        val emittedValues = result.toList()
        assertEquals(2, emittedValues.size)
        assertEquals(firstEmission, emittedValues[0])
        assertEquals(secondEmission, emittedValues[1])
    }

    @Test
    fun `invoke should preserve order of journals from repository`() = runTest {
        // Given
        val mockRepository = MockJournalRepository()
        val orderedJournals = listOf(
            Journal(
                id = Uuid.random(),
                title = "Charlie's Journal",
                created = Clock.System.now(),
                lastUpdated = Clock.System.now()
            ),
            Journal(
                id = Uuid.random(),
                title = "Alice's Journal",
                created = Clock.System.now(),
                lastUpdated = Clock.System.now()
            ),
            Journal(
                id = Uuid.random(),
                title = "Bob's Journal",
                created = Clock.System.now(),
                lastUpdated = Clock.System.now()
            )
        )
        mockRepository.allJournalsObservedResult = flowOf(orderedJournals)
        val useCase = GetCurrentUserJournalsUseCase(mockRepository)

        // When
        val result = useCase()

        // Then
        val emittedValues = result.toList()
        assertEquals(1, emittedValues.size)
        assertEquals(orderedJournals, emittedValues.first())
        assertEquals("Charlie's Journal", emittedValues.first()[0].title)
        assertEquals("Alice's Journal", emittedValues.first()[1].title)
        assertEquals("Bob's Journal", emittedValues.first()[2].title)
    }

    @Test
    fun `invoke should handle reactive updates to journal list`() = runTest {
        // Given
        val mockRepository = MockJournalRepository()
        val initialJournals = listOf(
            Journal(
                id = Uuid.random(),
                title = "Initial Journal",
                created = Clock.System.now(),
                lastUpdated = Clock.System.now()
            )
        )
        val updatedJournals = listOf(
            Journal(
                id = Uuid.random(), 
                title = "Updated Journal",
                created = Clock.System.now(),
                lastUpdated = Clock.System.now()
            )
        )
        mockRepository.allJournalsObservedResult = flowOf(initialJournals, updatedJournals)
        val useCase = GetCurrentUserJournalsUseCase(mockRepository)

        // When
        val result = useCase()

        // Then
        val emittedValues = result.toList()
        assertEquals(2, emittedValues.size)
        assertEquals("Initial Journal", emittedValues[0].first().title)
        assertEquals("Updated Journal", emittedValues[1].first().title)
    }
}
