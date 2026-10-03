package app.logdate.wear.v2.today

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DayClockTest {
    private val newYork = TimeZone.of("America/New_York")
    private val tokyo = TimeZone.of("Asia/Tokyo")
    private val lateEvening = LocalDateTime(2026, 10, 3, 22, 0).toInstant(newYork)

    @Test
    fun `today is reported at once`() =
        runTest {
            val clock = SchedulerClock(lateEvening, testScheduler)

            DayClock(timeChanges = emptyFlow(), clock = clock, timeZone = { newYork }).observe().test {
                val today = awaitItem()
                assertEquals(LocalDate(2026, 10, 3), today.date)
                assertEquals(LocalDate(2026, 10, 3).atStartOfDayIn(newYork), today.start)
                assertEquals(LocalDate(2026, 10, 4).atStartOfDayIn(newYork), today.end)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the day moves at local midnight`() =
        runTest {
            val clock = SchedulerClock(lateEvening, testScheduler)

            DayClock(timeChanges = emptyFlow(), clock = clock, timeZone = { newYork }).observe().test {
                awaitItem()
                val tomorrow = awaitItem()
                assertEquals(LocalDate(2026, 10, 4), tomorrow.date)
                assertEquals(LocalDate(2026, 10, 4).atStartOfDayIn(newYork), clock.now())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a time zone change moves the day at once`() =
        runTest {
            var zone = newYork
            val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

            DayClock(timeChanges = changes, clock = SchedulerClock(lateEvening, testScheduler), timeZone = { zone }).observe().test {
                assertEquals(LocalDate(2026, 10, 3), awaitItem().date)

                zone = tokyo
                changes.tryEmit(Unit)

                val moved = awaitItem()
                assertEquals(LocalDate(2026, 10, 4), moved.date)
                assertEquals(tokyo, moved.timeZone)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a clock change inside the same day reports nothing new`() =
        runTest {
            val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

            DayClock(timeChanges = changes, clock = SchedulerClock(lateEvening, testScheduler), timeZone = { newYork }).observe().test {
                awaitItem()

                changes.tryEmit(Unit)
                testScheduler.runCurrent()

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the day daylight saving time ends is 25 hours long`() {
        val fallBack = LocalDateTime(2026, 11, 1, 12, 0).toInstant(newYork)

        val day = DayClock(timeChanges = emptyFlow(), clock = FixedClock(fallBack), timeZone = { newYork }).current()

        assertEquals(25.hours, day.end - day.start)
    }

    @Test
    fun `the window includes its start but not its end`() {
        val day = DayClock(timeChanges = emptyFlow(), clock = FixedClock(lateEvening), timeZone = { newYork }).current()

        assertTrue(day.start in day)
        assertFalse(day.end in day)
    }
}

/** A clock that reads the test scheduler, so `delay` in the code under test moves it. */
@OptIn(ExperimentalCoroutinesApi::class)
private class SchedulerClock(
    private val start: Instant,
    private val scheduler: TestCoroutineScheduler,
) : Clock {
    override fun now(): Instant = start + scheduler.currentTime.milliseconds
}

private class FixedClock(
    private val instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}
