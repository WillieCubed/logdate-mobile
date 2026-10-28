package app.logdate.space

import kotlinx.io.RawSource
import kotlinx.serialization.Serializable

/** A committed encrypted blob, returned without transferring its payload. */
@Serializable
public data class BlobUploadReceipt(
    public val cid: String,
    public val sizeBytes: Long,
)

/** Explicit capability result: an unsupported server is never mistaken for an absent receipt. */
@Serializable
public data class BlobUploadReceiptLookup(
    public val supported: Boolean,
    public val receipt: BlobUploadReceipt? = null,
)

/** Optional write-once upload identities, scoped to the authenticated owner and space. */
public interface BlobUploadReceipts {
    public suspend fun lookupBlobUpload(
        space: SpaceRef,
        intent: String,
    ): BlobUploadReceiptLookup

    public suspend fun uploadBlobWithIntent(
        space: SpaceRef,
        intent: String,
        sizeBytes: Long,
        open: () -> RawSource,
    ): String
}
