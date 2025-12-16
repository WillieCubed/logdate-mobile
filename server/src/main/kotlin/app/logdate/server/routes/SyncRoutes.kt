package app.logdate.server.routes

import app.logdate.server.responses.error
import app.logdate.server.responses.success
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.math.absoluteValue

/**
 * Minimal in-memory sync implementation to replace previous 501 stubs.
 * This is intentionally simple but matches the shapes expected by the client Cloud API.
 * Data is stored in-memory per server instance and is not persisted.
 */
fun Route.syncRoutes() {
    route("/sync") {
        // High-level status (used by smoke tests)
        get("/status") {
            call.respond(
                success(
                    mapOf(
                        "contentCount" to SyncStore.content.size,
                        "journalCount" to SyncStore.journals.size,
                        "associationCount" to SyncStore.associations.size,
                        "lastTimestamp" to SyncStore.lastTimestamp.get()
                    )
                )
            )
        }

        // Legacy placeholder full sync trigger
        post("/") {
            call.respond(
                success(
                    mapOf(
                        "started" to true,
                        "timestamp" to SyncStore.now()
                    )
                )
            )
        }

        // Content
        route("/content") {
            post {
                val req = call.receive<ContentUploadRequest>()
                val version = SyncStore.nextVersion()
                val uploadedAt = SyncStore.now()
                SyncStore.content[req.id] = StoredContent(
                    id = req.id,
                    type = req.type,
                    content = req.content,
                    mediaUri = req.mediaUri,
                    createdAt = req.createdAt,
                    lastUpdated = req.lastUpdated,
                    serverVersion = version
                )
                call.respond(ContentUploadResponse(req.id, version, uploadedAt))
            }

            get("/changes") {
                val since = call.request.queryParameters["since"]?.toLongOrNull()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        error("MISSING_PARAMETER", "since parameter is required")
                    )

                val changes = SyncStore.content.values
                    .filter { it.lastUpdated > since || it.serverVersion > since }
                    .map { it.toChange() }
                val deletions = SyncStore.contentDeletions
                    .filterValues { it > since }
                    .map { ContentDeletion(id = it.key, deletedAt = it.value) }
                val lastTs = SyncStore.lastTimestamp.get()
                call.respond(ContentChangesResponse(changes, deletions, lastTs))
            }

            post("/{contentId}") {
                val contentId = call.parameters["contentId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    error("MISSING_PARAMETER", "contentId is required")
                )
                val req = call.receive<ContentUpdateRequest>()
                val existing = SyncStore.content[contentId]
                val version = SyncStore.nextVersion()
                val updatedAt = SyncStore.now()
                SyncStore.content[contentId] = StoredContent(
                    id = contentId,
                    type = existing?.type ?: "TEXT",
                    content = req.content ?: existing?.content,
                    mediaUri = req.mediaUri ?: existing?.mediaUri,
                    createdAt = existing?.createdAt ?: updatedAt,
                    lastUpdated = req.lastUpdated,
                    serverVersion = version
                )
                call.respond(ContentUpdateResponse(contentId, version, updatedAt))
            }

            post("/{contentId}/delete") {
                val contentId = call.parameters["contentId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    error("MISSING_PARAMETER", "contentId is required")
                )
                val deletedAt = SyncStore.now()
                SyncStore.content.remove(contentId)
                SyncStore.contentDeletions[contentId] = deletedAt
                call.respond(HttpStatusCode.OK)
            }
        }

        // Journals
        route("/journals") {
            post {
                val req = call.receive<JournalUploadRequest>()
                val version = SyncStore.nextVersion()
                val uploadedAt = SyncStore.now()
                SyncStore.journals[req.id] = StoredJournal(
                    id = req.id,
                    title = req.title,
                    description = req.description,
                    createdAt = req.createdAt,
                    lastUpdated = req.lastUpdated,
                    serverVersion = version
                )
                call.respond(JournalUploadResponse(req.id, version, uploadedAt))
            }

            get("/changes") {
                val since = call.request.queryParameters["since"]?.toLongOrNull()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        error("MISSING_PARAMETER", "since parameter is required")
                    )
                val changes = SyncStore.journals.values
                    .filter { it.lastUpdated > since || it.serverVersion > since }
                    .map { it.toChange() }
                val deletions = SyncStore.journalDeletions
                    .filterValues { it > since }
                    .map { JournalDeletion(id = it.key, deletedAt = it.value) }
                val lastTs = SyncStore.lastTimestamp.get()
                call.respond(JournalChangesResponse(changes, deletions, lastTs))
            }

            post("/{journalId}") {
                val journalId = call.parameters["journalId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    error("MISSING_PARAMETER", "journalId is required")
                )
                val req = call.receive<JournalUpdateRequest>()
                val existing = SyncStore.journals[journalId]
                val version = SyncStore.nextVersion()
                val updatedAt = SyncStore.now()
                SyncStore.journals[journalId] = StoredJournal(
                    id = journalId,
                    title = req.title ?: existing?.title.orEmpty(),
                    description = req.description ?: existing?.description.orEmpty(),
                    createdAt = existing?.createdAt ?: updatedAt,
                    lastUpdated = req.lastUpdated,
                    serverVersion = version
                )
                call.respond(JournalUpdateResponse(journalId, version, updatedAt))
            }

            post("/{journalId}/delete") {
                val journalId = call.parameters["journalId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    error("MISSING_PARAMETER", "journalId is required")
                )
                val deletedAt = SyncStore.now()
                SyncStore.journals.remove(journalId)
                SyncStore.journalDeletions[journalId] = deletedAt
                call.respond(HttpStatusCode.OK)
            }
        }

        // Associations
        route("/associations") {
            post {
                val req = call.receive<AssociationUploadRequest>()
                val uploadedAt = SyncStore.now()
                req.associations.forEach { association ->
                    SyncStore.associations[association.key()] = AssociationChange(
                        journalId = association.journalId,
                        contentId = association.contentId,
                        createdAt = association.createdAt,
                        serverVersion = SyncStore.nextVersion(),
                        isDeleted = false
                    )
                }
                call.respond(AssociationUploadResponse(req.associations.size, uploadedAt))
            }

            get("/changes") {
                val since = call.request.queryParameters["since"]?.toLongOrNull()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        error("MISSING_PARAMETER", "since parameter is required")
                    )
                val changes = SyncStore.associations.values
                    .filter { it.serverVersion > since || it.createdAt > since }
                    .toList()
                val deletions = SyncStore.associationDeletions
                    .filterValues { it > since }
                    .map { (key, deletedAt) ->
                        AssociationDeletion(
                            journalId = key.first,
                            contentId = key.second,
                            deletedAt = deletedAt
                        )
                    }
                val lastTs = SyncStore.lastTimestamp.get()
                call.respond(AssociationChangesResponse(changes, deletions, lastTs))
            }

            post("/delete") {
                val req = call.receive<AssociationDeleteRequest>()
                val deletedAt = SyncStore.now()
                req.associations.forEach { item ->
                    SyncStore.associations.remove(item.key())
                    SyncStore.associationDeletions[item.key()] = deletedAt
                }
                call.respond(HttpStatusCode.OK)
            }
        }

        // Media upload/download (no real storage)
        route("/media") {
            post {
                val req = call.receive<MediaUploadRequest>()
                val uploadedAt = SyncStore.now()
                val mediaId = "media-${Random.nextLong().absoluteValue}"
                val downloadUrl = "https://example.com/media/$mediaId"
                val record = MediaDownloadResponse(
                    contentId = req.contentId,
                    fileName = req.fileName,
                    mimeType = req.mimeType,
                    sizeBytes = req.sizeBytes,
                    data = req.data,
                    downloadUrl = downloadUrl
                )
                SyncStore.media[mediaId] = record
                call.respond(
                    MediaUploadResponse(
                        contentId = req.contentId,
                        mediaId = mediaId,
                        downloadUrl = downloadUrl,
                        uploadedAt = uploadedAt
                    )
                )
            }

            get("/{mediaId}") {
                val mediaId = call.parameters["mediaId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    error("MISSING_PARAMETER", "mediaId is required")
                )
                val record = SyncStore.media[mediaId]
                    ?: return@get call.respond(HttpStatusCode.NotFound, error("NOT_FOUND", "Media not found"))
                call.respond(record)
            }
        }
    }
}

