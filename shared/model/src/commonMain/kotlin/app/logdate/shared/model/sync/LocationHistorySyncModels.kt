package app.logdate.shared.model.sync

import kotlinx.serialization.Serializable

/** Only opaque ciphertext and synchronization metadata may cross this boundary. */
@Serializable
data class LocationHistoryUpload(
    val id: String,
    val recordType: String,
    val payload: String?,
    val payloadSchemaVersion: Int = 1,
    val deviceId: String,
    val deviceVersion: Long,
    val expectedServerVersion: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class LocationHistoryRecord(
    val id: String,
    val recordType: String,
    val payload: String?,
    val payloadSchemaVersion: Int,
    val deviceId: String,
    val deviceVersion: Long,
    val serverVersion: Long,
    val deleted: Boolean,
)

@Serializable
data class LocationHistoryBatchRequest(
    val records: List<LocationHistoryUpload>,
)

@Serializable
data class LocationHistoryBatchResponse(
    val records: List<LocationHistoryRecord>,
)

@Serializable
data class LocationHistoryChangesResponse(
    val records: List<LocationHistoryRecord>,
    val nextCursor: Long,
    val hasMore: Boolean,
)
