package app.logdate.client.domain.export

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import kotlinx.coroutines.flow.first

/**
 * Counts of exportable items per category.
 */
data class ExportCounts(
    val journalCount: Int,
    val noteCount: Int,
    val draftCount: Int,
    val mediaCount: Int,
)

/**
 * Use case for getting counts of exportable items per category.
 *
 * Used to populate the export options bottom sheet with item counts.
 */
class GetExportCountsUseCase(
    private val journalRepository: JournalRepository,
    private val journalNotesRepository: JournalNotesRepository,
) {
    suspend operator fun invoke(): ExportCounts {
        val journals = journalRepository.allJournalsObserved.first()
        val notes = journalNotesRepository.allNotesObserved.first()
        val drafts = journalRepository.getAllDrafts()
        val notesWithMedia =
            notes.count { note ->
                when (note) {
                    is JournalNote.Image -> true
                    is JournalNote.Audio -> true
                    is JournalNote.Video -> true
                    else -> false
                }
            }

        return ExportCounts(
            journalCount = journals.size,
            noteCount = notes.size,
            draftCount = drafts.size,
            mediaCount = notesWithMedia,
        )
    }
}
