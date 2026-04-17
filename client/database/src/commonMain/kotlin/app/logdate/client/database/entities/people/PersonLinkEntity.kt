package app.logdate.client.database.entities.people

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Entity(
    tableName = "person_links",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["person_id"]),
        Index(value = ["target_type", "target_id"]),
        Index(value = ["status"]),
        Index(value = ["provenance"]),
        Index(value = ["person_id", "target_type", "target_id"], unique = true),
    ],
)
data class PersonLinkEntity(
    @PrimaryKey
    val id: Uuid,
    @ColumnInfo(name = "person_id")
    val personId: Uuid,
    @ColumnInfo(name = "target_type")
    val targetType: String,
    @ColumnInfo(name = "target_id")
    val targetId: Uuid,
    val provenance: String,
    val confidence: Double,
    val status: String,
    val created: Instant,
    @ColumnInfo(name = "last_updated")
    val lastUpdated: Instant,
)
