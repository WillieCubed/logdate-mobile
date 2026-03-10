package app.logdate.server.logdate

import app.logdate.server.sync.MediaRecord
import app.logdate.server.sync.SyncRepository
import app.logdate.shared.model.sync.DeviceId
import java.util.UUID

/**
 * LogDate-owned media metadata record.
 *
 * This remains compatible with the existing sync APIs while removing direct route dependence on
 * [SyncRepository].
 */
data class LogDateMedia(
    val mediaId: String,
    val contentId: String,
    val userId: UUID,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val data: ByteArray,
    val storagePath: String? = null,
    val createdAt: Long,
    val version: Long,
    val deviceId: DeviceId,
    val encryptionVersion: Int? = null,
    val encryptionKeyId: String? = null,
    val encryptionMode: String? = null,
)

/**
 * Internal boundary for media metadata operations.
 */
interface LogDateMediaRepository {
    fun upsertMedia(
        userId: UUID,
        media: LogDateMedia,
    ): LogDateMedia

    fun getMedia(
        userId: UUID,
        mediaId: String,
    ): LogDateMedia?

    fun deleteMedia(
        userId: UUID,
        mediaId: String,
        deletedAt: Long,
    )
}

/**
 * Transitional media metadata implementation backed by the existing sync repository.
 */
class SyncBackedLogDateMediaRepository(
    private val syncRepository: SyncRepository,
) : LogDateMediaRepository {
    override fun upsertMedia(
        userId: UUID,
        media: LogDateMedia,
    ): LogDateMedia = syncRepository.upsertMedia(userId, media.toSyncRecord()).toLogDateMedia()

    override fun getMedia(
        userId: UUID,
        mediaId: String,
    ): LogDateMedia? = syncRepository.getMedia(userId, mediaId)?.toLogDateMedia()

    override fun deleteMedia(
        userId: UUID,
        mediaId: String,
        deletedAt: Long,
    ) {
        syncRepository.deleteMedia(userId, mediaId, deletedAt)
    }
}

private fun LogDateMedia.toSyncRecord(): MediaRecord =
    MediaRecord(
        mediaId = mediaId,
        contentId = contentId,
        userId = userId,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        data = data,
        storagePath = storagePath,
        createdAt = createdAt,
        serverVersion = version,
        deviceId = deviceId,
        encryptionVersion = encryptionVersion,
        encryptionKeyId = encryptionKeyId,
        encryptionMode = encryptionMode,
    )

private fun MediaRecord.toLogDateMedia(): LogDateMedia =
    LogDateMedia(
        mediaId = mediaId,
        contentId = contentId,
        userId = userId,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        data = data,
        storagePath = storagePath,
        createdAt = createdAt,
        version = serverVersion,
        deviceId = deviceId,
        encryptionVersion = encryptionVersion,
        encryptionKeyId = encryptionKeyId,
        encryptionMode = encryptionMode,
    )
