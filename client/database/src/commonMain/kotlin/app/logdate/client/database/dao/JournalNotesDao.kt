package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import app.logdate.client.database.entities.JournalEntity
import app.logdate.client.database.entities.JournalNoteCrossRef
import app.logdate.client.database.entities.JournalWithNotes
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * A DAO that provides methods for interacting with note-journal relationships.
 * 
 * This interface has been updated to use string IDs for journals.
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
    @Query("SELECT * FROM journal_notes WHERE id = :journalId")
    fun getNotesForJournal(journalId: Uuid): Flow<List<JournalNoteCrossRef>>

    /**
     * Gets all journals for a specific note.
     */
    @Query("SELECT journals.* FROM journals INNER JOIN journal_notes ON journals.id = journal_notes.id WHERE journal_notes.uid = :noteId")
    suspend fun journalsForNoteSync(noteId: Uuid): List<JournalEntity>

    /**
     * Observes all journals associated with a note.
     */
    @Query("SELECT journals.* FROM journals INNER JOIN journal_notes ON journals.id = journal_notes.id WHERE journal_notes.uid = :noteId")
    fun observeJournalsForNote(noteId: Uuid): Flow<List<JournalEntity>>

    /**
     * Adds a note to the journal with the given ID.
     */
    @Transaction
    @Query("INSERT INTO journal_notes (id, uid) VALUES (:journalId, :noteId)")
    suspend fun addNoteToJournal(journalId: Uuid, noteId: Uuid)

    /**
     * Removes a note from the journal with the given ID.
     */
    @Query("DELETE FROM journal_notes WHERE id = :journalId AND uid = :noteId")
    suspend fun removeNoteFromJournal(journalId: Uuid, noteId: Uuid)

    /**
     * Removes a note from all journals.
     *
     * This can be useful when a note is deleted and should be removed from all journals that
     * reference it.
     */
    @Query("DELETE FROM journal_notes WHERE uid = :noteId")
    suspend fun deleteNoteFromAllJournals(noteId: Uuid)
}