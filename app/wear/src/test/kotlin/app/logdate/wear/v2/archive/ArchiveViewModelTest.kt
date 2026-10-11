package app.logdate.wear.v2.archive

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
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveViewModelTest {
    private val zone = TimeZone.of("Europe/Berlin")
    private val saturday =
        TodayWindow(
            date = LocalDate(2026, 10, 3),
            timeZone = zone,
            start = LocalDate(2026, 10, 3).atStartOfDayIn(zone),
            end = LocalDate(2026, 10, 4).atStartOfDayIn(zone),
        )
    private val windowStart = LocalDate(2026, 9, 20).atStartOfDayIn(zone)
    private val recentStart = LocalDate(2026, 9, 27).atStartOfDayIn(zone)
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
    fun `earlier moments come a day at a time with the newest day first`() =
        runTest {
            val morning = voice(at(LocalDateTime(2026, 9, 21, 8, 0)))
            val evening = text("Dinner on the roof", at(LocalDateTime(2026, 9, 21, 20, 0)))
            val thursday = text("Long call", at(LocalDateTime(2026, 9, 24, 21, 0)))
            val checkIn = text("#mood:ok Fine", at(LocalDateTime(2026, 9, 24, 7, 0)))
            every { notesRepository.observeNotesInRange(windowStart, recentStart) } returns
                flowOf(listOf(morning, checkIn, evening, thursday))

            ArchiveViewModel(dayClock, notesRepository).uiState.test {
                assertEquals(
                    ArchiveUiState.Ready(
                        timeZone = zone,
                        days =
                            listOf(
                                ArchiveDay(LocalDate(2026, 9, 24), listOf(thursday)),
                                ArchiveDay(LocalDate(2026, 9, 21), listOf(evening, morning)),
                            ),
                    ),
                    expectMostRecentItem(),
                )
            }
        }

    @Test
    fun `nothing earlier is an empty archive`() =
        runTest {
            every { notesRepository.observeNotesInRange(windowStart, recentStart) } returns flowOf(emptyList())

            ArchiveViewModel(dayClock, notesRepository).uiState.test {
                assertEquals(ArchiveUiState.Ready(timeZone = zone, days = emptyList()), expectMostRecentItem())
            }
        }

    @Test
    fun `a failure reading notes shows the failed state`() =
        runTest {
            every { notesRepository.observeNotesInRange(any(), any()) } returns flow { throw IOException("database closed") }

            ArchiveViewModel(dayClock, notesRepository).uiState.test {
                assertEquals(ArchiveUiState.Failed, expectMostRecentItem())
            }
        }

    private fun at(local: LocalDateTime): Instant = local.toInstant(zone)

    private fun text(
        content: String,
        at: Instant,
    ) = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = content)

    private fun voice(at: Instant) = JournalNote.Audio(mediaRef = "v.m4a", durationMs = 30_000L, creationTimestamp = at, lastUpdated = at)
}
