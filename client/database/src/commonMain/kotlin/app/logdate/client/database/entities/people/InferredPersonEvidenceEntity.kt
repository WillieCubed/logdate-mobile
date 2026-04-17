package app.logdate.client.database.entities.people

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Entity(
    tableName = "inferred_person_evidence",
    foreignKeys = [
        ForeignKey(
            entity = InferredPersonClusterEntity::class,
            parentColumns = ["id"],
            childColumns = ["cluster_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["cluster_id"]),
        Index(value = ["source_type", "source_id"]),
    ],
)
data class InferredPersonEvidenceEntity(
    @PrimaryKey
    val id: Uuid,
    @ColumnInfo(name = "cluster_id")
    val clusterId: Uuid,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    @ColumnInfo(name = "source_id")
    val sourceId: Uuid,
    val label: String? = null,
    val confidence: Double,
    val created: Instant,
)
