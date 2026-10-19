package app.logdate.client.sync.space

import app.logdate.lexicon.app.logdate.DefsMedia
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import studio.hypertext.atproto.lexicon.data.Blob
import studio.hypertext.atproto.lexicon.data.CidLink
import studio.hypertext.atproto.repo.Cid
import kotlin.uuid.Uuid

/**
 * A media file stored as a blob in a personal space, and the reference a note keeps for it:
 * `https://space.logdate.invalid/media/{cid}?contentId=…&type=…&storedSize=…&size=…`.
 *
 * Why the reference has this shape:
 * - It is `https`, because the sync engine treats only `http(s)` references as files that live on
 *   a server (`SyncMediaTransfer.isRemoteRef`): it never uploads them again, and downloads them
 *   when the note is applied. A `logdate-media://` reference names a file in this device's media
 *   store, so the engine would try to upload it.
 * - Its path is `/media/{cid}`, because the engine reads a remote file's id from the segment after
 *   `media` (`SyncMediaTransfer.extractMediaId`).
 * - Its host ends in `.invalid`, a name reserved never to resolve (RFC 6761), so nothing can send
 *   it to a real server. The file is always read through the signed-in server's personal space,
 *   which is why the same reference keeps working after the account moves to another server.
 *
 * @property cid the content identifier of the stored ciphertext.
 * @property contentId the note or draft block the file belongs to.
 * @property mimeType the type of the decrypted file, such as `image/jpeg`.
 * @property storedSize the size of the stored ciphertext in bytes.
 * @property size the size of the decrypted file in bytes, when known.
 */
data class SpaceMediaReference(
    val cid: String,
    val contentId: Uuid,
    val mimeType: String,
    val storedSize: Long,
    val size: Long? = null,
) {
    fun format(): String =
        URLBuilder(protocol = URLProtocol.HTTPS, host = HOST)
            .apply {
                appendPathSegments(MEDIA_SEGMENT, cid)
                parameters.append(CONTENT_ID, contentId.toString())
                parameters.append(TYPE, mimeType)
                parameters.append(STORED_SIZE, storedSize.toString())
                size?.let { parameters.append(SIZE, it.toString()) }
            }.buildString()

    /**
     * The file as a record's `app.logdate.defs#media` names it. The blob is the stored ciphertext,
     * typed as opaque bytes; [mimeType] and [size] describe the decrypted file.
     */
    fun toMedia(
        owner: String,
        alt: String? = null,
    ): DefsMedia {
        val link =
            Cid.parse(cid).getOrElse { error ->
                throw SpaceRecordMappingException("$owner: its media file's content identifier isn't a CID", error)
            }
        return DefsMedia(
            blob = Blob(ref = CidLink(link), mimeType = STORED_MIME_TYPE, size = storedSize),
            mimeType = mimeType,
            size = size,
            alt = alt,
        )
    }

    companion object {
        private const val HOST = "space.logdate.invalid"
        private const val MEDIA_SEGMENT = "media"
        private const val CONTENT_ID = "contentId"
        private const val TYPE = "type"
        private const val STORED_SIZE = "storedSize"
        private const val SIZE = "size"

        /** Every blob in a personal space is ciphertext, whatever file it decrypts to. */
        const val STORED_MIME_TYPE = "application/octet-stream"

        /** The reference [reference] spells, or null when it names something else. */
        fun parse(reference: String): SpaceMediaReference? {
            val url = runCatching { Url(reference) }.getOrNull() ?: return null
            if (url.host != HOST || url.protocol != URLProtocol.HTTPS) return null
            val segments = url.segments
            if (segments.size != 2 || segments[0] != MEDIA_SEGMENT) return null
            if (Cid.parse(segments[1]).isFailure) return null
            val contentId = url.parameters[CONTENT_ID]?.let(Uuid::parseOrNull) ?: return null
            val mimeType = url.parameters[TYPE]?.takeIf { it.isNotBlank() } ?: return null
            val storedSize = url.parameters[STORED_SIZE]?.toLongOrNull()?.takeIf { it >= 0 } ?: return null
            val size =
                url.parameters[SIZE]?.let { value ->
                    value.toLongOrNull()?.takeIf { it >= 0 } ?: return null
                }
            return SpaceMediaReference(segments[1], contentId, mimeType, storedSize, size)
        }

        /** The file [media] names, belonging to [contentId]. */
        fun fromMedia(
            contentId: Uuid,
            media: DefsMedia,
        ): SpaceMediaReference = SpaceMediaReference(media.blob.ref.cid.value, contentId, media.mimeType, media.blob.size, media.size)
    }
}

/**
 * Connects the media references notes keep on this device with the files records name.
 *
 * Mappers neither upload nor download. The transport uploads a note's file before its record is
 * built, which leaves the note referring to the uploaded file by its [SpaceMediaReference]; a
 * downloaded note refers to its file the same way, and media recovery downloads it from there.
 */
interface SpaceMediaResolver {
    /** The uploaded file [mediaRef] names, or null when it isn't stored in the personal space yet. */
    fun uploaded(mediaRef: String): SpaceMediaReference?

    /** The media reference a downloaded item keeps for the file [media] names, which belongs to [contentId]. */
    fun downloaded(
        contentId: Uuid,
        media: DefsMedia,
    ): String

    /** Reads and writes [SpaceMediaReference]s. */
    companion object Default : SpaceMediaResolver {
        override fun uploaded(mediaRef: String): SpaceMediaReference? = SpaceMediaReference.parse(mediaRef)

        override fun downloaded(
            contentId: Uuid,
            media: DefsMedia,
        ): String = SpaceMediaReference.fromMedia(contentId, media).format()
    }
}

/**
 * The record's media for the file [mediaRef] names. Until the file is uploaded the item can't be
 * written, so the failure is retryable.
 */
fun SpaceMediaResolver.mediaFor(
    mediaRef: String,
    owner: String,
    alt: String? = null,
): DefsMedia = uploadedOrRetry(mediaRef, owner).toMedia(owner, alt)

/** The content identifier of the uploaded file [mediaRef] names, for the record's blob list. */
fun SpaceMediaResolver.blobCidFor(
    mediaRef: String,
    owner: String,
): String = uploadedOrRetry(mediaRef, owner).cid

private fun SpaceMediaResolver.uploadedOrRetry(
    mediaRef: String,
    owner: String,
): SpaceMediaReference =
    uploaded(mediaRef)
        ?: throw SpaceRecordMappingException("$owner: its media file hasn't been uploaded to the personal space yet", retryable = true)
