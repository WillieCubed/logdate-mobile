package app.logdate.client.domain.journals

import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.Journal
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

/**
 * Use case to update journal properties.
 * 
 * @param repository The repository for journal operations
 */
class UpdateJournalUseCase(
    private val repository: JournalRepository
) {
    /**
     * Updates a journal's properties.
     * 
     * @param journal The updated journal data
     * @return True if the update was successful, false otherwise
     */
    suspend operator fun invoke(journal: Journal): Boolean {
        return try {
            repository.update(journal)
            Napier.i("Journal updated successfully: ${journal.id}")
            true
        } catch (e: Exception) {
            Napier.e("Failed to update journal", e)
            false
        }
    }
    
    /**
     * Updates a journal's title.
     * 
     * @param journalId The ID of the journal to update
     * @param newTitle The new title for the journal
     * @return True if the update was successful, false otherwise
     */
    suspend operator fun invoke(journalId: Uuid, newTitle: String): Boolean {
        return try {
            val journal = repository.getJournalById(journalId) ?: return false
            val updated = journal.copy(title = newTitle)
            repository.update(updated)
            Napier.i("Journal title updated successfully: $journalId")
            true
        } catch (e: Exception) {
            Napier.e("Failed to update journal title", e)
            false
        }
    }
}