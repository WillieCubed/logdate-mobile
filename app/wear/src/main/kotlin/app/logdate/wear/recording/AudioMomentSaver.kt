package app.logdate.wear.recording

import app.logdate.client.domain.activity.CaptureActivitySource
import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.CrashSafeRecording
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.SystemCaptureTimeZone
import app.logdate.wear.capture.WatchCaptureWriteGuard
import app.logdate.wear.health.NoteHealthAnnotator
import app.logdate.wear.location.WearLocationCaptureCoordinator
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.uuid.Uuid

/** What became of a finished recording handed to [AudioMomentSaver.save]. */
sealed interface AudioSaveResult {
    /** The recording is a note now, and the raw file that marked it unsaved is gone. */
    data class Saved(
        val note: JournalNote.Audio,
    ) : AudioSaveResult

    /** Shorter than [AudioMomentSaver.MIN_DURATION_MS]. The file and its raw file are deleted. */
    data object TooShort : AudioSaveResult

    /**
     * The note could not be stored. The file and its raw file stay on disk, so saving again, or
     * startup recovery, can still make it a note.
     */
    data class Failed(
        val path: String,
    ) : AudioSaveResult
}

/**
 * Makes a finished recording an audio note: its length, where it was made, its health annotation,
 * and the removal of the raw file that marks it unsaved for startup recovery. Both watch recorders
 * save through it.
 *
 * The note is created with the recording's file path. The notes repository stores that as the
 * portable `logdate-media://` reference, so nothing here spells a reference itself.
 */
class AudioMomentSaver(
    private val notesRepository: JournalNotesRepository,
    private val durationResolver: AudioDurationResolver,
    private val noteHealthAnnotator: NoteHealthAnnotator,
    private val locationCaptureCoordinator: WearLocationCaptureCoordinator,
    private val clock: Clock = Clock.System,
    private val deleteFile: (String) -> Unit = { path -> File(path).delete() },
    private val captureGuard: WatchCaptureWriteGuard = WatchCaptureWriteGuard.Unrestricted,
    private val captureActivity: CaptureActivitySource = CaptureActivitySource.None,
) {
    /**
     * Saves the recording at [path]. [recorderElapsed] is the recorder's own clock. It counts when the
     * file's length doesn't read as a real recording, so a metadata hiccup can't make a long
     * recording look too short to save.
     */
    suspend fun save(
        path: String,
        recorderElapsed: Duration,
    ): AudioSaveResult {
        val durationMs = recordedDurationMs(path, recorderElapsed)
        if (durationMs < MIN_DURATION_MS) {
            discard(path)
            return AudioSaveResult.TooShort
        }
        return try {
            AudioSaveResult.Saved(persist(path, durationMs))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Failed to save recording $path", e)
            AudioSaveResult.Failed(path)
        }
    }

    /** Deletes the recording at [path] and the raw file its session left beside it. */
    fun discard(path: String) {
        deleteFile(path)
        deleteRawRecording(path)
    }

    private suspend fun recordedDurationMs(
        path: String,
        recorderElapsed: Duration,
    ): Long {
        val resolved = durationResolver.resolveDurationMs(path)
        if (resolved != null && resolved >= MIN_DURATION_MS) return resolved
        return maxOf(resolved ?: 0L, recorderElapsed.inWholeMilliseconds)
    }

    private suspend fun persist(
        path: String,
        durationMs: Long,
    ): JournalNote.Audio =
        captureGuard.write {
            val now = clock.now()
            val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) { locationCaptureCoordinator.captureForJournalEntry() }
            val note =
                JournalNote.Audio(
                    mediaRef = path,
                    uid = Uuid.random(),
                    creationTimestamp = now,
                    lastUpdated = now,
                    durationMs = durationMs,
                    location = location,
                    timeZoneId = SystemCaptureTimeZone.currentTimeZoneId(),
                    capturedDuring = captureActivity.now(),
                )
            notesRepository.create(note)
            // The raw file marks a recording as unsaved for startup recovery, so it goes once the note exists.
            deleteRawRecording(path)
            noteHealthAnnotator.annotate(note.uid)
            note
        }

    private fun deleteRawRecording(path: String) {
        val raw = CrashSafeRecording.inFlightFile(File(path))
        if (raw.isFile) deleteFile(raw.path)
    }

    companion object {
        /** A recording shorter than this is a slip of the finger, not a moment. */
        const val MIN_DURATION_MS = 500L

        /** How long a save waits for the place before saving without it. */
        const val LOCATION_TIMEOUT_MS = 3_000L
    }
}
