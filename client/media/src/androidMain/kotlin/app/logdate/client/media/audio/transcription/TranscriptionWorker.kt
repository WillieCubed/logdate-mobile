package app.logdate.client.media.audio.transcription

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.logdate.client.media.audio.transcription.AndroidTranscriptionManager.Companion.KEY_AUDIO_URI
import app.logdate.client.media.audio.transcription.AndroidTranscriptionManager.Companion.KEY_NOTE_ID
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.repository.transcription.TranscriptionStatus
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

/**
 * Worker that handles audio transcription in the background.
 * 
 * This worker:
 * 1. Retrieves the audio file URI from input data
 * 2. Updates the transcription status to IN_PROGRESS
 * 3. Calls the TranscriptionService to transcribe the audio
 * 4. Updates the transcription with the result
 */
class TranscriptionWorker(
    context: Context, 
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    // Inject dependencies from Koin
    private val transcriptionRepository: TranscriptionRepository by inject()
    private val transcriptionService: TranscriptionService by inject()
    
    companion object {
        const val TAG = "TranscriptionWorker"
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Get input data
            val noteIdString = inputData.getString(KEY_NOTE_ID)
                ?: return@withContext Result.failure()
                
            val audioUri = inputData.getString(KEY_AUDIO_URI)
                ?: return@withContext Result.failure()
            
            val noteId = try {
                Uuid.parse(noteIdString)
            } catch (e: Exception) {
                Napier.e("Invalid note ID: $noteIdString", e)
                return@withContext Result.failure()
            }
            
            Napier.d("Starting transcription for note $noteId, URI: $audioUri")
            
            // Update status to IN_PROGRESS
            val updateSuccess = transcriptionRepository.updateTranscription(
                noteId = noteId,
                text = null,
                status = TranscriptionStatus.IN_PROGRESS
            )
            
            if (!updateSuccess) {
                Napier.e("Failed to update transcription status to IN_PROGRESS")
                return@withContext Result.retry()
            }
            
            // Perform transcription
            val result = transcriptionService.transcribeAudioFile(audioUri)
            
            // Update transcription with result
            when (result) {
                is TranscriptionResult.Success -> {
                    Napier.d("Transcription successful for note $noteId")
                    transcriptionRepository.updateTranscription(
                        noteId = noteId,
                        text = result.text,
                        status = TranscriptionStatus.COMPLETED
                    )
                    Result.success()
                }
                is TranscriptionResult.Error -> {
                    Napier.e("Transcription failed for note $noteId: ${result.message}")
                    transcriptionRepository.updateTranscription(
                        noteId = noteId,
                        text = null,
                        status = TranscriptionStatus.FAILED,
                        errorMessage = result.message
                    )
                    Result.failure()
                }
                is TranscriptionResult.InProgress -> {
                    // This should not happen with transcribeAudioFile, but just in case
                    Napier.w("Transcription still in progress, will retry")
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Napier.e("Error in TranscriptionWorker", e)
            Result.failure()
        }
    }
}