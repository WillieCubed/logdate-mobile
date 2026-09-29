package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.sync.datalayer.NoteDataMapper
import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import kotlin.uuid.Uuid

/**
 * Where a watch audio note's file and not-yet-stored metadata live on the phone.
 *
 * Metadata and audio bytes reach the phone over separate Data Layer channels in either order, so
 * whichever arrives first has to survive until the other one does.
 */
interface WatchAudioStore {
    fun audioPath(noteId: Uuid): String

    fun hasAudio(noteId: Uuid): Boolean

    /** Writes the complete file at [audioPath], or returns false and leaves nothing behind. */
    suspend fun writeAudio(
        noteId: Uuid,
        source: InputStream,
    ): Boolean

    suspend fun stashMetadata(
        noteId: Uuid,
        data: Map<String, String>,
    )

    suspend fun stashedMetadata(noteId: Uuid): Map<String, String>?

    suspend fun clearMetadata(noteId: Uuid)

    /** Removes the file and any stashed metadata. */
    suspend fun discard(noteId: Uuid)
}

/** Tells the watch a note is stored on the phone, so the watch can stop retrying it. */
fun interface WatchNoteAcknowledger {
    suspend fun acknowledge(noteId: Uuid)
}

fun interface WatchNoteNotifier {
    fun noteReceived(note: JournalNote)
}

/**
 * Turns notes and audio bytes arriving from the watch into phone notes queued for cloud backup.
 *
 * A watch audio note is stored only once its file is on the phone, so the phone never lists a note
 * it cannot play and never uploads a note whose bytes are missing. Delivery is at-least-once: a
 * redelivered note or file is acknowledged again and never stored twice.
 */
class WatchNoteIngestor(
    private val notesRepository: JournalNotesRepository,
    private val audioStore: WatchAudioStore,
    private val acknowledger: WatchNoteAcknowledger,
    private val notifier: WatchNoteNotifier,
    private val noteDataMapper: NoteDataMapper = NoteDataMapper(),
) {
    private val mutex = Mutex()

    /** @throws IllegalArgumentException if [data] carries no decodable note. */
    suspend fun onNoteMetadata(data: Map<String, String>) =
        mutex.withLock {
            val note = noteDataMapper.fromDataMap(data)
            if (note is JournalNote.Audio) {
                receiveAudioMetadata(note, data)
            } else {
                receiveNote(note)
            }
        }

    suspend fun onAudioBytes(
        noteId: Uuid,
        source: InputStream,
    ) = mutex.withLock {
        if (notesRepository.getNoteById(noteId) != null) {
            acknowledger.acknowledge(noteId)
            return@withLock
        }
        if (!audioStore.writeAudio(noteId, source)) {
            Napier.w("Could not store audio bytes from the watch for note $noteId")
            return@withLock
        }
        storeIfComplete(noteId)
    }

    suspend fun onNoteDeleted(noteId: Uuid) =
        mutex.withLock {
            if (notesRepository.getNoteById(noteId) != null) {
                notesRepository.removeById(noteId)
            }
            audioStore.discard(noteId)
        }

    private suspend fun receiveAudioMetadata(
        note: JournalNote.Audio,
        data: Map<String, String>,
    ) {
        if (notesRepository.getNoteById(note.uid) != null) {
            acknowledger.acknowledge(note.uid)
            return
        }
        audioStore.stashMetadata(note.uid, data)
        storeIfComplete(note.uid)
    }

    private suspend fun receiveNote(note: JournalNote) {
        if (notesRepository.getNoteById(note.uid) != null) {
            (notesRepository as? SyncableJournalNotesRepository)?.createFromSync(note)
            return
        }
        notesRepository.create(note)
        notifier.noteReceived(note)
    }

    private suspend fun storeIfComplete(noteId: Uuid) {
        if (!audioStore.hasAudio(noteId)) return
        val data = audioStore.stashedMetadata(noteId) ?: return
        val watchNote = noteDataMapper.fromDataMap(data) as? JournalNote.Audio ?: return

        val note = watchNote.copy(mediaRef = audioStore.audioPath(noteId))
        notesRepository.create(note)
        audioStore.clearMetadata(noteId)
        acknowledger.acknowledge(noteId)
        notifier.noteReceived(note)
    }
}
