package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.FirstSyncEnqueueStore
import app.logdate.client.sync.metadata.SyncMetadataService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first

internal class FirstSyncEnqueuer(
    private val journalRepository: JournalRepository,
    private val journalNotesRepository: JournalNotesRepository,
    private val syncMetadataService: SyncMetadataService,
    private val firstSyncEnqueueStore: FirstSyncEnqueueStore,
    private val enqueueDrafts: suspend () -> Unit,
) {
    suspend fun enqueue() {
        val entityTypes = listOf(EntityType.JOURNAL, EntityType.NOTE)
        val neverSynced =
            entityTypes.filter { entityType ->
                !firstSyncEnqueueStore.hasEnqueued(entityType) && syncMetadataService.getLastSyncTime(entityType) == null
            }
        runCatching {
            if (EntityType.JOURNAL in neverSynced) {
                val journals = journalRepository.allJournalsObserved.first()
                journals.forEach { journal ->
                    syncMetadataService.enqueueCreateIfAbsent(
                        entityId = journal.id.toString(),
                        entityType = EntityType.JOURNAL,
                    )
                }
                // Marked only once the loop above has fully succeeded -- if it throws partway,
                // this line never runs and the next attempt retries the whole type from scratch.
                firstSyncEnqueueStore.markEnqueued(EntityType.JOURNAL)
                Napier.i("First sync: queued journals already on this device")
            }
            if (EntityType.NOTE in neverSynced) {
                val notes = journalNotesRepository.allNotesObserved.first()
                notes.forEach { note ->
                    syncMetadataService.enqueueCreateIfAbsent(
                        entityId = note.uid.toString(),
                        entityType = EntityType.NOTE,
                    )
                }
                firstSyncEnqueueStore.markEnqueued(EntityType.NOTE)
                Napier.i("First sync: queued entries already on this device")
            }
            enqueueDrafts()
        }.onFailure { error ->
            Napier.w("Could not queue existing entries for the first sync")
            throw error
        }
    }
}
