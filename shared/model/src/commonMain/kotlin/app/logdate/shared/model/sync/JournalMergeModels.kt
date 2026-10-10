package app.logdate.shared.model.sync

import kotlinx.serialization.Serializable

@Serializable
data class JournalMergeRequest(
    val operationId: String,
    val destinationId: String,
    val contentIds: List<String>,
)

@Serializable
data class JournalMergeResponse(
    val operationId: String,
    val sourceId: String,
    val destinationId: String,
)
