package studio.hypertext.atproto.pds.runtime

import studio.hypertext.atproto.pds.BlobDownload
import studio.hypertext.atproto.pds.BlobRef
import studio.hypertext.atproto.pds.GetBlobRequest
import studio.hypertext.atproto.pds.UploadBlobRequest

/**
 * Runtime storage adapter for AT Protocol blob semantics.
 *
 * This sits underneath [DefaultPdsBlobService] so server runtimes can expose blob uploads and
 * downloads without coupling their domain layer to AT Protocol route types.
 */
public interface PdsBlobStore {
    /**
     * Persists the raw bytes in [request] and returns the resulting blob reference.
     */
    public suspend fun putBlob(request: UploadBlobRequest): Result<BlobRef>

    /**
     * Returns the blob for [request], or `null` when it does not exist.
     */
    public suspend fun getBlob(request: GetBlobRequest): Result<BlobDownload?>
}
