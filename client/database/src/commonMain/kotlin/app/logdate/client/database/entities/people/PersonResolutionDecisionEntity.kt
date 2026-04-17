package app.logdate.client.database.entities.people

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Entity(
    tableName = "person_resolution_decisions",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["normalized_name"], unique = true),
        Index(value = ["person_id"]),
    ],
)
data class PersonResolutionDecisionEntity(
    @PrimaryKey
    val id: Uuid,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    val action: String,
    @ColumnInfo(name = "person_id")
    val personId: Uuid? = null,
    val created: Instant,
    @ColumnInfo(name = "last_updated")
    val lastUpdated: Instant,
)
