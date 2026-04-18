package app.logdate.server.routes.sync

import app.logdate.server.auth.TokenService
import app.logdate.server.crypto.EncryptionService
import app.logdate.server.entitlements.EntitlementEnforcer
import app.logdate.server.entitlements.QuotaCheck
import app.logdate.server.logdate.LogDateBackup
import app.logdate.server.logdate.LogDateBackupRepository
import app.logdate.server.logdate.LogDateBlobNamespace
import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.LogDateBlobWriteRequest
import app.logdate.server.ratelimit.SlidingWindowRateLimiter
import app.logdate.server.responses.error
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.shared.model.sync.BackupInfoResponse
import app.logdate.shared.model.sync.BackupListResponse
import app.logdate.shared.model.sync.BackupUploadResponse
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
 * `/api/v1/backups` endpoints — encrypted device backup upload, listing, binary download, delete.
 *
 * Same gate pattern as media: per-user rate limit first, then quota check.
 */
internal fun Route.syncBackupRoutes(
    tokenService: TokenService?,
    mediaStorage: LogDateBlobStorage?,
    metrics: SyncMetricsRegistry,
    encryptionService: EncryptionService,
    backupRepository: LogDateBackupRepository,
    entitlementEnforcer: EntitlementEnforcer?,
    rateLimiter: SlidingWindowRateLimiter?,
    config: SyncRouteConfig,
) {
    val mediaAccessPolicy = config.mediaAccessPolicy
    route("/backups") {
        post {
            val start = System.currentTimeMillis()
            var success = false
            var bytes = 0L
            try {
                val userId = extractUserId(call, tokenService) ?: return@post

                if (rateLimiter != null && !rateLimiter.allow("backup-upload:$userId", BACKUP_UPLOAD_RATE_LIMIT)) {
                    return@post respondRateLimited(call, rateLimiter, "backup-upload:$userId", BACKUP_UPLOAD_RATE_LIMIT)
                }

                val req = call.receiveBackupMultipartUpload() ?: return@post
                bytes = req.data.size.toLong()

                if (entitlementEnforcer != null) {
                    val quota = entitlementEnforcer.checkBackupUpload(userId, bytes)
                    if (quota is QuotaCheck.Denied) {
                        return@post respondQuotaExceeded(call, quota)
                    }
                }

                val storage =
                    mediaStorage ?: return@post call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        error("BACKUP_STORAGE_UNAVAILABLE", "Backup storage not configured"),
                    )

                val backupId = UUID.randomUUID()

                val encryptedData =
                    runCatching {
                        encryptionService
                            .processBackupUpload(
                                req.data,
                                userId.toString(),
                                backupId.toString(),
                            ).data
                    }.getOrElse { error ->
                        Napier.e("Failed to encrypt backup", error)
                        return@post call.respond(
                            HttpStatusCode.InternalServerError,
                            error("BACKUP_ENCRYPT_FAILED", "Failed to encrypt backup"),
                        )
                    }

                val storagePath =
                    storage.putBlob(
                        LogDateBlobWriteRequest(
                            ownerId = userId,
                            namespace = LogDateBlobNamespace.BACKUP,
                            blobId = backupId.toString(),
                            fileName = null,
                            contentType = "application/octet-stream",
                            bytes = encryptedData,
                        ),
                    )

                val record =
                    backupRepository.createBackup(
                        userId,
                        LogDateBackup(
                            id = backupId,
                            userId = userId,
                            deviceId = req.deviceId,
                            manifest = req.manifest,
                            storagePath = storagePath,
                            createdAt = System.currentTimeMillis(),
                            sizeBytes = bytes,
                        ),
                    )

                call.response.headers.append(HttpHeaders.Location, "/api/v1/backups/${record.id}")
                call.respond(
                    HttpStatusCode.Created,
                    BackupUploadResponse(
                        id = record.id.toString(),
                        createdAt = record.createdAt,
                        sizeBytes = record.sizeBytes,
                    ),
                )
                success = true
            } finally {
                metrics.recordOperation("sync.backup.upload", System.currentTimeMillis() - start, success, bytes)
            }
        }

        get {
            val start = System.currentTimeMillis()
            var success = false
            try {
                val userId = extractUserId(call, tokenService) ?: return@get
                val backups = backupRepository.listBackups(userId)
                call.respond(
                    BackupListResponse(
                        backups.map {
                            BackupInfoResponse(
                                id = it.id.toString(),
                                deviceId = it.deviceId,
                                manifest = it.manifest,
                                createdAt = it.createdAt,
                                sizeBytes = it.sizeBytes,
                                downloadUrl = resolveBackupDownloadUrl(call, it, mediaStorage, mediaAccessPolicy),
                            )
                        },
                    ),
                )
                success = true
            } finally {
                metrics.recordOperation("sync.backup.list", System.currentTimeMillis() - start, success)
            }
        }

        get("/{backupId}") {
            val start = System.currentTimeMillis()
            var success = false
            try {
                val userId = extractUserId(call, tokenService) ?: return@get
                val backupId = call.parseBackupId() ?: return@get

                val record =
                    backupRepository.getBackup(userId, backupId)
                        ?: return@get call.respond(HttpStatusCode.NotFound, error("NOT_FOUND", "Backup not found"))
                call.respond(
                    BackupInfoResponse(
                        id = record.id.toString(),
                        deviceId = record.deviceId,
                        manifest = record.manifest,
                        createdAt = record.createdAt,
                        sizeBytes = record.sizeBytes,
                        downloadUrl = resolveBackupDownloadUrl(call, record, mediaStorage, mediaAccessPolicy),
                    ),
                )
                success = true
            } finally {
                metrics.recordOperation("sync.backup.download", System.currentTimeMillis() - start, success)
            }
        }

        get("/{backupId}/binary") {
            val start = System.currentTimeMillis()
            var success = false
            try {
                val userId = extractUserId(call, tokenService) ?: return@get
                val backupId = call.parseBackupId() ?: return@get

                val record =
                    backupRepository.getBackup(userId, backupId)
                        ?: return@get call.respond(HttpStatusCode.NotFound, error("NOT_FOUND", "Backup not found"))

                val storage =
                    mediaStorage ?: return@get call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        error("BACKUP_STORAGE_UNAVAILABLE", "Backup storage not configured"),
                    )

                val encryptedData =
                    storage.getBlob(record.storagePath)
                        ?: return@get call.respond(HttpStatusCode.NotFound, error("NOT_FOUND", "Backup file not found"))

                val decryptedData =
                    runCatching {
                        encryptionService.processBackupDownload(encryptedData, shouldDecrypt = true)
                    }.getOrElse { error ->
                        Napier.e("Failed to decrypt backup $backupId", error)
                        return@get call.respond(
                            HttpStatusCode.InternalServerError,
                            error("BACKUP_DECRYPT_FAILED", "Failed to decrypt backup"),
                        )
                    }

                call.respondBytes(decryptedData, ContentType.Application.OctetStream)
                success = true
            } finally {
                metrics.recordOperation("sync.backup.download", System.currentTimeMillis() - start, success)
            }
        }

        delete("/{backupId}") {
            val start = System.currentTimeMillis()
            var success = false
            try {
                val userId = extractUserId(call, tokenService) ?: return@delete
                val backupId = call.parseBackupId() ?: return@delete

                val record = backupRepository.getBackup(userId, backupId)
                if (record != null) {
                    mediaStorage?.deleteBlob(record.storagePath)
                    backupRepository.deleteBackup(userId, backupId)
                }

                call.respond(HttpStatusCode.NoContent)
                success = true
            } finally {
                metrics.recordOperation("sync.backup.delete", System.currentTimeMillis() - start, success)
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.parseBackupId(): UUID? {
    val raw = requiredPathParam("backupId")
    return runCatching { UUID.fromString(raw) }.getOrNull() ?: run {
        respond(HttpStatusCode.BadRequest, error("INVALID_ID", "Invalid backupId"))
        null
    }
}
