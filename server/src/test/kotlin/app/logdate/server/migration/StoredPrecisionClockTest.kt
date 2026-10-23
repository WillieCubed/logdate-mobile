package app.logdate.server.migration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class StoredPrecisionClockTest {
    @Test
    fun `a nanosecond reading is truncated to the microsecond PostgreSQL stores`() {
        val clock = StoredPrecisionClock(TestClock(Instant.fromEpochSeconds(1_790_000_000, 123_456_999)))

        assertEquals(Instant.fromEpochSeconds(1_790_000_000, 123_456_000), clock.now())
    }

    @Test
    fun `a reading already at microsecond precision is unchanged`() {
        val at = Instant.fromEpochSeconds(1_790_000_000, 999_999_000)

        assertEquals(at, StoredPrecisionClock(TestClock(at)).now())
    }
}
