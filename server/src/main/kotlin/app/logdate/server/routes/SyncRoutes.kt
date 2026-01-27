package app.logdate.server.routes

import app.logdate.server.auth.TokenService
import app.logdate.server.responses.error
import app.logdate.server.responses.simpleSuccess
import app.logdate.shared.model.sync.AssociationChange
import app.logdate.shared.model.sync.AssociationChangesResponse
import app.logdate.shared.model.sync.AssociationDeleteRequest
import app.logdate.shared.model.sync.AssociationDeletion
import app.logdate.shared.model.sync.AssociationUploadRequest
import app.logdate.shared.model.sync.AssociationUploadResponse
import app.logdate.shared.model.sync.BackupInfoResponse
import app.logdate.shared.model.sync.BackupListResponse
import app.logdate.shared.model.sync.BackupUploadRequest
import app.logdate.shared.model.sync.BackupUploadResponse
import app.logdate.shared.model.sync.ContentChange
import app.logdate.shared.model.sync.ContentChangesResponse
import app.logdate.shared.model.sync.ContentDeletion
import app.logdate.shared.model.sync.ContentUpdateRequest
import app.logdate.shared.model.sync.ContentUpdateResponse
import app.logdate.shared.model.sync.ContentUploadRequest
import app.logdate.shared.model.sync.ContentUploadResponse
import app.logdate.shared.model.sync.JournalChange
import app.logdate.shared.model.sync.JournalChangesResponse
import app.logdate.shared.model.sync.JournalDeletion
import app.logdate.shared.model.sync.JournalUpdateRequest
import app.logdate.shared.model.sync.JournalUpdateResponse
import app.logdate.shared.model.sync.JournalUploadRequest
import app.logdate.shared.model.sync.JournalUploadResponse
import app.logdate.shared.model.sync.MediaDownloadResponse
import app.logdate.shared.model.sync.MediaUploadRequest
import app.logdate.shared.model.sync.MediaUploadResponse
import app.logdate.shared.model.sync.VersionConstraint
import app.logdate.server.sync.AssociationKey
import app.logdate.server.sync.AssociationRecord
import app.logdate.server.sync.BackupRecord
import app.logdate.server.sync.ContentRecord
import app.logdate.server.sync.GcsMediaStorage
import app.logdate.server.sync.JournalRecord
import app.logdate.server.crypto.EncryptionService
import app.logdate.server.sync.MediaAccessPolicy
import app.logdate.server.sync.MediaEncryptionService
import app.logdate.server.sync.MediaRecord
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.server.sync.SyncRepository
import io.github.aakira.napier.Napier
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
private data class SyncStatusSnapshot(
    val contentCount: Int,
    val journalCount: Int,
    val associationCount: Int,
    val lastTimestamp: Long
)

private const val DEFAULT_SYNC_PAGE_SIZE = 200
private const val MAX_SYNC_PAGE_SIZE = 500
private const val METRIC_SYNC_STATUS = "sync.status"
private const val METRIC_SYNC_METRICS = "sync.metrics"
private const val METRIC_SYNC_METRICS_PROM = "sync.metrics.prometheus"
private const val METRIC_CONTENT_UPLOAD = "sync.content.upload"
private const val METRIC_CONTENT_CHANGES = "sync.content.changes"
private const val METRIC_CONTENT_UPDATE = "sync.content.update"
private const val METRIC_CONTENT_DELETE = "sync.content.delete"
private const val METRIC_JOURNAL_UPLOAD = "sync.journal.upload"
private const val METRIC_JOURNAL_CHANGES = "sync.journal.changes"
private const val METRIC_JOURNAL_UPDATE = "sync.journal.update"
private const val METRIC_JOURNAL_DELETE = "sync.journal.delete"
private const val METRIC_ASSOCIATION_UPLOAD = "sync.association.upload"
private const val METRIC_ASSOCIATION_CHANGES = "sync.association.changes"
private const val METRIC_ASSOCIATION_DELETE = "sync.association.delete"
private const val METRIC_MEDIA_UPLOAD = "sync.media.upload"
private const val METRIC_MEDIA_DOWNLOAD = "sync.media.download"
private const val METRIC_SYNC_PURGE = "sync.maintenance.purge"
private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Extracts and validates the user ID from the Authorization header.
 * Returns null and responds with 401 if authentication fails.
 */
