package app.logdate.client.sync.space

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.crypto.IdentityKeyNotFoundException
import app.logdate.client.media.MediaFileSource
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.cloud.CloudRequestBinding
import app.logdate.client.sync.cloud.DownloadedMediaFile
import app.logdate.client.sync.cloud.FileMediaDataSource
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
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
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
    private val workDirectory: Path = Path(SystemTemporaryDirectory, "logdate-space-media"),
) : CloudMediaDataSource,
    ReferencedMediaDataSource,
    FileMediaDataSource {
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
        val reference = SpaceMediaReference(cid, contentId, media.mimeType, storedSize, media.sizeBytes.takeUnless { alreadyEncrypted })
        return Result.success(MediaUploadResult(mediaId = cid, downloadUrl = reference.format(), uploadedAt = Clock.System.now()))
    }

    /** A personal-space blob is found by its whole reference; see [downloadReferencedMedia]. */
    override suspend fun downloadMedia(
        accessToken: String,
        mediaId: String,
    ): Result<MediaFile> = Result.failure(SpaceUnavailableException("A personal-space file can only be found by its whole reference"))

    /**
     * Reads the file whole into memory, for callers that need its bytes. The media store's own path
     * is [downloadMediaToFile], which never holds the file whole.
     */
    override suspend fun downloadReferencedMedia(
        accessToken: String,
        reference: String,
        mediaId: String,
    ): Result<MediaFile> {
        val downloaded = downloadMediaToFile(accessToken, reference, mediaId).getOrElse { return Result.failure(it) }
        return try {
            val data = SystemFileSystem.source(downloaded.path).buffered().use { it.readByteArray() }
            Result.success(MediaFile(downloaded.contentId, downloaded.fileName, downloaded.mimeType, data.size.toLong(), data))
        } finally {
            SystemFileSystem.delete(downloaded.path, mustExist = false)
        }
    }

    /**
     * Streams the blob into a file while hashing it, checks the hash against the reference's content
     * identifier, then decrypts the file a chunk at a time into another. Neither step holds more than
     * one chunk, whatever the file's size.
     */
    override suspend fun downloadMediaToFile(
        accessToken: String,
        reference: String,
        mediaId: String,
    ): Result<DownloadedMediaFile> {
        val named =
            SpaceMediaReference.parse(reference) ?: return Result.failure(MediaIntegrityException("Not a personal-space file reference"))
        if (named.storedSize > MAX_BLOB_BYTES) return Result.failure(tooLarge(named.contentId, named.storedSize))
        val target = location() ?: return Result.failure(SpaceUnavailableException("No personal space for the signed-in account"))
        SystemFileSystem.createDirectories(workDirectory)
        val stored = Path(workDirectory, "${Uuid.random()}.stored")
        val plain = Path(workDirectory, "${Uuid.random()}.part")
        try {
            val download = verifiedDownload(accessToken, target, named, stored)
            download?.let { failure ->
                SystemFileSystem.delete(plain, mustExist = false)
                return Result.failure(failure)
            }
            val size =
                try {
                    val decryptor = mediaPayloadCrypto.streamDecryptor()
                    SystemFileSystem.source(stored).buffered().use { source ->
                        SystemFileSystem.sink(plain).buffered().use { sink -> decryptor.decrypt(source, sink) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (missing: IdentityKeyNotFoundException) {
                    SystemFileSystem.delete(plain, mustExist = false)
                    return Result.failure(missing)
                } catch (_: Exception) {
                    SystemFileSystem.delete(plain, mustExist = false)
                    return Result.failure(MediaDecryptionException())
                }
            return Result.success(DownloadedMediaFile(named.contentId, named.contentId.toString(), named.mimeType, plain, size))
        } catch (error: Throwable) {
            SystemFileSystem.delete(plain, mustExist = false)
            throw error
        } finally {
            SystemFileSystem.delete(stored, mustExist = false)
        }
    }

    /** Downloads [named]'s blob into [into], or returns why it can't be used. */
    private suspend fun verifiedDownload(
        accessToken: String,
        target: SpaceLocation,
        named: SpaceMediaReference,
        into: Path,
    ): Throwable? {
        val hash =
            CryptographyProvider.Default
                .get(SHA256)
                .hasher()
                .createHashFunction()
        val written =
            hash.use { function ->
                val counted = CappedSink(SystemFileSystem.sink(into), named.storedSize)
                val found =
                    counted.use { file ->
                        spaceCall {
                            homes
                                .open(
                                    target.origin,
                                    accessToken,
                                ).downloadBlob(target.space, named.cid, function.updatingSink(file))
                        }.getOrElse { return it }
                    }
                if (!found) return CloudApiException("MEDIA_NOT_FOUND", "The file is no longer stored", statusCode = 404)
                if (counted.overflowed || counted.written != named.storedSize) {
                    return MediaIntegrityException("The downloaded file isn't the size its reference names")
                }
                function.hashToByteArray()
            }
        if (Cid.fromBytes(RAW_SHA256_CID_PREFIX + written).value != named.cid) {
            return MediaIntegrityException("The downloaded file doesn't match its content identifier")
        }
        return null
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

        /** CID version 1, the raw codec, and a 32-byte SHA-256 multihash, ahead of the digest. */
        val RAW_SHA256_CID_PREFIX = byteArrayOf(0x01, 0x55, 0x12, 0x20)
    }
}

/** Writes to [delegate] until [limit] bytes, then refuses the rest, so a blob can't fill the disk. */
private class CappedSink(
    private val delegate: RawSink,
    private val limit: Long,
) : RawSink {
    var written = 0L
        private set
    var overflowed = false
        private set

    override fun write(
        source: Buffer,
        byteCount: Long,
    ) {
        if (written + byteCount > limit) {
            overflowed = true
            throw MediaIntegrityException("The downloaded file is larger than its reference names")
        }
        delegate.write(source, byteCount)
        written += byteCount
    }

    override fun flush() = delegate.flush()

    override fun close() = delegate.close()
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
    ReferencedMediaDataSource,
    FileMediaDataSource {
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

    override suspend fun downloadMediaToFile(
        accessToken: String,
        reference: String,
        mediaId: String,
    ): Result<DownloadedMediaFile>? {
        if (SpaceMediaReference.parse(reference) != null && space is FileMediaDataSource) {
            return space.downloadMediaToFile(accessToken, reference, mediaId)
        }
        return (legacy as? FileMediaDataSource)?.downloadMediaToFile(accessToken, reference, mediaId)
    }

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
