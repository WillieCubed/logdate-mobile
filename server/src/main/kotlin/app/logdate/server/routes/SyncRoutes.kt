package app.logdate.server.routes

import app.logdate.server.auth.TokenService
import app.logdate.server.crypto.EncryptionService
import app.logdate.server.entitlements.EntitlementEnforcer
import app.logdate.server.entitlements.QuotaCheck
import app.logdate.server.logdate.BackupRetentionPolicy
import app.logdate.server.logdate.LogDateAssociation
import app.logdate.server.logdate.LogDateAssociationRef
import app.logdate.server.logdate.LogDateBackup
import app.logdate.server.logdate.LogDateBackupRepository
import app.logdate.server.logdate.LogDateBlobNamespace
import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.LogDateBlobWriteRequest
import app.logdate.server.logdate.LogDateCollectionsRepository
import app.logdate.server.logdate.LogDateDraft
import app.logdate.server.logdate.LogDateEntry
import app.logdate.server.logdate.LogDateJournal
import app.logdate.server.logdate.LogDateMedia
import app.logdate.server.logdate.LogDateMediaBlobRepository
import app.logdate.server.ratelimit.SlidingWindowRateLimiter
import app.logdate.server.responses.error
import app.logdate.server.responses.simpleSuccess
import app.logdate.server.routes.sync.BACKUP_UPLOAD_RATE_LIMIT
import app.logdate.server.routes.sync.DEFAULT_SYNC_PAGE_SIZE
import app.logdate.server.routes.sync.MAX_SYNC_PAGE_SIZE
import app.logdate.server.routes.sync.MEDIA_UPLOAD_RATE_LIMIT
import app.logdate.server.routes.sync.METRIC_ASSOCIATION_CHANGES
import app.logdate.server.routes.sync.METRIC_ASSOCIATION_DELETE
import app.logdate.server.routes.sync.METRIC_ASSOCIATION_UPLOAD
import app.logdate.server.routes.sync.METRIC_CONTENT_CHANGES
import app.logdate.server.routes.sync.METRIC_CONTENT_DELETE
import app.logdate.server.routes.sync.METRIC_CONTENT_UPDATE
import app.logdate.server.routes.sync.METRIC_CONTENT_UPLOAD
import app.logdate.server.routes.sync.METRIC_JOURNAL_CHANGES
import app.logdate.server.routes.sync.METRIC_JOURNAL_DELETE
import app.logdate.server.routes.sync.METRIC_JOURNAL_UPDATE
import app.logdate.server.routes.sync.METRIC_JOURNAL_UPLOAD
import app.logdate.server.routes.sync.METRIC_MEDIA_DELETE
import app.logdate.server.routes.sync.METRIC_MEDIA_DOWNLOAD
import app.logdate.server.routes.sync.METRIC_MEDIA_UPLOAD
import app.logdate.server.routes.sync.METRIC_SYNC_METRICS
import app.logdate.server.routes.sync.METRIC_SYNC_METRICS_PROM
import app.logdate.server.routes.sync.METRIC_SYNC_PURGE
import app.logdate.server.routes.sync.METRIC_SYNC_STATUS
import app.logdate.server.routes.sync.MILLIS_PER_DAY
import app.logdate.server.routes.sync.buildMediaDownloadUrl
import app.logdate.server.routes.sync.extractUserId
import app.logdate.server.routes.sync.receiveBackupMultipartUpload
import app.logdate.server.routes.sync.receiveMediaMultipartUpload
import app.logdate.server.routes.sync.requiredPathParam
import app.logdate.server.routes.sync.resolveBackupDownloadUrl
import app.logdate.server.routes.sync.resolveMediaDownloadUrl
import app.logdate.server.routes.sync.respondQuotaExceeded
import app.logdate.server.routes.sync.respondRateLimited
import app.logdate.server.routes.sync.toAssociationChange
import app.logdate.server.routes.sync.toContentChange
import app.logdate.server.routes.sync.toJournalChange
import app.logdate.server.routes.sync.toPrometheus
import app.logdate.server.sync.MediaAccessPolicy
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.shared.model.sync.AssociationChangesResponse
import app.logdate.shared.model.sync.AssociationDeleteRequest
import app.logdate.shared.model.sync.AssociationDeletion
import app.logdate.shared.model.sync.AssociationUploadRequest
import app.logdate.shared.model.sync.AssociationUploadResponse
import app.logdate.shared.model.sync.BackupInfoResponse
import app.logdate.shared.model.sync.BackupListResponse
import app.logdate.shared.model.sync.BackupUploadResponse
import app.logdate.shared.model.sync.ContentChangesResponse
import app.logdate.shared.model.sync.ContentDeletion
import app.logdate.shared.model.sync.ContentUpdateRequest
import app.logdate.shared.model.sync.ContentUpdateResponse
import app.logdate.shared.model.sync.ContentUploadRequest
import app.logdate.shared.model.sync.ContentUploadResponse
import app.logdate.shared.model.sync.DeviceId
import app.logdate.shared.model.sync.JournalChangesResponse
import app.logdate.shared.model.sync.JournalDeletion
import app.logdate.shared.model.sync.JournalUpdateRequest
import app.logdate.shared.model.sync.JournalUpdateResponse
import app.logdate.shared.model.sync.JournalUploadRequest
import app.logdate.shared.model.sync.JournalUploadResponse
import app.logdate.shared.model.sync.MediaMetadataResponse
import app.logdate.shared.model.sync.MediaUploadResponse
import app.logdate.shared.model.sync.VersionConstraint
import io.github.aakira.napier.Napier
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
private data class SyncStatusSnapshot(
    val contentCount: Int,
    val journalCount: Int,
    val associationCount: Int,
    val lastTimestamp: Long,
)

