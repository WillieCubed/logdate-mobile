package app.logdate.wear.repository

import android.content.Context
import app.logdate.client.repository.journals.JournalNote
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant
import java.io.File
import kotlin.uuid.Uuid

/**
 * Simplified repository implementation for Wear OS.
 * 
 * Handles basic note storage with these optimizations for wearables:
 * - Minimal local storage usage
 * - Simplified data sync for reduced battery impact
 * - Lightweight operations for limited processing power
 */
class WearJournalNotesRepository(
    private val context: Context
) {
    private val notes = MutableStateFlow<List<JournalNote>>(emptyList())
    
    /**
     * Creates a new note and stores it locally on the watch.
     * For audio notes, copies the temporary recording to permanent storage.
     */
    suspend fun create(note: JournalNote): Uuid {
        try {
            // Handle different note types
            val processedNote = when (note) {
                is JournalNote.Audio -> processAudioNote(note)
                else -> note
            }
            
            // Add to our local cache
            val currentNotes = notes.value.toMutableList()
            currentNotes.add(processedNote)
            notes.value = currentNotes
            
            // Log success
            Napier.d("Note created on Wear OS: ${processedNote.uid}")
            
            return processedNote.uid
        } catch (e: Exception) {
            Napier.e("Failed to create note on Wear OS", e)
            throw e
        }
    }
    
    /**
     * Process an audio note by moving the temporary recording file to a permanent location.
     */
    private suspend fun processAudioNote(note: JournalNote.Audio): JournalNote.Audio {
        val sourceFile = File(note.mediaRef)
        if (!sourceFile.exists()) {
            Napier.e("Audio file doesn't exist: ${note.mediaRef}")
            return note
        }
        
        try {
            // Create directory for audio files if it doesn't exist
            val audioDir = File(context.filesDir, "audio_notes")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            
            // Create a permanent file with the note's UUID
            val destFile = File(audioDir, "${note.uid}.m4a")
            
            // Copy the file
            sourceFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // If copy was successful, create new note with updated path
            return if (destFile.exists()) {
                Napier.d("Audio file copied to: ${destFile.absolutePath}")
                note.copy(mediaRef = destFile.absolutePath)
            } else {
                Napier.e("Failed to copy audio file")
                note
            }
        } catch (e: Exception) {
            Napier.e("Error processing audio note", e)
            return note
        }
    }
    
    /**
     * Gets all notes as a flow.
     */
    fun getAllNotes(): Flow<List<JournalNote>> {
        return notes.asStateFlow()
    }
    
    /**
     * Gets recent notes (used for timeline display).
     */
    fun getRecentNotes(limit: Int = 10): Flow<List<JournalNote>> {
        // For simplicity, just return all notes
        // In a real implementation, this would filter by date
        return notes.asStateFlow()
    }
    
    /**
     * Gets notes created on a specific day.
     */
    fun getNotesForDay(day: Instant): Flow<List<JournalNote>> {
        // For simplicity, just return all notes
        // In a real implementation, this would filter by date
        return notes.asStateFlow()
    }
    
    /**
     * Removes a note.
     */
    suspend fun remove(noteId: Uuid) {
        try {
            // Find the note
            val noteToRemove = notes.value.find { it.uid == noteId }
            
            // Delete associated file if it's an audio note
            if (noteToRemove is JournalNote.Audio) {
                val audioFile = File(noteToRemove.mediaRef)
                if (audioFile.exists()) {
                    audioFile.delete()
                    Napier.d("Deleted audio file: ${noteToRemove.mediaRef}")
                }
            }
            
            // Remove from our local cache
            val updatedNotes = notes.value.filter { it.uid != noteId }
            notes.value = updatedNotes
            
            Napier.d("Note removed from Wear OS: $noteId")
        } catch (e: Exception) {
            Napier.e("Failed to remove note from Wear OS", e)
            throw e
        }
    }
}