private suspend fun extractUserId(
    call: ApplicationCall,
    tokenService: TokenService?
): UUID? {
    if (tokenService == null) {
        call.respond(
            HttpStatusCode.InternalServerError,
            error("SERVER_MISCONFIGURED", "Token service is not configured")
        )
        return null
    }

    val authHeader = call.request.header(HttpHeaders.Authorization)
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        call.respond(
            HttpStatusCode.Unauthorized,
            error("UNAUTHORIZED", "Missing or invalid Authorization header")
        )
        return null
    }

    val token = authHeader.removePrefix("Bearer ").trim()
    val accountId = tokenService.validateAccessToken(token)
    if (accountId == null) {
        call.respond(
            HttpStatusCode.Unauthorized,
            error("UNAUTHORIZED", "Invalid or expired token")
        )
        return null
    }

    return try {
        UUID.fromString(accountId)
    } catch (e: IllegalArgumentException) {
        Napier.e("Invalid account ID format in token: $accountId", e)
        call.respond(
            HttpStatusCode.Unauthorized,
            error("UNAUTHORIZED", "Invalid token payload")
        )
        null
    }
}

/**
 * Sync routes with JWT authentication.
 * All endpoints require a valid Bearer token and scope data by user ID.
 */
fun Route.syncRoutes(
    repository: SyncRepository,
    tokenService: TokenService? = null,
    mediaStorage: GcsMediaStorage? = null,
    metrics: SyncMetricsRegistry,
    mediaAccessPolicy: MediaAccessPolicy = MediaAccessPolicy.fromEnvironment(),
    mediaEncryption: MediaEncryptionService = MediaEncryptionService.fromEnvironment(),
    encryptionService: EncryptionService = EncryptionService.fromEnvironment()
) {
    route("/sync") {
        // High-level status (used by smoke tests)
        get("/status") {
            val start = System.currentTimeMillis()
            var success = false
            try {
                val userId = extractUserId(call, tokenService) ?: return@get
                val status = repository.status(userId)
                val snapshot = SyncStatusSnapshot(
                    contentCount = status.contentCount,
                    journalCount = status.journalCount,
                    associationCount = status.associationCount,
                    lastTimestamp = status.lastTimestamp
                )
                call.respond(simpleSuccess(snapshot))
                success = true
            } finally {
                metrics.recordOperation(METRIC_SYNC_STATUS, System.currentTimeMillis() - start, success)
            }
        }

        get("/metrics") {
            val start = System.currentTimeMillis()
            var success = false
            try {
                if (extractUserId(call, tokenService) == null) {
                    return@get
                }
                val snapshot = metrics.snapshot()
                call.respond(simpleSuccess(snapshot))
                success = true
            } finally {
                metrics.recordOperation(METRIC_SYNC_METRICS, System.currentTimeMillis() - start, success)
            }
        }

        get("/metrics/prometheus") {
            val start = System.currentTimeMillis()
            var success = false
            try {
                if (extractUserId(call, tokenService) == null) {
                    return@get
                }
                val snapshot = metrics.snapshot()
                call.respondText(
                    snapshot.toPrometheus(),
                    ContentType.Text.Plain
                )
                success = true
            } finally {
                metrics.recordOperation(METRIC_SYNC_METRICS_PROM, System.currentTimeMillis() - start, success)
            }
        }

        // Content
        route("/content") {
            post {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@post
                    val req = call.receive<ContentUploadRequest>()
                    Napier.d("Content upload for user $userId: ${req.id}")
                    val stored = repository.upsertContent(
                        userId,
                        ContentRecord(
                            id = req.id,
                            type = req.type,
                            content = req.content,
                            mediaUri = req.mediaUri,
                            durationMs = req.durationMs,
                            createdAt = req.createdAt,
                            lastUpdated = req.lastUpdated,
                            serverVersion = 0L,
                            deviceId = req.deviceId
                        )
                    )
                    call.respond(
                        ContentUploadResponse(
                            id = stored.id,
                            serverVersion = stored.serverVersion,
                            uploadedAt = stored.lastUpdated
                        )
                    )
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_CONTENT_UPLOAD, System.currentTimeMillis() - start, success)
                }
            }

            get("/changes") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@get
                    val since = call.request.queryParameters["since"]?.toLongOrNull()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            error("MISSING_PARAMETER", "since parameter is required")
                        )

                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_SYNC_PAGE_SIZE
                    val pageSize = limit.coerceIn(1, MAX_SYNC_PAGE_SIZE)
                    val changeSet = repository.contentChanges(userId, since, pageSize)
                    val changes = changeSet.changes.map {
                        ContentChange(
                            id = it.id,
                            type = it.type,
                            content = it.content,
                            mediaUri = it.mediaUri,
                            durationMs = it.durationMs ?: 0,
                            createdAt = it.createdAt,
                            lastUpdated = it.lastUpdated,
                            serverVersion = it.serverVersion,
                            isDeleted = false
                        )
                    }
                    val deletions = changeSet.deletions.map { ContentDeletion(id = it.id, deletedAt = it.deletedAt) }
                    call.respond(ContentChangesResponse(changes, deletions, changeSet.lastTimestamp, changeSet.hasMore))
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_CONTENT_CHANGES, System.currentTimeMillis() - start, success)
                }
            }

            post("/{contentId}") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@post
                    val contentId = call.parameters["contentId"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        error("MISSING_PARAMETER", "contentId is required")
                    )
                    val req = call.receive<ContentUpdateRequest>()
                    val existing = repository.getContent(userId, contentId)
                    when (val constraint = req.versionConstraint) {
                        is VersionConstraint.Known -> {
                            if (existing != null && existing.serverVersion > constraint.serverVersion) {
                                metrics.recordConflict()
                                return@post call.respond(
                                    HttpStatusCode.Conflict,
                                    error(
                                        "CONFLICT",
                                        "Server has a newer version (${constraint.serverVersion} < ${existing.serverVersion})"
                                    )
                                )
                            }
                        }
                        VersionConstraint.None -> Unit
                    }
                    val updated = repository.upsertContent(
                        userId,
                        ContentRecord(
                            id = contentId,
                            type = existing?.type ?: "TEXT",
                            content = req.content ?: existing?.content,
                            mediaUri = req.mediaUri ?: existing?.mediaUri,
                            durationMs = req.durationMs ?: existing?.durationMs,
                            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                            lastUpdated = req.lastUpdated,
                            serverVersion = existing?.serverVersion ?: 0L,
                            deviceId = req.deviceId
                        )
                    )
                    call.respond(ContentUpdateResponse(contentId, updated.serverVersion, updated.lastUpdated))
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_CONTENT_UPDATE, System.currentTimeMillis() - start, success)
                }
            }

            post("/{contentId}/delete") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@post
                    val contentId = call.parameters["contentId"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        error("MISSING_PARAMETER", "contentId is required")
                    )
                    val deletedAt = System.currentTimeMillis()
                    repository.deleteContent(userId, contentId, deletedAt)
                    call.respond(HttpStatusCode.OK)
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_CONTENT_DELETE, System.currentTimeMillis() - start, success)
                }
            }
        }

        // Journals
        route("/journals") {
            post {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@post
                    val req = call.receive<JournalUploadRequest>()
                    Napier.d("Journal upload for user $userId: ${req.id}")
                    val stored = repository.upsertJournal(
                        userId,
                        JournalRecord(
                            id = req.id,
                            title = req.title,
                            description = req.description,
                            createdAt = req.createdAt,
                            lastUpdated = req.lastUpdated,
                            serverVersion = 0L,
                            deviceId = req.deviceId
                        )
                    )
                    call.respond(JournalUploadResponse(req.id, stored.serverVersion, stored.lastUpdated))
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_JOURNAL_UPLOAD, System.currentTimeMillis() - start, success)
                }
            }

            get("/changes") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@get
                    val since = call.request.queryParameters["since"]?.toLongOrNull()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            error("MISSING_PARAMETER", "since parameter is required")
                        )
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_SYNC_PAGE_SIZE
                    val pageSize = limit.coerceIn(1, MAX_SYNC_PAGE_SIZE)
                    val changeSet = repository.journalChanges(userId, since, pageSize)
                    val changes = changeSet.changes.map {
                        JournalChange(
                            id = it.id,
                            title = it.title,
                            description = it.description,
                            createdAt = it.createdAt,
                            lastUpdated = it.lastUpdated,
                            serverVersion = it.serverVersion,
                            isDeleted = false
                        )
                    }
                    val deletions = changeSet.deletions.map { JournalDeletion(id = it.id, deletedAt = it.deletedAt) }
                    call.respond(JournalChangesResponse(changes, deletions, changeSet.lastTimestamp, changeSet.hasMore))
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_JOURNAL_CHANGES, System.currentTimeMillis() - start, success)
                }
            }

            post("/{journalId}") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@post
                    val journalId = call.parameters["journalId"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        error("MISSING_PARAMETER", "journalId is required")
                    )
                    val req = call.receive<JournalUpdateRequest>()
                    val existing = repository.getJournal(userId, journalId)
                    when (val constraint = req.versionConstraint) {
                        is VersionConstraint.Known -> {
                            if (existing != null && existing.serverVersion > constraint.serverVersion) {
                                metrics.recordConflict()
                                return@post call.respond(
                                    HttpStatusCode.Conflict,
                                    error(
                                        "CONFLICT",
                                        "Server has a newer version (${constraint.serverVersion} < ${existing.serverVersion})"
                                    )
                                )
                            }
                        }
                        VersionConstraint.None -> Unit
                    }
                    val updatedAt = System.currentTimeMillis()
                    val stored = repository.upsertJournal(
                        userId,
                        JournalRecord(
                            id = journalId,
                            title = req.title ?: existing?.title.orEmpty(),
                            description = req.description ?: existing?.description.orEmpty(),
                            createdAt = existing?.createdAt ?: updatedAt,
                            lastUpdated = req.lastUpdated,
                            serverVersion = existing?.serverVersion ?: 0L,
                            deviceId = req.deviceId
                        )
                    )
                    call.respond(JournalUpdateResponse(journalId, stored.serverVersion, stored.lastUpdated))
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_JOURNAL_UPDATE, System.currentTimeMillis() - start, success)
                }
            }

            post("/{journalId}/delete") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@post
                    val journalId = call.parameters["journalId"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        error("MISSING_PARAMETER", "journalId is required")
                    )
                    val deletedAt = System.currentTimeMillis()
                    repository.deleteJournal(userId, journalId, deletedAt)
                    call.respond(HttpStatusCode.OK)
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_JOURNAL_DELETE, System.currentTimeMillis() - start, success)
                }
            }
        }

        // Associations
        route("/associations") {
            post {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@post
                    val req = call.receive<AssociationUploadRequest>()
                    val uploadedAt = System.currentTimeMillis()
                    repository.upsertAssociations(
                        userId,
                        req.associations.map {
                            AssociationRecord(
                                journalId = it.journalId,
                                contentId = it.contentId,
                                createdAt = it.createdAt,
                                serverVersion = 0L,
                                deviceId = it.deviceId
                            )
                        }
                    )
                    call.respond(AssociationUploadResponse(req.associations.size, uploadedAt))
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_ASSOCIATION_UPLOAD, System.currentTimeMillis() - start, success)
                }
            }

            get("/changes") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@get
                    val since = call.request.queryParameters["since"]?.toLongOrNull()
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            error("MISSING_PARAMETER", "since parameter is required")
                        )
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_SYNC_PAGE_SIZE
                    val pageSize = limit.coerceIn(1, MAX_SYNC_PAGE_SIZE)
                    val changeSet = repository.associationChanges(userId, since, pageSize)
                    val changes = changeSet.changes.map {
                        AssociationChange(
                            journalId = it.journalId,
                            contentId = it.contentId,
                            createdAt = it.createdAt,
                            serverVersion = it.serverVersion,
                            isDeleted = false
                        )
                    }
                    val deletions = changeSet.deletions.map {
                        AssociationDeletion(
                            journalId = it.key.journalId,
                            contentId = it.key.contentId,
                            deletedAt = it.deletedAt
                        )
                    }
                    call.respond(
                        AssociationChangesResponse(changes, deletions, changeSet.lastTimestamp, changeSet.hasMore)
                    )
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_ASSOCIATION_CHANGES, System.currentTimeMillis() - start, success)
                }
            }

            post("/delete") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@post
                    val req = call.receive<AssociationDeleteRequest>()
                    val deletedAt = System.currentTimeMillis()
                    repository.deleteAssociations(
                        userId,
                        req.associations.map { AssociationKey(it.journalId, it.contentId) },
                        deletedAt
                    )
                    call.respond(HttpStatusCode.OK)
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_ASSOCIATION_DELETE, System.currentTimeMillis() - start, success)
                }
            }
        }

        // Media upload/download
        route("/media") {
            post {
                val start = System.currentTimeMillis()
                var success = false
                var bytes = 0L
                try {
                    val userId = extractUserId(call, tokenService) ?: return@post
                    val req = call.receive<MediaUploadRequest>()
                    bytes = req.sizeBytes
                    Napier.d("Media upload for user $userId: ${req.fileName}")
                    val mediaId = UUID.randomUUID().toString()
                    
                    val encryptedPayload = runCatching {
                        encryptionService.processMediaUpload(
                            req.data,
                            userId.toString(),
                            mediaId,
                            req.contentId
                        )
                    }.getOrElse { error ->
                        Napier.e("Failed to encrypt media payload", error)
                        return@post call.respond(
                            HttpStatusCode.InternalServerError,
                            error("MEDIA_ENCRYPT_FAILED", "Failed to encrypt media payload")
                        )
                    }
                    
                    val storagePath = if (mediaStorage != null) {
                        runCatching {
                            mediaStorage.uploadMedia(
                                userId = userId,
                                mediaId = UUID.fromString(mediaId),
                                fileName = req.fileName,
                                mimeType = req.mimeType,
                                data = encryptedPayload.data
                            )
                        }.getOrElse { error ->
                            Napier.e("Failed to upload media to external storage", error)
                            return@post call.respond(
                                HttpStatusCode.InternalServerError,
                                error("MEDIA_UPLOAD_FAILED", "Failed to store media")
                            )
                        }
                    } else {
                        null
                    }
                    val stored = repository.upsertMedia(
                        userId,
                        MediaRecord(
                            mediaId = mediaId,
                            contentId = req.contentId,
                            fileName = req.fileName,
                            mimeType = req.mimeType,
                            sizeBytes = req.sizeBytes,
                            data = if (storagePath == null) encryptedPayload.data else ByteArray(0),
                            storagePath = storagePath,
                            createdAt = System.currentTimeMillis(),
                            serverVersion = 0L,
                            deviceId = req.deviceId
                        )
                    )
                    val downloadUrl = resolveMediaDownloadUrl(
                        call,
                        stored,
                        mediaStorage,
                        mediaAccessPolicy
                    )
                    val response = MediaUploadResponse(
                        contentId = req.contentId,
                        mediaId = stored.mediaId,
                        downloadUrl = downloadUrl,
                        uploadedAt = stored.createdAt
                    )
                    call.respond(response)
                    success = true
                } finally {
                    metrics.recordOperation(
                        METRIC_MEDIA_UPLOAD,
                        System.currentTimeMillis() - start,
                        success,
                        bytes
                    )
                }
            }

            get("/{mediaId}") {
                val start = System.currentTimeMillis()
                var success = false
                var bytes = 0L
                try {
                    val userId = extractUserId(call, tokenService) ?: return@get
                    val mediaId = call.parameters["mediaId"] ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        error("MISSING_PARAMETER", "mediaId is required")
                    )
                    val record = repository.getMedia(userId, mediaId)
                        ?: return@get call.respond(HttpStatusCode.NotFound, error("NOT_FOUND", "Media not found"))
                    
                    val encryptedPayload = if (record.storagePath != null) {
                        val storage = mediaStorage
                            ?: return@get call.respond(
                                HttpStatusCode.InternalServerError,
                                error("MEDIA_STORAGE_UNAVAILABLE", "Media storage not configured")
                            )
                        storage.downloadMedia(record.storagePath)
                            ?: return@get call.respond(
                                HttpStatusCode.NotFound,
                                error("NOT_FOUND", "Media not found")
                            )
                    } else {
                        record.data
                    }
                    
                    val payload = runCatching {
                        encryptionService.processMediaDownload(encryptedPayload, shouldDecrypt = true)
                    }.getOrElse { error ->
                        Napier.e("Failed to decrypt media payload for $mediaId", error)
                        return@get call.respond(
                            HttpStatusCode.InternalServerError,
                            error("MEDIA_DECRYPT_FAILED", "Failed to decrypt media payload")
                        )
                    }
                    bytes = payload.size.toLong()
                    call.respond(
                        MediaDownloadResponse(
                            contentId = record.contentId,
                            fileName = record.fileName,
                            mimeType = record.mimeType,
                            sizeBytes = record.sizeBytes,
                            data = payload,
                            downloadUrl = resolveMediaDownloadUrl(
                                call,
                                record,
                                mediaStorage,
                                mediaAccessPolicy
                            )
                        )
                    )
                    success = true
                } finally {
                    metrics.recordOperation(
                        METRIC_MEDIA_DOWNLOAD,
                        System.currentTimeMillis() - start,
                        success,
                        bytes
                    )
                }
            }
        }

        // Backups
        route("/backups") {
            post {
                val start = System.currentTimeMillis()
                var success = false
                var bytes = 0L
                try {
                    val userId = extractUserId(call, tokenService) ?: return@post
                    val req = call.receive<BackupUploadRequest>()
                    bytes = req.data.size.toLong()
                    
                    val storage = mediaStorage ?: return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        error("BACKUP_STORAGE_UNAVAILABLE", "Backup storage not configured")
                    )
                    
                    val backupId = UUID.randomUUID()
                    
                    val encryptedData = runCatching {
                        encryptionService.processBackupUpload(
                            req.data,
                            userId.toString(),
                            backupId.toString()
                        ).data
                    }.getOrElse { error ->
                        Napier.e("Failed to encrypt backup", error)
                        return@post call.respond(
                            HttpStatusCode.InternalServerError,
                            error("BACKUP_ENCRYPT_FAILED", "Failed to encrypt backup")
                        )
                    }
                    
                    val storagePath = storage.uploadBackup(userId, backupId, encryptedData)
                    
                    val record = repository.createBackupRecord(
                        userId,
                        BackupRecord(
                            id = backupId,
                            userId = userId,
                            deviceId = req.deviceId,
                            manifest = req.manifest,
                            storagePath = storagePath,
                            createdAt = System.currentTimeMillis(),
                            sizeBytes = bytes
                        )
                    )
                    
                    call.respond(
                        BackupUploadResponse(
                            id = record.id.toString(),
                            createdAt = record.createdAt,
                            sizeBytes = record.sizeBytes
                        )
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
                    val backups = repository.listBackups(userId)
                    call.respond(
                        BackupListResponse(
                            backups.map {
                                BackupInfoResponse(
                                    id = it.id.toString(),
                                    deviceId = it.deviceId,
                                    manifest = it.manifest,
                                    createdAt = it.createdAt,
                                    sizeBytes = it.sizeBytes,
                                    downloadUrl = resolveBackupDownloadUrl(call, it, mediaStorage, mediaAccessPolicy)
                                )
                            }
                        )
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
                    val backupId = call.parameters["backupId"]?.let { UUID.fromString(it) } ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        error("INVALID_ID", "Invalid backupId")
                    )
                    
                    val record = repository.getBackupRecord(userId, backupId)
                        ?: return@get call.respond(HttpStatusCode.NotFound, error("NOT_FOUND", "Backup not found"))
                        
                    val storage = mediaStorage ?: return@get call.respond(
                        HttpStatusCode.InternalServerError,
                        error("BACKUP_STORAGE_UNAVAILABLE", "Backup storage not configured")
                    )
                    
                    val encryptedData = storage.downloadMedia(record.storagePath)
                        ?: return@get call.respond(HttpStatusCode.NotFound, error("NOT_FOUND", "Backup file not found"))
                    
                    val decryptedData = runCatching {
                        encryptionService.processBackupDownload(encryptedData, shouldDecrypt = true)
                    }.getOrElse { error ->
                        Napier.e("Failed to decrypt backup $backupId", error)
                        return@get call.respond(
                            HttpStatusCode.InternalServerError,
                            error("BACKUP_DECRYPT_FAILED", "Failed to decrypt backup")
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
                    val backupId = call.parameters["backupId"]?.let { UUID.fromString(it) } ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        error("INVALID_ID", "Invalid backupId")
                    )
                    
                    val record = repository.getBackupRecord(userId, backupId)
                    if (record != null) {
                        mediaStorage?.deleteMedia(record.storagePath)
                        repository.deleteBackup(userId, backupId)
                    }
                    
                    call.respond(HttpStatusCode.OK)
                    success = true
                } finally {
                    metrics.recordOperation("sync.backup.delete", System.currentTimeMillis() - start, success)
                }
            }
        }

        // Maintenance
        route("/maintenance") {
            post("/purge") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@post
                    val retentionDays = call.request.queryParameters["retentionDays"]?.toLongOrNull() ?: 30L
                    if (retentionDays <= 0) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            error("INVALID_PARAMETER", "retentionDays must be > 0")
                        )
                    }
                    val safeRetentionDays = retentionDays.coerceAtMost(3650L)
                    val cutoff = System.currentTimeMillis() - (safeRetentionDays * MILLIS_PER_DAY)
                    val result = repository.purgeTombstones(userId, cutoff)
                    call.respond(simpleSuccess(result))
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_SYNC_PURGE, System.currentTimeMillis() - start, success)
                }
            }
        }
    }
}

