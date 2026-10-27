package app.logdate.client.domain.rewind

import app.logdate.shared.model.Rewind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class GetCurrentWeekRewindUseCaseTest {
    private val now = Instant.parse("2026-10-26T18:00:00Z")
    private val start = Instant.parse("2026-10-26T07:00:00Z")
    private val calls = mutableListOf<RewindParams>()
    private val saved = MutableStateFlow<List<Rewind>>(emptyList())

    private fun subject(firstDay: DayOfWeek = DayOfWeek.MONDAY) =
        GetCurrentWeekRewindUseCase(
            getRewind = { params ->
                calls += params
                flowOf(RewindQueryResult.Success(snapshot(params.start, params.end)))
            },
            observeRewinds = { _, _ -> saved },
            firstDayOfWeekPreference = flowOf(firstDay),
            clock =
                object : Clock {
                    override fun now() = now
                },
            timeZone = { TimeZone.of("America/Los_Angeles") },
        )

    @Test
    fun firstWeekCanGenerateThroughNowWithoutACompletedWeek() =
        runTest {
            val result = subject()().first()

            assertIs<RewindQueryResult.Success>(result)
            assertEquals(listOf(RewindParams(start, now)), calls)
        }

    @Test
    fun reopeningReusesTheSavedSnapshotInsteadOfCreatingAnotherCutoff() =
        runTest {
            val cutoff = Instant.parse("2026-10-26T16:00:00Z")
            saved.value = listOf(snapshot(start, cutoff))

            val result = subject()().first()

            assertEquals(saved.value.single().uid, assertIs<RewindQueryResult.Success>(result).rewind.uid)
            assertEquals(emptyList(), calls)
        }

    @Test
    fun latestSnapshotWinsWithoutUsingOtherWeeksOrFutureContent() =
        runTest {
            val cutoff = Instant.parse("2026-10-26T17:00:00Z")
            saved.value =
                listOf(
                    snapshot(start, Instant.parse("2026-10-26T15:00:00Z")),
                    snapshot(Instant.parse("2026-10-19T07:00:00Z"), cutoff),
                    snapshot(start, cutoff),
                    snapshot(start, Instant.parse("2026-10-27T18:00:00Z")),
                )

            val result = subject()().first()

            assertEquals(cutoff, assertIs<RewindQueryResult.Success>(result).rewind.endDate)
            assertEquals(emptyList(), calls)
        }

    @Test
    fun chosenWeekStartUsesLocalMidnight() =
        runTest {
            subject(DayOfWeek.SUNDAY)().first()

            assertEquals(Instant.parse("2026-10-25T07:00:00Z"), calls.single().start)
            assertEquals(now, calls.single().end)
        }

    @Test
    fun updatedSnapshotRemainsObservableAfterTheOriginalCutoff() =
        runTest {
            var liveNow = now
            val results = mutableListOf<RewindQueryResult>()
            val subject =
                GetCurrentWeekRewindUseCase(
                    getRewind = { params ->
                        calls += params
                        flowOf(RewindQueryResult.Success(snapshot(params.start, params.end)))
                    },
                    observeRewinds = { observedStart, observedEnd ->
                        assertEquals(start, observedStart)
                        assertEquals(Instant.parse("2026-11-02T08:00:00Z"), observedEnd)
                        saved
                    },
                    firstDayOfWeekPreference = flowOf(DayOfWeek.MONDAY),
                    clock =
                        object : Clock {
                            override fun now() = liveNow
                        },
                    timeZone = { TimeZone.of("America/Los_Angeles") },
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { subject().collect { results += it } }
            liveNow = Instant.parse("2026-10-26T19:00:00Z")
            saved.value = listOf(snapshot(start, liveNow))

            assertEquals(listOf(RewindParams(start, now)), calls)
            assertEquals(liveNow, assertIs<RewindQueryResult.Success>(results.last()).rewind.endDate)
            saved.value = saved.value.map { it.copy(viewCount = 1) }
            assertEquals(1, calls.size)
            assertEquals(1, assertIs<RewindQueryResult.Success>(results.last()).rewind.viewCount)
        }

    @Test
    fun anOpenCurrentWeekMovesToTheNewWeekAtTheLocalBoundary() =
        runTest {
            var liveNow = Instant.parse("2026-11-02T07:59:59Z")
            val subject =
                GetCurrentWeekRewindUseCase(
                    getRewind = { params ->
                        calls += params
                        flowOf(RewindQueryResult.NoneAvailable)
                    },
                    observeRewinds = { _, _ -> saved },
                    firstDayOfWeekPreference = flowOf(DayOfWeek.MONDAY),
                    clock =
                        object : Clock {
                            override fun now() = liveNow
                        },
                    timeZone = { TimeZone.of("America/Los_Angeles") },
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { subject().collect {} }
            liveNow = Instant.parse("2026-11-02T08:00:01Z")
            advanceTimeBy(1001)
            runCurrent()

            assertEquals(Instant.parse("2026-11-02T08:00:00Z"), calls.last().start)
            assertEquals(liveNow, calls.last().end)
        }

    @Test
    fun updateKeepsTheLatestSnapshotIdentityAndUsesNow() =
        runTest {
            val existing = snapshot(start, Instant.parse("2026-10-26T16:00:00Z"))
            saved.value = listOf(existing)
            var replacement: Rewind? = null
            val refresh =
                RefreshCurrentWeekRewindUseCase(
                    generate = { from, to, old ->
                        assertEquals(start, from)
                        assertEquals(now, to)
                        replacement = old
                        GenerateBasicRewindResult.NoContent
                    },
                    observeRewinds = { _, _ -> saved },
                    firstDayOfWeekPreference = flowOf(DayOfWeek.MONDAY),
                    clock =
                        object : Clock {
                            override fun now() = this@GetCurrentWeekRewindUseCaseTest.now
                        },
                    timeZone = { TimeZone.of("America/Los_Angeles") },
                )

            refresh()

            assertEquals(existing, replacement)
        }

    private fun snapshot(
        start: Instant,
        end: Instant,
    ) = Rewind(
        uid = Uuid.random(),
        startDate = start,
        endDate = end,
        generationDate = end,
        label = "2026#44",
        title = "A few moments",
    )
}