// In-memory store used by the stubbed sync routes
private object SyncStore {
    val content = ConcurrentHashMap<String, StoredContent>()
    val contentDeletions = ConcurrentHashMap<String, Long>()
    val journals = ConcurrentHashMap<String, StoredJournal>()
    val journalDeletions = ConcurrentHashMap<String, Long>()
    val associations = ConcurrentHashMap<Pair<String, String>, AssociationChange>()
    val associationDeletions = ConcurrentHashMap<Pair<String, String>, Long>()
    val media = ConcurrentHashMap<String, MediaDownloadResponse>()
    val lastTimestamp = AtomicLong(System.currentTimeMillis())

    fun now(): Long {
        val ts = System.currentTimeMillis()
        lastTimestamp.set(ts)
        return ts
    }

    fun nextVersion(): Long = lastTimestamp.incrementAndGet()
}

// Storage representations
private data class StoredContent(
    val id: String,
    val type: String,
    val content: String?,
    val mediaUri: String?,
    val createdAt: Long,
    val lastUpdated: Long,
    val serverVersion: Long
) {
    fun toChange(): ContentChange = ContentChange(
        id = id,
        type = type,
        content = content,
        mediaUri = mediaUri,
        createdAt = createdAt,
        lastUpdated = lastUpdated,
        serverVersion = serverVersion,
        isDeleted = false
    )
}

