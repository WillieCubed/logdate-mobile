package app.logdate.server.routes

import app.logdate.server.responses.error
import app.logdate.server.responses.simpleSuccess
// Transport-only DTOs for sync; do not leak sync metadata into core domain models.
import app.logdate.shared.model.sync.Association
import app.logdate.shared.model.sync.AssociationChange
import app.logdate.shared.model.sync.AssociationChangesResponse
import app.logdate.shared.model.sync.AssociationDeleteItem
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
import app.logdate.shared.model.sync.DeviceId
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
import app.logdate.server.sync.AssociationDeletionMarker
import app.logdate.server.sync.AssociationKey
import app.logdate.server.sync.AssociationRecord
import app.logdate.server.sync.ContentDeletionMarker
import app.logdate.server.sync.ContentRecord
import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.server.sync.JournalDeletionMarker
import app.logdate.server.sync.JournalRecord
import app.logdate.server.sync.MediaRecord
import app.logdate.server.sync.SyncRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * Minimal in-memory sync implementation to replace previous 501 stubs.
 * This is intentionally simple but matches the shapes expected by the client Cloud API.
 * Data is stored in-memory per server instance and is not persisted.
 */
fun Route.syncRoutes(
    repository: SyncRepository = InMemorySyncRepository()
) {
    @Serializable
    data class SyncStatusSnapshot(
        val contentCount: Int,
        val journalCount: Int,
        val associationCount: Int,
        val lastTimestamp: Long
    )

    @Serializable
    data class SyncTriggerResponse(
        val started: Boolean,
        val timestamp: Long
    )

    route("/sync") {
        // High-level status (used by smoke tests)
        get("/status") {
            val status = repository.status()
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
            val payload = SyncTriggerResponse(
                started = true,
                timestamp = System.currentTimeMillis()
            )
            call.respond(simpleSuccess(payload))
        }

        // Content
        route("/content") {
            post {
                val req = call.receive<ContentUploadRequest>()
                val stored = repository.upsertContent(
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
                val since = call.request.queryParameters["since"]?.toLongOrNull()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        error("MISSING_PARAMETER", "since parameter is required")
                    )

                val changeSet = repository.contentChanges(since)
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
                val contentId = call.parameters["contentId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    error("MISSING_PARAMETER", "contentId is required")
                )
                val req = call.receive<ContentUpdateRequest>()
                val existing = repository.getContent(contentId)
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
                val contentId = call.parameters["contentId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    error("MISSING_PARAMETER", "contentId is required")
                )
                val deletedAt = System.currentTimeMillis()
                repository.deleteContent(contentId, deletedAt)
                call.respond(HttpStatusCode.OK)
            }
        }

        // Journals
        route("/journals") {
            post {
                val req = call.receive<JournalUploadRequest>()
                val stored = repository.upsertJournal(
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
                val since = call.request.queryParameters["since"]?.toLongOrNull()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        error("MISSING_PARAMETER", "since parameter is required")
                    )
                val changeSet = repository.journalChanges(since)
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
                val journalId = call.parameters["journalId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    error("MISSING_PARAMETER", "journalId is required")
                )
                val req = call.receive<JournalUpdateRequest>()
                val existing = repository.getJournal(journalId)
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
                val journalId = call.parameters["journalId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    error("MISSING_PARAMETER", "journalId is required")
                )
                val deletedAt = System.currentTimeMillis()
                repository.deleteJournal(journalId, deletedAt)
                call.respond(HttpStatusCode.OK)
            }
        }

        // Associations
        route("/associations") {
            post {
                val req = call.receive<AssociationUploadRequest>()
                val uploadedAt = System.currentTimeMillis()
                repository.upsertAssociations(
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
                val since = call.request.queryParameters["since"]?.toLongOrNull()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        error("MISSING_PARAMETER", "since parameter is required")
                    )
                val changeSet = repository.associationChanges(since)
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
                val req = call.receive<AssociationDeleteRequest>()
                val deletedAt = System.currentTimeMillis()
                repository.deleteAssociations(
                    req.associations.map { AssociationKey(it.journalId, it.contentId) },
                    deletedAt
                )
                call.respond(HttpStatusCode.OK)
            }
        }

        // Media upload/download (no real storage)
        route("/media") {
            post {
                val req = call.receive<MediaUploadRequest>()
                val stored = repository.upsertMedia(
                    MediaRecord(
                        mediaId = "",
                        contentId = req.contentId,
                        fileName = req.fileName,
                        mimeType = req.mimeType,
                        sizeBytes = req.sizeBytes,
                        data = req.data,
                        createdAt = System.currentTimeMillis(),
                        serverVersion = 0L,
                        deviceId = req.deviceId
                    )
                )
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
                val mediaId = call.parameters["mediaId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    error("MISSING_PARAMETER", "mediaId is required")
                )
                val record = repository.getMedia(mediaId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, error("NOT_FOUND", "Media not found"))
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
