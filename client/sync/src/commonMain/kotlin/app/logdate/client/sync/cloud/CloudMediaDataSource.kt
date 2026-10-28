package app.logdate.client.sync.cloud

import app.logdate.client.device.crypto.IdentityKeyNotFoundException
import app.logdate.client.media.MediaFileSource
import app.logdate.client.sync.crypto.MediaPayloadCrypto
import app.logdate.client.sync.crypto.NoOpMediaPayloadCrypto
import app.logdate.client.sync.crypto.isClientEncryptedMedia
import kotlinx.coroutines.CancellationException
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

/** A media file downloaded and decrypted into [path] on this device, ready to move into the media store. */
data class DownloadedMediaFile(
    val contentId: Uuid,
    val fileName: String,
    val mimeType: String,
    val path: kotlinx.io.files.Path,
    val sizeBytes: Long,
)

/** A media source that downloads straight into a file, so no file is ever held whole in memory. */
interface FileMediaDataSource {
    /**
     * Downloads and decrypts the file [reference] names into a new file the caller moves into the
     * media store and then deletes, or returns null when this source reads [reference] another way.
     */
    suspend fun downloadMediaToFile(
        accessToken: String,
        reference: String,
        mediaId: String,
    ): Result<DownloadedMediaFile>?
}

/**
 * A media source that finds a file by its whole reference, not only the identifier the sync engine
 * reads from it. A separate interface rather than a default method, so a source that wraps another
 * by delegation keeps its own [CloudMediaDataSource.downloadMedia].
 */
interface ReferencedMediaDataSource {
    /** Downloads the media [reference] names; [mediaId] is the identifier read from it. */
    suspend fun downloadReferencedMedia(
        accessToken: String,
        reference: String,
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
    private val uploadIntents: app.logdate.client.sync.metadata.MediaUploadIntentStore? = null,
) : CloudMediaDataSource {
    override suspend fun uploadMedia(
        accessToken: String,
        contentId: Uuid,
        media: MediaFileSource,
    ): Result<MediaUploadResult> {
        if (media.sizeBytes > MAX_MEDIA_UPLOAD_BYTES) return Result.failure(tooLarge(contentId, media.sizeBytes))
        val request =
            try {
                prepareUpload(contentId, media)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (cause: Exception) {
                return Result.failure(cause)
            }
        if (request.sizeBytes > MAX_MEDIA_UPLOAD_BYTES) return Result.failure(tooLarge(contentId, request.sizeBytes))
        val fingerprint = request.receiptFingerprint
        val store = uploadIntents
        if (store == null || fingerprint == null) return uploadPrepared(accessToken, contentId, request, null)
        val intent =
            try {
                store.getOrCreate(contentId.toString(), fingerprint)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (cause: Exception) {
                return Result.failure(cause)
            }
        return store.withIntentLock(intent) { uploadPrepared(accessToken, contentId, request, intent) }
    }

    private suspend fun uploadPrepared(
        accessToken: String,
        contentId: Uuid,
        prepared: MediaUpload,
        intent: String?,
    ): Result<MediaUploadResult> {
        var request = prepared
        if (intent != null) {
            val lookup = cloudApiClient.getMediaUploadReceipt(accessToken, intent)
            val failure = lookup.exceptionOrNull()
            // Explicitly unsupported older/custom servers keep their legacy behavior.
            // Network/auth/storage errors never authorize blind payload replay.
            if (failure != null && failure !is UnsupportedOperationException) return Result.failure(failure)
            rememberReceiptSupport(intent, failure).exceptionOrNull()?.let { return Result.failure(it) }
            if (failure == null) {
                lookup.getOrThrow()?.let { receipt ->
                    if (receipt.contentId != contentId.toString() || receipt.uploadIntentId != intent) {
                        return Result.failure(MediaIntegrityException("Upload receipt does not match this content version"))
                    }
                    try {
                        request.validateReceiptVersion()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (cause: Exception) {
                        return Result.failure(cause)
                    }
                    return Result.success(
                        MediaUploadResult(
                            receipt.mediaId,
                            receipt.downloadUrl,
                            Instant.fromEpochMilliseconds(receipt.uploadedAt),
                        ),
                    )
                }
                request = request.withIntent(intent)
            }
        }
        val uploaded =
            try {
                cloudApiClient.uploadMedia(accessToken, request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (cause: Exception) {
                return Result.failure(cause)
            }
        val response = uploaded.getOrElse { return Result.failure(it) }
        if (request.uploadIntentId != null &&
            (response.uploadIntentId != request.uploadIntentId || response.contentId != contentId.toString())
        ) {
            return Result.failure(MediaIntegrityException("Upload acknowledgement does not match this content version"))
        }
        return Result.success(
            MediaUploadResult(
                response.mediaId,
                response.downloadUrl,
                Instant.fromEpochMilliseconds(response.uploadedAt),
            ),
        )
    }

    private suspend fun rememberReceiptSupport(
        intent: String,
        failure: Throwable?,
    ): Result<Unit> =
        try {
            if (failure is UnsupportedOperationException && uploadIntents?.receiptRequired(intent) == true) {
                Result.failure(CloudApiException("UPLOAD_RECEIPT_UNAVAILABLE", "Could not confirm the previous upload"))
            } else {
                if (failure == null) uploadIntents?.markReceiptSupported(intent)
                Result.success(Unit)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (cause: Exception) {
            Result.failure(cause)
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
        val fingerprint = if (uploadIntents != null) encryptor.receiptContext?.let { mediaReceiptFingerprint(media, it) } else null
        return MediaUpload(
            contentId = contentId.toString(),
            fileName = media.fileName,
            mimeType = media.mimeType,
            // The encrypted payload carries a header and an auth tag per chunk, so it is always
            // larger than the file on disk. The server checks this against the bytes it
            // actually receives and rejects a mismatch, so it has to describe the ciphertext.
            sizeBytes = encryptor.encryptedSizeBytes(media.sizeBytes),
            receiptFingerprint = fingerprint?.value,
            validateReceipt =
                fingerprint?.let { expected ->
                    {
                        if (mediaReceiptFingerprint(media, checkNotNull(encryptor.receiptContext)).value != expected.value) {
                            throw MediaReadException("Media changed while its upload receipt was being checked")
                        }
                    }
                },
        ) { encryptor.encrypt(fingerprint?.let { ReceiptVerifiedSource(media, it.contentDigest) } ?: SizeCheckedSource(media)) }
    }

    override suspend fun downloadMedia(
        accessToken: String,
        mediaId: String,
    ): Result<MediaFile> {
        val responseResult = cloudApiClient.downloadMedia(accessToken, mediaId)
        return responseResult.fold(
            onSuccess = { response ->
                try {
                    val decrypted =
                        try {
                            mediaPayloadCrypto.decrypt(response.data)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (missing: IdentityKeyNotFoundException) {
                            throw missing
                        } catch (_: Exception) {
                            throw MediaDecryptionException()
                        }
                    Result.success(
                        MediaFile(
                            contentId = Uuid.parse(response.contentId),
                            fileName = response.fileName,
                            mimeType = response.mimeType,
                            sizeBytes = decrypted.size.toLong(),
                            data = decrypted,
                        ),
                    )
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
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
internal class SizeCheckedSource(
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

class MediaDecryptionException : Exception("MEDIA_DECRYPTION_FAILED")

/** Downloaded media isn't the file its reference names: its content identifier doesn't match. */
class MediaIntegrityException(
    message: String,
) : Exception(message)
