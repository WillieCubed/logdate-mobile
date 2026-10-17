package app.logdate.client.sync

import app.logdate.client.networking.DataUsageMode
import kotlin.test.Test
import kotlin.test.assertEquals

class PeriodicSyncScheduleDeciderTest {
    @Test
    fun `returning from metered to unrestricted wakes recovery immediately`() {
        val decider = PeriodicSyncScheduleDecider()
        decider.next(true, DataUsageMode.Conservative)
        kotlin.test.assertTrue(decider.next(true, DataUsageMode.Unrestricted) != PeriodicSyncScheduleChange.Unchanged)
        kotlin.test.assertEquals(PeriodicSyncScheduleChange.Unchanged, decider.next(true, DataUsageMode.Unrestricted))
    }

    @Test
    fun `sign in schedules once and unchanged network policy leaves it running`() {
        val decider = PeriodicSyncScheduleDecider()

        assertEquals(
            PeriodicSyncScheduleChange.Enable(PeriodicNetworkRequirement.CONNECTED),
            decider.next(authenticated = true, mode = DataUsageMode.Unrestricted),
        )
        assertEquals(
            PeriodicSyncScheduleChange.Unchanged,
            decider.next(authenticated = true, mode = DataUsageMode.Unrestricted),
        )
    }

    @Test
    fun `network policy changes update the schedule only while signed in`() {
        val decider = PeriodicSyncScheduleDecider()

        assertEquals(PeriodicSyncScheduleChange.Disable, decider.next(false, DataUsageMode.Unrestricted))
        assertEquals(PeriodicSyncScheduleChange.Unchanged, decider.next(false, DataUsageMode.Restricted))
        assertEquals(
            PeriodicSyncScheduleChange.Enable(PeriodicNetworkRequirement.UNMETERED),
            decider.next(true, DataUsageMode.Restricted),
        )
        assertEquals(
            PeriodicSyncScheduleChange.Enable(PeriodicNetworkRequirement.CONNECTED),
            decider.next(true, DataUsageMode.Conservative),
        )
        assertEquals(PeriodicSyncScheduleChange.Disable, decider.next(false, DataUsageMode.Conservative))
    }
}
