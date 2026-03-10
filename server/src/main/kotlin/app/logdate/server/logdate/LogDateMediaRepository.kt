package app.logdate.server.logdate

import app.logdate.shared.model.sync.DeviceId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * LogDate-owned media metadata record.
 *
 * This remains compatible with the existing sync APIs while keeping the server's internal
 * language focused on LogDate media concepts rather than sync persistence details.
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
 * In-memory media metadata implementation for tests and non-database server runs.
 */
class InMemoryLogDateMediaRepository : LogDateMediaRepository {
    private val rows = ConcurrentHashMap<UUID, ConcurrentHashMap<String, StoredLogDateMedia>>()
    private val versions = ConcurrentHashMap<UUID, AtomicLong>()

    override fun upsertMedia(
        userId: UUID,
        media: LogDateMedia,
    ): LogDateMedia {
        val mediaId = media.mediaId.ifBlank { "media-${Random.nextLong().absoluteValue}" }
        val version = nextVersion(userId)
        val stored =
            media.copy(
                mediaId = mediaId,
                userId = userId,
                version = version,
            )
        rowsForUser(userId)[mediaId] = StoredLogDateMedia(media = stored, deletedAt = null)
        return stored
    }

    override fun getMedia(
        userId: UUID,
        mediaId: String,
    ): LogDateMedia? =
        rowsForUser(userId)[mediaId]
            ?.takeIf { it.deletedAt == null }
            ?.media

    override fun deleteMedia(
        userId: UUID,
        mediaId: String,
        deletedAt: Long,
    ) {
        val existing = rowsForUser(userId)[mediaId] ?: return
        rowsForUser(userId)[mediaId] =
            StoredLogDateMedia(
                media = existing.media.copy(version = nextVersion(userId)),
                deletedAt = deletedAt,
            )
    }

    private fun rowsForUser(userId: UUID): ConcurrentHashMap<String, StoredLogDateMedia> = rows.getOrPut(userId) { ConcurrentHashMap() }

    private fun nextVersion(userId: UUID): Long {
        val counter = versions.getOrPut(userId) { AtomicLong(System.currentTimeMillis()) }
        val candidate = System.currentTimeMillis()
        return counter.updateAndGet { previous ->
            when {
                candidate > previous -> candidate
                else -> previous + 1L
            }
        }
    }
}

private data class StoredLogDateMedia(
    val media: LogDateMedia,
    val deletedAt: Long?,
)
