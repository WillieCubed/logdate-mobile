package app.logdate.client.domain.notes

import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours

/**
 * Checks if the user has created any notes for the current day.
 *
 * This use case returns a Flow<Boolean> that emits true if there are any notes
 * created today, false otherwise. It's used to determine whether to show
 * the "no memories today" empty state.
 */
class HasNotesForTodayUseCase(
    private val repository: JournalNotesRepository,
) {
    operator fun invoke(): Flow<Boolean> {
        val start = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .atStartOfDayIn(TimeZone.currentSystemDefault())
        val end = start + 24.hours
        
        return repository.observeNotesInRange(start, end)
            .map { notes -> notes.isNotEmpty() }
    }
}