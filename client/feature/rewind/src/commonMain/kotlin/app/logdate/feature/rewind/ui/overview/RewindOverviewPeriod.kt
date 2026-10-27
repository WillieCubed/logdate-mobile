package app.logdate.feature.rewind.ui.overview

import app.logdate.client.domain.rewind.RewindQueryResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

enum class RewindOverviewPeriod {
    PreviousWeek,
    ThisWeek,
}

/** Cancels the previous query and identifies every result with the period that produced it. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeSelectedRewind(
    selection: Flow<RewindOverviewPeriod>,
    retryRevision: Flow<Int>,
    previousWeek: () -> Flow<RewindQueryResult>,
    thisWeek: () -> Flow<RewindQueryResult>,
): Flow<Pair<RewindOverviewPeriod, RewindQueryResult>> =
    combine(selection, retryRevision) { period, _ -> period }.flatMapLatest { period ->
        val query = if (period == RewindOverviewPeriod.ThisWeek) thisWeek() else previousWeek()
        query.map { period to it }.onStart { emit(period to RewindQueryResult.Generating) }
    }
