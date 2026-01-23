package app.logdate.client.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

@Entity(
    tableName = "image_notes",
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["place_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("place_id"),
        Index("latitude", "longitude")
    ]
)
data class ImageNoteEntity(
    val contentUri: String,
    @PrimaryKey
    override val uid: Uuid = Uuid.random(),
    override val lastUpdated: Instant,
    override val created: Instant,
    override val syncVersion: Long = 0,
    override val lastSynced: Instant? = null,
    override val deletedAt: Instant? = null,
    override val latitude: Double? = null,
    override val longitude: Double? = null,
    override val altitude: Double? = null,
    @ColumnInfo(name = "location_accuracy")
    override val locationAccuracy: Float? = null,
    @ColumnInfo(name = "place_id")
    override val placeId: Uuid? = null,
) : GenericNoteData()