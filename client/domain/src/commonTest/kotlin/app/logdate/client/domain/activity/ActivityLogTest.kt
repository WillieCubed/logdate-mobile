package app.logdate.client.domain.activity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ActivityLogTest {
    private val start = Instant.fromEpochSeconds(1_760_000_000)

    @Test
    fun `a walk long enough ends after its grace period`() {
        val log =
            ActivityLog()
                .accept(ActivityTransition(ActivityKind.MOVING, entered = true, at = start))
                .accept(ActivityTransition(ActivityKind.MOVING, entered = false, at = start + 40.minutes))
        assertEquals(Activity(ActivityKind.MOVING, start, endedAt = null), log.current)
        assertEquals(start + 43.minutes, log.settlesAt())

        val settled = log.settle(start + 43.minutes)

        assertNull(settled.current)
        assertEquals(Activity(ActivityKind.MOVING, start, start + 40.minutes), settled.lastEnded)
    }

    @Test
    fun `a walk is still under way inside its grace period`() {
        val log =
            ActivityLog()
                .accept(ActivityTransition(ActivityKind.MOVING, entered = true, at = start))
                .accept(ActivityTransition(ActivityKind.MOVING, entered = false, at = start + 40.minutes))
                .settle(start + 42.minutes)

        assertEquals(Activity(ActivityKind.MOVING, start, endedAt = null), log.current)
        assertNull(log.lastEnded)
    }

    @Test
    fun `stopping briefly does not end a drive`() {
        val log =
            ActivityLog()
                .accept(ActivityTransition(ActivityKind.IN_VEHICLE, entered = true, at = start))
                .accept(ActivityTransition(ActivityKind.IN_VEHICLE, entered = false, at = start + 10.minutes))
                .settle(start + 12.minutes)
                .accept(ActivityTransition(ActivityKind.IN_VEHICLE, entered = true, at = start + 12.minutes))

        assertEquals(Activity(ActivityKind.IN_VEHICLE, start, endedAt = null), log.current)
        assertNull(log.settlesAt())
    }

    @Test
    fun `an activity shorter than its minimum is forgotten`() {
        val log =
            ActivityLog()
                .accept(ActivityTransition(ActivityKind.MOVING, entered = true, at = start))
                .accept(ActivityTransition(ActivityKind.MOVING, entered = false, at = start + 4.minutes))
                .settle(start + 7.minutes)

        assertNull(log.current)
        assertNull(log.lastEnded)
    }

    @Test
    fun `a short activity leaves the last one that counted`() {
        val walk = Activity(ActivityKind.MOVING, start - 2.hours, start - 1.hours)
        val log =
            ActivityLog(lastEnded = walk)
                .accept(ActivityTransition(ActivityKind.IN_VEHICLE, entered = true, at = start))
                .accept(ActivityTransition(ActivityKind.IN_VEHICLE, entered = false, at = start + 2.minutes))
                .settle(start + 6.minutes)

        assertEquals(walk, log.lastEnded)
    }

    @Test
    fun `a new kind ends the open activity where it was left`() {
        val log =
            ActivityLog()
                .accept(ActivityTransition(ActivityKind.MOVING, entered = true, at = start))
                .accept(ActivityTransition(ActivityKind.MOVING, entered = false, at = start + 15.minutes))
                .accept(ActivityTransition(ActivityKind.IN_VEHICLE, entered = true, at = start + 16.minutes))

        assertEquals(Activity(ActivityKind.MOVING, start, start + 15.minutes), log.lastEnded)
        assertEquals(Activity(ActivityKind.IN_VEHICLE, start + 16.minutes, endedAt = null), log.current)
    }

    @Test
    fun `a new kind without an exit ends the open activity when the new one starts`() {
        val log =
            ActivityLog()
                .accept(ActivityTransition(ActivityKind.MOVING, entered = true, at = start))
                .accept(ActivityTransition(ActivityKind.IN_VEHICLE, entered = true, at = start + 20.minutes))

        assertEquals(Activity(ActivityKind.MOVING, start, start + 20.minutes), log.lastEnded)
    }

    @Test
    fun `an exit for another kind is ignored`() {
        val log =
            ActivityLog()
                .accept(ActivityTransition(ActivityKind.MOVING, entered = true, at = start))
                .accept(ActivityTransition(ActivityKind.IN_VEHICLE, entered = false, at = start + 5.minutes))

        assertNull(log.settlesAt())
        assertEquals(Activity(ActivityKind.MOVING, start, endedAt = null), log.current)
    }

    @Test
    fun `an exit with nothing under way is ignored`() {
        val log = ActivityLog().accept(ActivityTransition(ActivityKind.MOVING, entered = false, at = start))

        assertEquals(ActivityLog(), log)
    }

    @Test
    fun `sleep must last hours to count`() {
        val nap =
            ActivityLog()
                .accept(ActivityTransition(ActivityKind.SLEEP, entered = true, at = start))
                .accept(ActivityTransition(ActivityKind.SLEEP, entered = false, at = start + 40.minutes))
                .settle(start + 2.hours)
        val night =
            ActivityLog()
                .accept(ActivityTransition(ActivityKind.SLEEP, entered = true, at = start))
                .accept(ActivityTransition(ActivityKind.SLEEP, entered = false, at = start + 7.hours))
                .settle(start + 8.hours)

        assertNull(nap.lastEnded)
        assertEquals(Activity(ActivityKind.SLEEP, start, start + 7.hours), night.lastEnded)
    }
}
