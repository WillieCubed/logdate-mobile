package app.logdate.client.domain.journals

import app.logdate.client.repository.journals.JournalRepository
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

/**
 * Use case to delete a journal.
 * 
 * @param repository The repository for journal operations
 */
class DeleteJournalUseCase(
    private val repository: JournalRepository
) {
    /**
     * Deletes a journal by its ID.
     * 
     * @param journalId The ID of the journal to delete
     * @return True if the deletion was successful, false otherwise
     */
    suspend operator fun invoke(journalId: Uuid): Boolean {
        return try {
            repository.delete(journalId)
            Napier.i("Journal deleted successfully: $journalId")
            true
        } catch (e: Exception) {
            Napier.e("Failed to delete journal", e)
            false
        }
    }
}