private fun buildMediaDownloadUrl(call: ApplicationCall, mediaId: String): String {
    val origin = call.request.local
    val defaultPort = (origin.scheme == "http" && origin.localPort == 80) ||
        (origin.scheme == "https" && origin.localPort == 443)
    val portPart = if (defaultPort) "" else ":${origin.localPort}"
    return "${origin.scheme}://${origin.localHost}$portPart/api/v1/sync/media/$mediaId"
}

private fun resolveMediaDownloadUrl(
    call: ApplicationCall,
    record: MediaRecord,
    mediaStorage: GcsMediaStorage?,
    accessPolicy: MediaAccessPolicy
): String {
    if (accessPolicy.useSignedUrls && mediaStorage != null && record.storagePath != null) {
        return runCatching {
            mediaStorage.getSignedDownloadUrl(record.storagePath, accessPolicy.signedUrlTtlHours)
        }.getOrElse { error ->
            Napier.e("Failed to generate signed URL for ${record.mediaId}", error)
            buildMediaDownloadUrl(call, record.mediaId)
        }
    }
    return buildMediaDownloadUrl(call, record.mediaId)
}

private fun resolveBackupDownloadUrl(
    call: ApplicationCall,
    record: BackupRecord,
    mediaStorage: GcsMediaStorage?,
    accessPolicy: MediaAccessPolicy
): String {
    if (accessPolicy.useSignedUrls && mediaStorage != null) {
        return runCatching {
            mediaStorage.getSignedDownloadUrl(record.storagePath, accessPolicy.signedUrlTtlHours)
        }.getOrElse { error ->
            Napier.e("Failed to generate signed URL for backup ${record.id}", error)
            buildBackupDownloadUrl(call, record.id.toString())
        }
    }
    return buildBackupDownloadUrl(call, record.id.toString())
}

