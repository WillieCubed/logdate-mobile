package app.logdate.client.sync.space

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.crypto.IdentityKeyNotFoundException
import app.logdate.client.media.MediaFileSource
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.CloudRequestBinding
import app.logdate.client.sync.cloud.MediaDecryptionException
import app.logdate.client.sync.cloud.MediaFile
import app.logdate.client.sync.cloud.MediaIntegrityException
import app.logdate.client.sync.cloud.MediaReadException
import app.logdate.client.sync.cloud.MediaTooLargeException
import app.logdate.client.sync.cloud.MediaUploadResult
import app.logdate.client.sync.cloud.ReferencedMediaDataSource
import app.logdate.client.sync.cloud.SizeCheckedSource
import app.logdate.client.sync.crypto.MediaPayloadCrypto
import app.logdate.client.sync.crypto.NoOpMediaPayloadCrypto
import app.logdate.client.sync.crypto.isClientEncryptedMedia
import app.logdate.space.SpaceRef
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import studio.hypertext.atproto.repo.Cid
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** The server and personal space media goes to for the signed-in account. */
data class SpaceLocation(
    val origin: String,
    val space: SpaceRef,
)

/**
 * Media stored as blobs in the account's personal space: chunk-encrypted with the account media key
 * exactly as before (LDCE2), streamed to `uploadBlob` while it is read from disk, and named by a
 * [SpaceMediaReference] that carries the blob's content identifier.
 *
 * @param location the signed-in account's server and space, or null when there is none.
 */
class SpaceMediaDataSource(
    private val homes: SpaceDataHomes,
    private val mediaPayloadCrypto: MediaPayloadCrypto,
    private val location: suspend () -> SpaceLocation?,
) : CloudMediaDataSource,
    ReferencedMediaDataSource {
    override suspend fun uploadMedia(
        accessToken: String,
        contentId: Uuid,
        media: MediaFileSource,
    ): Result<MediaUploadResult> {
        if (media.sizeBytes > MAX_BLOB_BYTES) return Result.failure(tooLarge(contentId, media.sizeBytes))
        val target = location() ?: return Result.failure(SpaceUnavailableException("No personal space for the signed-in account"))
        val alreadyEncrypted =
            try {
                media.isClientEncryptedMedia()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                return Result.failure(MediaReadException("Could not read ${media.fileName}: ${error.message}", error))
            }
        val encryptor = if (alreadyEncrypted) NoOpMediaPayloadCrypto.streamEncryptor() else mediaPayloadCrypto.streamEncryptor()
        val storedSize = encryptor.encryptedSizeBytes(media.sizeBytes)
        if (storedSize > MAX_BLOB_BYTES) return Result.failure(tooLarge(contentId, storedSize))
        val uploaded =
            spaceCall {
                homes.open(target.origin, accessToken).uploadBlob(target.space, storedSize) { encryptor.encrypt(SizeCheckedSource(media)) }
            }
        val cid = uploaded.getOrElse { return Result.failure(uploadFailure(it, contentId, storedSize)) }
        val reference = SpaceMediaReference(cid, contentId, media.mimeType, storedSize)
        return Result.success(MediaUploadResult(mediaId = cid, downloadUrl = reference.format(), uploadedAt = Clock.System.now()))
    }

    /** A personal-space blob is found by its whole reference; see [downloadReferencedMedia]. */
    override suspend fun downloadMedia(
        accessToken: String,
        mediaId: String,
    ): Result<MediaFile> = Result.failure(SpaceUnavailableException("A personal-space file can only be found by its whole reference"))

    override suspend fun downloadReferencedMedia(
        accessToken: String,
        reference: String,
        mediaId: String,
    ): Result<MediaFile> {
        val named =
            SpaceMediaReference.parse(reference) ?: return Result.failure(MediaIntegrityException("Not a personal-space file reference"))
        val target = location() ?: return Result.failure(SpaceUnavailableException("No personal space for the signed-in account"))
        val buffer = Buffer()
        val found =
            spaceCall { homes.open(target.origin, accessToken).downloadBlob(target.space, named.cid, buffer) }
                .getOrElse { return Result.failure(it) }
        if (!found) return Result.failure(CloudApiException("MEDIA_NOT_FOUND", "The file is no longer stored", statusCode = 404))
        val stored = buffer.readByteArray()
        if (Cid.rawSha256(stored).value != named.cid) {
            return Result.failure(MediaIntegrityException("The downloaded file doesn't match its content identifier"))
        }
        val decrypted =
            try {
                mediaPayloadCrypto.decrypt(stored)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (missing: IdentityKeyNotFoundException) {
                return Result.failure(missing)
            } catch (_: Exception) {
                return Result.failure(MediaDecryptionException())
            }
        return Result.success(MediaFile(named.contentId, named.contentId.toString(), named.mimeType, decrypted.size.toLong(), decrypted))
    }

    private fun uploadFailure(
        error: Throwable,
        contentId: Uuid,
        storedSize: Long,
    ): Throwable = if (error is CloudApiException && error.statusCode == PAYLOAD_TOO_LARGE) tooLarge(contentId, storedSize) else error

    private fun tooLarge(
        contentId: Uuid,
        sizeBytes: Long,
    ) = MediaTooLargeException("Media for $contentId is $sizeBytes bytes, over the $MAX_BLOB_BYTES byte limit for a personal-space file")

    private companion object {
        /** The largest blob a personal space accepts. */
        const val MAX_BLOB_BYTES = 100L * 1024 * 1024
        const val PAYLOAD_TOO_LARGE = 413
    }
}

/**
 * Sends media uploads to the personal space while notes sync there, and reads each file from where
 * its reference says it lives, so a file uploaded before or after the switch can still be read.
 */
class SpaceRoutingMediaDataSource(
    private val legacy: CloudMediaDataSource,
    private val space: CloudMediaDataSource,
    private val uploadsToSpace: suspend () -> Boolean,
) : CloudMediaDataSource,
    ReferencedMediaDataSource {
    override suspend fun uploadMedia(
        accessToken: String,
        contentId: Uuid,
        media: MediaFileSource,
    ): Result<MediaUploadResult> =
        if (uploadsToSpace()) space.uploadMedia(accessToken, contentId, media) else legacy.uploadMedia(accessToken, contentId, media)

    override suspend fun downloadMedia(
        accessToken: String,
        mediaId: String,
    ): Result<MediaFile> = legacy.downloadMedia(accessToken, mediaId)

    override suspend fun downloadReferencedMedia(
        accessToken: String,
        reference: String,
        mediaId: String,
    ): Result<MediaFile> =
        when {
            SpaceMediaReference.parse(reference) != null && space is ReferencedMediaDataSource ->
                space.downloadReferencedMedia(accessToken, reference, mediaId)
            SpaceMediaReference.parse(reference) != null -> space.downloadMedia(accessToken, mediaId)
            legacy is ReferencedMediaDataSource -> legacy.downloadReferencedMedia(accessToken, reference, mediaId)
            else -> legacy.downloadMedia(accessToken, mediaId)
        }
}

/**
 * The signed-in account's server and personal space: the server a request is bound to, or the
 * session's, and the space named by the account's DID.
 */
fun spaceLocation(
    sessionStorage: SessionStorage,
    accountDid: suspend () -> String?,
): suspend () -> SpaceLocation? =
    {
        val origin =
            currentCoroutineContext()[CloudRequestBinding]?.location?.origin
                ?: sessionStorage.getOriginBoundSession()?.origin
        val did = accountDid()
        if (origin == null || did == null) null else SpaceLocation(origin, SpaceRef.personal(did))
    }
