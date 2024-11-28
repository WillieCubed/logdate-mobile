package app.logdate.client.database.dao

import androidx.room.Dao
import androidx.room.Query
import app.logdate.client.database.entities.media.MediaImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserMediaDao {
    @Query("SELECT * FROM media_images")
    fun getAllImageMedia(): Flow<MediaImageEntity>

    @Query("SELECT * FROM media_images")
    fun getAllVideoMedia(): Flow<MediaImageEntity>

    /**
     * Removes a media item given its URI.
     */
    @Query("DELETE FROM media_images WHERE uri = :uri")
    fun removeImageMedia(uri: String): Int
}