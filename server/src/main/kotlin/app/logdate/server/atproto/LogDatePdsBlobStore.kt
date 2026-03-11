@file:OptIn(ExperimentalUuidApi::class)

package app.logdate.server.atproto

import app.logdate.server.database.toJavaUUID
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.logdate.LogDateAtprotoBlob
import app.logdate.server.logdate.LogDateBlobNamespace
import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.LogDateBlobWriteRequest
import app.logdate.server.logdate.LogDateMediaBlobRepository
import studio.hypertext.atproto.pds.BlobDownload
import studio.hypertext.atproto.pds.BlobRef
import studio.hypertext.atproto.pds.CidLink
import studio.hypertext.atproto.pds.GetBlobRequest
import studio.hypertext.atproto.pds.UploadBlobRequest
import studio.hypertext.atproto.pds.runtime.PdsBlobStore
import studio.hypertext.atproto.repo.Cid
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi

/**
 * Server adapter that exposes LogDate-owned blob persistence through the shared PDS blob store
 * contract.
 */
class LogDatePdsBlobStore(
    private val identityService: AtprotoIdentityService,
    private val mediaBlobRepository: LogDateMediaBlobRepository,
    private val blobStorage: LogDateBlobStorage,
) : PdsBlobStore {
    override suspend fun putBlob(request: UploadBlobRequest): Result<BlobRef> =
        runCatching {
            val account = requireNotNull(identityService.findByDid(request.repo.toString())) { "Unknown repo DID: ${request.repo}" }
            val userId = account.id.toJavaUUID()
            val cid = Cid.rawSha256(request.bytes)
            val storagePath =
                blobStorage.putBlob(
                    LogDateBlobWriteRequest(
                        ownerId = userId,
                        namespace = LogDateBlobNamespace.ATPROTO,
                        blobId = cid.toString(),
                        contentType = request.contentType,
                        bytes = request.bytes,
                    ),
                )
            mediaBlobRepository.upsertAtprotoBlob(
                userId = userId,
                blob =
                    LogDateAtprotoBlob(
                        cid = cid.toString(),
                        userId = userId,
                        mimeType = request.contentType,
                        sizeBytes = request.bytes.size.toLong(),
                        storagePath = storagePath,
                        createdAt = Clock.System.now().toEpochMilliseconds(),
                    ),
            )
            BlobRef(
                ref = CidLink(cid),
                mimeType = request.contentType,
                size = request.bytes.size.toLong(),
            )
        }

    override suspend fun getBlob(request: GetBlobRequest): Result<BlobDownload?> =
        runCatching {
            val account = identityService.findByDid(request.did.toString()) ?: return@runCatching null
            val blob =
                mediaBlobRepository.getAtprotoBlob(
                    userId = account.id.toJavaUUID(),
                    cid = request.cid.toString(),
                ) ?: return@runCatching null
            val bytes = blobStorage.getBlob(blob.storagePath) ?: return@runCatching null
            BlobDownload(
                contentType = blob.mimeType,
                bytes = bytes,
            )
        }
}
