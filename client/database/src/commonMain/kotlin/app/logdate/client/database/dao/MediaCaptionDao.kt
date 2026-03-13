package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.MediaCaptionEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface MediaCaptionDao {
    /**
     * Returns a flow of all captions, keyed by note ID.
     * Emits on every insert, update, or delete.
     */
    @Query("SELECT * FROM media_captions")
    fun observeAll(): Flow<List<MediaCaptionEntity>>

    /**
     * Returns the caption for a single note, or null if none has been saved.
     */
    @Query("SELECT * FROM media_captions WHERE noteId = :noteId")
    suspend fun getCaption(noteId: Uuid): MediaCaptionEntity?

    /**
     * Inserts or replaces the caption for a note.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCaption(entity: MediaCaptionEntity)

    /**
     * Removes the caption for a note when the note itself is deleted.
     */
    @Query("DELETE FROM media_captions WHERE noteId = :noteId")
    suspend fun deleteCaption(noteId: Uuid)
}