private data class StoredJournal(
    val id: String,
    val title: String,
    val description: String,
    val createdAt: Long,
    val lastUpdated: Long,
    val serverVersion: Long
) {
    fun toChange(): JournalChange = JournalChange(
        id = id,
        title = title,
        description = description,
        createdAt = createdAt,
        lastUpdated = lastUpdated,
        serverVersion = serverVersion,
        isDeleted = false
    )
}

// Request/response models mirrored from client sync module
@Serializable
data class ContentUploadRequest(
    val id: String,
    val type: String,
    val content: String?,
    val mediaUri: String?,
    val createdAt: Long,
    val lastUpdated: Long,
    val syncVersion: Long = 0
)

@Serializable
data class ContentUploadResponse(
    val id: String,
    val serverVersion: Long,
    val uploadedAt: Long
)

@Serializable
data class ContentUpdateRequest(
    val content: String? = null,
    val mediaUri: String? = null,
    val lastUpdated: Long,
    val syncVersion: Long = 0
)

@Serializable
data class ContentUpdateResponse(
    val id: String,
    val serverVersion: Long,
    val updatedAt: Long
)

@Serializable
data class ContentChangesResponse(
    val changes: List<ContentChange>,
    val deletions: List<ContentDeletion>,
    val lastTimestamp: Long
)

@Serializable
data class ContentChange(
    val id: String,
    val type: String,
    val content: String? = null,
    val mediaUri: String? = null,
    val createdAt: Long,
    val lastUpdated: Long,
    val serverVersion: Long,
    val isDeleted: Boolean = false
)

@Serializable
data class ContentDeletion(
    val id: String,
    val deletedAt: Long
)

@Serializable
data class JournalUploadRequest(
    val id: String,
    val title: String,
    val description: String,
    val createdAt: Long,
    val lastUpdated: Long,
    val syncVersion: Long = 0
)

@Serializable
data class JournalUploadResponse(
    val id: String,
    val serverVersion: Long,
    val uploadedAt: Long
)

@Serializable
data class JournalUpdateRequest(
    val title: String? = null,
    val description: String? = null,
    val lastUpdated: Long,
    val syncVersion: Long = 0
)

@Serializable
data class JournalUpdateResponse(
    val id: String,
    val serverVersion: Long,
    val updatedAt: Long
)

@Serializable
data class JournalChangesResponse(
    val changes: List<JournalChange>,
    val deletions: List<JournalDeletion>,
    val lastTimestamp: Long
)

@Serializable
data class JournalChange(
    val id: String,
    val title: String,
    val description: String,
    val createdAt: Long,
    val lastUpdated: Long,
    val serverVersion: Long,
    val isDeleted: Boolean = false
)

@Serializable
data class JournalDeletion(
    val id: String,
    val deletedAt: Long
)

@Serializable
data class AssociationUploadRequest(
    val associations: List<Association>
)

@Serializable
data class Association(
    val journalId: String,
    val contentId: String,
    val createdAt: Long,
    val syncVersion: Long = 0
) {
    fun key(): Pair<String, String> = journalId to contentId
}

@Serializable
data class AssociationUploadResponse(
    val uploadedCount: Int,
    val uploadedAt: Long
)

@Serializable
data class AssociationChangesResponse(
    val changes: List<AssociationChange>,
    val deletions: List<AssociationDeletion>,
    val lastTimestamp: Long
)

@Serializable
data class AssociationChange(
    val journalId: String,
    val contentId: String,
    val createdAt: Long,
    val serverVersion: Long,
    val isDeleted: Boolean = false
)

@Serializable
data class AssociationDeletion(
    val journalId: String,
    val contentId: String,
    val deletedAt: Long
)

@Serializable
data class AssociationDeleteRequest(
    val associations: List<AssociationDeleteItem>
)

@Serializable
data class AssociationDeleteItem(
    val journalId: String,
    val contentId: String
) {
    fun key(): Pair<String, String> = journalId to contentId
}

@Serializable
data class MediaUploadRequest(
    val contentId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val data: ByteArray
)

@Serializable
data class MediaUploadResponse(
    val contentId: String,
    val mediaId: String,
    val downloadUrl: String,
    val uploadedAt: Long
)

@Serializable
data class MediaDownloadResponse(
    val contentId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val data: ByteArray,
    val downloadUrl: String
)
