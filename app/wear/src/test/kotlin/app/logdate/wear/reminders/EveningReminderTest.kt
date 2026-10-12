package app.logdate.wear.reminders

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class EveningReminderTest {
    @Test fun `night owl evening check crosses midnight`() {
        val learned =
            app.logdate.client.domain.reminders
                .WakingHours(kotlinx.datetime.LocalTime(9, 0), kotlinx.datetime.LocalTime(2, 0))
        assertEquals(berlin(11, 0), nextEveningCheck(berlin(10, 21), REMINDER_ZONE, learned))
    }

    @Test fun `learned evening preserves elapsed lead across daylight saving transition`() {
        val learned =
            app.logdate.client.domain.reminders
                .WakingHours(kotlinx.datetime.LocalTime(9, 0), kotlinx.datetime.LocalTime(4, 0))
        val now = LocalDateTime(2026, 10, 24, 21, 0).toInstant(REMINDER_ZONE)
        val sleep = LocalDateTime(2026, 10, 25, 4, 0).toInstant(REMINDER_ZONE)
        assertEquals(sleep - 120.minutes, nextEveningCheck(now, REMINDER_ZONE, learned))
    }

    @Test
    fun `before 20 30 the check runs tonight`() {
        assertEquals(berlin(10, 20, 30), nextEveningCheck(berlin(10, 18), REMINDER_ZONE))
    }

    @Test
    fun `after 20 30 the check runs tomorrow`() {
        assertEquals(berlin(11, 20, 30), nextEveningCheck(berlin(10, 21), REMINDER_ZONE))
    }

    @Test
    fun `across a daylight saving change the check is still at 20 30 local`() {
        val beforeChange = LocalDateTime(2026, 10, 24, 21, 0).toInstant(REMINDER_ZONE)

        assertEquals(LocalDateTime(2026, 10, 25, 20, 30).toInstant(REMINDER_ZONE), nextEveningCheck(beforeChange, REMINDER_ZONE))
    }

    @Test
    fun `scheduling waits until the next check`() {
        val work = FakeEveningWork()

        EveningReminderScheduler(work, FixedClock(berlin(10, 18)), timeZone = { REMINDER_ZONE }).scheduleNext()

        assertEquals(listOf(150.minutes.inWholeMilliseconds), work.delays)
    }
}
