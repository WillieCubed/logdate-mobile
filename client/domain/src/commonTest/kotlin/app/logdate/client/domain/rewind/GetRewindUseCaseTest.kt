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

class GetRewindUseCaseTest {

    private lateinit var mockRepository: MockRewindRepository
    private lateinit var useCase: GetRewindUseCase

    @BeforeTest
    fun setUp() {
        mockRepository = MockRewindRepository()
        useCase = GetRewindUseCase(rewindRepository = mockRepository)
    }

    @Test
    fun `invoke should return Success when rewind exists`() = runTest {
        // Given
        val start = Instant.fromEpochMilliseconds(1000)
        val end = Instant.fromEpochMilliseconds(5000)
        val testRewind = createTestRewind()
        val params = RewindParams(start, end)
        
        mockRepository.rewindResult = testRewind
        
        // When
        val result = useCase(params).first()
        
        // Then
        assertTrue(result is RewindQueryResult.Success)
        assertEquals(testRewind, (result as RewindQueryResult.Success).rewind)
        assertEquals(1, mockRepository.getRewindBetweenCalls.size)
        
        val call = mockRepository.getRewindBetweenCalls.first()
        assertEquals(start, call.first)
        assertEquals(end, call.second)
    }

    @Test
    fun `invoke should return NotReady when rewind is null`() = runTest {
        // Given
        val start = Instant.fromEpochMilliseconds(2000)
        val end = Instant.fromEpochMilliseconds(6000)
        val params = RewindParams(start, end)
        
        mockRepository.rewindResult = null
        
        // When
        val result = useCase(params).first()
        
        // Then
        assertEquals(RewindQueryResult.NotReady, result)
        assertEquals(1, mockRepository.getRewindBetweenCalls.size)
    }

    @Test
    fun `invoke should handle different time ranges correctly`() = runTest {
        // Given
        val params1 = RewindParams(
            start = Instant.fromEpochMilliseconds(1000),
            end = Instant.fromEpochMilliseconds(2000)
        )
        val params2 = RewindParams(
            start = Instant.fromEpochMilliseconds(5000),
            end = Instant.fromEpochMilliseconds(10000)
        )
        
        val rewind1 = createTestRewind()
        mockRepository.rewindResult = rewind1
        
        // When
        val result1 = useCase(params1).first()
        
        mockRepository.rewindResult = null
        val result2 = useCase(params2).first()
        
        // Then
        assertTrue(result1 is RewindQueryResult.Success)
        assertEquals(rewind1, (result1 as RewindQueryResult.Success).rewind)
        assertEquals(RewindQueryResult.NotReady, result2)
        assertEquals(2, mockRepository.getRewindBetweenCalls.size)
    }

    @Test
    fun `invoke should handle multiple calls with same parameters`() = runTest {
        // Given
        val params = RewindParams(
            start = Instant.fromEpochMilliseconds(3000),
            end = Instant.fromEpochMilliseconds(7000)
        )
        val testRewind = createTestRewind()
        mockRepository.rewindResult = testRewind
        
        // When
        val result1 = useCase(params).first()
        val result2 = useCase(params).first()
        
        // Then
        assertTrue(result1 is RewindQueryResult.Success)
        assertTrue(result2 is RewindQueryResult.Success)
        assertEquals(testRewind, (result1 as RewindQueryResult.Success).rewind)
        assertEquals(testRewind, (result2 as RewindQueryResult.Success).rewind)
        assertEquals(2, mockRepository.getRewindBetweenCalls.size)
    }

    @Test
    fun `invoke should handle edge case time ranges`() = runTest {
        // Given - Start and end are the same
        val sameInstant = Instant.fromEpochMilliseconds(5000)
        val params = RewindParams(start = sameInstant, end = sameInstant)
        mockRepository.rewindResult = null
        
        // When
        val result = useCase(params).first()
        
        // Then
        assertEquals(RewindQueryResult.NotReady, result)
        assertEquals(1, mockRepository.getRewindBetweenCalls.size)
        
        val call = mockRepository.getRewindBetweenCalls.first()
        assertEquals(sameInstant, call.first)
        assertEquals(sameInstant, call.second)
    }

    @Test
    fun `invoke should handle future time ranges`() = runTest {
        // Given - Time range in the future
        val futureStart = Instant.fromEpochMilliseconds(Long.MAX_VALUE - 1000)
        val futureEnd = Instant.fromEpochMilliseconds(Long.MAX_VALUE)
        val params = RewindParams(start = futureStart, end = futureEnd)
        mockRepository.rewindResult = null
        
        // When
        val result = useCase(params).first()
        
        // Then
        assertEquals(RewindQueryResult.NotReady, result)
    }

    private fun createTestRewind() = Rewind(
        uid = Uuid.random(),
        startTime = Clock.System.now(),
        endTime = Clock.System.now(),
        summary = "Test rewind summary",
        highlights = emptyList(),
        createdAt = Clock.System.now()
    )

    private class MockRewindRepository : RewindRepository {
        var rewindResult: Rewind? = null
        val getRewindBetweenCalls = mutableListOf<Pair<Instant, Instant>>()

        override fun getRewindBetween(start: Instant, end: Instant): Flow<Rewind?> {
            getRewindBetweenCalls.add(Pair(start, end))
            return flowOf(rewindResult)
        }

        override fun getAllRewinds(): Flow<List<Rewind>> = flowOf(emptyList())
        override fun getRewind(uid: Uuid): Flow<Rewind> = flowOf(createTestRewind())
        override suspend fun isRewindAvailable(start: Instant, end: Instant): Boolean = false
        override suspend fun createRewind(start: Instant, end: Instant): Rewind = createTestRewind()

        private fun createTestRewind() = Rewind(
            uid = Uuid.random(),
            startTime = Clock.System.now(),
            endTime = Clock.System.now(),
            summary = "Mock rewind",
            highlights = emptyList(),
            createdAt = Clock.System.now()
        )
    }
}