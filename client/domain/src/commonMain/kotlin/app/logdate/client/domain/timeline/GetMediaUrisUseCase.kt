package app.logdate.client.domain.timeline

import app.logdate.client.media.MediaManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

/**
 * Returns all media for the given day.
 */
class GetMediaUrisUseCase(
    private val mediaManager: MediaManager,
) {
    operator fun invoke(day: LocalDate): Flow<List<String>> = flow {
        val startOfDay = day.atStartOfDayIn(TimeZone.currentSystemDefault())
        val endOfDay = day.plus(DatePeriod(days = 1))
            .atStartOfDayIn(TimeZone.currentSystemDefault())
        val mediaUris = mediaManager.queryMediaByDate(startOfDay, endOfDay).map { it ->
            it.map { it.uri }
        }
        emitAll(mediaUris)
    }
}