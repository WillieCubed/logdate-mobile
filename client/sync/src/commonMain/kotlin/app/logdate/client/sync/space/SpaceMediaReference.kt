package app.logdate.client.sync.space

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.uuid.Uuid

/**
 * Names a media file stored as a blob in a personal space, in the form the sync engine keeps for
 * media that lives on a server: `https://space.logdate.invalid/media/{cid}?contentId=…&type=…&size=…`.
 *
 * The `.invalid` host never resolves. The reference only identifies the blob, which is always
 * read through the signed-in server's personal space, so the same reference works on every server
 * the account moves to.
 *
 * @property cid the content identifier of the stored ciphertext.
 * @property contentId the note or draft block the file belongs to.
 * @property mimeType the type of the decrypted file, such as `image/jpeg`.
 * @property storedSize the size of the stored ciphertext in bytes.
 */
data class SpaceMediaReference(
    val cid: String,
    val contentId: Uuid,
    val mimeType: String,
    val storedSize: Long,
) {
    fun format(): String =
        URLBuilder(protocol = URLProtocol.HTTPS, host = HOST)
            .apply {
                appendPathSegments(MEDIA_SEGMENT, cid)
                parameters.append(CONTENT_ID, contentId.toString())
                parameters.append(TYPE, mimeType)
                parameters.append(SIZE, storedSize.toString())
            }.buildString()

    /** The record's `blob` field for this file: the stored ciphertext, typed as opaque bytes. */
    fun blob(): JsonObject =
        buildJsonObject {
            put("\$type", "blob")
            putJsonObject("ref") { put("\$link", cid) }
            put("mimeType", STORED_MIME_TYPE)
            put("size", storedSize)
        }

    companion object {
        private const val HOST = "space.logdate.invalid"
        private const val MEDIA_SEGMENT = "media"
        private const val CONTENT_ID = "contentId"
        private const val TYPE = "type"
        private const val SIZE = "size"

        /** Every blob in a personal space is ciphertext, whatever file it decrypts to. */
        const val STORED_MIME_TYPE = "application/octet-stream"

        /** The reference [reference] spells, or null when it names something else. */
        fun parse(reference: String): SpaceMediaReference? {
            val url = runCatching { Url(reference) }.getOrNull() ?: return null
            if (url.host != HOST || url.protocol != URLProtocol.HTTPS) return null
            val segments = url.segments
            if (segments.size != 2 || segments[0] != MEDIA_SEGMENT) return null
            val contentId = url.parameters[CONTENT_ID]?.let(Uuid::parseOrNull) ?: return null
            val mimeType = url.parameters[TYPE]?.takeIf { it.isNotBlank() } ?: return null
            val size = url.parameters[SIZE]?.toLongOrNull()?.takeIf { it >= 0 } ?: return null
            return SpaceMediaReference(segments[1], contentId, mimeType, size)
        }

        /**
         * The reference for a record's [blob] field, belonging to [contentId] and decrypting to a
         * [mimeType] file, or null when [blob] isn't a blob.
         */
        fun fromBlob(
            contentId: Uuid,
            blob: JsonObject,
            mimeType: String,
        ): SpaceMediaReference? {
            val cid = ((blob["ref"] as? JsonObject)?.get("\$link") as? JsonPrimitive)?.contentOrNull ?: return null
            val size = (blob["size"] as? JsonPrimitive)?.longOrNull ?: return null
            return SpaceMediaReference(cid, contentId, mimeType, size)
        }
    }
}
