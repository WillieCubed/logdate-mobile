package app.logdate.client.sync.cloud

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.shared.model.sync.DeviceId
import app.logdate.shared.model.sync.DraftUploadRequest
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Data source for syncing editor drafts with LogDate Cloud.
 *
 * Enables cross-device handoff: start a note on one device,
 * continue editing on another.
 */
interface CloudDraftDataSource {
    suspend fun uploadDraft(
        accessToken: String,
        draft: EntryDraft,
        deviceId: DeviceId,
    ): Result<SyncUploadResult>

    suspend fun deleteDraft(
        accessToken: String,
        draftId: Uuid,
    ): Result<Unit>

    suspend fun getDraftChanges(
        accessToken: String,
        since: Instant,
        limit: Int? = null,
    ): Result<DraftSyncResult>
}

data class DraftSyncResult(
    val changes: List<SyncedDraft>,
    val deletions: List<Uuid>,
    val lastSyncTimestamp: Instant,
    val hasMore: Boolean = false,
)

data class SyncedDraft(
    val id: Uuid,
    val content: String,
    val deviceId: DeviceId,
    val createdAt: Instant,
    val lastUpdated: Instant,
    val serverVersion: Long,
)

/**
 * Default implementation using the CloudApiClient.
 */
class DefaultCloudDraftDataSource(
    private val cloudApiClient: CloudApiClient,
) : CloudDraftDataSource {
    override suspend fun uploadDraft(
        accessToken: String,
        draft: EntryDraft,
        deviceId: DeviceId,
    ): Result<SyncUploadResult> {
        val request =
            DraftUploadRequest(
                id = draft.id.toString(),
                content = draft.notes.joinToString("\n") { note -> note.uid.toString() },
                createdAt = draft.createdAt.toEpochMilliseconds(),
                lastUpdated = draft.updatedAt.toEpochMilliseconds(),
                deviceId = deviceId,
            )
        return cloudApiClient.uploadDraft(accessToken, request).map {
            SyncUploadResult(
                serverVersion = it.serverVersion,
                syncedAt = Instant.fromEpochMilliseconds(it.uploadedAt),
            )
        }
    }

    override suspend fun deleteDraft(
        accessToken: String,
        draftId: Uuid,
    ): Result<Unit> = cloudApiClient.deleteDraft(accessToken, draftId.toString())

    override suspend fun getDraftChanges(
        accessToken: String,
        since: Instant,
        limit: Int?,
    ): Result<DraftSyncResult> =
        cloudApiClient.getDraftChanges(accessToken, since.toEpochMilliseconds(), limit).map { response ->
            DraftSyncResult(
                changes =
                    response.drafts.filter { !it.isDeleted }.map { change ->
                        SyncedDraft(
                            id = Uuid.parse(change.id),
                            content = change.content,
                            deviceId = change.deviceId,
                            createdAt = Instant.fromEpochMilliseconds(change.createdAt),
                            lastUpdated = Instant.fromEpochMilliseconds(change.lastUpdated),
                            serverVersion = change.serverVersion,
                        )
                    },
                deletions = response.drafts.filter { it.isDeleted }.map { Uuid.parse(it.id) },
                lastSyncTimestamp =
                    Instant.fromEpochMilliseconds(
                        response.drafts.maxOfOrNull { it.lastUpdated } ?: 0L,
                    ),
                hasMore = response.cursor != null,
            )
        }
}
