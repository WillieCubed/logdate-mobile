package app.logdate.client.database.entities.sync

import androidx.room.Entity

/** Original wire payloads belong to recovery state, never diagnostic reports. Settled rows retain version watermarks. */
@Entity(tableName = "sync_download_inbox", primaryKeys = ["ownerId", "serverOrigin", "entityType", "entityId"])
data class DownloadInboxEntity(
    val ownerId: String,
    val serverOrigin: String,
    val entityType: String,
    val entityId: String,
    val version: Long,
    val deleted: Boolean,
    val payload: String,
    val operationId: String,
    val state: String,
    val attempts: Int,
    val nextAttemptAt: Long,
    val reason: String,
)

@Entity(tableName = "sync_download_checkpoints", primaryKeys = ["ownerId", "serverOrigin", "entityType"])
data class DownloadCheckpointEntity(
    val ownerId: String,
    val serverOrigin: String,
    val entityType: String,
    val cursor: Long,
    val fetchFailure: String = "NONE",
)
