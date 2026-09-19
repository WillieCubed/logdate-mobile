package app.logdate.client.sync.cloud

import app.logdate.client.media.MediaFileSource
import app.logdate.client.sync.crypto.MediaPayloadCrypto
import app.logdate.client.sync.crypto.NoOpMediaPayloadCrypto
import app.logdate.client.sync.crypto.isClientEncryptedMedia
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Data source for syncing media files with LogDate Cloud.
 *
 * Handles uploading and downloading media files (images, videos, audio)
 * associated with journal content.
 */
interface CloudMediaDataSource {
    /**
     * Uploads the media for [contentId], reading and encrypting it from [media] as it is sent.
     */
    suspend fun uploadMedia(
        accessToken: String,
        contentId: Uuid,
        media: MediaFileSource,
    ): Result<MediaUploadResult>

    /**
     * Downloads a media file from the cloud.
     */
    suspend fun downloadMedia(
        accessToken: String,
        mediaId: String,
    ): Result<MediaFile>
}

/**
 * Represents a media file for sync operations.
 */
data class MediaFile(
    val contentId: Uuid,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MediaFile

        if (contentId != other.contentId) return false
        if (fileName != other.fileName) return false
        if (mimeType != other.mimeType) return false
        if (sizeBytes != other.sizeBytes) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = contentId.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * Result of a media upload operation.
 */
data class MediaUploadResult(
    val mediaId: String,
    val downloadUrl: String,
    val uploadedAt: Instant,
)

/**
 * Default implementation of CloudMediaDataSource using the CloudApiClient.
 *
 * **Encryption:** [mediaPayloadCrypto] defaults to [NoOpMediaPayloadCrypto] only so tests can
 * construct this class without wiring a key source. Production builds receive
 * [StoredMediaPayloadCrypto] via DI (`CloudModule.kt`) and bytes on the wire are always
 * AES-GCM–encrypted client-side before they reach the server.
 */
class DefaultCloudMediaDataSource(
    private val cloudApiClient: CloudApiClient,
    private val mediaPayloadCrypto: MediaPayloadCrypto = NoOpMediaPayloadCrypto,
) : CloudMediaDataSource {
    override suspend fun uploadMedia(
        accessToken: String,
        contentId: Uuid,
        media: MediaFileSource,
    ): Result<MediaUploadResult> {
        // Encryption only ever adds bytes, so a file already over the limit is refused before
        // anything is read from it.
        if (media.sizeBytes > MAX_MEDIA_UPLOAD_BYTES) return Result.failure(tooLarge(contentId, media.sizeBytes))
        val request =
            try {
                prepareUpload(contentId, media)
            } catch (error: Exception) {
                return Result.failure(error)
            }
        if (request.sizeBytes > MAX_MEDIA_UPLOAD_BYTES) return Result.failure(tooLarge(contentId, request.sizeBytes))

        return cloudApiClient.uploadMedia(accessToken, request).map { response ->
            MediaUploadResult(
                mediaId = response.mediaId,
                downloadUrl = response.downloadUrl,
                uploadedAt = Instant.fromEpochMilliseconds(response.uploadedAt),
            )
        }
    }

    /**
     * The hosting platform refuses a request this large before the server ever sees it, so
     * retrying can never succeed. Failing here names the reason instead of leaving the entry
     * queued forever behind an unexplained rejection.
     */
    private fun tooLarge(
        contentId: Uuid,
        sizeBytes: Long,
    ) = MediaTooLargeException("Media for $contentId is $sizeBytes bytes, over the $MAX_MEDIA_UPLOAD_BYTES byte upload limit")

    /**
     * Describes the upload without reading the file: its wire size follows from the file size.
     * A payload that is already client-encrypted is sent as-is rather than encrypted twice.
     */
    private suspend fun prepareUpload(
        contentId: Uuid,
        media: MediaFileSource,
    ): MediaUpload {
        val alreadyEncrypted =
            try {
                media.isClientEncryptedMedia()
            } catch (error: Exception) {
                throw MediaReadException("Could not read ${media.fileName}: ${error.message}", error)
            }
        val encryptor =
            if (alreadyEncrypted) {
                NoOpMediaPayloadCrypto.streamEncryptor()
            } else {
                mediaPayloadCrypto.streamEncryptor()
            }
        return MediaUpload(
            contentId = contentId.toString(),
            fileName = media.fileName,
            mimeType = media.mimeType,
            // The encrypted payload carries a header and an auth tag per chunk, so it is always
            // larger than the file on disk. The server checks this against the bytes it
            // actually receives and rejects a mismatch, so it has to describe the ciphertext.
            sizeBytes = encryptor.encryptedSizeBytes(media.sizeBytes),
        ) { encryptor.encrypt(SizeCheckedSource(media)) }
    }

    override suspend fun downloadMedia(
        accessToken: String,
        mediaId: String,
    ): Result<MediaFile> {
        val responseResult = cloudApiClient.downloadMedia(accessToken, mediaId)
        return responseResult.fold(
            onSuccess = { response ->
                try {
                    val decrypted = mediaPayloadCrypto.decrypt(response.data)
                    Result.success(
                        MediaFile(
                            contentId = Uuid.parse(response.contentId),
                            fileName = response.fileName,
                            mimeType = response.mimeType,
                            sizeBytes = response.sizeBytes,
                            data = decrypted,
                        ),
                    )
                } catch (error: Exception) {
                    Result.failure(error)
                }
            },
            onFailure = { error -> Result.failure(error) },
        )
    }
}

/** Raised when a media payload exceeds what the upload endpoint will accept. */
class MediaTooLargeException(
    message: String,
) : Exception(message)

/**
 * Ceiling for a single media upload. Cloud Run rejects a non-streaming request above 32 MiB before
 * it reaches the server, leaving a little headroom for the multipart envelope.
 */
private const val MAX_MEDIA_UPLOAD_BYTES = 31 * 1024 * 1024

/**
 * Reads [media] and fails with [MediaReadException] if it cannot be read or its length differs
 * from [MediaFileSource.sizeBytes], which the upload declared before reading a byte. A file that
 * changed or vanished since then must not be sent as a truncated or oversized body.
 */
private class SizeCheckedSource(
    private val media: MediaFileSource,
) : RawSource {
    private val source = readingMedia { media.open() }
    private var bytesRead = 0L

    override fun readAtMostTo(
        sink: Buffer,
        byteCount: Long,
    ): Long {
        val read = readingMedia { source.readAtMostTo(sink, byteCount) }
        if (read == -1L) {
            if (bytesRead != media.sizeBytes) throw sizeChanged()
            return read
        }
        bytesRead += read
        if (bytesRead > media.sizeBytes) throw sizeChanged()
        return read
    }

    override fun close() {
        source.close()
    }

    private fun sizeChanged() =
        MediaReadException("${media.fileName} changed size since the upload started: expected ${media.sizeBytes} bytes")

    private inline fun <T> readingMedia(block: () -> T): T =
        try {
            block()
        } catch (error: MediaReadException) {
            throw error
        } catch (error: Exception) {
            throw MediaReadException("Could not read ${media.fileName}: ${error.message}", error)
        }
}
