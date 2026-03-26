package app.logdate.client.domain.timeline

import app.logdate.client.health.model.DayBounds
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class GetDayBoundsUseCaseTest {
    private val repository = FakeHealthRepository()
    private val useCase = GetDayBoundsUseCase(repository, FakeDayBoundarySettingsRepository())
    private val testTimeZone = TimeZone.of("UTC")
    private val testDate = LocalDate(2023, 6, 15)

    @Test
    fun `invoke returns successful result when repository returns day bounds`() =
        runTest {
            val expectedStart = Instant.fromEpochSeconds(1686812400)
            val expectedEnd = Instant.fromEpochSeconds(1686866400)
            val expectedBounds = DayBounds(expectedStart, expectedEnd)

            repository.throwable = null
            repository.bounds = expectedBounds

            val result = useCase(testDate, testTimeZone)

            assertTrue(result.isSuccess)
            assertEquals(expectedBounds, result.getOrNull())
        }

    @Test
    fun `invoke returns failure result when repository throws exception`() =
        runTest {
            val expectedException = RuntimeException("Test exception")
            repository.throwable = expectedException

            val result = useCase(testDate, testTimeZone)

            assertTrue(result.isFailure)
            assertEquals(expectedException, result.exceptionOrNull())
        }

    @Test
    fun `invoke uses system default time zone when none provided`() =
        runTest {
            val systemTimeZone = TimeZone.currentSystemDefault()
            val expectedStart = Instant.fromEpochSeconds(1686812400)
            val expectedEnd = Instant.fromEpochSeconds(1686866400)
            val expectedBounds = DayBounds(expectedStart, expectedEnd)

            repository.throwable = null
            repository.bounds = expectedBounds

            val result = useCase(testDate, systemTimeZone)

            assertTrue(result.isSuccess)
            assertEquals(expectedBounds, result.getOrNull())
        }
}
