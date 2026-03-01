package app.logdate.wear.repository

import android.content.Context
import app.logdate.client.repository.journals.JournalNote
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.time.Instant
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
     */
    suspend fun create(note: JournalNote): Uuid {
        try {
            // Add to our local cache
            val currentNotes = notes.value.toMutableList()
            currentNotes.add(note)
            notes.value = currentNotes
            
            // Log success
            Napier.d("Note created on Wear OS: ${note.uid}")
            
            return note.uid
        } catch (e: Exception) {
            Napier.e("Failed to create note on Wear OS", e)
            throw e
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
