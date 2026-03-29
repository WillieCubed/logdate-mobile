package app.logdate.client.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Room entity storing Postcard metadata alongside the serialized document JSON.
 *
 * The full [PostcardDocument] is stored as a JSON string in [documentJson].
 * Metadata fields are extracted into columns for efficient querying (list views,
 * sorting by date) without deserializing the document.
 */
@Entity(
    tableName = "postcards",
    indices = [
        Index("modified_at"),
        Index("source_moment_ref"),
    ],
)
data class PostcardEntity(
    @PrimaryKey
    val id: Uuid,
    val title: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "modified_at")
    val modifiedAt: Instant,
    @ColumnInfo(name = "source_moment_ref")
    val sourceMomentRef: Uuid? = null,
    @ColumnInfo(name = "document_json")
    val documentJson: String,
)
