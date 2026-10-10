package app.logdate.wear.recording

import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.sync.WearNoteRemovalNotifier
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.uuid.Uuid

/** The note a save just made, while Undo can still take it back. */
data class AfterSaveWindow(
    val noteId: Uuid,
)

/**
 * The seconds after a recording is saved. [open] starts a window of [UNDO_WINDOW_MS]; while it
 * lasts, [undo] removes the note, deletes its audio once no note refers to it, and tells the phone,
 * which may already have the note.
 *
 * A saved note's audio is named by a `logdate-media://` reference, which [mediaFiles] turns back into
 * the file to delete. Without it the reference is used as a file path, as it was before references
 * were portable.
 */
class AfterSaveController(
    private val scope: CoroutineScope,
    private val notesRepository: JournalNotesRepository,
    private val removalNotifier: WearNoteRemovalNotifier,
    private val deleteFile: (String) -> Unit = { path -> File(path).delete() },
    private val mediaFiles: MediaFileResolver? = null,
) {
    private val mutableWindow = MutableStateFlow<AfterSaveWindow?>(null)
    private var closeJob: Job? = null

    /** The open window, or null once it has closed. */
    val window: StateFlow<AfterSaveWindow?> = mutableWindow.asStateFlow()

    fun open(noteId: Uuid) {
        closeJob?.cancel()
        mutableWindow.value = AfterSaveWindow(noteId)
        closeJob =
            scope.launch {
                delay(UNDO_WINDOW_MS)
                mutableWindow.value = null
            }
    }

    /** Ends the window early, as recording again does. The note stays. */
    fun close() {
        closeJob?.cancel()
        closeJob = null
        mutableWindow.value = null
    }

    /**
     * Removes [noteId] and its audio, and tells the phone. Returns false when the note could not be
     * removed; the phone is told nothing then.
     */
    suspend fun undo(noteId: Uuid): Boolean {
        if (mutableWindow.value?.noteId == noteId) close()
        return try {
            val mediaRef = (notesRepository.getNoteById(noteId) as? JournalNote.Audio)?.mediaRef
            notesRepository.removeById(noteId)
            if (mediaRef != null && notesRepository.getNoteById(noteId) == null) deleteAudio(mediaRef)
            // The outbox drops a delete that meets a pending create, but the phone may already have
            // the note, so it is told directly.
            removalNotifier.notifyRemoved(noteId)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Failed to undo saved recording $noteId", e)
            false
        }
    }

    private fun deleteAudio(mediaRef: String) {
        val path = if (mediaFiles == null) mediaRef else mediaFiles.filePath(mediaRef) ?: return
        deleteFile(path)
    }

    companion object {
        const val UNDO_WINDOW_MS = 5_000L
    }
}
