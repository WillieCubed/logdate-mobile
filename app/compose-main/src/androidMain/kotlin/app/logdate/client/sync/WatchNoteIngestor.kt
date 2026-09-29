package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.sync.datalayer.NoteDataMapper
import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
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

    /** Removes the file and any stashed metadata. A deletion marker set by [markDeleted] stays. */
    suspend fun discard(noteId: Uuid)

    /**
     * Remembers that the watch deleted this note. Data items and channels are not ordered against each
     * other, so a delete can arrive before the note it removes; the marker stops the late note and
     * audio from being stored after the fact.
     */
    suspend fun markDeleted(noteId: Uuid)

    suspend fun isDeleted(noteId: Uuid): Boolean
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
 * redelivered note or file is acknowledged again and never stored twice, and a note the watch has
 * deleted is never stored, even when its data arrives after the delete.
 *
 * Work is serialized per note, not globally, so a long audio transfer for one note does not hold up
 * the others.
 */
class WatchNoteIngestor(
    private val notesRepository: JournalNotesRepository,
    private val audioStore: WatchAudioStore,
    private val acknowledger: WatchNoteAcknowledger,
    private val notifier: WatchNoteNotifier,
    private val noteDataMapper: NoteDataMapper = NoteDataMapper(),
) {
    private val noteLocks = ConcurrentHashMap<Uuid, Mutex>()

    private suspend fun <T> withNoteLock(
        noteId: Uuid,
        block: suspend () -> T,
    ): T = noteLocks.getOrPut(noteId) { Mutex() }.withLock { block() }

    /** @throws IllegalArgumentException if [data] carries no decodable note. */
    suspend fun onNoteMetadata(data: Map<String, String>) {
        val note = noteDataMapper.fromDataMap(data)
        withNoteLock(note.uid) {
            when {
                audioStore.isDeleted(note.uid) -> Napier.d("Ignoring note ${note.uid}: the watch deleted it")
                note is JournalNote.Audio -> receiveAudioMetadata(note, data)
                else -> receiveNote(note)
            }
        }
    }

    suspend fun onAudioBytes(
        noteId: Uuid,
        source: InputStream,
    ) = withNoteLock(noteId) {
        if (audioStore.isDeleted(noteId)) {
            Napier.d("Dropping audio for note $noteId: the watch deleted it")
            return@withNoteLock
        }
        if (notesRepository.getNoteById(noteId) != null) {
            acknowledger.acknowledge(noteId)
            return@withNoteLock
        }
        if (!audioStore.writeAudio(noteId, source)) {
            Napier.w("Could not store audio bytes from the watch for note $noteId")
            return@withNoteLock
        }
        storeIfComplete(noteId)
    }

    suspend fun onNoteDeleted(noteId: Uuid) =
        withNoteLock(noteId) {
            audioStore.markDeleted(noteId)
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
