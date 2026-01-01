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
import app.logdate.server.sync.ContentRecord
import app.logdate.server.sync.JournalRecord
import app.logdate.server.sync.MediaRecord
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

@Serializable
private data class SyncTriggerResponse(
    val started: Boolean,
    val timestamp: Long
)

/**
 * Extracts and validates the user ID from the Authorization header.
 * Returns null and responds with 401 if authentication fails.
 */
private suspend fun extractUserId(
    call: ApplicationCall,
    tokenService: TokenService?
): UUID? {
    // If no token service is configured, use a test user ID for development
    if (tokenService == null) {
        Napier.w("TokenService not configured, using test user ID")
        return UUID.fromString("00000000-0000-0000-0000-000000000001")
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
    tokenService: TokenService? = null
) {
    route("/sync") {
        // High-level status (used by smoke tests)
        get("/status") {
            val userId = extractUserId(call, tokenService) ?: return@get
            val status = repository.status(userId)
            val snapshot = SyncStatusSnapshot(
                contentCount = status.contentCount,
                journalCount = status.journalCount,
                associationCount = status.associationCount,
                lastTimestamp = status.lastTimestamp
            )
            call.respond(simpleSuccess(snapshot))
        }

        // Legacy placeholder full sync trigger
        post("/") {
            val userId = extractUserId(call, tokenService) ?: return@post
            val payload = SyncTriggerResponse(
                started = true,
                timestamp = System.currentTimeMillis()
            )
            call.respond(simpleSuccess(payload))
        }

        // Content
        route("/content") {
            post {
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
            }

            get("/changes") {
                val userId = extractUserId(call, tokenService) ?: return@get
                val since = call.request.queryParameters["since"]?.toLongOrNull()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        error("MISSING_PARAMETER", "since parameter is required")
                    )

                val changeSet = repository.contentChanges(userId, since)
                val changes = changeSet.changes.map {
                    ContentChange(
                        id = it.id,
                        type = it.type,
                        content = it.content,
                        mediaUri = it.mediaUri,
                        createdAt = it.createdAt,
                        lastUpdated = it.lastUpdated,
                        serverVersion = it.serverVersion,
                        isDeleted = false
                    )
                }
                val deletions = changeSet.deletions.map { ContentDeletion(id = it.id, deletedAt = it.deletedAt) }
                call.respond(ContentChangesResponse(changes, deletions, changeSet.lastTimestamp))
            }

            post("/{contentId}") {
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
                            return@post call.respond(
                                HttpStatusCode.Conflict,
                                error("CONFLICT", "Server has a newer version (${constraint.serverVersion} < ${existing.serverVersion})")
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
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        lastUpdated = req.lastUpdated,
                        serverVersion = existing?.serverVersion ?: 0L,
                        deviceId = req.deviceId
                    )
                )
                call.respond(ContentUpdateResponse(contentId, updated.serverVersion, updated.lastUpdated))
            }

            post("/{contentId}/delete") {
                val userId = extractUserId(call, tokenService) ?: return@post
                val contentId = call.parameters["contentId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    error("MISSING_PARAMETER", "contentId is required")
                )
                val deletedAt = System.currentTimeMillis()
                repository.deleteContent(userId, contentId, deletedAt)
                call.respond(HttpStatusCode.OK)
            }
        }

        // Journals
        route("/journals") {
            post {
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
            }

            get("/changes") {
                val userId = extractUserId(call, tokenService) ?: return@get
                val since = call.request.queryParameters["since"]?.toLongOrNull()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        error("MISSING_PARAMETER", "since parameter is required")
                    )
                val changeSet = repository.journalChanges(userId, since)
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
                call.respond(JournalChangesResponse(changes, deletions, changeSet.lastTimestamp))
            }

            post("/{journalId}") {
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
                            return@post call.respond(
                                HttpStatusCode.Conflict,
                                error("CONFLICT", "Server has a newer version (${constraint.serverVersion} < ${existing.serverVersion})")
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
            }

            post("/{journalId}/delete") {
                val userId = extractUserId(call, tokenService) ?: return@post
                val journalId = call.parameters["journalId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    error("MISSING_PARAMETER", "journalId is required")
                )
                val deletedAt = System.currentTimeMillis()
                repository.deleteJournal(userId, journalId, deletedAt)
                call.respond(HttpStatusCode.OK)
            }
        }

        // Associations
        route("/associations") {
            post {
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
            }

            get("/changes") {
                val userId = extractUserId(call, tokenService) ?: return@get
                val since = call.request.queryParameters["since"]?.toLongOrNull()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        error("MISSING_PARAMETER", "since parameter is required")
                    )
                val changeSet = repository.associationChanges(userId, since)
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
                call.respond(AssociationChangesResponse(changes, deletions, changeSet.lastTimestamp))
            }

            post("/delete") {
                val userId = extractUserId(call, tokenService) ?: return@post
                val req = call.receive<AssociationDeleteRequest>()
                val deletedAt = System.currentTimeMillis()
                repository.deleteAssociations(
                    userId,
                    req.associations.map { AssociationKey(it.journalId, it.contentId) },
                    deletedAt
                )
                call.respond(HttpStatusCode.OK)
            }
        }

        // Media upload/download
        route("/media") {
            post {
                val userId = extractUserId(call, tokenService) ?: return@post
                val req = call.receive<MediaUploadRequest>()
                Napier.d("Media upload for user $userId: ${req.fileName}")
                val stored = repository.upsertMedia(
                    userId,
                    MediaRecord(
                        mediaId = "",
                        contentId = req.contentId,
                        fileName = req.fileName,
                        mimeType = req.mimeType,
                        sizeBytes = req.sizeBytes,
                        data = req.data,
                        storagePath = null,
                        createdAt = System.currentTimeMillis(),
                        serverVersion = 0L,
                        deviceId = req.deviceId
                    )
                )
                // TODO: Replace with GCS signed URL when GcsMediaStorage is wired
                val downloadUrl = "https://example.com/media/${stored.mediaId}"
                val response = MediaUploadResponse(
                    contentId = req.contentId,
                    mediaId = stored.mediaId,
                    downloadUrl = downloadUrl,
                    uploadedAt = stored.createdAt
                )
                call.respond(response)
            }

            get("/{mediaId}") {
                val userId = extractUserId(call, tokenService) ?: return@get
                val mediaId = call.parameters["mediaId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    error("MISSING_PARAMETER", "mediaId is required")
                )
                val record = repository.getMedia(userId, mediaId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, error("NOT_FOUND", "Media not found"))
                // TODO: Replace with GCS signed URL when GcsMediaStorage is wired
                call.respond(
                    MediaDownloadResponse(
                        contentId = record.contentId,
                        fileName = record.fileName,
                        mimeType = record.mimeType,
                        sizeBytes = record.sizeBytes,
                        data = record.data,
                        downloadUrl = "https://example.com/media/${record.mediaId}"
                    )
                )
            }
        }
    }
}
