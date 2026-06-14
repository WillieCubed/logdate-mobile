package app.logdate.server.routes.sync

import app.logdate.server.auth.TokenService
import app.logdate.server.crypto.EncryptionService
import app.logdate.server.entitlements.EntitlementEnforcer
import app.logdate.server.entitlements.QuotaCheck
import app.logdate.server.logdate.LogDateBlobNamespace
import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.LogDateBlobWriteRequest
import app.logdate.server.logdate.LogDateMedia
import app.logdate.server.logdate.LogDateMediaBlobRepository
import app.logdate.server.ratelimit.SlidingWindowRateLimiter
import app.logdate.server.responses.error
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.shared.model.sync.DeviceId
import app.logdate.shared.model.sync.MediaMetadataResponse
import app.logdate.shared.model.sync.MediaUploadResponse
import io.github.aakira.napier.Napier
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import java.util.UUID

/**
 * `/api/v1/media` endpoints — multipart upload, metadata read, binary download, delete.
 *
 * Write path is gated twice before any work happens:
 *   1. [SlidingWindowRateLimiter] throttles by `media-upload:<accountId>`.
 *   2. [EntitlementEnforcer] rejects over-quota uploads with 402.
 */
internal fun Route.syncMediaRoutes(
    tokenService: TokenService?,
    mediaStorage: LogDateBlobStorage?,
    metrics: SyncMetricsRegistry,
    encryptionService: EncryptionService,
    mediaBlobRepository: LogDateMediaBlobRepository,
    entitlementEnforcer: EntitlementEnforcer?,
    rateLimiter: SlidingWindowRateLimiter?,
    config: SyncRouteConfig,
) {
    val mediaAccessPolicy = config.mediaAccessPolicy
    route("/media") {
        post({
            tags = listOf("Media")
            summary = "Upload media"
            description = "Upload a media file (image, video, etc.) for a content entry."
            securitySchemeNames = listOf("bearerAuth")
            request {
                body<ByteArray> {
                    mediaTypes = setOf(ContentType.MultiPart.FormData)
                }
            }
            response {
                HttpStatusCode.Created to {
                    description = "Media uploaded successfully"
                    body<MediaUploadResponse>()
                }
            }
        }) {
            val start = System.currentTimeMillis()
            var success = false
            var bytes = 0L
            try {
                val userId = extractUserId(call, tokenService) ?: return@post

                if (rateLimiter != null && !rateLimiter.allow("media-upload:$userId", MEDIA_UPLOAD_RATE_LIMIT)) {
                    return@post respondRateLimited(call, rateLimiter, "media-upload:$userId", MEDIA_UPLOAD_RATE_LIMIT)
                }

                val req = call.receiveMediaMultipartUpload() ?: return@post
                bytes = req.sizeBytes
                Napier.d("Media upload for user $userId: ${req.fileName}")

                if (entitlementEnforcer != null) {
                    val quota = entitlementEnforcer.checkMediaUpload(userId, req.sizeBytes)
                    if (quota is QuotaCheck.Denied) {
                        return@post respondQuotaExceeded(call, quota)
                    }
                }
                val mediaId = UUID.randomUUID().toString()

                val encryptedPayload =
                    runCatching {
                        encryptionService.processMediaUpload(
                            req.data,
                            userId.toString(),
                            mediaId,
                            req.contentId,
                        )
                    }.getOrElse { error ->
                        Napier.e("Failed to encrypt media payload", error)
                        return@post call.respond(
                            HttpStatusCode.InternalServerError,
                            error("MEDIA_ENCRYPT_FAILED", "Failed to encrypt media payload"),
                        )
                    }

                val storagePath =
                    if (mediaStorage != null) {
                        runCatching {
                            mediaStorage.putBlob(
                                LogDateBlobWriteRequest(
                                    ownerId = userId,
                                    namespace = LogDateBlobNamespace.MEDIA,
                                    blobId = mediaId,
                                    fileName = req.fileName,
                                    contentType = req.mimeType,
                                    bytes = encryptedPayload.data,
                                ),
                            )
                        }.getOrElse { error ->
                            Napier.e("Failed to upload media to external storage", error)
                            return@post call.respond(
                                HttpStatusCode.InternalServerError,
                                error("MEDIA_UPLOAD_FAILED", "Failed to store media"),
                            )
                        }
                    } else {
                        null
                    }
                val stored =
                    runCatching {
                        mediaBlobRepository.upsertMedia(
                            userId,
                            LogDateMedia(
                                mediaId = mediaId,
                                contentId = req.contentId,
                                userId = userId,
                                fileName = req.fileName,
                                mimeType = req.mimeType,
                                sizeBytes = req.sizeBytes,
                                data = if (storagePath == null) encryptedPayload.data else ByteArray(0),
                                storagePath = storagePath,
                                createdAt = System.currentTimeMillis(),
                                version = 0L,
                                deviceId = DeviceId(req.deviceId),
                                encryptionVersion = 1,
                                encryptionKeyId = "default",
                                encryptionMode = "SERVER",
                            ),
                        )
                    }.getOrElse { error ->
                        if (storagePath != null) {
                            runCatching { mediaStorage?.deleteBlob(storagePath) }
                                .onFailure { deleteError ->
                                    Napier.w("Failed to roll back media blob $mediaId after metadata write failure", deleteError)
                                }
                        }
                        Napier.e("Failed to persist media metadata", error)
                        return@post call.respond(
                            HttpStatusCode.InternalServerError,
                            error("MEDIA_METADATA_WRITE_FAILED", "Failed to store media metadata"),
                        )
                    }
                val downloadUrl = resolveMediaDownloadUrl(call, stored, mediaStorage, mediaAccessPolicy)
                val response =
                    MediaUploadResponse(
                        contentId = req.contentId,
                        mediaId = stored.mediaId,
                        downloadUrl = downloadUrl,
                        uploadedAt = stored.createdAt,
                    )
                call.response.headers.append(HttpHeaders.Location, "/api/v1/media/${stored.mediaId}")
                call.respond(HttpStatusCode.Created, response)
                success = true
            } finally {
                metrics.recordOperation(METRIC_MEDIA_UPLOAD, System.currentTimeMillis() - start, success, bytes)
            }
        }

        get("/{mediaId}", {
            tags = listOf("Media")
            summary = "Get media metadata"
            description = "Retrieve metadata for a specific media file."
            securitySchemeNames = listOf("bearerAuth")
            request {
                pathParameter<String>("mediaId") {
                    description = "The ID of the media to retrieve metadata for."
                }
            }
            response {
                HttpStatusCode.OK to {
                    description = "Media metadata retrieved successfully"
                    body<MediaMetadataResponse>()
                }
            }
        }) {
            val start = System.currentTimeMillis()
            var success = false
            try {
                val userId = extractUserId(call, tokenService) ?: return@get
                val mediaId = call.requiredPathParam("mediaId")
                val record =
                    mediaBlobRepository.getMedia(userId, mediaId)
                        ?: return@get call.respond(HttpStatusCode.NotFound, error("NOT_FOUND", "Media not found"))

                call.respond(
                    MediaMetadataResponse(
                        contentId = record.contentId,
                        mediaId = record.mediaId,
                        fileName = record.fileName,
                        mimeType = record.mimeType,
                        sizeBytes = record.sizeBytes,
                        downloadUrl = resolveMediaDownloadUrl(call, record, mediaStorage, mediaAccessPolicy),
                        uploadedAt = record.createdAt,
                    ),
                )
                success = true
            } finally {
                metrics.recordOperation(METRIC_MEDIA_DOWNLOAD, System.currentTimeMillis() - start, success)
            }
        }

        get("/{mediaId}/binary", {
            tags = listOf("Media")
            summary = "Download media binary"
            description = "Download the raw binary data of a media file."
            securitySchemeNames = listOf("bearerAuth")
            request {
                pathParameter<String>("mediaId") {
                    description = "The ID of the media to download."
                }
            }
            response {
                HttpStatusCode.OK to {
                    description = "Media binary data retrieved successfully"
                    body<ByteArray> {
                        mediaTypes = setOf(ContentType.Application.OctetStream)
                    }
                }
            }
        }) {
            val start = System.currentTimeMillis()
            var success = false
            var bytes = 0L
            try {
                val userId = extractUserId(call, tokenService) ?: return@get
                val mediaId = call.requiredPathParam("mediaId")
                val record =
                    mediaBlobRepository.getMedia(userId, mediaId)
                        ?: return@get call.respond(HttpStatusCode.NotFound, error("NOT_FOUND", "Media not found"))

                val encryptedPayload =
                    if (record.storagePath != null) {
                        val storage =
                            mediaStorage
                                ?: return@get call.respond(
                                    HttpStatusCode.InternalServerError,
                                    error("MEDIA_STORAGE_UNAVAILABLE", "Media storage not configured"),
                                )
                        storage.getBlob(record.storagePath)
                            ?: return@get call.respond(
                                HttpStatusCode.NotFound,
                                error("NOT_FOUND", "Media not found"),
                            )
                    } else {
                        record.data
                    }

                val payload =
                    runCatching {
                        encryptionService.processMediaDownload(encryptedPayload, shouldDecrypt = true)
                    }.getOrElse { error ->
                        Napier.e("Failed to decrypt media payload for $mediaId", error)
                        return@get call.respond(
                            HttpStatusCode.InternalServerError,
                            error("MEDIA_DECRYPT_FAILED", "Failed to decrypt media payload"),
                        )
                    }
                bytes = payload.size.toLong()
                val contentType =
                    runCatching { ContentType.parse(record.mimeType) }.getOrElse { ContentType.Application.OctetStream }
                call.respondBytes(payload, contentType)
                success = true
            } finally {
                metrics.recordOperation(METRIC_MEDIA_DOWNLOAD, System.currentTimeMillis() - start, success, bytes)
            }
        }

        delete("/{mediaId}", {
            tags = listOf("Media")
            summary = "Delete media"
            description = "Delete a specific media file and its metadata."
            securitySchemeNames = listOf("bearerAuth")
            request {
                pathParameter<String>("mediaId") {
                    description = "The ID of the media to delete."
                }
            }
            response {
                HttpStatusCode.NoContent to {
                    description = "Media deleted successfully"
                }
            }
        }) {
            val start = System.currentTimeMillis()
            var success = false
            try {
                val userId = extractUserId(call, tokenService) ?: return@delete
                val mediaId = call.requiredPathParam("mediaId")
                val record = mediaBlobRepository.getMedia(userId, mediaId)
                if (record != null) {
                    val storagePath = record.storagePath
                    if (storagePath != null) {
                        val storage =
                            mediaStorage
                                ?: return@delete call.respond(
                                    HttpStatusCode.ServiceUnavailable,
                                    error("MEDIA_STORAGE_UNAVAILABLE", "Media storage not configured"),
                                )
                        if (!storage.deleteBlob(storagePath)) {
                            Napier.w("Failed to delete media blob for $mediaId at $storagePath")
                            return@delete call.respond(
                                HttpStatusCode.InternalServerError,
                                error("MEDIA_DELETE_FAILED", "Failed to delete media blob"),
                            )
                        }
                    }
                    mediaBlobRepository.deleteMedia(userId, mediaId, System.currentTimeMillis())
                }
                call.respond(HttpStatusCode.NoContent)
                success = true
            } finally {
                metrics.recordOperation(METRIC_MEDIA_DELETE, System.currentTimeMillis() - start, success)
            }
        }
    }
}
