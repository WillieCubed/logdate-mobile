package app.logdate.client.data.journals

import app.logdate.client.database.entities.journals.JournalMergeEntity
import app.logdate.client.database.entities.sync.PendingUploadEntity
import app.logdate.client.repository.journals.JournalMergeOperation
import app.logdate.client.repository.journals.JournalMergePreview
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.shared.model.Journal
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

internal fun JournalMergeEntity.toOperation() =
    JournalMergeOperation(
        operationId,
        sourceId,
        if (pending) requestedDestinationId else destinationId,
        Json.decodeFromString<List<String>>(contentIds).mapTo(mutableSetOf()) {
            Uuid.parse(it)
        },
        JournalMergeScope(ownerId, serverOrigin),
        sourceTitle,
        destinationTitle,
        Json.decodeFromString<Journal?>(sourceJournal),
        needsDestination,
    )

internal fun JournalMergePreview.toPendingMergeEntity(
    scope: JournalMergeScope,
    operationId: Uuid,
    createdAt: Long,
) = JournalMergeEntity(
    scope.ownerId,
    scope.serverOrigin,
    source.id,
    destination.id,
    operationId,
    Json.encodeToString(sourceContentIds.map { it.toString() }),
    source.title,
    Json.encodeToString(source),
    destination.title,
    true,
    createdAt,
)

internal fun JournalMergeEntity.retargeted(
    preview: JournalMergePreview,
    replacementOperationId: Uuid,
) = copy(
    destinationId = preview.destination.id,
    requestedDestinationId = preview.destination.id,
    operationId = replacementOperationId,
    contentIds = Json.encodeToString(preview.sourceContentIds.map { it.toString() }),
    recoveryContentIds = "[]",
    destinationTitle = preview.destination.title,
    needsDestination = false,
    previousOperationIds = Json.encodeToString(Json.decodeFromString<List<String>>(previousOperationIds) + operationId.toString()),
)

internal fun journalRedirectEntity(
    scope: JournalMergeScope,
    sourceId: Uuid,
    destinationId: Uuid,
    sourceTitle: String,
    createdAt: Long,
) = JournalMergeEntity(
    scope.ownerId,
    scope.serverOrigin,
    sourceId,
    destinationId,
    Uuid.random(),
    "[]",
    sourceTitle,
    "null",
    "",
    false,
    createdAt,
)

internal fun JournalMergeEntity.recoveryContents(): Set<Uuid> =
    Json.decodeFromString<List<String>>(recoveryContentIds).mapTo(mutableSetOf(), Uuid::parse)

internal fun JournalMergeEntity.redirected(
    destination: Uuid,
    retained: Set<Uuid>,
    superseded: Boolean,
) = copy(
    destinationId = destination,
    pending = pending && !superseded,
    needsDestination = needsDestination && !superseded,
    recoveryContentIds = if (pending && !superseded) recoveryContentIds else Json.encodeToString(retained.map { it.toString() }),
)

internal fun JournalMergeEntity.toPendingUpload() =
    PendingUploadEntity(
        ownerId,
        serverOrigin,
        "JOURNAL_MERGE",
        operationId.toString(),
        "CREATE",
        createdAt,
        operationId = operationId.toString(),
    )

internal fun JournalMergeScope.toPendingAssociation(
    journalId: Uuid,
    contentId: Uuid,
    createdAt: Long,
) = PendingUploadEntity(
    ownerId,
    serverOrigin,
    "ASSOCIATION",
    AssociationPendingKey(journalId, contentId).toPendingId(),
    "CREATE",
    createdAt,
)
