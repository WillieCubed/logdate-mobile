package app.logdate.server.routes.sync

import app.logdate.server.auth.TokenService
import app.logdate.server.logdate.LogDateAssociation
import app.logdate.server.logdate.LogDateAssociationRef
import app.logdate.server.logdate.LogDateCollectionsRepository
import app.logdate.server.logdate.LogDateDraft
import app.logdate.server.logdate.LogDateEntry
import app.logdate.server.logdate.LogDateJournal
import app.logdate.server.responses.error
import app.logdate.server.sync.SyncMetricsRegistry
import app.logdate.shared.model.sync.AssociationChangesResponse
import app.logdate.shared.model.sync.AssociationDeleteRequest
import app.logdate.shared.model.sync.AssociationDeletion
import app.logdate.shared.model.sync.AssociationUploadRequest
import app.logdate.shared.model.sync.AssociationUploadResponse
import app.logdate.shared.model.sync.ContentChangesResponse
import app.logdate.shared.model.sync.ContentDeletion
import app.logdate.shared.model.sync.ContentUpdateRequest
import app.logdate.shared.model.sync.ContentUpdateResponse
import app.logdate.shared.model.sync.ContentUploadRequest
import app.logdate.shared.model.sync.ContentUploadResponse
import app.logdate.shared.model.sync.DeviceId
import app.logdate.shared.model.sync.DraftChange
import app.logdate.shared.model.sync.DraftChangesResponse
import app.logdate.shared.model.sync.DraftUploadRequest
import app.logdate.shared.model.sync.DraftUploadResponse
import app.logdate.shared.model.sync.JournalChangesResponse
import app.logdate.shared.model.sync.JournalDeletion
import app.logdate.shared.model.sync.JournalUpdateRequest
import app.logdate.shared.model.sync.JournalUpdateResponse
import app.logdate.shared.model.sync.JournalUploadRequest
import app.logdate.shared.model.sync.JournalUploadResponse
import app.logdate.shared.model.sync.VersionConstraint
import io.github.aakira.napier.Napier
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

/**
 * CRUD + change-feed endpoints for the four sync collections: contents, journals, associations,
 * and drafts. Each block follows the same shape — PUT to upsert, GET with `since` for paginated
 * change feeds, PATCH with optimistic locking, DELETE for soft deletion.
 */
internal fun Route.syncCollectionRoutes(
    tokenService: TokenService?,
    metrics: SyncMetricsRegistry,
    collectionsRepository: LogDateCollectionsRepository,
) {
    contentRoutes(tokenService, metrics, collectionsRepository)
    journalRoutes(tokenService, metrics, collectionsRepository)
    associationRoutes(tokenService, metrics, collectionsRepository)
    draftRoutes(tokenService, collectionsRepository)
}

