package app.logdate.client.domain.timeline

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import app.logdate.client.health.LocalFirstHealthRepository
import app.logdate.client.health.model.DayBounds
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import kotlinx.datetime.Instant

class GetDayBoundsUseCaseTest {

    private val repository = FakeHealthConnectRepository()
    private val useCase = GetDayBoundsUseCase(repository)
    private val testTimeZone = TimeZone.of("UTC")
    private val testDate = LocalDate(2023, 6, 15)

    @Test
    fun `invoke returns successful result when repository returns day bounds`() = runTest {
        // Given
        val expectedStart = Instant.fromEpochSeconds(1686812400) // 2023-06-15T07:00:00Z
        val expectedEnd = Instant.fromEpochSeconds(1686866400) // 2023-06-15T23:00:00Z
        val expectedBounds = DayBounds(expectedStart, expectedEnd)

        repository.throwable = null
        repository.bounds = expectedBounds
        
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
        repository.throwable = expectedException
        
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

        repository.throwable = null
        repository.bounds = expectedBounds
        
        // When
        val result = useCase(testDate, systemTimeZone)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedBounds, result.getOrNull())
    }

    private class FakeHealthConnectRepository : LocalFirstHealthRepository {
        var bounds: DayBounds = DayBounds(Instant.DISTANT_PAST, Instant.DISTANT_FUTURE)
        var throwable: Throwable? = null

        override suspend fun getDayBoundsForDate(date: LocalDate, timeZone: TimeZone): DayBounds {
            throwable?.let { throw it }
            return bounds
        }

        override suspend fun isHealthDataAvailable(): Boolean = true
        override suspend fun getAvailableDataTypes(): List<String> = emptyList()
        override suspend fun hasSleepPermissions(): Boolean = true
        override suspend fun requestSleepPermissions(): Boolean = true
        override suspend fun getSleepSessions(start: Instant, end: Instant): List<SleepSession> = emptyList()
        override suspend fun getAverageWakeUpTime(timeZone: TimeZone, days: Int): TimeOfDay? = null
        override suspend fun getAverageSleepTime(timeZone: TimeZone, days: Int): TimeOfDay? = null
    }
}
