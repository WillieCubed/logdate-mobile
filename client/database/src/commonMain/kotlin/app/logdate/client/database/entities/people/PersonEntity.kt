package app.logdate.client.database.entities.people

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Entity(
    tableName = "people",
    indices = [
        Index("name"),
        Index(value = ["contact_lookup_key"], unique = true),
        Index("origin"),
    ],
)
data class PersonEntity(
    @PrimaryKey
    val id: Uuid,
    val name: String,
    @ColumnInfo(name = "photo_uri")
    val photoUri: String? = null,
    val aliases: List<String> = emptyList(),
    @ColumnInfo(name = "relationship_label")
    val relationshipLabel: String? = null,
    val notes: String? = null,
    val origin: String,
    @ColumnInfo(name = "contact_lookup_key")
    val contactLookupKey: String? = null,
    val created: Instant,
    @ColumnInfo(name = "last_updated")
    val lastUpdated: Instant,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Instant? = null,
)
