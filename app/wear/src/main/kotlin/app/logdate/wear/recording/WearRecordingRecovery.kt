package app.logdate.wear.recording

import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.AudioRemuxer
import app.logdate.client.media.audio.CrashSafeRecording
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.SystemCaptureTimeZone
import app.logdate.wear.presentation.recording.WearRecordingViewModel
import io.github.aakira.napier.Napier
import java.io.File
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Turns recordings a dead process left behind into notes, so killing the app mid-recording or
 * between stopping and saving does not lose the audio.
 *
 * A crash-safe session writes raw AAC beside the m4a it will become, and the raw file stays until a
 * note exists for the recording. That raw file is what marks a recording as unsaved: an m4a with no
 * raw file beside it was saved, deleted, or pulled from the phone, and is never adopted. Audio pulled
 * from the phone can itself end in .aac under the same name, so a file a note references is never
 * treated as a raw recording. Run once at
 * launch with the time the process started as the cutoff, so a recording the user starts right after
 * launch is never mistaken for one a dead process left.
 *
 * Nothing is deleted unless it is empty, too short to keep, or already has a note. A recording that
 * cannot be recovered yet stays for the next launch.
 */
class WearRecordingRecovery(
    private val audioDirectory: File,
    private val notesRepository: JournalNotesRepository,
    private val durationResolver: AudioDurationResolver,
    private val remuxer: AudioRemuxer,
) {
    /**
     * @param modifiedBefore Only raw files last written before this epoch millisecond are considered.
     * @return how many notes were created.
     */
    suspend fun recover(modifiedBefore: Long = Long.MAX_VALUE): Int = rawRecordings(modifiedBefore).count { recover(it) }

    private fun rawRecordings(modifiedBefore: Long): List<File> =
        audioDirectory
            .listFiles()
            .orEmpty()
            .filter {
                it.isFile &&
                    it.name.startsWith(RECORDING_PREFIX) &&
                    it.extension == CrashSafeRecording.IN_FLIGHT_EXTENSION &&
                    it.lastModified() < modifiedBefore
            }

    private suspend fun recover(raw: File): Boolean {
        if (isReferenced(raw)) return false
        val final = CrashSafeRecording.finalFile(raw)
        if (!final.isFile && !wrap(raw, final)) return false
        if (isReferenced(final)) {
            raw.delete()
            return false
        }
        val stored = storeAsNote(final, endedAtMs = raw.lastModified())
        if (stored || !final.exists()) raw.delete()
        return stored
    }

    private suspend fun isReferenced(file: File): Boolean = notesRepository.notesReferencingMediaPaths(setOf(file.absolutePath)).isNotEmpty()

    private fun wrap(
        raw: File,
        final: File,
    ): Boolean {
        if (remuxer.remuxToM4a(raw, final)) return true
        if (raw.length() == 0L) {
            raw.delete()
        } else {
            Napier.e("Could not recover ${raw.name}; it stays for the next launch")
        }
        return false
    }

    private suspend fun storeAsNote(
        file: File,
        endedAtMs: Long,
    ): Boolean {
        if (file.length() == 0L) {
            file.delete()
            return false
        }
        val durationMs = durationResolver.resolveDurationMs(file.absolutePath)
        if (durationMs == null) {
            Napier.e("Could not read the length of ${file.name}; it stays for the next launch")
            return false
        }
        if (durationMs < WearRecordingViewModel.MIN_DURATION_MS) {
            file.delete()
            return false
        }
        return try {
            notesRepository.create(
                JournalNote.Audio(
                    mediaRef = file.absolutePath,
                    uid = Uuid.random(),
                    creationTimestamp = Instant.fromEpochMilliseconds(endedAtMs - durationMs),
                    lastUpdated = Clock.System.now(),
                    durationMs = durationMs,
                    timeZoneId = SystemCaptureTimeZone.currentTimeZoneId(),
                ),
            )
            Napier.i("Recovered ${file.name} as a note")
            true
        } catch (e: Exception) {
            Napier.e("Could not save the recovered recording ${file.name}", e)
            false
        }
    }

    private companion object {
        const val RECORDING_PREFIX = "recording_"
    }
}
