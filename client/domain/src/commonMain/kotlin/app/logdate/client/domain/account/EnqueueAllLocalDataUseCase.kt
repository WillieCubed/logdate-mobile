package app.logdate.client.domain.account

import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first

/**
 * Queues every journal, entry, journal-entry link and draft on this device for upload to the
 * server the app is currently connected to.
 *
 * Used when a server first needs everything this device holds: the first sign-in to an account,
 * and moving an account to another server.
 */
class EnqueueAllLocalDataUseCase(
    private val journalRepository: JournalRepository,
    private val journalNotesRepository: JournalNotesRepository,
    private val syncMetadataService: SyncMetadataService,
) {
    data class Counts(
        val journals: Int,
        val notes: Int,
        val associations: Int,
        val drafts: Int,
    )

    suspend operator fun invoke(): Result<Counts> =
        runCatching {
            val journals = journalRepository.allJournalsObserved.first()
            val notes = journalNotesRepository.allNotesObserved.first()
            val associations = journalNotesRepository.getAllJournalNoteLinks()
            val drafts = journalRepository.getAllDrafts()

            journals.forEach { enqueue(it.id.toString(), EntityType.JOURNAL, PendingOperation.CREATE) }
            notes.forEach { enqueue(it.uid.toString(), EntityType.NOTE, PendingOperation.CREATE) }
            associations.forEach { (journalId, contentId) ->
                enqueue(AssociationPendingKey(journalId, contentId).toPendingId(), EntityType.ASSOCIATION, PendingOperation.CREATE)
            }
            // Drafts are always sent as updates, the same way saving one queues it.
            drafts.forEach { enqueue(it.id.toString(), EntityType.DRAFT, PendingOperation.UPDATE) }

            Counts(journals = journals.size, notes = notes.size, associations = associations.size, drafts = drafts.size)
        }.onFailure { Napier.e("Could not queue this device's data for upload", it) }

    private suspend fun enqueue(
        entityId: String,
        entityType: EntityType,
        operation: PendingOperation,
    ) = syncMetadataService.enqueuePending(entityId = entityId, entityType = entityType, operation = operation)
}
