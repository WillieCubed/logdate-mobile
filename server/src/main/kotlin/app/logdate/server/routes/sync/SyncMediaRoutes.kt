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
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
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
        post {
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

        get("/{mediaId}") {
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

        get("/{mediaId}/binary") {
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

        delete("/{mediaId}") {
            val start = System.currentTimeMillis()
            var success = false
            try {
                val userId = extractUserId(call, tokenService) ?: return@delete
                val mediaId = call.requiredPathParam("mediaId")
                val record = mediaBlobRepository.getMedia(userId, mediaId)
                if (record != null) {
                    record.storagePath?.let { mediaStorage?.deleteBlob(it) }
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
