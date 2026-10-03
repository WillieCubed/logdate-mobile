package app.logdate.wear.v2.moments

import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ClockDurationTest {
    @Test
    fun `under an hour reads as minutes and seconds`() {
        assertEquals("4:12", formatClockDuration(252.seconds, Locale.US))
    }

    @Test
    fun `an hour or more adds the hours`() {
        assertEquals("1:04:12", formatClockDuration(1.hours + 4.minutes + 12.seconds, Locale.US))
    }

    @Test
    fun `nothing reads as zero`() {
        assertEquals("0:00", formatClockDuration(Duration.ZERO, Locale.US))
    }

    @Test
    fun `a negative length reads as zero`() {
        assertEquals("0:00", formatClockDuration((-5).seconds, Locale.US))
    }

    @Test
    fun `parts of a second are dropped`() {
        assertEquals("0:01", formatClockDuration(1_999.milliseconds, Locale.US))
    }
}
