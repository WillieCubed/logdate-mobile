package app.logdate.client.domain.activity

import app.logdate.client.sync.peer.state.ActivitySnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ActivityAgreementTest {
    private val at = Instant.fromEpochSeconds(1_760_000_000)
    private val walk = Activity(ActivityKind.MOVING, at, null)
    private val drive = Activity(ActivityKind.IN_VEHICLE, at, null)
    private val sleep = Activity(ActivityKind.SLEEP, at, null)

    @Test
    fun `the phone decides vehicles`() {
        assertEquals(drive, agreedActivity(phone = drive, watch = walk, watchWorn = true))
    }

    @Test
    fun `the worn watch decides sleep`() {
        assertEquals(sleep, agreedActivity(phone = walk, watch = sleep, watchWorn = true))
    }

    @Test
    fun `sleep from a watch that is not worn does not count`() {
        assertNull(agreedActivity(phone = null, watch = sleep, watchWorn = false))
    }

    @Test
    fun `a walk the phone sees counts though the watch saw no exercise`() {
        assertEquals(walk, agreedActivity(phone = walk, watch = null, watchWorn = true))
    }

    @Test
    fun `exercise the watch sees counts though the phone stayed behind`() {
        assertEquals(walk, agreedActivity(phone = null, watch = walk, watchWorn = true))
    }

    @Test
    fun `the later ending wins`() {
        val earlier = Activity(ActivityKind.MOVING, at, at + 20.minutes)
        val later = Activity(ActivityKind.IN_VEHICLE, at + 30.minutes, at + 50.minutes)

        assertEquals(later, latestEnded(earlier, later, null))
    }

    @Test
    fun `a log travels to the peer and back`() {
        val log =
            ActivityLog(
                open = OpenActivity(ActivityKind.IN_VEHICLE, at + 60.minutes, leftAt = at + 70.minutes),
                lastEnded = Activity(ActivityKind.MOVING, at, at + 20.minutes),
            )

        val snapshot = log.toSnapshot()

        assertEquals(Activity(ActivityKind.IN_VEHICLE, at + 60.minutes, endedAt = null), snapshot?.current())
        assertEquals(Activity(ActivityKind.MOVING, at, at + 20.minutes), snapshot?.lastEnded())
    }

    @Test
    fun `an empty log sends nothing`() {
        assertNull(ActivityLog().toSnapshot())
    }

    @Test
    fun `a kind this build does not know reads as nothing`() {
        val snapshot = ActivitySnapshot(kind = "SWIMMING", startedAt = 1, lastEndedKind = "SWIMMING", lastStartedAt = 1, lastEndedAt = 2)

        assertNull(snapshot.current())
        assertNull(snapshot.lastEnded())
    }
}
