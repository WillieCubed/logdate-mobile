package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.AudioNoteEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Dao
interface AudioNoteDao {
    /**
     * Retrieves an observable [AudioNoteEntity] by the given ID.
     */
    @Query("SELECT * FROM audio_notes WHERE uid = :uid")
    fun getNote(uid: Uuid): Flow<AudioNoteEntity>

    /**
     * Fetches a non-observable [AudioNoteEntity] by its ID.
     */
    @Query("SELECT * FROM audio_notes WHERE uid = :uid")
    suspend fun getNoteOneOff(uid: Uuid): AudioNoteEntity

    /**
     * Returns a flow of all [AudioNoteEntity]s.
     */
    @Query("SELECT * FROM audio_notes ORDER BY created DESC")
    fun getAllNotes(): Flow<List<AudioNoteEntity>>

    /**
     * Returns all audio notes as a list (for quota calculation).
     */
    @Query("SELECT * FROM audio_notes")
    suspend fun getAll(): List<AudioNoteEntity>

    /**
     * Fast query for recent notes to enable immediate timeline display.
     */
    @Query("SELECT * FROM audio_notes ORDER BY created DESC LIMIT :limit")
    fun getRecentNotes(limit: Int = 20): Flow<List<AudioNoteEntity>>

    @Query("SELECT * FROM audio_notes WHERE created < :beforeTimestamp ORDER BY created DESC LIMIT :limit")
    suspend fun getRecentNotesBefore(
        beforeTimestamp: Long,
        limit: Int,
    ): List<AudioNoteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM audio_notes WHERE created < :beforeTimestamp)")
    suspend fun hasNotesBefore(beforeTimestamp: Long): Boolean

    /**
     * Returns notes within a specific date range.
     */
    @Query("SELECT * FROM audio_notes WHERE created BETWEEN :startTimestamp AND :endTimestamp ORDER BY created DESC")
    fun getNotesInRange(
        startTimestamp: Long,
        endTimestamp: Long,
    ): Flow<List<AudioNoteEntity>>

    /**
     * Inserts a [note] into the DB if it doesn't exist, and ignores it if it does.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addNote(note: AudioNoteEntity)

    /**
     * Removes the given note from the DB.
     */
    @Query("DELETE FROM audio_notes WHERE uid = :noteId")
    suspend fun removeNote(noteId: Uuid)

    /**
     * Removes the given notes from the DB.
     */
    @Query("DELETE FROM audio_notes WHERE uid IN (:noteIds)")
    suspend fun removeNote(noteIds: List<Uuid>)

    @Query("UPDATE audio_notes SET syncVersion = :syncVersion, lastSynced = :lastSynced WHERE uid = :noteId")
    suspend fun updateSyncMetadata(
        noteId: Uuid,
        syncVersion: Long,
        lastSynced: Instant,
    )

    @Query("UPDATE audio_notes SET contentUri = :contentUri WHERE uid = :noteId")
    suspend fun updateContentUri(
        noteId: Uuid,
        contentUri: String,
    )
}
