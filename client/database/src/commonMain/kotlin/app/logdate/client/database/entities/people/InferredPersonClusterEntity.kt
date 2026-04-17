package app.logdate.client.database.entities.people

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Entity(
    tableName = "inferred_person_clusters",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["linked_person_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("status"),
        Index(value = ["linked_person_id"]),
        Index(value = ["normalized_name"]),
    ],
)
data class InferredPersonClusterEntity(
    @PrimaryKey
    val id: Uuid,
    @ColumnInfo(name = "display_name_hint")
    val displayNameHint: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    val status: String,
    @ColumnInfo(name = "linked_person_id")
    val linkedPersonId: Uuid? = null,
    val created: Instant,
    @ColumnInfo(name = "last_updated")
    val lastUpdated: Instant,
)
