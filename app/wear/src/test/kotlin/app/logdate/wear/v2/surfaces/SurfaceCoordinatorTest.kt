package app.logdate.wear.v2.surfaces

import app.logdate.wear.v2.today.TodayWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SurfaceCoordinatorTest {
    private val refresher = CountingRefresher()
    private val switch = RecordingSwitch()
    private val flag = MutableStateFlow(false)
    private val pending = MutableStateFlow(emptySet<String>())
    private val days = MutableStateFlow(day(10))

    @Test
    fun `the flag on swaps the surfaces and refreshes once`() =
        runTest {
            start()

            flag.value = true
            runCurrent()

            assertEquals(false, switch.states["OldTile"])
            assertEquals(true, switch.states["NewTile"])
            assertEquals(1, refresher.count)
        }

    @Test
    fun `the flag off restores the current surfaces and stops refreshing`() =
        runTest {
            val updater = start()
            flag.value = true
            runCurrent()

            flag.value = false
            runCurrent()
            updater.refreshNow()

            assertEquals(true, switch.states["OldTile"])
            assertEquals(1, refresher.count)
        }

    @Test
    fun `a growing outbox refreshes at once and a shrinking one does not`() =
        runTest {
            start()
            flag.value = true
            runCurrent()

            pending.value = setOf("moment-v1")
            runCurrent()
            assertEquals(2, refresher.count)
            pending.value = emptySet()
            runCurrent()

            assertEquals(2, refresher.count)
        }

    @Test
    fun `a new day refreshes`() =
        runTest {
            start()
            flag.value = true
            runCurrent()

            days.value = day(11)
            runCurrent()

            assertEquals(2, refresher.count)
        }

    @Test
    fun `editing an already pending moment refreshes despite unchanged count`() =
        runTest {
            start()
            flag.value = true
            runCurrent()
            pending.value = setOf("moment-v1")
            runCurrent()
            pending.value = setOf("moment-v2")
            runCurrent()
            assertEquals(3, refresher.count)
        }

    private fun TestScope.start(): SurfaceUpdater {
        val updater = SurfaceUpdater(refresher = refresher, scope = backgroundScope, clock = SchedulerClock(testScheduler))
        SurfaceCoordinator(
            newAppEnabled = flag,
            components = SurfaceComponents(switch, newAppOnly = listOf("NewTile"), currentAppOnly = listOf("OldTile")),
            updater = updater,
            pendingUploads = pending,
            days = days,
        ).start(backgroundScope)
        runCurrent()
        return updater
    }

    private fun day(date: Int): TodayWindow {
        val zone = TimeZone.UTC
        return TodayWindow(
            date = LocalDate(2026, 10, date),
            timeZone = zone,
            start = LocalDate(2026, 10, date).atStartOfDayIn(zone),
            end = LocalDate(2026, 10, date + 1).atStartOfDayIn(zone),
        )
    }
}
