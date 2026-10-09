package app.logdate.wear.v2.flow

import app.cash.turbine.test
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.v2.today.DayClock
import app.logdate.wear.v2.today.TodayWindow
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FlowViewModelTest {
    private val zone = TimeZone.of("Europe/Berlin")
    private val saturday =
        TodayWindow(
            date = LocalDate(2026, 10, 3),
            timeZone = zone,
            start = LocalDate(2026, 10, 3).atStartOfDayIn(zone),
            end = LocalDate(2026, 10, 4).atStartOfDayIn(zone),
        )
    private val weekStart = LocalDate(2026, 9, 27).atStartOfDayIn(zone)
    private val dayClock = mockk<DayClock> { every { observe() } returns flowOf(saturday) }
    private val notesRepository = mockk<JournalNotesRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the list covers today and the 6 days before it`() =
        runTest {
            every { notesRepository.observeNotesInRange(weekStart, saturday.end) } returns flowOf(emptyList())

            FlowViewModel(dayClock, notesRepository).uiState.test {
                assertEquals(FlowUiState.Ready(timeZone = zone, moments = emptyList()), expectMostRecentItem())
            }
        }

    @Test
    fun `moments are newest first and check-ins are left out`() =
        runTest {
            val monday = text("Monday walk", at(LocalDateTime(2026, 9, 28, 9, 0)))
            val friday = text("Friday dinner", at(LocalDateTime(2026, 10, 2, 20, 0)))
            val checkIn = text("#mood:ok Feeling ok", at(LocalDateTime(2026, 10, 3, 8, 0)))
            every { notesRepository.observeNotesInRange(weekStart, saturday.end) } returns flowOf(listOf(monday, checkIn, friday))

            FlowViewModel(dayClock, notesRepository).uiState.test {
                assertEquals(listOf<JournalNote>(friday, monday), (expectMostRecentItem() as FlowUiState.Ready).moments)
            }
        }

    @Test
    fun `notes from before the 7 days are left out`() =
        runTest {
            val older = text("Last week", weekStart - 1.hours)
            val sunday = text("Sunday", at(LocalDateTime(2026, 9, 27, 10, 0)))
            every { notesRepository.observeNotesInRange(weekStart, saturday.end) } returns flowOf(listOf(older, sunday))

            FlowViewModel(dayClock, notesRepository).uiState.test {
                assertEquals(listOf<JournalNote>(sunday), (expectMostRecentItem() as FlowUiState.Ready).moments)
            }
        }

    @Test
    fun `a failure reading notes shows the failed state`() =
        runTest {
            every { notesRepository.observeNotesInRange(any(), any()) } returns flow { throw IOException("database closed") }

            FlowViewModel(dayClock, notesRepository).uiState.test {
                assertEquals(FlowUiState.Failed, expectMostRecentItem())
            }
        }

    private fun at(local: LocalDateTime): Instant = local.toInstant(zone)

    private fun text(
        content: String,
        at: Instant,
    ) = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = content)
}
