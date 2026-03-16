package app.logdate.client.database.dao.media

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.logdate.client.database.entities.media.MediaExifMetadataEntity
import kotlin.uuid.Uuid

/**
 * Data access object for EXIF/camera metadata.
 */
@Dao
interface MediaExifDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MediaExifMetadataEntity)

    @Query("SELECT * FROM media_exif_metadata WHERE mediaUid = :mediaUid")
    suspend fun getByMediaUid(mediaUid: Uuid): MediaExifMetadataEntity?

    @Query("DELETE FROM media_exif_metadata WHERE mediaUid = :mediaUid")
    suspend fun deleteByMediaUid(mediaUid: Uuid)
}
