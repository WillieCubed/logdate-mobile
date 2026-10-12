package app.logdate.client.domain.reminders

import app.logdate.client.sync.peer.state.Presence
import app.logdate.client.sync.peer.state.PresenceState
import kotlin.test.Test
import kotlin.test.assertEquals

class NudgeTargetPolicyTest {
    @Test
    fun wornReachableWatchReceivesTheReminder() {
        assertEquals(NudgeTarget.WATCH, choose())
    }

    @Test
    fun unknownOrOffWristWatchLeavesTheReminderOnPhone() {
        for (state in listOf(PresenceState.UNKNOWN, PresenceState.OFF_WRIST)) {
            assertEquals(NudgeTarget.PHONE, choose(presence = Presence(state, 10_000)))
        }
    }

    @Test
    fun unreachableOrUnsupportedWatchLeavesTheReminderOnPhone() {
        assertEquals(NudgeTarget.PHONE, choose(reachable = false))
        assertEquals(NudgeTarget.PHONE, choose(supported = false))
    }

    @Test
    fun staleFutureOrAbsentReadingDoesNotRingTheWatch() {
        for (observedAt in listOf(0L, 7_999L, 10_001L)) {
            assertEquals(NudgeTarget.PHONE, choose(presence = Presence(PresenceState.ON_WRIST, observedAt)))
        }
    }

    @Test
    fun readingAtTheTwoSecondBoundaryStillRingsTheWatch() {
        assertEquals(NudgeTarget.WATCH, choose(presence = Presence(PresenceState.ON_WRIST, 8_000)))
    }

    private fun choose(
        reachable: Boolean = true,
        supported: Boolean = true,
        presence: Presence = Presence(PresenceState.ON_WRIST, 10_000),
    ) = NudgeTargetPolicy.choose(reachable, supported, presence, now = 10_000)
}
