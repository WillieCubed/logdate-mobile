package app.logdate.client.repository.transcription

import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Repository for managing audio transcriptions.
 */
interface TranscriptionRepository {
    
    /**
     * Requests transcription for an audio note.
     * 
     * @param noteId The ID of the audio note to transcribe.
     * @return true if the transcription request was successfully queued, false otherwise.
     */
    suspend fun requestTranscription(noteId: Uuid): Boolean
    
    /**
     * Gets the current transcription for an audio note.
     * 
     * @param noteId The ID of the audio note.
     * @return The transcription data, or null if no transcription exists.
     */
    suspend fun getTranscription(noteId: Uuid): TranscriptionData?
    
    /**
     * Observes the transcription for an audio note.
     * 
     * @param noteId The ID of the audio note.
     * @return A Flow emitting the transcription data whenever it changes.
     */
    fun observeTranscription(noteId: Uuid): Flow<TranscriptionData?>
    
    /**
     * Observes the transcription for an audio note.
     * 
     * @param note The audio note.
     * @return A Flow emitting the transcription data whenever it changes.
     */
    fun observeTranscription(note: JournalNote.Audio): Flow<TranscriptionData?> =
        observeTranscription(note.uid)
    
    /**
     * Gets all pending transcriptions.
     * 
     * @return A list of pending transcription data.
     */
    suspend fun getPendingTranscriptions(): List<TranscriptionData>
    
    /**
     * Updates the transcription for an audio note.
     * 
     * @param noteId The ID of the audio note.
     * @param text The transcribed text.
     * @param status The new status of the transcription.
     * @param errorMessage An optional error message if the transcription failed.
     * @return true if the update was successful, false otherwise.
     */
    suspend fun updateTranscription(
        noteId: Uuid,
        text: String?,
        status: TranscriptionStatus,
        errorMessage: String? = null
    ): Boolean
    
    /**
     * Deletes the transcription for an audio note.
     * 
     * @param noteId The ID of the audio note.
     * @return true if the deletion was successful, false otherwise.
     */
    suspend fun deleteTranscription(noteId: Uuid): Boolean
}