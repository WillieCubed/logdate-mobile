package app.logdate.client.sync

import app.logdate.client.networking.DataUsageMode
import app.logdate.client.sync.metadata.UploadScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodicSyncScheduleControllerTest {
    @Test
    fun `failed scheduling retries without another state emission`() =
        runTest {
            val targets = MutableStateFlow(target("owner-a"))
            val persisted = mutableListOf<PeriodicSyncScheduleChange>()
            var attempts = 0
            val controller =
                PeriodicSyncScheduleController(isCurrent = { true }) { change ->
                    attempts++
                    if (attempts == 1) throw IOException("Transient storage failure")
                    persisted += change
                }
            backgroundScope.launch { controller.collect(targets) }
            runCurrent()
            assertTrue(persisted.isEmpty())

            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(
                listOf<PeriodicSyncScheduleChange>(PeriodicSyncScheduleChange.Enable(PeriodicNetworkRequirement.CONNECTED)),
                persisted,
            )
        }

    @Test
    fun `owner replacement remains required when its first persistence attempt fails`() =
        runTest {
            val targets = MutableStateFlow(target("owner-a"))
            val persisted = mutableListOf<PeriodicSyncScheduleChange>()
            var failReplacement = true
            val controller =
                PeriodicSyncScheduleController(isCurrent = { true }) { change ->
                    if (change == PeriodicSyncScheduleChange.ResumeOwner && failReplacement) {
                        failReplacement = false
                        throw IOException("Transient cancellation failure")
                    }
                    persisted += change
                }
            backgroundScope.launch { controller.collect(targets) }
            runCurrent()
            targets.value = target("owner-b")
            runCurrent()

            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(
                listOf<PeriodicSyncScheduleChange>(
                    PeriodicSyncScheduleChange.Enable(PeriodicNetworkRequirement.CONNECTED),
                    PeriodicSyncScheduleChange.Enable(PeriodicNetworkRequirement.CONNECTED, ownerChanged = true),
                ),
                persisted,
            )
        }

    @Test
    fun `returning to the prior owner reconciles an interrupted replacement`() =
        runTest {
            val targets = MutableStateFlow(target("owner-a"))
            val persisted = mutableListOf<PeriodicSyncScheduleChange>()
            val started = CompletableDeferred<Unit>()
            val controller =
                PeriodicSyncScheduleController(isCurrent = { true }) { change ->
                    if (change == PeriodicSyncScheduleChange.ResumeOwner) {
                        started.complete(Unit)
                        awaitCancellation()
                    }
                    persisted += change
                }
            backgroundScope.launch { controller.collect(targets) }
            runCurrent()
            targets.value = target("owner-b")
            runCurrent()
            assertTrue(started.isCompleted)

            targets.value = target("owner-a")
            runCurrent()

            assertEquals(PeriodicSyncScheduleChange.Enable(PeriodicNetworkRequirement.CONNECTED, ownerChanged = true), persisted.last())
        }

    @Test
    fun `superseded policy restores a partially persisted periodic constraint`() =
        runTest {
            val targets = MutableStateFlow(target("owner-a"))
            var periodic = PeriodicNetworkRequirement.CONNECTED
            val controller =
                PeriodicSyncScheduleController(isCurrent = { true }) { change ->
                    if (change is PeriodicSyncScheduleChange.Enable) {
                        periodic = change.requirement
                        if (periodic == PeriodicNetworkRequirement.UNMETERED) awaitCancellation()
                    }
                }
            backgroundScope.launch { controller.collect(targets) }
            runCurrent()
            targets.value = target("owner-a", DataUsageMode.Restricted)
            runCurrent()
            assertEquals(PeriodicNetworkRequirement.UNMETERED, periodic)

            targets.value = target("owner-a")
            runCurrent()

            assertEquals(PeriodicNetworkRequirement.CONNECTED, periodic)
        }

    @Test
    fun `sign out supersedes a pending retry immediately`() =
        runTest {
            val targets = MutableStateFlow(target("owner-a"))
            val persisted = mutableListOf<PeriodicSyncScheduleChange>()
            val controller =
                PeriodicSyncScheduleController(isCurrent = { true }) { change ->
                    if (change is PeriodicSyncScheduleChange.Enable) throw IOException("Storage temporarily unavailable")
                    persisted += change
                }
            backgroundScope.launch { controller.collect(targets) }
            runCurrent()

            targets.value = PeriodicSyncScheduleTarget(DataUsageMode.Unrestricted, null)
            runCurrent()
            advanceTimeBy(30_000)
            runCurrent()

            assertEquals(listOf<PeriodicSyncScheduleChange>(PeriodicSyncScheduleChange.Disable), persisted)
        }

    @Test
    fun `an uncertain snapshot stays quiet and resumes without a new emission`() =
        runTest {
            val targets = MutableStateFlow(target("owner-a"))
            val persisted = mutableListOf<PeriodicSyncScheduleChange>()
            var current = false
            val controller = PeriodicSyncScheduleController(isCurrent = { current }) { persisted += it }
            backgroundScope.launch { controller.collect(targets) }
            runCurrent()
            assertTrue(persisted.isEmpty(), "An unhydrated session must not authorize scheduling")
            advanceTimeBy(5_000)
            runCurrent()
            assertTrue(persisted.isEmpty(), "Uncertain state must not be acknowledged or applied")

            current = true
            advanceTimeBy(30_000)
            runCurrent()

            assertEquals(
                listOf<PeriodicSyncScheduleChange>(PeriodicSyncScheduleChange.Enable(PeriodicNetworkRequirement.CONNECTED)),
                persisted,
            )
        }

    @Test
    fun `an obsolete retry waits for a matching current owner`() =
        runTest {
            val targets = MutableStateFlow(target("owner-a"))
            val persisted = mutableListOf<PeriodicSyncScheduleChange>()
            var current = true
            var attempts = 0
            val controller =
                PeriodicSyncScheduleController(isCurrent = { current }) { change ->
                    attempts++
                    if (attempts == 1) {
                        current = false
                        throw IOException("Session storage became unavailable")
                    }
                    persisted += change
                }
            backgroundScope.launch { controller.collect(targets) }
            runCurrent()
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(1, attempts, "Retrying must not act on an obsolete authenticated snapshot")

            current = true
            advanceTimeBy(30_000)
            runCurrent()

            assertEquals(2, attempts)
            assertEquals(
                listOf<PeriodicSyncScheduleChange>(PeriodicSyncScheduleChange.Enable(PeriodicNetworkRequirement.CONNECTED)),
                persisted,
            )
        }

    @Test
    fun `a completed operation is reconciled if its snapshot became obsolete`() =
        runTest {
            val targets = MutableStateFlow(target("owner-a"))
            var current = true
            var applications = 0
            val controller =
                PeriodicSyncScheduleController(isCurrent = { current }) { _ ->
                    applications++
                    if (applications == 1) current = false
                }
            backgroundScope.launch { controller.collect(targets) }
            runCurrent()
            assertEquals(1, applications)

            current = true
            advanceTimeBy(30_000)
            runCurrent()

            assertEquals(2, applications, "A superseded snapshot must not be acknowledged as reconciled")
        }

    @Test
    fun `same owner reauthentication retries replacement without replaying old work`() =
        runTest {
            val targets = MutableStateFlow(target("owner-a"))
            val persisted = mutableListOf<PeriodicSyncScheduleChange>()
            var failReplacement = true
            val controller =
                PeriodicSyncScheduleController(isCurrent = { true }) { change ->
                    if (change is PeriodicSyncScheduleChange.Enable && change.ownerChanged && failReplacement) {
                        failReplacement = false
                        throw IOException("Transient replacement failure")
                    }
                    persisted += change
                }
            backgroundScope.launch { controller.collect(targets) }
            runCurrent()
            targets.value = PeriodicSyncScheduleTarget(DataUsageMode.Unrestricted, null)
            runCurrent()
            targets.value = target("owner-a")
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(
                listOf<PeriodicSyncScheduleChange>(
                    PeriodicSyncScheduleChange.Enable(PeriodicNetworkRequirement.CONNECTED),
                    PeriodicSyncScheduleChange.Disable,
                    PeriodicSyncScheduleChange.Enable(PeriodicNetworkRequirement.CONNECTED, ownerChanged = true),
                ),
                persisted,
            )
        }

    private fun target(
        owner: String,
        mode: DataUsageMode = DataUsageMode.Unrestricted,
    ) = PeriodicSyncScheduleTarget(mode, UploadScope(owner, "https://journal.example"))
}
