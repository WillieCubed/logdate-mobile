package app.logdate.client.domain.rewind

import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.shared.model.Rewind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class GetPastRewindsUseCaseTest {

    private lateinit var mockRepository: MockRewindRepository
    private lateinit var useCase: GetPastRewindsUseCase

    @BeforeTest
    fun setUp() {
        mockRepository = MockRewindRepository()
        useCase = GetPastRewindsUseCase(rewindRepository = mockRepository)
    }

    @Test
    fun `invoke should return all rewinds from repository`() = runTest {
        // Given
        val rewind1 = createTestRewind("First rewind")
        val rewind2 = createTestRewind("Second rewind")
        val rewind3 = createTestRewind("Third rewind")
        val allRewinds = listOf(rewind1, rewind2, rewind3)
        
        mockRepository.allRewinds = allRewinds
        
        // When
        val result = useCase().first()
        
        // Then
        assertEquals(allRewinds, result)
        assertEquals(3, result.size)
        assertEquals(1, mockRepository.getAllRewindsCalls)
    }

    @Test
    fun `invoke should return empty list when no rewinds exist`() = runTest {
        // Given
        mockRepository.allRewinds = emptyList()
        
        // When
        val result = useCase().first()
        
        // Then
        assertTrue(result.isEmpty())
        assertEquals(1, mockRepository.getAllRewindsCalls)
    }

    @Test
    fun `invoke should return single rewind when only one exists`() = runTest {
        // Given
        val singleRewind = createTestRewind("Single rewind")
        mockRepository.allRewinds = listOf(singleRewind)
        
        // When
        val result = useCase().first()
        
        // Then
        assertEquals(1, result.size)
        assertEquals(singleRewind, result.first())
        assertEquals(1, mockRepository.getAllRewindsCalls)
    }

    @Test
    fun `invoke should handle multiple calls correctly`() = runTest {
        // Given
        val testRewinds = listOf(
            createTestRewind("Rewind A"),
            createTestRewind("Rewind B")
        )
        mockRepository.allRewinds = testRewinds
        
        // When
        val result1 = useCase().first()
        val result2 = useCase().first()
        
        // Then
        assertEquals(testRewinds, result1)
        assertEquals(testRewinds, result2)
        assertEquals(2, mockRepository.getAllRewindsCalls)
    }

    @Test
    fun `invoke should handle large number of rewinds`() = runTest {
        // Given
        val manyRewinds = (1..100).map { i ->
            createTestRewind("Rewind $i")
        }
        mockRepository.allRewinds = manyRewinds
        
        // When
        val result = useCase().first()
        
        // Then
        assertEquals(100, result.size)
        assertEquals(manyRewinds, result)
        assertEquals(1, mockRepository.getAllRewindsCalls)
    }

    @Test
    fun `invoke should preserve rewind order from repository`() = runTest {
        // Given
        val orderedRewinds = listOf(
            createTestRewind("First chronologically"),
            createTestRewind("Second chronologically"),
            createTestRewind("Third chronologically")
        )
        mockRepository.allRewinds = orderedRewinds
        
        // When
        val result = useCase().first()
        
        // Then
        assertEquals(orderedRewinds.size, result.size)
        orderedRewinds.forEachIndexed { index, expectedRewind ->
            assertEquals(expectedRewind, result[index])
        }
    }

    @Test
    fun `invoke should handle rewinds with different time periods`() = runTest {
        // Given
        val now = Clock.System.now()
        val rewind1 = Rewind(
            uid = Uuid.random(),
            startTime = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 7000),
            endTime = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 6000),
            summary = "Short rewind",
            highlights = emptyList(),
            createdAt = now
        )
        val rewind2 = Rewind(
            uid = Uuid.random(),
            startTime = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 10000),
            endTime = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 1000),
            summary = "Long rewind",
            highlights = emptyList(),
            createdAt = now
        )
        
        mockRepository.allRewinds = listOf(rewind1, rewind2)
        
        // When
        val result = useCase().first()
        
        // Then
        assertEquals(2, result.size)
        assertEquals(rewind1, result[0])
        assertEquals(rewind2, result[1])
    }

    private fun createTestRewind(summary: String) = Rewind(
        uid = Uuid.random(),
        startTime = Clock.System.now(),
        endTime = Clock.System.now(),
        summary = summary,
        highlights = emptyList(),
        createdAt = Clock.System.now()
    )

    private class MockRewindRepository : RewindRepository {
        var allRewinds = emptyList<Rewind>()
        var getAllRewindsCalls = 0

        override fun getAllRewinds(): Flow<List<Rewind>> {
            getAllRewindsCalls++
            return flowOf(allRewinds)
        }

        override fun getRewind(uid: Uuid): Flow<Rewind> = flowOf(createTestRewind("Single rewind"))
        override fun getRewindBetween(start: Instant, end: Instant): Flow<Rewind?> = flowOf(null)
        override suspend fun isRewindAvailable(start: Instant, end: Instant): Boolean = false
        override suspend fun createRewind(start: Instant, end: Instant): Rewind = createTestRewind("Created rewind")

        private fun createTestRewind(summary: String) = Rewind(
            uid = Uuid.random(),
            startTime = Clock.System.now(),
            endTime = Clock.System.now(),
            summary = summary,
            highlights = emptyList(),
            createdAt = Clock.System.now()
        )
    }
}