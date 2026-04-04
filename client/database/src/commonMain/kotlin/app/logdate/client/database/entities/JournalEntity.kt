package app.logdate.client.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Entity(
    tableName = "journals",
)
data class JournalEntity(
    @PrimaryKey
    val id: Uuid = Uuid.random(), // Using Kotlin's built-in Uuid as primary key
    val title: String,
    val description: String,
    val created: Instant,
    val lastUpdated: Instant,
    val syncVersion: Long = 0,
    val lastSynced: Instant? = null,
    val deletedAt: Instant? = null,
    val coverImageUri: String? = null,
)
