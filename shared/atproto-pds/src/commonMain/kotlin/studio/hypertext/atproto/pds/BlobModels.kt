package studio.hypertext.atproto.pds

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.Cid

/**
 * CID link wrapper used by AT Protocol blob references.
 */
@Serializable
public data class CidLink(
    @SerialName("\$link")
    val cid: Cid,
)

/**
 * AT Protocol blob reference payload.
 */
@Serializable
public data class BlobRef(
    @EncodeDefault
    @SerialName("\$type")
    val type: String = TYPE,
    val ref: CidLink,
    val mimeType: String,
    val size: Long,
) {
    public companion object {
        /**
         * AT Protocol blob `$type` discriminator.
         */
        public const val TYPE: String = "blob"
    }
}

/**
 * Request to upload a blob for [repo].
 */
public data class UploadBlobRequest(
    val repo: AtprotoDid,
    val contentType: String,
    val bytes: ByteArray,
)

/**
 * Response payload for `com.atproto.repo.uploadBlob`.
 */
@Serializable
public data class UploadBlobResponse(
    val blob: BlobRef,
)

/**
 * Request to fetch a blob by [did] and [cid].
 */
public data class GetBlobRequest(
    val did: AtprotoDid,
    val cid: Cid,
)

/**
 * Raw blob download result.
 */
public data class BlobDownload(
    val contentType: String,
    val bytes: ByteArray,
)
