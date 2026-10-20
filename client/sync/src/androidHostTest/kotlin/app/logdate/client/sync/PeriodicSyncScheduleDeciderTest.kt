package app.logdate.client.sync

import app.logdate.client.networking.DataUsageMode
import app.logdate.client.sync.metadata.UploadScope
import kotlin.test.Test
import kotlin.test.assertEquals

class PeriodicSyncScheduleDeciderTest {
    @Test
    fun `returning from metered to unrestricted wakes recovery immediately`() {
        val decider = PeriodicSyncScheduleDecider()
        decider.persistedNext(true, DataUsageMode.Conservative)
        kotlin.test.assertTrue(decider.persistedNext(true, DataUsageMode.Unrestricted) != PeriodicSyncScheduleChange.Unchanged)
        kotlin.test.assertEquals(PeriodicSyncScheduleChange.Unchanged, decider.persistedNext(true, DataUsageMode.Unrestricted))
    }

    @Test
    fun `sign in schedules once and unchanged network policy leaves it running`() {
        val decider = PeriodicSyncScheduleDecider()

        assertEquals(
            PeriodicSyncScheduleChange.Enable(PeriodicNetworkRequirement.CONNECTED),
            decider.persistedNext(authenticated = true, mode = DataUsageMode.Unrestricted),
        )
        assertEquals(
            PeriodicSyncScheduleChange.Unchanged,
            decider.persistedNext(authenticated = true, mode = DataUsageMode.Unrestricted),
        )
    }

    @Test
    fun `network policy changes update the schedule only while signed in`() {
        val decider = PeriodicSyncScheduleDecider()

        assertEquals(PeriodicSyncScheduleChange.Disable, decider.persistedNext(false, DataUsageMode.Unrestricted))
        assertEquals(PeriodicSyncScheduleChange.Unchanged, decider.persistedNext(false, DataUsageMode.Restricted))
        assertEquals(
            PeriodicSyncScheduleChange.Enable(PeriodicNetworkRequirement.UNMETERED, ownerChanged = true),
            decider.persistedNext(true, DataUsageMode.Restricted),
        )
        assertEquals(
            PeriodicSyncScheduleChange.Enable(PeriodicNetworkRequirement.CONNECTED),
            decider.persistedNext(true, DataUsageMode.Conservative),
        )
        assertEquals(PeriodicSyncScheduleChange.Disable, decider.persistedNext(false, DataUsageMode.Conservative))
    }

    @Test
    fun `a direct account switch resumes recovery without replacing the periodic schedule`() {
        val decider = PeriodicSyncScheduleDecider()
        decider.persistedNext(true, DataUsageMode.Unrestricted, UploadScope("owner-a", "https://one.example"))
        assertEquals(
            PeriodicSyncScheduleChange.ResumeOwner,
            decider.persistedNext(true, DataUsageMode.Unrestricted, UploadScope("owner-b", "https://one.example")),
        )
        assertEquals(
            PeriodicSyncScheduleChange.Unchanged,
            decider.persistedNext(true, DataUsageMode.Unrestricted, UploadScope("owner-b", "https://one.example")),
        )
    }

    @Test
    fun `the same account at a different server resumes recovery`() {
        val decider = PeriodicSyncScheduleDecider()
        decider.persistedNext(true, DataUsageMode.Conservative, UploadScope("owner", "https://one.example"))
        assertEquals(
            PeriodicSyncScheduleChange.ResumeOwner,
            decider.persistedNext(true, DataUsageMode.Conservative, UploadScope("owner", "https://two.example")),
        )
    }

    @Test
    fun `signing out cannot hide a later switch to another account`() {
        val decider = PeriodicSyncScheduleDecider()
        decider.persistedNext(true, DataUsageMode.Unrestricted, UploadScope("owner-a", "https://one.example"))
        decider.persistedNext(false, DataUsageMode.Unrestricted)
        assertEquals(
            PeriodicSyncScheduleChange.Enable(PeriodicNetworkRequirement.CONNECTED, ownerChanged = true),
            decider.persistedNext(true, DataUsageMode.Unrestricted, UploadScope("owner-b", "https://one.example")),
        )
    }

    @Test
    fun `simultaneous owner and network policy changes retain the owner transition`() {
        val decider = PeriodicSyncScheduleDecider()
        decider.persistedNext(true, DataUsageMode.Restricted, UploadScope("owner-a", "https://one.example"))
        assertEquals(
            PeriodicSyncScheduleChange.Enable(PeriodicNetworkRequirement.CONNECTED, ownerChanged = true),
            decider.persistedNext(true, DataUsageMode.Unrestricted, UploadScope("owner-b", "https://one.example")),
        )
    }

    @Test
    fun `an unpersisted enable remains eligible without another owner change`() {
        val decider = PeriodicSyncScheduleDecider()
        val owner = UploadScope("owner-a", "https://one.example")
        val expected = PeriodicSyncScheduleChange.Enable(PeriodicNetworkRequirement.CONNECTED)

        assertEquals(expected, decider.next(true, DataUsageMode.Unrestricted, owner))
        assertEquals(expected, decider.next(true, DataUsageMode.Unrestricted, owner))
    }

    @Test
    fun `an unpersisted disable remains eligible without another auth emission`() {
        val decider = PeriodicSyncScheduleDecider()

        assertEquals(PeriodicSyncScheduleChange.Disable, decider.next(false, DataUsageMode.Unrestricted))
        assertEquals(PeriodicSyncScheduleChange.Disable, decider.next(false, DataUsageMode.Unrestricted))
    }

    @Test
    fun `returning to the same owner after sign out replaces obsolete work`() {
        val decider = PeriodicSyncScheduleDecider()
        val owner = UploadScope("owner-a", "https://one.example")
        decider.persistedNext(true, DataUsageMode.Unrestricted, owner)
        decider.persistedNext(false, DataUsageMode.Unrestricted)

        repeat(2) {
            assertEquals(
                PeriodicSyncScheduleChange.Enable(PeriodicNetworkRequirement.CONNECTED, ownerChanged = true),
                decider.next(true, DataUsageMode.Unrestricted, owner),
            )
        }
        decider.acknowledge(true, DataUsageMode.Unrestricted, owner)
        assertEquals(PeriodicSyncScheduleChange.Unchanged, decider.next(true, DataUsageMode.Unrestricted, owner))
    }

    @Test
    fun `first sign in after confirmed absence replaces unauthenticated work`() {
        val decider = PeriodicSyncScheduleDecider()
        decider.persistedNext(false, DataUsageMode.Unrestricted)

        assertEquals(
            PeriodicSyncScheduleChange.Enable(PeriodicNetworkRequirement.CONNECTED, ownerChanged = true),
            decider.next(true, DataUsageMode.Unrestricted, UploadScope("owner-a", "https://one.example")),
        )
    }

    private fun PeriodicSyncScheduleDecider.persistedNext(
        authenticated: Boolean,
        mode: DataUsageMode,
        ownerScope: UploadScope? = null,
    ): PeriodicSyncScheduleChange =
        next(authenticated, mode, ownerScope).also {
            acknowledge(authenticated, mode, ownerScope)
        }
}
