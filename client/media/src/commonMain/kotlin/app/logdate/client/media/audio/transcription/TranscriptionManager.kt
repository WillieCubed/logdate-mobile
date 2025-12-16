package app.logdate.client.media.audio.transcription

import kotlin.uuid.Uuid

/**
 * Manager for handling audio transcription jobs.
 * This interface abstracts the platform-specific implementation of scheduling
 * and managing transcription jobs.
 */
interface TranscriptionManager {
    /**
     * Enqueues an audio file for transcription.
     * 
     * @param noteId The ID of the audio note to transcribe.
     * @param audioUri The URI of the audio file to transcribe.
     * @return true if the transcription job was successfully enqueued, false otherwise.
     */
    suspend fun enqueueTranscription(noteId: Uuid, audioUri: String): Boolean
    
    /**
     * Cancels a pending transcription job.
     * 
     * @param noteId The ID of the audio note whose transcription job should be canceled.
     * @return true if the job was canceled, false otherwise.
     */
    suspend fun cancelTranscription(noteId: Uuid): Boolean
    
    /**
     * Cancels all pending transcription jobs.
     * 
     * @return The number of jobs canceled.
     */
    suspend fun cancelAllTranscriptions(): Int
}