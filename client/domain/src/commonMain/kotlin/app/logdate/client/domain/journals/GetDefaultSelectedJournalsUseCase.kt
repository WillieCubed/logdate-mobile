package app.logdate.client.domain.journals

import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNotesRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

/**
 * Use case for getting the default journals to be selected when creating a new entry.
 * This returns the journals that were used in the most recent entry, or an empty list if no entries exist.
 */
class GetDefaultSelectedJournalsUseCase(
    private val journalNotesRepository: JournalNotesRepository,
    private val journalContentRepository: JournalContentRepository
) {
    /**
     * Returns a list of journal IDs that should be pre-selected when creating a new entry.
     * This is based on the journals used in the most recent entry.
     * If no entries exist, returns an empty list.
     * 
     * This implementation efficiently chains flow operations to minimize conversions.
     */
    suspend operator fun invoke(): List<Uuid> {
        try {
            // Get the most recent note ID directly without collecting entire list
            val mostRecentNote = journalNotesRepository.observeRecentNotes(1)
                .map { notes -> notes.firstOrNull()?.uid }
                .firstOrNull() ?: return emptyList()
            
            // Convert journals to journal IDs in a single flow operation
            return journalContentRepository.observeJournalsForContent(mostRecentNote)
                .map { journals -> journals.map { it.id } }
                .catch { e ->
                    Napier.w("Error getting journals for note: ${e.message}", e)
                    emit(emptyList())
                }
                .firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            Napier.w("Error getting default selected journals: ${e.message}", e)
            return emptyList()
        }
    }
}