package app.logdate.client.database.entities.journals

import androidx.room.Entity
import kotlin.uuid.Uuid

@Entity(tableName = "journal_merges", primaryKeys = ["ownerId", "serverOrigin", "sourceId"])
data class JournalMergeEntity(
    val ownerId: String,
    val serverOrigin: String,
    val sourceId: Uuid,
    val destinationId: Uuid,
    val operationId: Uuid,
    val contentIds: String,
    val sourceTitle: String,
    val sourceJournal: String,
    val destinationTitle: String,
    val pending: Boolean,
    val createdAt: Long,
    val requestedDestinationId: Uuid = destinationId,
    val needsDestination: Boolean = false,
    val previousOperationIds: String = "[]",
    val recoveryContentIds: String = "[]",
)