@Serializable
private data class SyncPurgeResponse(
    val contentPurged: Int,
    val journalPurged: Int,
    val associationPurged: Int,
    val mediaPurged: Int,
    val cutoff: Long,
    val retentionDaysApplied: Long,
)

@Serializable
private data class AssociationLinkUpsertRequest(
    val createdAt: Long,
    val deviceId: DeviceId = DeviceId.UNKNOWN,
)

// Multipart parsers, auth helpers, and wire-shape mappers live in
// [app.logdate.server.routes.sync.SyncHelpers] and are imported at the top of this file.

/**
 * Sync routes with JWT authentication.
 * All endpoints require a valid Bearer token and scope data by user ID.
 */
fun Route.syncRoutes(
    tokenService: TokenService? = null,
    mediaStorage: LogDateBlobStorage? = null,
    metrics: SyncMetricsRegistry,
    mediaAccessPolicy: MediaAccessPolicy = MediaAccessPolicy.fromEnvironment(),
    encryptionService: EncryptionService = EncryptionService.fromEnvironment(),
    collectionsRepository: LogDateCollectionsRepository,
    mediaBlobRepository: LogDateMediaBlobRepository,
    backupRepository: LogDateBackupRepository,
    entitlementEnforcer: EntitlementEnforcer? = null,
    rateLimiter: SlidingWindowRateLimiter? = SlidingWindowRateLimiter(),
) {
    route("") {
        // High-level status (used by smoke tests)
        get("/ops/sync/status") {
            val start = System.currentTimeMillis()
            var success = false
            try {
                val userId = extractUserId(call, tokenService) ?: return@get
                val status = collectionsRepository.status(userId)
                val snapshot =
                    SyncStatusSnapshot(
                        contentCount = status.entryCount,
                        journalCount = status.journalCount,
                        associationCount = status.associationCount,
                        lastTimestamp = status.lastTimestamp,
                    )
                call.respond(simpleSuccess(snapshot))
                success = true
            } finally {
                metrics.recordOperation(METRIC_SYNC_STATUS, System.currentTimeMillis() - start, success)
            }
        }

        get("/ops/sync/metrics") {
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

        get("/ops/sync/metrics/prometheus") {
            val start = System.currentTimeMillis()
            var success = false
            try {
                if (extractUserId(call, tokenService) == null) {
                    return@get
                }
                val snapshot = metrics.snapshot()
                call.respondText(
                    snapshot.toPrometheus(),
                    ContentType.Text.Plain,
                )
                success = true
            } finally {
                metrics.recordOperation(METRIC_SYNC_METRICS_PROM, System.currentTimeMillis() - start, success)
            }
        }

        // Contents
        route("/contents") {
            put("/{contentId}") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@put
                    val contentId = call.requiredPathParam("contentId")
                    val req = call.receive<ContentUploadRequest>()
                    if (req.id != contentId) {
                        return@put call.respond(
                            HttpStatusCode.BadRequest,
                            error("VALIDATION_ERROR", "Request body id must match path contentId"),
                        )
                    }
                    val wasCreated = collectionsRepository.getEntry(userId, contentId) == null
                    Napier.d("Content upsert for user $userId: $contentId")
                    val stored =
                        collectionsRepository.upsertEntry(
                            userId = userId,
                            entry =
                                LogDateEntry(
                                    id = contentId,
                                    type = req.type,
                                    content = req.content,
                                    mediaUri = req.mediaUri,
                                    durationMs = req.durationMs,
                                    createdAt = req.createdAt,
                                    lastUpdated = req.lastUpdated,
                                    version = 0L,
                                    deviceId = req.deviceId,
                                ),
                        )
                    val response =
                        ContentUploadResponse(
                            id = stored.id,
                            serverVersion = stored.version,
                            uploadedAt = stored.lastUpdated,
                        )
                    if (wasCreated) {
                        call.response.headers.append(HttpHeaders.Location, "/api/v1/contents/${stored.id}")
                        call.respond(HttpStatusCode.Created, response)
                    } else {
                        call.respond(HttpStatusCode.OK, response)
                    }
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_CONTENT_UPLOAD, System.currentTimeMillis() - start, success)
                }
            }

            get {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@get
                    val sinceParam = call.request.queryParameters["since"]
                    val since =
                        when {
                            sinceParam == null -> 0L
                            else ->
                                sinceParam.toLongOrNull()
                                    ?: return@get call.respond(
                                        HttpStatusCode.BadRequest,
                                        error("INVALID_PARAMETER", "since parameter must be a valid long"),
                                    )
                        }

                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_SYNC_PAGE_SIZE
                    val pageSize = limit.coerceIn(1, MAX_SYNC_PAGE_SIZE)
                    val changeSet = collectionsRepository.entryChanges(userId, since, pageSize)
                    val changes = changeSet.changes.map(LogDateEntry::toContentChange)
                    val deletions = changeSet.deletions.map { ContentDeletion(id = it.id, deletedAt = it.deletedAt) }
                    call.respond(ContentChangesResponse(changes, deletions, changeSet.lastTimestamp, changeSet.hasMore))
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_CONTENT_CHANGES, System.currentTimeMillis() - start, success)
                }
            }

            get("/{contentId}") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@get
                    val contentId = call.requiredPathParam("contentId")
                    val record =
                        collectionsRepository.getEntry(userId, contentId)
                            ?: return@get call.respond(HttpStatusCode.NotFound, error("NOT_FOUND", "Content not found"))
                    call.respond(record.toContentChange())
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_CONTENT_CHANGES, System.currentTimeMillis() - start, success)
                }
            }

            patch("/{contentId}") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@patch
                    val contentId = call.requiredPathParam("contentId")
                    val req = call.receive<ContentUpdateRequest>()
                    val existing = collectionsRepository.getEntry(userId, contentId)
                    when (val constraint = req.versionConstraint) {
                        is VersionConstraint.Known -> {
                            if (existing != null && existing.version > constraint.serverVersion) {
                                metrics.recordConflict()
                                return@patch call.respond(
                                    HttpStatusCode.Conflict,
                                    error(
                                        "CONFLICT",
                                        "Server has a newer version (${constraint.serverVersion} < ${existing.version})",
                                    ),
                                )
                            }
                        }
                        VersionConstraint.None -> Unit
                    }
                    val updated =
                        collectionsRepository.upsertEntry(
                            userId = userId,
                            entry =
                                LogDateEntry(
                                    id = contentId,
                                    type = existing?.type ?: "TEXT",
                                    content = req.content ?: existing?.content,
                                    mediaUri = req.mediaUri ?: existing?.mediaUri,
                                    durationMs = req.durationMs,
                                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                                    lastUpdated = req.lastUpdated,
                                    version = existing?.version ?: 0L,
                                    deviceId = req.deviceId,
                                ),
                        )
                    call.respond(ContentUpdateResponse(contentId, updated.version, updated.lastUpdated))
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_CONTENT_UPDATE, System.currentTimeMillis() - start, success)
                }
            }

            delete("/{contentId}") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@delete
                    val contentId = call.requiredPathParam("contentId")
                    val deletedAt = System.currentTimeMillis()
                    collectionsRepository.deleteEntry(userId, contentId, deletedAt)
                    call.respond(HttpStatusCode.NoContent)
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_CONTENT_DELETE, System.currentTimeMillis() - start, success)
                }
            }
        }

        // Journals
        route("/journals") {
            put("/{journalId}") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@put
                    val journalId = call.requiredPathParam("journalId")
                    val req = call.receive<JournalUploadRequest>()
                    if (req.id != journalId) {
                        return@put call.respond(
                            HttpStatusCode.BadRequest,
                            error("VALIDATION_ERROR", "Request body id must match path journalId"),
                        )
                    }
                    val wasCreated = collectionsRepository.getJournal(userId, journalId) == null
                    Napier.d("Journal upsert for user $userId: $journalId")
                    val stored =
                        collectionsRepository.upsertJournal(
                            userId = userId,
                            journal =
                                LogDateJournal(
                                    id = journalId,
                                    title = req.title,
                                    description = req.description,
                                    createdAt = req.createdAt,
                                    lastUpdated = req.lastUpdated,
                                    version = 0L,
                                    deviceId = req.deviceId,
                                ),
                        )
                    val response = JournalUploadResponse(journalId, stored.version, stored.lastUpdated)
                    if (wasCreated) {
                        call.response.headers.append(HttpHeaders.Location, "/api/v1/journals/$journalId")
                        call.respond(HttpStatusCode.Created, response)
                    } else {
                        call.respond(HttpStatusCode.OK, response)
                    }
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_JOURNAL_UPLOAD, System.currentTimeMillis() - start, success)
                }
            }

            get {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@get
                    val sinceParam = call.request.queryParameters["since"]
                    val since =
                        when {
                            sinceParam == null -> 0L
                            else ->
                                sinceParam.toLongOrNull()
                                    ?: return@get call.respond(
                                        HttpStatusCode.BadRequest,
                                        error("INVALID_PARAMETER", "since parameter must be a valid long"),
                                    )
                        }
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_SYNC_PAGE_SIZE
                    val pageSize = limit.coerceIn(1, MAX_SYNC_PAGE_SIZE)
                    val changeSet = collectionsRepository.journalChanges(userId, since, pageSize)
                    val changes = changeSet.changes.map(LogDateJournal::toJournalChange)
                    val deletions = changeSet.deletions.map { JournalDeletion(id = it.id, deletedAt = it.deletedAt) }
                    call.respond(JournalChangesResponse(changes, deletions, changeSet.lastTimestamp, changeSet.hasMore))
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_JOURNAL_CHANGES, System.currentTimeMillis() - start, success)
                }
            }

            get("/{journalId}") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@get
                    val journalId = call.requiredPathParam("journalId")
                    val record =
                        collectionsRepository.getJournal(userId, journalId)
                            ?: return@get call.respond(HttpStatusCode.NotFound, error("NOT_FOUND", "Journal not found"))
                    call.respond(record.toJournalChange())
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_JOURNAL_CHANGES, System.currentTimeMillis() - start, success)
                }
            }

            patch("/{journalId}") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@patch
                    val journalId = call.requiredPathParam("journalId")
                    val req = call.receive<JournalUpdateRequest>()
                    val existing = collectionsRepository.getJournal(userId, journalId)
                    when (val constraint = req.versionConstraint) {
                        is VersionConstraint.Known -> {
                            if (existing != null && existing.version > constraint.serverVersion) {
                                metrics.recordConflict()
                                return@patch call.respond(
                                    HttpStatusCode.Conflict,
                                    error(
                                        "CONFLICT",
                                        "Server has a newer version (${constraint.serverVersion} < ${existing.version})",
                                    ),
                                )
                            }
                        }
                        VersionConstraint.None -> Unit
                    }
                    val updatedAt = System.currentTimeMillis()
                    val stored =
                        collectionsRepository.upsertJournal(
                            userId = userId,
                            journal =
                                LogDateJournal(
                                    id = journalId,
                                    title = req.title ?: existing?.title.orEmpty(),
                                    description = req.description ?: existing?.description.orEmpty(),
                                    createdAt = existing?.createdAt ?: updatedAt,
                                    lastUpdated = req.lastUpdated,
                                    version = existing?.version ?: 0L,
                                    deviceId = req.deviceId,
                                ),
                        )
                    call.respond(JournalUpdateResponse(journalId, stored.version, stored.lastUpdated))
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_JOURNAL_UPDATE, System.currentTimeMillis() - start, success)
                }
            }

            delete("/{journalId}") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@delete
                    val journalId = call.requiredPathParam("journalId")
                    val deletedAt = System.currentTimeMillis()
                    collectionsRepository.deleteJournal(userId, journalId, deletedAt)
                    call.respond(HttpStatusCode.NoContent)
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_JOURNAL_DELETE, System.currentTimeMillis() - start, success)
                }
            }
        }

        // Associations
        route("/associations") {
            put {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@put
                    val req = call.receive<AssociationUploadRequest>()
                    val uploadedAt = System.currentTimeMillis()
                    collectionsRepository.upsertAssociations(
                        userId = userId,
                        associations =
                            req.associations.map {
                                LogDateAssociation(
                                    journalId = it.journalId,
                                    entryId = it.contentId,
                                    createdAt = it.createdAt,
                                    version = 0L,
                                    deviceId = it.deviceId,
                                )
                            },
                    )
                    call.respond(AssociationUploadResponse(req.associations.size, uploadedAt))
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_ASSOCIATION_UPLOAD, System.currentTimeMillis() - start, success)
                }
            }

            get {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@get
                    val sinceParam = call.request.queryParameters["since"]
                    val since =
                        when {
                            sinceParam == null -> 0L
                            else ->
                                sinceParam.toLongOrNull()
                                    ?: return@get call.respond(
                                        HttpStatusCode.BadRequest,
                                        error("INVALID_PARAMETER", "since parameter must be a valid long"),
                                    )
                        }
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_SYNC_PAGE_SIZE
                    val pageSize = limit.coerceIn(1, MAX_SYNC_PAGE_SIZE)
                    val changeSet = collectionsRepository.associationChanges(userId, since, pageSize)
                    val changes = changeSet.changes.map(LogDateAssociation::toAssociationChange)
                    val deletions =
                        changeSet.deletions.map {
                            AssociationDeletion(
                                journalId = it.association.journalId,
                                contentId = it.association.entryId,
                                deletedAt = it.deletedAt,
                            )
                        }
                    call.respond(
                        AssociationChangesResponse(changes, deletions, changeSet.lastTimestamp, changeSet.hasMore),
                    )
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_ASSOCIATION_CHANGES, System.currentTimeMillis() - start, success)
                }
            }

            put("/{journalId}/{contentId}") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@put
                    val journalId = call.requiredPathParam("journalId")
                    val contentId = call.requiredPathParam("contentId")
                    val req = call.receive<AssociationLinkUpsertRequest>()
                    collectionsRepository.upsertAssociations(
                        userId = userId,
                        associations =
                            listOf(
                                LogDateAssociation(
                                    journalId = journalId,
                                    entryId = contentId,
                                    createdAt = req.createdAt,
                                    version = 0L,
                                    deviceId = req.deviceId,
                                ),
                            ),
                    )
                    call.respond(HttpStatusCode.NoContent)
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_ASSOCIATION_UPLOAD, System.currentTimeMillis() - start, success)
                }
            }

            delete("/{journalId}/{contentId}") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@delete
                    val journalId = call.requiredPathParam("journalId")
                    val contentId = call.requiredPathParam("contentId")
                    val deletedAt = System.currentTimeMillis()
                    collectionsRepository.deleteAssociations(
                        userId,
                        listOf(LogDateAssociationRef(journalId, contentId)),
                        deletedAt,
                    )
                    call.respond(HttpStatusCode.NoContent)
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_ASSOCIATION_DELETE, System.currentTimeMillis() - start, success)
                }
            }

            delete {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@delete
                    val req = call.receive<AssociationDeleteRequest>()
                    val deletedAt = System.currentTimeMillis()
                    collectionsRepository.deleteAssociations(
                        userId,
                        req.associations.map { LogDateAssociationRef(it.journalId, it.contentId) },
                        deletedAt,
                    )
                    call.respond(HttpStatusCode.NoContent)
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_ASSOCIATION_DELETE, System.currentTimeMillis() - start, success)
                }
            }
        }

        route("/drafts") {
            put("/{draftId}") {
                val userId = extractUserId(call, tokenService) ?: return@put
                val draftId = call.requiredPathParam("draftId")
                val req = call.receive<app.logdate.shared.model.sync.DraftUploadRequest>()
                val stored =
                    collectionsRepository.upsertDraft(
                        userId = userId,
                        draft =
                            LogDateDraft(
                                id = draftId,
                                content = req.content,
                                blockTypes = req.blockTypes,
                                journalIds = req.journalIds,
                                createdAt = req.createdAt,
                                lastUpdated = req.lastUpdated,
                                version = 0L,
                                deviceId = req.deviceId,
                            ),
                    )
                call.respond(
                    HttpStatusCode.OK,
                    app.logdate.shared.model.sync.DraftUploadResponse(
                        id = stored.id,
                        serverVersion = stored.version,
                        uploadedAt = stored.lastUpdated,
                    ),
                )
            }

            get("/changes") {
                val userId = extractUserId(call, tokenService) ?: return@get
                val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                val changeSet = collectionsRepository.draftChanges(userId, since, limit)
                call.respond(
                    app.logdate.shared.model.sync.DraftChangesResponse(
                        drafts =
                            changeSet.changes.map { draft ->
                                app.logdate.shared.model.sync.DraftChange(
                                    id = draft.id,
                                    content = draft.content,
                                    blockTypes = draft.blockTypes,
                                    journalIds = draft.journalIds,
                                    createdAt = draft.createdAt,
                                    lastUpdated = draft.lastUpdated,
                                    deviceId = draft.deviceId,
                                    serverVersion = draft.version,
                                )
                            },
                    ),
                )
            }

            delete("/{draftId}") {
                val userId = extractUserId(call, tokenService) ?: return@delete
                val draftId = call.requiredPathParam("draftId")
                collectionsRepository.deleteDraft(userId, draftId, System.currentTimeMillis())
                call.respond(HttpStatusCode.NoContent)
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
                    val downloadUrl =
                        resolveMediaDownloadUrl(
                            call,
                            stored,
                            mediaStorage,
                            mediaAccessPolicy,
                        )
                    val response =
                        MediaUploadResponse(
                            contentId = req.contentId,
                            mediaId = stored.mediaId,
                            downloadUrl = downloadUrl,
                            uploadedAt = stored.createdAt,
                        )
                    call.response.headers.append(
                        HttpHeaders.Location,
                        "/api/v1/media/${stored.mediaId}",
                    )
                    call.respond(HttpStatusCode.Created, response)
                    success = true
                } finally {
                    metrics.recordOperation(
                        METRIC_MEDIA_UPLOAD,
                        System.currentTimeMillis() - start,
                        success,
                        bytes,
                    )
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
                    metrics.recordOperation(
                        METRIC_MEDIA_DOWNLOAD,
                        System.currentTimeMillis() - start,
                        success,
                    )
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
                        runCatching { ContentType.parse(record.mimeType) }.getOrElse {
                            ContentType.Application.OctetStream
                        }
                    call.respondBytes(payload, contentType)
                    success = true
                } finally {
                    metrics.recordOperation(
                        METRIC_MEDIA_DOWNLOAD,
                        System.currentTimeMillis() - start,
                        success,
                        bytes,
                    )
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
                    metrics.recordOperation(
                        METRIC_MEDIA_DELETE,
                        System.currentTimeMillis() - start,
                        success,
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

                    call.response.headers.append(
                        HttpHeaders.Location,
                        "/api/v1/backups/${record.id}",
                    )
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
                    val backupId =
                        call
                            .requiredPathParam("backupId")
                            .let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
                            ?: return@get call.respond(
                                HttpStatusCode.BadRequest,
                                error("INVALID_ID", "Invalid backupId"),
                            )

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
                    val backupId =
                        call
                            .requiredPathParam("backupId")
                            .let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
                            ?: return@get call.respond(
                                HttpStatusCode.BadRequest,
                                error("INVALID_ID", "Invalid backupId"),
                            )

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
                    val backupId =
                        call
                            .requiredPathParam("backupId")
                            .let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
                            ?: return@delete call.respond(
                                HttpStatusCode.BadRequest,
                                error("INVALID_ID", "Invalid backupId"),
                            )

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

        // Maintenance
        route("/ops/sync") {
            post("/tombstones:purge") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@post
                    val retentionDays = call.request.queryParameters["retentionDays"]?.toLongOrNull() ?: 30L
                    if (retentionDays <= 0) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            error("INVALID_PARAMETER", "retentionDays must be > 0"),
                        )
                    }
                    val safeRetentionDays = retentionDays.coerceAtMost(3650L)
                    val cutoff = System.currentTimeMillis() - (safeRetentionDays * MILLIS_PER_DAY)
                    val result = collectionsRepository.purgeTombstones(userId, cutoff)
                    call.respond(
                        SyncPurgeResponse(
                            contentPurged = result.entryPurged,
                            journalPurged = result.journalPurged,
                            associationPurged = result.associationPurged,
                            mediaPurged = 0,
                            cutoff = result.cutoff,
                            retentionDaysApplied = safeRetentionDays,
                        ),
                    )
                    success = true
                } finally {
                    metrics.recordOperation(METRIC_SYNC_PURGE, System.currentTimeMillis() - start, success)
                }
            }
        }

        route("/ops/backups") {
            post(":purge") {
                val start = System.currentTimeMillis()
                var success = false
                try {
                    val userId = extractUserId(call, tokenService) ?: return@post
                    val keepPerDevice =
                        call.request.queryParameters["keepPerDevice"]?.toIntOrNull()
                            ?: BackupRetentionPolicy.DEFAULT.keepPerDevice
                    val maxAgeDays =
                        call.request.queryParameters["maxAgeDays"]?.toLongOrNull()
                            ?: (BackupRetentionPolicy.DEFAULT.maxAgeMillis / MILLIS_PER_DAY)
                    if (keepPerDevice < 0 || maxAgeDays <= 0) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            error("INVALID_PARAMETER", "keepPerDevice must be >= 0 and maxAgeDays must be > 0"),
                        )
                    }
                    val policy =
                        BackupRetentionPolicy(
                            keepPerDevice = keepPerDevice,
                            maxAgeMillis = maxAgeDays.coerceAtMost(3650L) * MILLIS_PER_DAY,
                        )
                    val backups = backupRepository.listBackups(userId)
                    val toPurge = policy.backupsToPurge(backups, System.currentTimeMillis())
                    toPurge.forEach { backup ->
                        runCatching { mediaStorage?.deleteBlob(backup.storagePath) }
                        backupRepository.deleteBackup(userId, backup.id)
                    }
                    call.respond(
                        BackupPurgeResponse(
                            deleted = toPurge.size,
                            remaining = backups.size - toPurge.size,
                            keepPerDevice = keepPerDevice,
                            maxAgeDaysApplied = maxAgeDays,
                        ),
                    )
                    success = true
                } finally {
                    metrics.recordOperation("sync.backups.purge", System.currentTimeMillis() - start, success)
                }
            }
        }
    }
}

@kotlinx.serialization.Serializable
data class BackupPurgeResponse(
    val deleted: Int,
    val remaining: Int,
    val keepPerDevice: Int,
    val maxAgeDaysApplied: Long,
)

// URL-resolution, Prometheus export, rate-limit policies, and quota/rate-limit responders live
// in [app.logdate.server.routes.sync.SyncHelpers].
