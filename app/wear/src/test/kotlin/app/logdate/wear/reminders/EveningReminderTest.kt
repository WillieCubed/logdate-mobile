package app.logdate.wear.reminders

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class EveningReminderTest {
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
