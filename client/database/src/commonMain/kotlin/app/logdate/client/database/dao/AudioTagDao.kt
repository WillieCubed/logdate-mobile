package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.logdate.client.database.entities.AudioTagEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Data access for [AudioTagEntity] — the ambient sounds detected on an
 * audio note by the on-device tagger.
 */
@Dao
interface AudioTagDao {
    /**
     * Inserts the provided tags. Existing rows for the note are NOT cleared
     * here; callers that want to overwrite a note's tag set should call
     * [replaceTagsForNote] instead.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<AudioTagEntity>)

    /**
     * Atomically replaces the entire tag set for [noteId] with [tags]. The
     * common write path on this DAO — the tagger emits cumulative results
     * over a recording, and on each emission we want the database to reflect
     * exactly what the tagger has seen so far.
     */
    @Transaction
    suspend fun replaceTagsForNote(
        noteId: Uuid,
        tags: List<AudioTagEntity>,
    ) {
        deleteTagsForNote(noteId)
        if (tags.isNotEmpty()) {
            insertTags(tags)
        }
    }

    @Query("SELECT * FROM audio_tags WHERE noteId = :noteId ORDER BY confidence DESC")
    suspend fun getTagsForNote(noteId: Uuid): List<AudioTagEntity>

    @Query("SELECT * FROM audio_tags WHERE noteId = :noteId ORDER BY confidence DESC")
    fun observeTagsForNote(noteId: Uuid): Flow<List<AudioTagEntity>>

    /**
     * Returns notes that have at least one tag whose [AudioTagEntity.soundName]
     * matches [soundName] (case-insensitive). Used to power "find notes with
     * birds" style search.
     */
    @Query(
        """
        SELECT DISTINCT noteId FROM audio_tags
        WHERE soundName LIKE :soundName COLLATE NOCASE
        ORDER BY confidence DESC
        """,
    )
    suspend fun findNotesBySoundName(soundName: String): List<Uuid>

    @Query("DELETE FROM audio_tags WHERE noteId = :noteId")
    suspend fun deleteTagsForNote(noteId: Uuid): Int
}
