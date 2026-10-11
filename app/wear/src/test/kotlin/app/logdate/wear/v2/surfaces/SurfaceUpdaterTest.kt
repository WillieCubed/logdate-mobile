package app.logdate.wear.v2.surfaces

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class SurfaceUpdaterTest {
    private val refresher = CountingRefresher()

    @Test
    fun `nothing refreshes while inactive`() =
        runTest {
            val updater = updater()

            updater.refreshNow()
            updater.refreshForSync()
            advanceTimeBy(5.minutes)
            runCurrent()

            assertEquals(0, refresher.count)
        }

    @Test
    fun `a change on the watch refreshes at once`() =
        runTest {
            val updater = updater().also { it.setActive(true) }

            updater.refreshNow()

            assertEquals(1, refresher.count)
        }

    @Test
    fun `a sync change soon after a refresh waits until 5 minutes have passed`() =
        runTest {
            val updater = updater().also { it.setActive(true) }
            updater.refreshNow()
            advanceTimeBy(1.minutes)

            updater.refreshForSync()
            runCurrent()
            assertEquals(1, refresher.count)
            advanceTimeBy(4.minutes)
            runCurrent()

            assertEquals(2, refresher.count)
        }

    @Test
    fun `a burst of sync changes refreshes once and then once more after 5 minutes`() =
        runTest {
            val updater = updater().also { it.setActive(true) }

            repeat(3) { updater.refreshForSync() }
            runCurrent()
            assertEquals(1, refresher.count)
            advanceTimeBy(1.minutes)
            repeat(2) { updater.refreshForSync() }
            advanceTimeBy(4.minutes)
            runCurrent()

            assertEquals(2, refresher.count)
            assertEquals(5.minutes.inWholeMilliseconds, testScheduler.currentTime)
        }

    @Test
    fun `a change on the watch replaces a waiting sync refresh`() =
        runTest {
            val updater = updater().also { it.setActive(true) }
            updater.refreshNow()
            updater.refreshForSync()

            updater.refreshNow()
            advanceTimeBy(5.minutes)
            runCurrent()

            assertEquals(2, refresher.count)
        }

    @Test
    fun `turning inactive drops a waiting refresh`() =
        runTest {
            val updater = updater().also { it.setActive(true) }
            updater.refreshNow()
            updater.refreshForSync()

            updater.setActive(false)
            advanceTimeBy(5.minutes)
            runCurrent()

            assertEquals(1, refresher.count)
        }

    private fun TestScope.updater() = SurfaceUpdater(refresher = refresher, scope = backgroundScope, clock = SchedulerClock(testScheduler))
}