private fun Route.contentRoutes(
    tokenService: TokenService?,
    metrics: SyncMetricsRegistry,
    collectionsRepository: LogDateCollectionsRepository,
) {
    route("/contents") {
        put("/{contentId}", {
            tags = listOf("Contents")
            summary = "Upsert content"
            description = "Create or update a content entry by ID."
            securitySchemeNames = listOf("bearerAuth")
            request {
                pathParameter<String>("contentId") {
                    description = "The ID of the content to upsert."
                }
                body<ContentUploadRequest>()
            }
            response {
                HttpStatusCode.OK to { description = "Content updated" }
                HttpStatusCode.Created to { description = "Content created" }
            }
        }) {
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

        get({
            tags = listOf("Contents")
            summary = "Get contents"
            description = "Retrieve all contents for the authenticated user."
            securitySchemeNames = listOf("bearerAuth")
            response {
                HttpStatusCode.OK to {
                    description = "Successful retrieval"
                    body<ContentChangesResponse>()
                }
            }
        }) {
            val start = System.currentTimeMillis()
            var success = false
            try {
                val userId = extractUserId(call, tokenService) ?: return@get
                val since = parseSinceParameter(call) ?: return@get
                val pageSize = call.resolvePageSize()
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

        patch("/{contentId}", {
            tags = listOf("Contents")
            summary = "Patch content"
            description = "Update specific fields of a content entry with optimistic locking."
            securitySchemeNames = listOf("bearerAuth")
            request {
                pathParameter<String>("contentId") {
                    description = "The ID of the content to patch."
                }
                body<ContentUpdateRequest>()
            }
            response {
                HttpStatusCode.OK to {
                    description = "Content patched successfully"
                    body<ContentUpdateResponse>()
                }
            }
        }) {
            val start = System.currentTimeMillis()
            var success = false
            try {
                val userId = extractUserId(call, tokenService) ?: return@patch
                val contentId = call.requiredPathParam("contentId")
                val req = call.receive<ContentUpdateRequest>()
                val existing = collectionsRepository.getEntry(userId, contentId)
                if (existing != null && req.isOutdated(existing.version)) {
                    metrics.recordConflict()
                    return@patch call.respond(
                        HttpStatusCode.Conflict,
                        error("CONFLICT", "Server has a newer version"),
                    )
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
                collectionsRepository.deleteEntry(userId, contentId, System.currentTimeMillis())
                call.respond(HttpStatusCode.NoContent)
                success = true
            } finally {
                metrics.recordOperation(METRIC_CONTENT_DELETE, System.currentTimeMillis() - start, success)
            }
        }
    }
}

private fun Route.journalRoutes(
    tokenService: TokenService?,
    metrics: SyncMetricsRegistry,
    collectionsRepository: LogDateCollectionsRepository,
) {
    route("/journals") {
        put("/{journalId}", {
            tags = listOf("Journals")
            summary = "Upsert journal"
            description = "Create or update a journal by ID."
            securitySchemeNames = listOf("bearerAuth")
            request {
                pathParameter<String>("journalId") {
                    description = "The ID of the journal to upsert."
                }
                body<JournalUploadRequest>()
            }
            response {
                HttpStatusCode.OK to {
                    description = "Journal updated"
                    body<JournalUploadResponse>()
                }
                HttpStatusCode.Created to {
                    description = "Journal created"
                    body<JournalUploadResponse>()
                }
            }
        }) {
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

        get({
            tags = listOf("Journals")
            summary = "Get journals"
            description = "Retrieve all journals for the authenticated user."
            securitySchemeNames = listOf("bearerAuth")
            response {
                HttpStatusCode.OK to {
                    description = "Successful retrieval"
                    body<JournalChangesResponse>()
                }
            }
        }) {
            val start = System.currentTimeMillis()
            var success = false
            try {
                val userId = extractUserId(call, tokenService) ?: return@get
                val since = parseSinceParameter(call) ?: return@get
                val pageSize = call.resolvePageSize()
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

        patch("/{journalId}", {
            tags = listOf("Journals")
            summary = "Patch journal"
            description = "Update specific fields of a journal with optimistic locking."
            securitySchemeNames = listOf("bearerAuth")
            request {
                pathParameter<String>("journalId") {
                    description = "The ID of the journal to patch."
                }
                body<JournalUpdateRequest>()
            }
            response {
                HttpStatusCode.OK to {
                    description = "Journal patched successfully"
                    body<JournalUpdateResponse>()
                }
            }
        }) {
            val start = System.currentTimeMillis()
            var success = false
            try {
                val userId = extractUserId(call, tokenService) ?: return@patch
                val journalId = call.requiredPathParam("journalId")
                val req = call.receive<JournalUpdateRequest>()
                val existing = collectionsRepository.getJournal(userId, journalId)
                if (existing != null && req.isOutdated(existing.version)) {
                    metrics.recordConflict()
                    return@patch call.respond(
                        HttpStatusCode.Conflict,
                        error("CONFLICT", "Server has a newer version"),
                    )
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
                collectionsRepository.deleteJournal(userId, journalId, System.currentTimeMillis())
                call.respond(HttpStatusCode.NoContent)
                success = true
            } finally {
                metrics.recordOperation(METRIC_JOURNAL_DELETE, System.currentTimeMillis() - start, success)
            }
        }
    }
}

private fun Route.associationRoutes(
    tokenService: TokenService?,
    metrics: SyncMetricsRegistry,
    collectionsRepository: LogDateCollectionsRepository,
) {
    route("/associations") {
        post({
            tags = listOf("Associations")
            summary = "Upload associations"
            description = "Batch upload associations between entities."
            securitySchemeNames = listOf("bearerAuth")
            request {
                body<AssociationUploadRequest>()
            }
            response {
                HttpStatusCode.OK to {
                    description = "Associations uploaded successfully"
                    body<AssociationUploadResponse>()
                }
            }
        }) {
            val start = System.currentTimeMillis()

            var success = false
            try {
                val userId = extractUserId(call, tokenService) ?: return@post
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

        get({
            tags = listOf("Associations")
            summary = "Get associations"
            description = "Retrieve all associations for the authenticated user."
            securitySchemeNames = listOf("bearerAuth")
            response {
                HttpStatusCode.OK to {
                    description = "Successful retrieval"
                    body<AssociationChangesResponse>()
                }
            }
        }) {
            val start = System.currentTimeMillis()
            var success = false
            try {
                val userId = extractUserId(call, tokenService) ?: return@get
                val since = parseSinceParameter(call) ?: return@get
                val pageSize = call.resolvePageSize()
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
                call.respond(AssociationChangesResponse(changes, deletions, changeSet.lastTimestamp, changeSet.hasMore))
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
                collectionsRepository.deleteAssociations(
                    userId,
                    listOf(LogDateAssociationRef(journalId, contentId)),
                    System.currentTimeMillis(),
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
                collectionsRepository.deleteAssociations(
                    userId,
                    req.associations.map { LogDateAssociationRef(it.journalId, it.contentId) },
                    System.currentTimeMillis(),
                )
                call.respond(HttpStatusCode.NoContent)
                success = true
            } finally {
                metrics.recordOperation(METRIC_ASSOCIATION_DELETE, System.currentTimeMillis() - start, success)
            }
        }
    }
}

private fun Route.draftRoutes(
    tokenService: TokenService?,
    collectionsRepository: LogDateCollectionsRepository,
) {
    route("/drafts") {
        put("/{draftId}") {
            val userId = extractUserId(call, tokenService) ?: return@put
            val draftId = call.requiredPathParam("draftId")
            val req = call.receive<DraftUploadRequest>()
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
                DraftUploadResponse(
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
                DraftChangesResponse(
                    drafts =
                        changeSet.changes.map { draft ->
                            DraftChange(
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
}

@Serializable
internal data class AssociationLinkUpsertRequest(
    val createdAt: Long,
    val deviceId: DeviceId = DeviceId.UNKNOWN,
)

private suspend fun parseSinceParameter(call: io.ktor.server.application.ApplicationCall): Long? {
    val raw = call.request.queryParameters["since"] ?: return 0L
    return raw.toLongOrNull() ?: run {
        call.respond(
            HttpStatusCode.BadRequest,
            error("INVALID_PARAMETER", "since parameter must be a valid long"),
        )
        null
    }
}

private fun io.ktor.server.application.ApplicationCall.resolvePageSize(): Int =
    (request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_SYNC_PAGE_SIZE)
        .coerceIn(1, MAX_SYNC_PAGE_SIZE)

private fun ContentUpdateRequest.isOutdated(currentVersion: Long): Boolean =
    (versionConstraint as? VersionConstraint.Known)?.serverVersion?.let { it < currentVersion } == true

private fun JournalUpdateRequest.isOutdated(currentVersion: Long): Boolean =
    (versionConstraint as? VersionConstraint.Known)?.serverVersion?.let { it < currentVersion } == true
