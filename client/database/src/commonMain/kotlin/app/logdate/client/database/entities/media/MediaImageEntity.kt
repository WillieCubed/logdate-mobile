package app.logdate.client.database.entities.media

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "media_images"
)
data class MediaImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    /**
     * The URI of where this resource can be found.
     *
     * This must be a URI that's accessible from the device this database is stored.
     */
    val uri: String,
    /**
     * When this resource was inserted
     *
     * For purposes of accuracy, this only needs to reflect when the user initiated the insertion
     * on the client, not necessarily
     */
    val addedTimestamp: Instant,
    /**
     * When this resource was last updated.
     */
    val lastUpdated: Instant,
) {
    /**
     * The name of this entity.
     */
    val lastSegment: String
        get() = uri.substringAfterLast('/')
}