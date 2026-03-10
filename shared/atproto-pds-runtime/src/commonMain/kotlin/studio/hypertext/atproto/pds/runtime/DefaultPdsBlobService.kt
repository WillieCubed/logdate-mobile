package studio.hypertext.atproto.pds.runtime

import studio.hypertext.atproto.pds.BlobDownload
import studio.hypertext.atproto.pds.GetBlobRequest
import studio.hypertext.atproto.pds.PdsBlobService
import studio.hypertext.atproto.pds.UploadBlobRequest
import studio.hypertext.atproto.pds.UploadBlobResponse

/**
 * Default blob service implementation backed by a [PdsBlobStore].
 */
public class DefaultPdsBlobService(
    private val blobStore: PdsBlobStore,
) : PdsBlobService {
    override suspend fun uploadBlob(request: UploadBlobRequest): Result<UploadBlobResponse> =
        blobStore.putBlob(request).mapCatching(::UploadBlobResponse)

    override suspend fun getBlob(request: GetBlobRequest): Result<BlobDownload?> = blobStore.getBlob(request)
}
