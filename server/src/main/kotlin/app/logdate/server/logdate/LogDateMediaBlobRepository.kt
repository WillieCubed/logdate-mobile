package app.logdate.server.logdate

import java.util.UUID

/**
 * Unified LogDate-owned boundary for first-party media metadata and AT Protocol blob metadata.
 *
 * This keeps binary object lifecycle concerns behind one internal interface even while the
 * current persistence layer still stores sync media rows and ATProto blob rows separately.
 */
interface LogDateMediaBlobRepository {
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

    fun upsertAtprotoBlob(
        userId: UUID,
        blob: LogDateAtprotoBlob,
    ): LogDateAtprotoBlob

    fun getAtprotoBlob(
        userId: UUID,
        cid: String,
    ): LogDateAtprotoBlob?
}

/**
 * Composite runtime adapter for the current media and ATProto blob repositories.
 */
class CompositeLogDateMediaBlobRepository(
    private val mediaRepository: LogDateMediaRepository,
    private val atprotoBlobRepository: LogDateAtprotoBlobRepository,
) : LogDateMediaBlobRepository {
    override fun upsertMedia(
        userId: UUID,
        media: LogDateMedia,
    ): LogDateMedia = mediaRepository.upsertMedia(userId, media)

    override fun getMedia(
        userId: UUID,
        mediaId: String,
    ): LogDateMedia? = mediaRepository.getMedia(userId, mediaId)

    override fun deleteMedia(
        userId: UUID,
        mediaId: String,
        deletedAt: Long,
    ) {
        mediaRepository.deleteMedia(userId, mediaId, deletedAt)
    }

    override fun upsertAtprotoBlob(
        userId: UUID,
        blob: LogDateAtprotoBlob,
    ): LogDateAtprotoBlob = atprotoBlobRepository.upsertBlob(userId, blob)

    override fun getAtprotoBlob(
        userId: UUID,
        cid: String,
    ): LogDateAtprotoBlob? = atprotoBlobRepository.getBlob(userId, cid)
}

/**
 * Transitional adapter for call sites that only need the media half of the unified boundary.
 */
class MediaOnlyLogDateMediaBlobRepository(
    private val mediaRepository: LogDateMediaRepository,
) : LogDateMediaBlobRepository {
    override fun upsertMedia(
        userId: UUID,
        media: LogDateMedia,
    ): LogDateMedia = mediaRepository.upsertMedia(userId, media)

    override fun getMedia(
        userId: UUID,
        mediaId: String,
    ): LogDateMedia? = mediaRepository.getMedia(userId, mediaId)

    override fun deleteMedia(
        userId: UUID,
        mediaId: String,
        deletedAt: Long,
    ) {
        mediaRepository.deleteMedia(userId, mediaId, deletedAt)
    }

    override fun upsertAtprotoBlob(
        userId: UUID,
        blob: LogDateAtprotoBlob,
    ): LogDateAtprotoBlob = blob

    override fun getAtprotoBlob(
        userId: UUID,
        cid: String,
    ): LogDateAtprotoBlob? = null
}

fun LogDateMediaRepository.asLogDateMediaBlobRepository(): LogDateMediaBlobRepository = MediaOnlyLogDateMediaBlobRepository(this)
