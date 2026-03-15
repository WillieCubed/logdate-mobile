package app.logdate.wear.sync

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.repository.journals.JournalNotesRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Receives data items from the paired phone via the Wear Data Layer API.
 *
 * When the phone creates or updates a note, it puts a DataItem at
 * `/logdate/notes/<noteId>`. This service deserializes the note and
 * inserts it via [SyncableJournalNotesRepository.createFromSync] to
 * avoid re-triggering outbound sync.
 */
class WearDataLayerListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val noteDataMapper = NoteDataMapper()

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val notesRepository = try {
            org.koin.java.KoinJavaComponent.getKoin().get<JournalNotesRepository>()
        } catch (e: Exception) {
            Napier.w("Koin not ready in WearDataLayerListenerService", e)
            return
        }

        for (event in dataEvents) {
            val path = event.dataItem.uri.path ?: continue

            when {
                NoteDataMapper.isDeletePath(path) -> {
                    val noteId = NoteDataMapper.noteIdFromPath(path)
                    Napier.d("Received delete signal for note: $noteId")
                    serviceScope.launch {
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
                }

                NoteDataMapper.isNotePath(path) -> {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val stringMap = mutableMapOf<String, String>()
                    for (key in dataMap.keySet()) {
                        if (key.startsWith("_")) continue // skip internal keys
                        dataMap.getString(key)?.let { stringMap[key] = it }
                    }

                    serviceScope.launch {
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
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
