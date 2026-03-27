package app.logdate.client.domain.recommendation

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteType
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Finds past entries worth revisiting using either an "on this day" heuristic
 * or a broader archive heuristic.
 *
 * On This Day algorithm:
 * 1. Check for entries exactly one year ago today
 * 2. If none, widen to a ±3 day window around the anniversary date
 * 3. Pick the day with the richest qualifying content
 *
 * From the archive algorithm:
 * 1. Look at older notes outside the recent-history window
 * 2. Prefer the most recent day with visual media or multiple qualifying notes
 * 3. Fall back to the most recent day with any qualifying notes
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
        private const val ARCHIVE_MIN_AGE_DAYS = 30
        private const val ARCHIVE_NOTE_LOOKBACK_LIMIT = 500
    }

    /**
     * @param aiEnabled Whether AI-powered recall is enabled by the user's preferences.
     *   When false, the [aiRecallProvider] is skipped even if present.
     */
    operator fun invoke(
        aiEnabled: Boolean = false,
        recallMode: RecallMode = RecallMode.ON_THIS_DAY,
        contentTypes: Set<WidgetContentType> = WidgetContentType.ALL,
    ): Flow<MemoryRecallData?> =
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

            val recallData =
                when (recallMode) {
                    RecallMode.ON_THIS_DAY -> findOnThisDayEntries(contentTypes)
                    RecallMode.REDISCOVER -> findFromArchiveEntries(contentTypes)
                }

            emit(recallData)
        }

    private suspend fun findOnThisDayEntries(contentTypes: Set<WidgetContentType>): MemoryRecallData? {
        val today = currentLocalDate()
        val targetDate = today.minus(1, DateTimeUnit.YEAR)

        val exactDayNotes = notesRepository.getNotesForDay(targetDate)
        exactDayNotes.toRecallData(targetDate, contentTypes)?.let { return it }

        return findBestDayInWindow(targetDate, contentTypes)
    }

    private suspend fun findFromArchiveEntries(contentTypes: Set<WidgetContentType>): MemoryRecallData? {
        val timezone = TimeZone.currentSystemDefault()
        val recentCutoff =
            currentLocalDate()
                .minus(ARCHIVE_MIN_AGE_DAYS, DateTimeUnit.DAY)
                .atStartOfDayIn(timezone)

        val olderNotes =
            notesRepository.getNotesBefore(
                beforeExclusive = recentCutoff,
                limit = ARCHIVE_NOTE_LOOKBACK_LIMIT,
            )

        val notesByDay =
            olderNotes
                .groupBy { note -> note.creationTimestamp.toLocalDateTime(timezone).date }
                .entries
                .sortedByDescending { entry -> entry.key }

        notesByDay
            .firstNotNullOfOrNull { (day, notes) ->
                if (notes.isNotableArchiveDay(contentTypes)) {
                    notes.toRecallData(day, contentTypes)
                } else {
                    null
                }
            }?.let { return it }

        return notesByDay.firstNotNullOfOrNull { (day, notes) ->
            notes.toRecallData(day, contentTypes)
        }
    }

    private suspend fun findBestDayInWindow(
        centerDate: LocalDate,
        contentTypes: Set<WidgetContentType>,
    ): MemoryRecallData? {
        var bestDay: LocalDate? = null
        var bestNotes: List<JournalNote> = emptyList()
        var bestScore = 0

        for (offset in 1..WINDOW_DAYS) {
            val before = centerDate.minus(offset, DateTimeUnit.DAY)
            val after = centerDate.plus(offset, DateTimeUnit.DAY)

            val notesBefore = notesRepository.getNotesForDay(before)
            val notesAfter = notesRepository.getNotesForDay(after)
            val beforeScore = notesBefore.scoreFor(contentTypes)
            val afterScore = notesAfter.scoreFor(contentTypes)

            if (beforeScore > bestScore) {
                bestDay = before
                bestNotes = notesBefore
                bestScore = beforeScore
            }
            if (afterScore > bestScore) {
                bestDay = after
                bestNotes = notesAfter
                bestScore = afterScore
            }
        }

        return bestDay?.let { day -> bestNotes.toRecallData(day, contentTypes) }
    }

    private fun List<JournalNote>.toRecallData(
        date: LocalDate,
        contentTypes: Set<WidgetContentType>,
    ): MemoryRecallData? {
        val eligibleNotes = filterFor(contentTypes)
        if (eligibleNotes.isEmpty()) return null

        val summary =
            eligibleNotes
                .filterIsInstance<JournalNote.Text>()
                .firstOrNull()
                ?.content
                ?.take(SUMMARY_MAX_LENGTH)
                .orEmpty()

        val mediaUris =
            eligibleNotes.mapNotNull { note ->
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

    private fun List<JournalNote>.filterFor(contentTypes: Set<WidgetContentType>): List<JournalNote> {
        val allowedTypes = contentTypes.toAllowedNoteTypes()
        if (allowedTypes.isEmpty()) return emptyList()
        return filter { note -> note.type in allowedTypes }
    }

    private fun List<JournalNote>.scoreFor(contentTypes: Set<WidgetContentType>): Int = filterFor(contentTypes).size

    private fun List<JournalNote>.isNotableArchiveDay(contentTypes: Set<WidgetContentType>): Boolean {
        val eligibleNotes = filterFor(contentTypes)
        if (eligibleNotes.isEmpty()) return false

        val hasVisualMedia =
            eligibleNotes.any { note ->
                note is JournalNote.Image || note is JournalNote.Video
            }
        return hasVisualMedia || eligibleNotes.size >= 2
    }

    private fun Set<WidgetContentType>.toAllowedNoteTypes(): Set<NoteType> =
        buildSet {
            if (WidgetContentType.TEXT in this@toAllowedNoteTypes) {
                add(NoteType.TEXT)
            }
            if (WidgetContentType.PHOTOS in this@toAllowedNoteTypes) {
                add(NoteType.IMAGE)
                add(NoteType.VIDEO)
            }
            if (WidgetContentType.AUDIO in this@toAllowedNoteTypes) {
                add(NoteType.AUDIO)
            }
        }

    private fun currentLocalDate(): LocalDate =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
}
