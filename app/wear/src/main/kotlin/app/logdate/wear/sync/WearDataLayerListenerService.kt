package app.logdate.wear.sync

import app.logdate.client.database.dao.journals.JournalContentDao
import app.logdate.client.database.entities.journals.JournalContentEntityLink
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableJournalContentRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.client.sync.datalayer.AssociationDataMapper
import app.logdate.client.sync.datalayer.JournalDataMapper
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Receives data items from the paired phone via the Wear Data Layer API.
 *
 * Handles notes, journals, and journal-content associations.
 * Uses [SyncableJournalNotesRepository.createFromSync] (and equivalents) to
 * avoid re-triggering outbound sync.
 *
 * Koin resilience: If DI is not ready when data arrives (e.g. app was just
 * started by the system), retries up to [MAX_KOIN_RETRIES] times with a short
 * delay before dropping the event.
 */
class WearDataLayerListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val noteDataMapper = NoteDataMapper()
    private val journalDataMapper = JournalDataMapper()
    private val associationDataMapper = AssociationDataMapper()

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // Snapshot events before they are recycled by the system
        val events = mutableListOf<SnapshotDataEvent>()
        for (event in dataEvents) {
            val path = event.dataItem.uri.path ?: continue
            val stringMap = extractStringMap(event)
            events.add(SnapshotDataEvent(path, stringMap))
        }

        if (events.isEmpty()) return

        serviceScope.launch {
            val koin = resolveKoin()
            if (koin == null) {
                Napier.e("Koin unavailable after $MAX_KOIN_RETRIES retries, dropping ${events.size} data events")
                return@launch
            }

            val notesRepository = koin.get<JournalNotesRepository>()
            val journalRepository = koin.get<JournalRepository>()

            for (event in events) {
                processEvent(event, koin, notesRepository, journalRepository)
            }
        }
    }

    /**
     * Processes a single data event. Runs inside [NonCancellable] to ensure
     * database writes complete even if the service is destroyed mid-flight.
     */
    private suspend fun processEvent(
        event: SnapshotDataEvent,
        koin: org.koin.core.Koin,
        notesRepository: JournalNotesRepository,
        journalRepository: JournalRepository,
    ) = withContext(NonCancellable) {
        val path = event.path

        try {
            when {
                NoteDataMapper.isDeletePath(path) -> handleNoteDelete(path, notesRepository)
                NoteDataMapper.isNotePath(path) -> handleNoteSync(path, event.data, notesRepository)
                JournalDataMapper.isDeletePath(path) -> handleJournalDelete(path, journalRepository)
                JournalDataMapper.isJournalPath(path) -> handleJournalSync(path, event.data, journalRepository)
                AssociationDataMapper.isDeletePath(path) -> handleAssociationDelete(path, koin)
                AssociationDataMapper.isAssociationPath(path) -> handleAssociationSync(path, event.data, koin)
            }
        } catch (e: Exception) {
            Napier.e("Unhandled error processing sync event at path: $path", e)
        }
    }

    private suspend fun handleNoteDelete(
        path: String,
        notesRepository: JournalNotesRepository,
    ) {
        val noteId =
            try {
                NoteDataMapper.noteIdFromPath(path)
            } catch (e: IllegalArgumentException) {
                Napier.w("Invalid note ID in delete path: $path", e)
                return
            }

        Napier.d("Received delete signal for note: $noteId")
        try {
            if (notesRepository is SyncableJournalNotesRepository) {
                notesRepository.deleteFromSync(noteId)
            } else {
                notesRepository.removeById(noteId)
            }
        } catch (e: Exception) {
            Napier.w("Failed to delete synced note: $noteId", e)
        }
    }

    private suspend fun handleNoteSync(
        path: String,
        stringMap: Map<String, String>,
        notesRepository: JournalNotesRepository,
    ) {
        try {
            val note = noteDataMapper.fromDataMap(stringMap)
            Napier.d("Received note from phone: ${note.uid} (${note.type})")
            if (notesRepository is SyncableJournalNotesRepository) {
                notesRepository.createFromSync(note)
            } else {
                notesRepository.create(note)
            }
        } catch (e: Exception) {
            Napier.w("Failed to process synced note from path: $path", e)
        }
    }

    private suspend fun handleJournalDelete(
        path: String,
        journalRepository: JournalRepository,
    ) {
        val journalId =
            try {
                JournalDataMapper.journalIdFromPath(path)
            } catch (e: IllegalArgumentException) {
                Napier.w("Invalid journal ID in delete path: $path", e)
                return
            }

        Napier.d("Received delete signal for journal: $journalId")
        try {
            if (journalRepository is SyncableJournalRepository) {
                journalRepository.deleteFromSync(journalId)
            } else {
                journalRepository.delete(journalId)
            }
        } catch (e: Exception) {
            Napier.w("Failed to delete synced journal: $journalId", e)
        }
    }

    private suspend fun handleJournalSync(
        path: String,
        stringMap: Map<String, String>,
        journalRepository: JournalRepository,
    ) {
        try {
            val journal = journalDataMapper.fromDataMap(stringMap)
            Napier.d("Received journal from phone: ${journal.id}")
            if (journalRepository is SyncableJournalRepository) {
                // createFromSync uses upsert — safe for duplicates and updates
                journalRepository.createFromSync(journal)
            } else {
                journalRepository.create(journal)
            }
        } catch (e: Exception) {
            Napier.w("Failed to process synced journal from path: $path", e)
        }
    }

    private suspend fun handleAssociationDelete(
        path: String,
        koin: org.koin.core.Koin,
    ) {
        val (journalId, contentId) =
            try {
                AssociationDataMapper.idsFromPath(path)
            } catch (e: Exception) {
                Napier.w("Invalid association path: $path", e)
                return
            }

        Napier.d("Received association delete: journal=$journalId, content=$contentId")
        try {
            val contentRepo = koin.getOrNull<SyncableJournalContentRepository>()
            if (contentRepo != null) {
                contentRepo.removeContentFromJournalFromSync(contentId, journalId)
            } else {
                val dao = koin.get<JournalContentDao>()
                dao.removeContentFromJournal(journalId, contentId)
            }
        } catch (e: Exception) {
            Napier.w("Failed to delete synced association: $path", e)
        }
    }

    private suspend fun handleAssociationSync(
        path: String,
        stringMap: Map<String, String>,
        koin: org.koin.core.Koin,
    ) {
        try {
            val (journalId, contentId) = associationDataMapper.fromDataMap(stringMap)
            Napier.d("Received association from phone: journal=$journalId, content=$contentId")
            val contentRepo = koin.getOrNull<SyncableJournalContentRepository>()
            if (contentRepo != null) {
                contentRepo.addContentToJournalFromSync(contentId, journalId)
            } else {
                val dao = koin.get<JournalContentDao>()
                dao.addContentToJournal(JournalContentEntityLink(journalId, contentId))
            }
        } catch (e: Exception) {
            Napier.w("Failed to process synced association from path: $path", e)
        }
    }

    /**
     * Resolves the Koin instance, retrying with a short delay if DI isn't ready yet.
     *
     * The system may start this service before [Application.onCreate] completes
     * (and therefore before Koin is initialized). A short retry gives the app
     * time to finish initialization rather than silently dropping data.
     */
    private suspend fun resolveKoin(): org.koin.core.Koin? {
        repeat(MAX_KOIN_RETRIES) { attempt ->
            try {
                return org.koin.java.KoinJavaComponent
                    .getKoin()
            } catch (e: Exception) {
                if (attempt < MAX_KOIN_RETRIES - 1) {
                    Napier.d("Koin not ready (attempt ${attempt + 1}/$MAX_KOIN_RETRIES), retrying...")
                    delay(KOIN_RETRY_DELAY_MS)
                }
            }
        }
        return null
    }

    private fun extractStringMap(event: com.google.android.gms.wearable.DataEvent): Map<String, String> {
        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
        val stringMap = mutableMapOf<String, String>()
        for (key in dataMap.keySet()) {
            if (key.startsWith("_")) continue
            dataMap.getString(key)?.let { stringMap[key] = it }
        }
        return stringMap
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    /**
     * Snapshot of a data event's path and payload.
     * Created before [DataEventBuffer] is recycled by the system.
     */
    private data class SnapshotDataEvent(
        val path: String,
        val data: Map<String, String>,
    )

    private companion object {
        const val MAX_KOIN_RETRIES = 3
        const val KOIN_RETRY_DELAY_MS = 500L
    }
}
