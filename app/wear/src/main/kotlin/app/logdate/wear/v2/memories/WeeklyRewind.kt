package app.logdate.wear.v2.memories

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.shared.model.Rewind
import app.logdate.shared.model.RewindContent
import app.logdate.wear.v2.library.LibraryWindow
import app.logdate.wear.v2.today.TodayWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus

/** This week's Rewind as the watch shows it: its title, and the moment that opens it. */
data class WeeklyRewind(
    val title: String,
    val leadMoment: JournalNote,
)

/**
 * The newest Rewind that ended in the last [REWIND_AGE_DAYS] days, when one of its voice or text
 * moments is on this watch; null otherwise.
 */
class WeeklyRewindSource(
    private val rewinds: RewindRepository,
    private val notesRepository: JournalNotesRepository,
) {
    fun observe(today: TodayWindow): Flow<WeeklyRewind?> {
        val since = today.date.minus(REWIND_AGE_DAYS, DateTimeUnit.DAY).atStartOfDayIn(today.timeZone)
        return rewinds.getAllRewinds().map { all ->
            all.filter { it.endDate >= since }.maxByOrNull(Rewind::endDate)?.let { rewind -> leadFor(rewind) }
        }
    }

    private suspend fun leadFor(rewind: Rewind): WeeklyRewind? {
        val lead =
            rewind.content
                .filter { it is RewindContent.AudioNote || it is RewindContent.TextNote }
                .firstNotNullOfOrNull { content -> notesRepository.getNoteById(content.sourceId)?.takeIf(LibraryWindow::isLibraryMoment) }
                ?: return null
        return WeeklyRewind(title = rewind.title, leadMoment = lead)
    }

    companion object {
        const val REWIND_AGE_DAYS = 7
    }
}
