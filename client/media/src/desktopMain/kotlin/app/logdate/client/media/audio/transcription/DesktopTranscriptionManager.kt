package app.logdate.client.media.audio.transcription

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * Desktop implementation of [TranscriptionManager].
 *
 * This implementation schedules file transcription through the configured [TranscriptionService].
 */
class DesktopTranscriptionManager(
    private val transcriptionService: TranscriptionService,
) : TranscriptionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Uuid, Boolean>()

    override suspend fun enqueueTranscription(
        noteId: Uuid,
        audioUri: String,
    ): Boolean {
        if (activeJobs.containsKey(noteId)) {
            Napier.w("Transcription job for note $noteId already in progress, ignoring new request")
            return false
        }

        // Check if the audio file exists
        val file = File(audioUri)
        if (!file.exists() || !file.isFile) {
            Napier.e("Cannot enqueue transcription: Audio file does not exist at $audioUri")
            return false
        }

        Napier.d("Enqueueing transcription for note $noteId with audio at $audioUri")

        // Mark job as active
        activeJobs[noteId] = true

        // Start the transcription job
        scope.launch {
            try {
                Napier.d("Starting transcription job for note $noteId")
                val result = transcriptionService.transcribeAudioFile(audioUri)

                // Log the result
                when (result) {
                    is TranscriptionResult.Success -> {
                        Napier.d("Transcription completed for note $noteId: ${result.text}")
                    }
                    is TranscriptionResult.Error -> {
                        Napier.e("Transcription failed for note $noteId: ${result.reason}")
                    }
                    is TranscriptionResult.InProgress -> {
                        Napier.d("Transcription in progress for note $noteId")
                    }
                }
            } catch (e: Exception) {
                Napier.e("Error in transcription job for note $noteId", e)
            } finally {
                // Mark job as complete
                activeJobs.remove(noteId)
            }
        }

        return true
    }

    override suspend fun cancelTranscription(noteId: Uuid): Boolean {
        // For the desktop implementation, we simply mark the job as inactive
        val wasActive = activeJobs.remove(noteId) != null

        if (wasActive) {
            Napier.d("Canceled transcription job for note $noteId")
        } else {
            Napier.d("No active transcription job found for note $noteId")
        }

        return wasActive
    }

    override suspend fun cancelAllTranscriptions(): Int {
        val count = activeJobs.size

        if (count > 0) {
            Napier.d("Canceling all transcription jobs (count: $count)")
            activeJobs.clear()
        } else {
            Napier.d("No active transcription jobs to cancel")
        }

        return count
    }
}
