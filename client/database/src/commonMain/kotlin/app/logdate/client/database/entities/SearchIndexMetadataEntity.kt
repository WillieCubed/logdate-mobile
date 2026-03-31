package app.logdate.client.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_index_metadata")
data class SearchIndexMetadataEntity(
    @PrimaryKey
    val id: Int = 1,
    val schemaVersion: Long = 0,
    val generation: Long = 0,
    val lastRebuiltAt: Long = 0,
)
