package app.logdate.client.domain.recommendation

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Finds past entries worth revisiting using an "on this day" heuristic.
 *
 * The algorithm:
 * 1. Check for entries exactly one year ago today
 * 2. If none, widen to a ±3 day window around the anniversary date
 * 3. Pick the day with the richest content
 * 4. Extract a summary, people, and media URIs from that day's notes
 *
 * When an [AiRecallProvider] is injected, the AI provider is tried first,
 * falling back to the heuristic on failure.
 */
class GetMemoryRecallUseCase(
    private val notesRepository: JournalNotesRepository,
    private val aiRecallProvider: AiRecallProvider? = null,
) {
    companion object {
        private const val SUMMARY_MAX_LENGTH = 120
        private const val WINDOW_DAYS = 3
    }

    /**
     * @param aiEnabled Whether AI-powered recall is enabled by the user's preferences.
     *   When false, the [aiRecallProvider] is skipped even if present.
     */
    operator fun invoke(aiEnabled: Boolean = false): Flow<MemoryRecallData?> =
        flow {
            if (aiEnabled && aiRecallProvider != null) {
                val aiResult =
                    runCatching { aiRecallProvider.suggestRecall() }
                        .onFailure { Napier.w("AI recall failed, falling back to heuristic", it) }
                        .getOrNull()
                if (aiResult != null) {
                    emit(aiResult)
                    return@flow
                }
            }

            emit(findOnThisDayEntries())
        }

    private suspend fun findOnThisDayEntries(): MemoryRecallData? {
        val today =
            Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
        val targetDate = today.minus(1, DateTimeUnit.YEAR)

        val exactDayNotes = notesRepository.getNotesForDay(targetDate)
        if (exactDayNotes.isNotEmpty()) {
            return exactDayNotes.toRecallData(targetDate)
        }

        return findBestDayInWindow(targetDate)
    }

    private suspend fun findBestDayInWindow(centerDate: LocalDate): MemoryRecallData? {
        var bestDay: LocalDate? = null
        var bestNotes: List<JournalNote> = emptyList()

        for (offset in 1..WINDOW_DAYS) {
            val before = centerDate.minus(offset, DateTimeUnit.DAY)
            val after = centerDate.plus(offset, DateTimeUnit.DAY)

            val notesBefore = notesRepository.getNotesForDay(before)
            val notesAfter = notesRepository.getNotesForDay(after)

            if (notesBefore.size > bestNotes.size) {
                bestDay = before
                bestNotes = notesBefore
            }
            if (notesAfter.size > bestNotes.size) {
                bestDay = after
                bestNotes = notesAfter
            }
        }

        return bestDay?.let { day -> bestNotes.toRecallData(day) }
    }

    private fun List<JournalNote>.toRecallData(date: LocalDate): MemoryRecallData {
        val summary =
            filterIsInstance<JournalNote.Text>()
                .firstOrNull()
                ?.content
                ?.take(SUMMARY_MAX_LENGTH)
                ?: ""

        val mediaUris =
            mapNotNull { note ->
                when (note) {
                    is JournalNote.Image -> note.mediaRef
                    is JournalNote.Video -> note.mediaRef
                    else -> null
                }
            }

        return MemoryRecallData(
            date = date,
            summary = summary,
            mediaUris = mediaUris,
        )
    }
}
