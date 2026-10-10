package app.logdate.client.repository.journals

import app.logdate.shared.model.Journal
import kotlin.uuid.Uuid

data class JournalMergeScope(
    val ownerId: String,
    val serverOrigin: String,
)

data class JournalMergeCandidate(
    val journal: Journal,
    val itemCount: Int,
)

data class JournalMergePreview(
    val source: Journal,
    val destination: Journal,
    val sourceContentIds: Set<Uuid>,
    val destinationContentIds: Set<Uuid>,
) {
    val sourceCount: Int get() = sourceContentIds.size
    val overlapCount: Int get() = sourceContentIds.intersect(destinationContentIds).size
    val combinedCount: Int get() = sourceContentIds.union(destinationContentIds).size
}

data class JournalMergeOperation(
    val operationId: Uuid,
    val sourceId: Uuid,
    val destinationId: Uuid,
    val contentIds: Set<Uuid>,
    val scope: JournalMergeScope,
    val sourceTitle: String,
    val destinationTitle: String,
    val source: Journal? = null,
    val needsDestination: Boolean = false,
)

sealed interface JournalMergeResult {
    data class Merged(
        val operation: JournalMergeOperation,
    ) : JournalMergeResult

    data class ReviewChanged(
        val preview: JournalMergePreview,
    ) : JournalMergeResult

    data object Unavailable : JournalMergeResult

    data object Failed : JournalMergeResult
}
