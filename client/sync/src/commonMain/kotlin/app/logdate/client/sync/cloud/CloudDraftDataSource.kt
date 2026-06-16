package app.logdate.client.sync.cloud

import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableAudioBlock
import app.logdate.shared.model.SerializableCameraBlock
import app.logdate.shared.model.SerializableEntryBlock
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.SerializableVideoBlock
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
        draft: EditorDraft,
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
    val journalIds: List<Uuid> = emptyList(),
    val blockTypes: List<String> = emptyList(),
)

/**
 * Default implementation using the CloudApiClient.
 */
class DefaultCloudDraftDataSource(
    private val cloudApiClient: CloudApiClient,
    private val syncPayloadCipher: SyncPayloadCipher? = null,
) : CloudDraftDataSource {
    override suspend fun uploadDraft(
        accessToken: String,
        draft: EditorDraft,
        deviceId: DeviceId,
    ): Result<SyncUploadResult> {
        val request =
            try {
                DraftUploadRequest(
                    id = draft.id.toString(),
                    content = encryptDraftContent(draft.id, draft.textContent()),
                    blockTypes = draft.blocks.map { it.syncBlockType() },
                    journalIds = draft.selectedJournalIds.map { it.toString() },
                    createdAt = draft.createdAt.toEpochMilliseconds(),
                    lastUpdated = draft.lastModifiedAt.toEpochMilliseconds(),
                    deviceId = deviceId,
                )
            } catch (error: Exception) {
                return Result.failure(error)
            }
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
                            content = decryptDraftContent(Uuid.parse(change.id), change.content),
                            deviceId = change.deviceId,
                            createdAt = Instant.fromEpochMilliseconds(change.createdAt),
                            lastUpdated = Instant.fromEpochMilliseconds(change.lastUpdated),
                            serverVersion = change.serverVersion,
                            journalIds = change.journalIds.mapNotNull { id -> runCatching { Uuid.parse(id) }.getOrNull() },
                            blockTypes = change.blockTypes,
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

    private suspend fun encryptDraftContent(
        draftId: Uuid,
        content: String,
    ): String = syncPayloadCipher?.encryptString(draftFieldId(draftId), content) ?: content

    private suspend fun decryptDraftContent(
        draftId: Uuid,
        content: String,
    ): String = syncPayloadCipher?.decryptString(draftFieldId(draftId), content) ?: content

    private fun draftFieldId(draftId: Uuid): String = "sync:draft:$draftId:content"

    private fun EditorDraft.textContent(): String =
        blocks
            .filterIsInstance<SerializableTextBlock>()
            .joinToString("\n") { it.content }

    private fun SerializableEntryBlock.syncBlockType(): String =
        when (this) {
            is SerializableTextBlock -> "TEXT"
            is SerializableImageBlock -> "IMAGE"
            is SerializableVideoBlock -> "VIDEO"
            is SerializableAudioBlock -> "AUDIO"
            is SerializableCameraBlock -> "CAMERA"
        }
}
