package app.logdate.wear.v2.memories

import app.cash.turbine.test
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.v2.today.DayClock
import app.logdate.wear.v2.today.TodayWindow
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MemoriesViewModelTest {
    private val zone = TimeZone.UTC
    private val today =
        TodayWindow(
            date = LocalDate(2026, 10, 10),
            timeZone = zone,
            start = LocalDate(2026, 10, 10).atStartOfDayIn(zone),
            end = LocalDate(2026, 10, 11).atStartOfDayIn(zone),
        )
    private val dayClock = mockk<DayClock> { every { observe() } returns flowOf(today) }
    private val finder = mockk<OnThisDayFinder>()
    private val rewindSource = mockk<WeeklyRewindSource>()
    private val at = Instant.fromEpochMilliseconds(1_760_000_000_000)
    private val note = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Beach day")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `both cards come from today's date`() =
        runTest {
            val onThisDay = OnThisDay(LocalDate(2025, 10, 10), yearsAgo = 1, leadMoment = note, momentCount = 1, summary = "Beach day")
            val rewind = WeeklyRewind("A quiet week", note)
            coEvery { finder.find(today.date) } returns onThisDay
            every { rewindSource.observe(today) } returns flowOf(rewind)

            MemoriesViewModel(dayClock, finder, rewindSource).cards.test {
                assertEquals(MemoryCards(onThisDay, rewind), expectMostRecentItem())
            }
        }

    @Test
    fun `a failure shows no cards`() =
        runTest {
            coEvery { finder.find(any()) } throws IOException("database closed")
            every { rewindSource.observe(any()) } returns flowOf(null)

            MemoriesViewModel(dayClock, finder, rewindSource).cards.test {
                assertEquals(MemoryCards(), expectMostRecentItem())
            }
        }
}
