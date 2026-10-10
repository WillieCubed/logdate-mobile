package app.logdate.wear.recording

import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.capture.Feeling
import app.logdate.wear.capture.FeelingSaver
import app.logdate.wear.sync.WearNoteRemovalNotifier
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.uuid.Uuid

/**
 * The note a save just made, while Undo can still take it back.
 *
 * @property feelingsOffered True while the five feelings show beside Undo.
 * @property feeling The feeling chosen for this recording, once one is saved.
 */
data class AfterSaveWindow(
    val noteId: Uuid,
    val feelingsOffered: Boolean = false,
    val feeling: Feeling? = null,
)

/**
 * The seconds after a recording is saved. [open] starts a window of [UNDO_WINDOW_MS]; while it
 * lasts, [undo] removes the note, deletes its audio once no note refers to it, and tells the phone,
 * which may already have the note. A window opened with feelings offers them for
 * [FEELINGS_WINDOW_MS], and [chooseFeeling] saves the one chosen as a check-in through [feelingSaver].
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
    private val feelingSaver: FeelingSaver = FeelingSaver.None,
) {
    private val mutableWindow = MutableStateFlow<AfterSaveWindow?>(null)
    private var closeJob: Job? = null
    private var feelingsJob: Job? = null

    /** The open window, or null once it has closed. */
    val window: StateFlow<AfterSaveWindow?> = mutableWindow.asStateFlow()

    fun open(
        noteId: Uuid,
        offerFeelings: Boolean = false,
    ) {
        cancelTimers()
        mutableWindow.value = AfterSaveWindow(noteId, feelingsOffered = offerFeelings)
        closeJob =
            scope.launch {
                delay(UNDO_WINDOW_MS)
                mutableWindow.value = null
            }
        if (!offerFeelings) return
        feelingsJob =
            scope.launch {
                delay(FEELINGS_WINDOW_MS)
                mutableWindow.update { window -> window?.copy(feelingsOffered = false) }
            }
    }

    /** Ends the window early, as recording again does. The note stays. */
    fun close() {
        cancelTimers()
        mutableWindow.value = null
    }

    /**
     * Saves [feeling] as a check-in while the feelings are offered. Returns false when they are not
     * offered or the check-in could not be stored; the feelings stay offered then.
     */
    suspend fun chooseFeeling(feeling: Feeling): Boolean {
        val window = mutableWindow.value ?: return false
        if (!window.feelingsOffered) return false
        if (!feelingSaver.save(feeling)) return false
        feelingsJob?.cancel()
        mutableWindow.update { current ->
            if (current == null || current.noteId != window.noteId) current else current.copy(feelingsOffered = false, feeling = feeling)
        }
        return true
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

    private fun cancelTimers() {
        closeJob?.cancel()
        closeJob = null
        feelingsJob?.cancel()
        feelingsJob = null
    }

    companion object {
        const val UNDO_WINDOW_MS = 5_000L
        const val FEELINGS_WINDOW_MS = 4_000L
    }
}