private fun buildBackupDownloadUrl(call: ApplicationCall, backupId: String): String {
    val origin = call.request.local
    val defaultPort = (origin.scheme == "http" && origin.localPort == 80) ||
        (origin.scheme == "https" && origin.localPort == 443)
    val portPart = if (defaultPort) "" else ":${origin.localPort}"
    return "${origin.scheme}://${origin.localHost}$portPart/api/v1/sync/backups/$backupId"
}

private fun app.logdate.server.sync.SyncMetricsSnapshot.toPrometheus(): String {
    val builder = StringBuilder()
    builder.appendLine("# HELP logdate_sync_conflicts_total Total sync conflicts detected.")
    builder.appendLine("# TYPE logdate_sync_conflicts_total counter")
    builder.appendLine("logdate_sync_conflicts_total $conflictCount")
    builder.appendLine("# HELP logdate_sync_operation_success_total Successful sync operations by type.")
    builder.appendLine("# TYPE logdate_sync_operation_success_total counter")
    builder.appendLine("# HELP logdate_sync_operation_error_total Failed sync operations by type.")
    builder.appendLine("# TYPE logdate_sync_operation_error_total counter")
    builder.appendLine("# HELP logdate_sync_operation_duration_ms_total Total sync operation duration by type.")
    builder.appendLine("# TYPE logdate_sync_operation_duration_ms_total counter")
    builder.appendLine("# HELP logdate_sync_operation_bytes_total Total bytes processed by operation type.")
    builder.appendLine("# TYPE logdate_sync_operation_bytes_total counter")

    operations.forEach { operation ->
        val label = escapeLabelValue(operation.name)
        builder.appendLine("logdate_sync_operation_success_total{operation=\"$label\"} ${operation.successCount}")
        builder.appendLine("logdate_sync_operation_error_total{operation=\"$label\"} ${operation.errorCount}")
        builder.appendLine("logdate_sync_operation_duration_ms_total{operation=\"$label\"} ${operation.totalDurationMs}")
        builder.appendLine("logdate_sync_operation_bytes_total{operation=\"$label\"} ${operation.totalBytes}")
    }

    return builder.toString()
}

private fun escapeLabelValue(value: String): String {
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
}
