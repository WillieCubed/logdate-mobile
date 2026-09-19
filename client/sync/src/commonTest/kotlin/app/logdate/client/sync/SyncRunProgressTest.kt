package app.logdate.client.sync

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * What the backup notification reports as a sync run moves along.
 *
 * The notification used to say "Starting…" for the whole of a backup, however long it took, so a
 * healthy backup of several hundred entries looked exactly like one that had stopped.
 */
class SyncRunProgressTest {
    private val period = 500.milliseconds

    private fun status(
        total: Int?,
        completed: Int,
    ) = SyncStatus(
        isEnabled = true,
        lastSyncTime = null,
        pendingUploads = 0,
        isSyncing = true,
        hasErrors = false,
        totalForRun = total,
        completedInRun = completed,
    )

    /** Emits each status, waiting [gap] after each one, then long enough for a last sample. */
    private fun statuses(
        vararg values: SyncStatus,
        gap: Duration = period * 2,
    ): Flow<SyncStatus> =
        flow {
            values.forEach {
                emit(it)
                delay(gap)
            }
            delay(period * 2)
        }

    @Test
    fun `each step of a run is reported`() =
        runTest {
            val initial = status(total = null, completed = 0)

            val updates =
                statuses(initial, status(4, 0), status(4, 1), status(4, 2))
                    .runProgressUpdates(since = initial, period = period)
                    .toList()

            assertEquals(listOf(SyncRunProgress(0, 4), SyncRunProgress(1, 4), SyncRunProgress(2, 4)), updates)
        }

    @Test
    fun `progress left over from an earlier run is not shown`() =
        runTest {
            val finishedEarlier = status(total = 5, completed = 5)

            val updates =
                statuses(finishedEarlier, finishedEarlier, status(3, 0), status(3, 1))
                    .runProgressUpdates(since = finishedEarlier, period = period)
                    .toList()

            assertEquals(listOf(SyncRunProgress(0, 3), SyncRunProgress(1, 3)), updates)
        }

    @Test
    fun `a burst of progress is reported at most once per period and ends on the latest`() =
        runTest {
            val initial = status(total = null, completed = 0)
            val burst = (0..100).map { status(total = 100, completed = it) }.toTypedArray()

            val updates =
                statuses(initial, *burst, gap = 10.milliseconds)
                    .runProgressUpdates(since = initial, period = period)
                    .toList()

            assertTrue(updates.size <= 4, "${updates.size} notification updates for about one second of progress")
            assertEquals(SyncRunProgress(100, 100), updates.last())
        }

    @Test
    fun `a run with nothing to upload reports nothing`() =
        runTest {
            val initial = status(total = 2, completed = 2)

            val updates =
                statuses(initial, status(total = null, completed = 0), status(total = 0, completed = 0))
                    .runProgressUpdates(since = initial, period = period)
                    .toList()

            assertEquals(emptyList(), updates)
        }

    @Test
    fun `completed never runs past the total`() =
        runTest {
            val initial = status(total = null, completed = 0)

            val updates =
                statuses(initial, status(total = 5, completed = 6))
                    .runProgressUpdates(since = initial, period = period)
                    .toList()

            assertEquals(listOf(SyncRunProgress(5, 5)), updates)
        }
}
