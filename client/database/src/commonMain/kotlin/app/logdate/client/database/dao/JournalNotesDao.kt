package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import app.logdate.client.database.entities.JournalWithNotes
import app.logdate.client.database.entities.NoteJournals
import kotlinx.coroutines.flow.Flow

/**
 * A DAO that provides methods for interacting with note-journal relationships.
 */
@Dao
interface JournalNotesDao {
    /**
     * Queries all journals and their notes.
     */
    @Transaction
    @Query("SELECT * FROM journals")
    suspend fun getAll(): List<JournalWithNotes>

    /**
     * Observes all journals and their notes.
     */
    @Transaction
    @Query("SELECT * FROM journals")
    fun observeAll(): Flow<List<JournalWithNotes>>

    /**
     * Gets all notes for the journal with the given ID.
     */
    @Transaction
    @Query("SELECT * FROM journal_notes INNER JOIN journals ON journal_notes.id = journals.id WHERE journal_notes.id = :journalId")
    fun getNotesForJournal(journalId: Int): Flow<List<NoteJournals>>

    /**
     * Adds a note to the journal with the given ID.
     */
    @Transaction
    @Query("INSERT INTO journal_notes (id, uid) VALUES (:journalId, :noteId)")
    suspend fun addNoteToJournal(journalId: Int, noteId: Int)

    /**
     * Removes a note from the journal with the given ID.
     */
    @Query("DELETE FROM journal_notes WHERE id = :journalId AND uid = :noteId")
    suspend fun removeNoteFromJournal(journalId: Int, noteId: Int)

    /**
     * Removes a note from all journals.
     *
     * This can be useful when a note is deleted and should be removed from all journals that
     * reference it.
     */
    @Query("DELETE FROM journal_notes WHERE uid = :noteId")
    suspend fun deleteNoteFromAllJournals(noteId: Int)
}