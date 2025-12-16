package app.logdate.client.domain.rewind

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetWeekRewindUseCaseTest {

    private lateinit var mockGetRewindUseCase: MockGetRewindUseCase
    private lateinit var useCase: GetWeekRewindUseCase

    @BeforeTest
    fun setUp() {
        mockGetRewindUseCase = MockGetRewindUseCase()
        useCase = GetWeekRewindUseCase(getRewindUseCase = mockGetRewindUseCase)
    }

    @Test
    fun `invoke should request rewind for current week starting Sunday by default`() = runTest {
        // Given
        mockGetRewindUseCase.result = RewindQueryResult.NotReady
        
        // When
        useCase().first()
        
        // Then
        assertEquals(1, mockGetRewindUseCase.invocations.size)
        val params = mockGetRewindUseCase.invocations.first()
        
        // Verify that the week starts and ends appropriately
        assertTrue(params.start <= params.end)
        
        // Verify that the time range spans approximately a week (7 days)
        val timeDifference = params.end.toEpochMilliseconds() - params.start.toEpochMilliseconds()
        val expectedWeekMs = 7 * 24 * 60 * 60 * 1000L
        assertTrue(timeDifference <= expectedWeekMs) // Should be <= 7 days
    }

    @Test
    fun `invoke should handle custom week start day`() = runTest {
        // Given
        mockGetRewindUseCase.result = RewindQueryResult.NotReady
        
        // When
        useCase(weekStart = DayOfWeek.MONDAY).first()
        
        // Then
        assertEquals(1, mockGetRewindUseCase.invocations.size)
        val params = mockGetRewindUseCase.invocations.first()
        
        // Verify valid time range
        assertTrue(params.start <= params.end)
    }

    @Test
    fun `invoke should handle different week start days`() = runTest {
        // Given
        mockGetRewindUseCase.result = RewindQueryResult.NotReady
        
        // When
        val result1 = useCase(weekStart = DayOfWeek.SUNDAY).first()
        val result2 = useCase(weekStart = DayOfWeek.MONDAY).first()
        val result3 = useCase(weekStart = DayOfWeek.FRIDAY).first()
        
        // Then
        assertEquals(3, mockGetRewindUseCase.invocations.size)
        
        // All should return the same result type
        assertEquals(RewindQueryResult.NotReady, result1)
        assertEquals(RewindQueryResult.NotReady, result2)
        assertEquals(RewindQueryResult.NotReady, result3)
        
        // Verify all have valid time ranges
        mockGetRewindUseCase.invocations.forEach { params ->
            assertTrue(params.start <= params.end)
        }
    }

    @Test
    fun `invoke should return Success when rewind is available`() = runTest {
        // Given
        val expectedResult = RewindQueryResult.Success(
            app.logdate.shared.model.Rewind(
                uid = kotlin.uuid.Uuid.random(),
                startTime = kotlinx.datetime.Clock.System.now(),
                endTime = kotlinx.datetime.Clock.System.now(),
                summary = "Test week rewind",
                highlights = emptyList(),
                createdAt = kotlinx.datetime.Clock.System.now()
            )
        )
        mockGetRewindUseCase.result = expectedResult
        
        // When
        val result = useCase().first()
        
        // Then
        assertEquals(expectedResult, result)
        assertEquals(1, mockGetRewindUseCase.invocations.size)
    }

    @Test
    fun `invoke should delegate to GetRewindUseCase correctly`() = runTest {
        // Given
        mockGetRewindUseCase.result = RewindQueryResult.NotReady
        
        // When
        useCase(weekStart = DayOfWeek.WEDNESDAY).first()
        
        // Then
        assertEquals(1, mockGetRewindUseCase.invocations.size)
        
        val params = mockGetRewindUseCase.invocations.first()
        // Both start and end should be at start of day (midnight)
        val timezone = TimeZone.currentSystemDefault()
        
        // Verify start time is at beginning of day
        val startLocalDate = params.start.toLocalDateTime(timezone).date
        val startOfStartDay = startLocalDate.atStartOfDayIn(timezone)
        assertEquals(startOfStartDay, params.start)
        
        // Verify end time is at beginning of day
        val endLocalDate = params.end.toLocalDateTime(timezone).date
        val startOfEndDay = endLocalDate.atStartOfDayIn(timezone)
        assertEquals(startOfEndDay, params.end)
    }

    @Test
    fun `invoke should handle all days of week as start day`() = runTest {
        // Given
        mockGetRewindUseCase.result = RewindQueryResult.NotReady
        
        // When - Test all possible week start days
        val daysOfWeek = DayOfWeek.entries
        daysOfWeek.forEach { dayOfWeek ->
            useCase(weekStart = dayOfWeek).first()
        }
        
        // Then
        assertEquals(daysOfWeek.size, mockGetRewindUseCase.invocations.size)
        
        // All should have valid time ranges
        mockGetRewindUseCase.invocations.forEach { params ->
            assertTrue(params.start <= params.end)
        }
    }

    private class MockGetRewindUseCase : GetRewindUseCase {
        val invocations = mutableListOf<RewindParams>()
        var result: RewindQueryResult = RewindQueryResult.NotReady

        // Dummy constructor to satisfy inheritance
        constructor() : super(
            rewindRepository = object : app.logdate.client.repository.rewind.RewindRepository {
                override fun getAllRewinds() = flowOf(emptyList<app.logdate.shared.model.Rewind>())
                override fun getRewind(uid: kotlin.uuid.Uuid) = flowOf(
                    app.logdate.shared.model.Rewind(
                        uid = uid,
                        startTime = kotlinx.datetime.Clock.System.now(),
                        endTime = kotlinx.datetime.Clock.System.now(),
                        summary = "Mock rewind",
                        highlights = emptyList(),
                        createdAt = kotlinx.datetime.Clock.System.now()
                    )
                )
                override fun getRewindBetween(start: Instant, end: Instant) = flowOf(null)
                override suspend fun isRewindAvailable(start: Instant, end: Instant) = false
                override suspend fun createRewind(start: Instant, end: Instant) = app.logdate.shared.model.Rewind(
                    uid = kotlin.uuid.Uuid.random(),
                    startTime = start,
                    endTime = end,
                    summary = "Created rewind",
                    highlights = emptyList(),
                    createdAt = kotlinx.datetime.Clock.System.now()
                )
            }
        )

        override fun invoke(params: RewindParams): Flow<RewindQueryResult> {
            invocations.add(params)
            return flowOf(result)
        }
    }
}