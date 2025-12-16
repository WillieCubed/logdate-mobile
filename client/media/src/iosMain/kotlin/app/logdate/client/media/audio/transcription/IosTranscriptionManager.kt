package app.logdate.client.media.audio.transcription

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import kotlin.concurrent.AtomicReference
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * iOS implementation of [TranscriptionManager].
 * 
 * This implementation provides a basic simulation of transcription jobs for iOS environments.
 * It uses NSFileManager to validate audio files and simulates the transcription process
 * with delays and logging.
 */
class IosTranscriptionManager(
    private val transcriptionService: TranscriptionService
) : TranscriptionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeJobs = AtomicReference<MutableMap<Uuid, Boolean>>(mutableMapOf())

    override suspend fun enqueueTranscription(noteId: Uuid, audioUri: String): Boolean {
        val currentJobs = activeJobs.value
        if (currentJobs.containsKey(noteId)) {
            Napier.w("Transcription job for note $noteId already in progress, ignoring new request")
            return false
        }
        
        // Check if the audio file exists
        val fileManager = NSFileManager.defaultManager
        val url = NSURL.URLWithString(audioUri) ?: run {
            Napier.e("Invalid audio URI: $audioUri")
            return false
        }
        
        if (!fileManager.fileExistsAtPath(url.path ?: "")) {
            Napier.e("Cannot enqueue transcription: Audio file does not exist at $audioUri")
            return false
        }
        
        Napier.d("Enqueueing transcription for note $noteId with audio at $audioUri")
        
        // Mark job as active
        val updatedJobs = currentJobs.toMutableMap()
        updatedJobs[noteId] = true
        activeJobs.value = updatedJobs
        
        // Start the transcription job
        scope.launch {
            try {
                // Simulate a background job with a delay
                Napier.d("Starting transcription job for note $noteId")
                delay(2.seconds) // Simulate initialization delay
                
                // Perform the actual transcription using the service
                val result = transcriptionService.transcribeAudioFile(audioUri)
                
                // Log the result
                when (result) {
                    is TranscriptionResult.Success -> {
                        Napier.d("Transcription completed for note $noteId: ${result.text}")
                    }
                    is TranscriptionResult.Error -> {
                        Napier.e("Transcription failed for note $noteId: ${result.message}")
                    }
                    is TranscriptionResult.InProgress -> {
                        Napier.d("Transcription in progress for note $noteId")
                    }
                }
            } catch (e: Exception) {
                Napier.e("Error in transcription job for note $noteId: ${e.message}")
            } finally {
                // Mark job as complete
                val finalJobs = activeJobs.value.toMutableMap()
                finalJobs.remove(noteId)
                activeJobs.value = finalJobs
            }
        }
        
        return true
    }
    
    override suspend fun cancelTranscription(noteId: Uuid): Boolean {
        val currentJobs = activeJobs.value
        val wasActive = currentJobs.containsKey(noteId)
        
        if (wasActive) {
            val updatedJobs = currentJobs.toMutableMap()
            updatedJobs.remove(noteId)
            activeJobs.value = updatedJobs
            Napier.d("Canceled transcription job for note $noteId")
        } else {
            Napier.d("No active transcription job found for note $noteId")
        }
        
        return wasActive
    }
    
    override suspend fun cancelAllTranscriptions(): Int {
        val currentJobs = activeJobs.value
        val count = currentJobs.size
        
        if (count > 0) {
            Napier.d("Canceling all transcription jobs (count: $count)")
            activeJobs.value = mutableMapOf()
        } else {
            Napier.d("No active transcription jobs to cancel")
        }
        
        return count
    }
}