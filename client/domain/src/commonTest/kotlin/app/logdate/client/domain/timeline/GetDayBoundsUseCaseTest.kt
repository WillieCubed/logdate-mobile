package app.logdate.client.domain.timeline

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetDayBoundsUseCaseTest {

    private val mockRepository = mockk<HealthConnectRepository>()
    private val useCase = GetDayBoundsUseCase(mockRepository)
    private val testTimeZone = TimeZone.of("UTC")
    private val testDate = LocalDate(2023, 6, 15)

    @Test
    fun `invoke returns successful result when repository returns day bounds`() = runTest {
        // Given
        val expectedStart = Instant.fromEpochSeconds(1686812400) // 2023-06-15T07:00:00Z
        val expectedEnd = Instant.fromEpochSeconds(1686866400) // 2023-06-15T23:00:00Z
        val expectedBounds = DayBounds(expectedStart, expectedEnd)
        
        coEvery { mockRepository.getDayBoundsForDate(testDate, testTimeZone) } returns expectedBounds
        
        // When
        val result = useCase(testDate, testTimeZone)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedBounds, result.getOrNull())
    }
    
    @Test
    fun `invoke returns failure result when repository throws exception`() = runTest {
        // Given
        val expectedException = RuntimeException("Test exception")
        coEvery { mockRepository.getDayBoundsForDate(testDate, testTimeZone) } throws expectedException
        
        // When
        val result = useCase(testDate, testTimeZone)
        
        // Then
        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
    }
    
    @Test
    fun `invoke uses system default time zone when none provided`() = runTest {
        // Given
        val systemTimeZone = TimeZone.currentSystemDefault()
        val expectedStart = Instant.fromEpochSeconds(1686812400)
        val expectedEnd = Instant.fromEpochSeconds(1686866400)
        val expectedBounds = DayBounds(expectedStart, expectedEnd)
        
        coEvery { mockRepository.getDayBoundsForDate(testDate, systemTimeZone) } returns expectedBounds
        
        // When
        val result = useCase(testDate) // No timezone provided
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedBounds, result.getOrNull())
    }
}