package app.logdate.feature.rewind.ui.overview

import app.logdate.client.domain.rewind.RewindQueryResult
import app.logdate.shared.model.Rewind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class RewindPeriodSelectionTest {
    @Test
    fun switchingWeeksDropsLateResultsFromThePreviousQuery() =
        runTest {
            val selected = MutableStateFlow(RewindOverviewPeriod.PreviousWeek)
            val oldResults = MutableSharedFlow<RewindQueryResult>()
            val current = RewindQueryResult.Success(rewind())
            val results = mutableListOf<Pair<RewindOverviewPeriod, RewindQueryResult>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                observeSelectedRewind(selected, MutableStateFlow(0), { oldResults }, { flowOf(current) })
                    .collect { results += it }
            }
            runCurrent()

            selected.value = RewindOverviewPeriod.ThisWeek
            runCurrent()
            oldResults.emit(RewindQueryResult.Failed)

            assertEquals(RewindOverviewPeriod.ThisWeek to current, results.last())
            assertEquals(0, oldResults.subscriptionCount.value)
        }

    @Test
    fun retryRestartsOnlyTheSelectedWeek() =
        runTest {
            val selected = MutableStateFlow(RewindOverviewPeriod.ThisWeek)
            val retries = MutableStateFlow(0)
            var previousQueries = 0
            var currentQueries = 0
            val results = mutableListOf<RewindQueryResult>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                observeSelectedRewind(
                    selected,
                    retries,
                    {
                        previousQueries++
                        flowOf(RewindQueryResult.NoneAvailable)
                    },
                    {
                        currentQueries++
                        flowOf(if (currentQueries == 1) RewindQueryResult.Failed else RewindQueryResult.Success(rewind()))
                    },
                ).collect { results += it.second }
            }
            runCurrent()

            retries.value++
            runCurrent()

            assertEquals(0, previousQueries)
            assertEquals(2, currentQueries)
            assertEquals(RewindQueryResult.Generating, results[results.lastIndex - 1])
            kotlin.test.assertIs<RewindQueryResult.Success>(results.last())
        }

    private fun rewind() =
        Rewind(
            uid = Uuid.random(),
            startDate = Instant.parse("2026-10-26T07:00:00Z"),
            endDate = Instant.parse("2026-10-26T18:00:00Z"),
            generationDate = Instant.parse("2026-10-26T18:00:00Z"),
            label = "2026#44",
            title = "A quiet morning",
        )
}
