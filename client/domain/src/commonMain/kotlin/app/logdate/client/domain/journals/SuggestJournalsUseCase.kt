package app.logdate.client.domain.journals

import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid

/**
 * Suggests new journals based on unorganized notes.
 *
 * Finds notes that don't belong to any journal and clusters them by week.
 * Returns suggestions only when the user has enough unorganized content
 * to make a journal worthwhile.
 */
class SuggestJournalsUseCase(
    private val notesRepository: JournalNotesRepository,
    private val contentRepository: JournalContentRepository,
) {
    suspend operator fun invoke(): List<JournalSuggestion> {
        val recentNotes = notesRepository.observeRecentNotes(limit = 100).firstOrNull() ?: return emptyList()
        if (recentNotes.size < MINIMUM_UNORGANIZED_THRESHOLD) return emptyList()

        // Find notes not in any journal
        val noteIds = recentNotes.map { it.uid }.toSet()
        val membership = contentRepository.observeJournalsForContents(noteIds).firstOrNull() ?: emptyMap()
        val unorganized =
            recentNotes.filter { note ->
                membership[note.uid].isNullOrEmpty()
            }

        if (unorganized.size < MINIMUM_UNORGANIZED_THRESHOLD) return emptyList()

        return clusterByWeek(unorganized)
    }

    private fun clusterByWeek(notes: List<JournalNote>): List<JournalSuggestion> {
        val tz = TimeZone.currentSystemDefault()

        val weekGroups =
            notes.groupBy { note ->
                val date = note.creationTimestamp.toLocalDateTime(tz).date
                "${date.year}-W${(date.dayOfYear / 7) + 1}"
            }

        return weekGroups
            .filter { (_, groupNotes) -> groupNotes.size >= MINIMUM_CLUSTER_SIZE }
            .map { (_, groupNotes) ->
                val firstDate = groupNotes.minOf { it.creationTimestamp }
                val firstLocal = firstDate.toLocalDateTime(tz).date

                JournalSuggestion(
                    suggestedTitle = "Week of ${firstLocal.month.name.lowercase().replaceFirstChar {
                        it.uppercase()
                    }} ${firstLocal.dayOfMonth}",
                    noteIds = groupNotes.map { it.uid },
                    noteCount = groupNotes.size,
                    reason = "${groupNotes.size} entries from this week aren't in any journal",
                )
            }.sortedByDescending { it.noteCount }
            .take(MAX_SUGGESTIONS)
    }

    companion object {
        private const val MINIMUM_UNORGANIZED_THRESHOLD = 5
        private const val MINIMUM_CLUSTER_SIZE = 3
        private const val MAX_SUGGESTIONS = 3
    }
}

/**
 * A suggestion to create a journal from unorganized notes.
 */
data class JournalSuggestion(
    val suggestedTitle: String,
    val noteIds: List<Uuid>,
    val noteCount: Int,
    val reason: String,
)
