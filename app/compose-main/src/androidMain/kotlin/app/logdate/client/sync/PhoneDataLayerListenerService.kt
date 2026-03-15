package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.sync.datalayer.NoteDataMapper
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Receives data items from the paired Wear OS watch via the Data Layer API.
 *
 * When the watch creates, updates, or deletes a note, it puts a DataItem at
 * `/logdate/notes/<noteId>` (or `/logdate/notes/<noteId>/delete`). This service
 * deserializes the note and inserts it via [SyncableJournalNotesRepository.createFromSync]
 * to avoid re-triggering outbound sync.
 */
class PhoneDataLayerListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val noteDataMapper = NoteDataMapper()

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val notesRepository =
            try {
                org.koin.java.KoinJavaComponent
                    .getKoin()
                    .get<JournalNotesRepository>()
            } catch (e: Exception) {
                Napier.w("Koin not ready in PhoneDataLayerListenerService", e)
                return
            }

        for (event in dataEvents) {
            val path = event.dataItem.uri.path ?: continue

            when {
                NoteDataMapper.isDeletePath(path) -> handleDelete(path, notesRepository)
                NoteDataMapper.isNotePath(path) -> handleNoteSync(event, path, notesRepository)
            }
        }
    }

    private fun handleDelete(
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

        Napier.d("Received delete signal from watch for note: $noteId")
        serviceScope.launch {
            try {
                if (notesRepository is SyncableJournalNotesRepository) {
                    notesRepository.deleteFromSync(noteId)
                } else {
                    notesRepository.removeById(noteId)
                }
            } catch (e: Exception) {
                Napier.w("Failed to delete synced note from watch: $noteId", e)
            }
        }
    }

    private fun handleNoteSync(
        event: com.google.android.gms.wearable.DataEvent,
        path: String,
        notesRepository: JournalNotesRepository,
    ) {
        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
        val stringMap = mutableMapOf<String, String>()
        for (key in dataMap.keySet()) {
            if (key.startsWith("_")) continue // skip internal keys like _syncTimestamp
            dataMap.getString(key)?.let { stringMap[key] = it }
        }

        serviceScope.launch {
            try {
                val note = noteDataMapper.fromDataMap(stringMap)
                Napier.d("Received note from watch: ${note.uid} (${note.type})")
                if (notesRepository is SyncableJournalNotesRepository) {
                    notesRepository.createFromSync(note)
                } else {
                    notesRepository.create(note)
                }
            } catch (e: Exception) {
                Napier.w("Failed to process synced note from watch at path: $path", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
