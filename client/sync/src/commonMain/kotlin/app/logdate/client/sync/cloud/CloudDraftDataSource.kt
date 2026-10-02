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
import app.logdate.shared.model.textContent
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    val deletionVersions: Map<Uuid, Long> = emptyMap(),
    val lastSyncTimestamp: Instant,
    val hasMore: Boolean = false,
    /** Drafts on the server this device could not decrypt -- see [SyncDownloadEngine.repairUnreadable]. */
    val unreadable: List<Uuid> = emptyList(),
    val incompatible: List<Uuid> = emptyList(),
    val failures: List<RemoteRecordFailure> = emptyList(),
    val unreadableVersions: Map<Uuid, Long> = emptyMap(),
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
    val richDraft: EditorDraft? = null,
)

/**
 * Default implementation using the CloudApiClient.
 */
class DefaultCloudDraftDataSource(
    private val cloudApiClient: CloudApiClient,
    private val syncPayloadCipher: SyncPayloadCipher? = null,
    private val supportsRichDrafts: () -> Boolean = { false },
) : CloudDraftDataSource {
    private val json =
        Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
        }

    override suspend fun uploadDraft(
        accessToken: String,
        draft: EditorDraft,
        deviceId: DeviceId,
    ): Result<SyncUploadResult> {
        if (!supportsRichDrafts()) {
            return Result.failure(UnsupportedOperationException("Connected server has not advertised rich draft sync"))
        }
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
                    encryptedBlocksVersion = 1,
                    encryptedBlocks = encryptDraftBlocks(draft),
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
        cloudApiClient.getDraftChanges(accessToken, since.toEpochMilliseconds(), limit).mapRecordPage { response ->
            val records =
                response.drafts.filterNot { it.isDeleted }.readEach(
                    idOf = { it.id },
                    versionOf = { it.serverVersion },
                ) { change ->
                    val id = Uuid.parse(change.id)
                    val rich =
                        if (change.encryptedBlocks != null || change.encryptedBlocksVersion != null) {
                            if (change.encryptedBlocksVersion != 1) throw UnsupportedRemoteFormatException()
                            val encrypted = requireNotNull(change.encryptedBlocks)
                            require(encrypted.startsWith("LDSE2:"))
                            val plaintext = requireNotNull(syncPayloadCipher).decryptString(blocksFieldId(id), encrypted)
                            json.decodeFromString<EditorDraft>(plaintext).also { require(it.id == id) }
                        } else {
                            null
                        }
                    SyncedDraft(
                        id = id,
                        content = rich?.textContent() ?: decryptDraftContent(id, change.content),
                        deviceId = change.deviceId,
                        createdAt = Instant.fromEpochMilliseconds(change.createdAt),
                        lastUpdated = Instant.fromEpochMilliseconds(change.lastUpdated),
                        serverVersion = change.serverVersion,
                        journalIds = change.journalIds.map(Uuid::parse),
                        blockTypes = change.blockTypes,
                        richDraft = rich,
                    )
                }
            val removed =
                response.deletions.readEach(idOf = { it.id }, versionOf = { it.serverVersion }) {
                    Uuid.parse(it.id) to it.serverVersion
                }
            val tombstones =
                response.drafts.filter { it.isDeleted }.readEach(idOf = { it.id }, versionOf = { it.serverVersion }) {
                    Uuid.parse(it.id) to it.serverVersion
                }
            val deletions = removed.readable + tombstones.readable
            DraftSyncResult(
                changes = records.readable,
                deletions = deletions.map { it.first },
                deletionVersions = deletions.toMap(),
                lastSyncTimestamp =
                    Instant.fromEpochMilliseconds(
                        response.lastTimestamp.takeIf { it > 0L }
                            ?: response.drafts.maxOfOrNull { it.lastUpdated } ?: 0L,
                    ),
                hasMore = response.hasMore || response.cursor != null,
                unreadable = records.unreadable,
                unreadableVersions = records.unreadableVersions,
                failures = records.failures + removed.failures + tombstones.failures,
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

    private suspend fun encryptDraftBlocks(draft: EditorDraft): String =
        requireNotNull(syncPayloadCipher) { "Draft encryption is unavailable" }
            .encryptString(blocksFieldId(draft.id), json.encodeToString(draft))

    private fun blocksFieldId(draftId: Uuid): String = "sync:draft:$draftId:blocks"

    private fun SerializableEntryBlock.syncBlockType(): String =
        when (this) {
            is SerializableTextBlock -> "TEXT"
            is SerializableImageBlock -> "IMAGE"
            is SerializableVideoBlock -> "VIDEO"
            is SerializableAudioBlock -> "AUDIO"
            is SerializableCameraBlock -> "CAMERA"
        }
}
