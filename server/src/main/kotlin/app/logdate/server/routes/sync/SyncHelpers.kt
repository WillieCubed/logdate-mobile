package app.logdate.server.routes.sync

import app.logdate.server.audit.AuditCategory
import app.logdate.server.audit.AuditLogger
import app.logdate.server.auth.TokenService
import app.logdate.server.entitlements.QuotaCheck
import app.logdate.server.entitlements.QuotaReason
import app.logdate.server.logdate.LogDateAssociation
import app.logdate.server.logdate.LogDateBackup
import app.logdate.server.logdate.LogDateBlobStorage
import app.logdate.server.logdate.LogDateEntry
import app.logdate.server.logdate.LogDateJournal
import app.logdate.server.logdate.LogDateMedia
import app.logdate.server.ratelimit.RateLimitPolicy
import app.logdate.server.ratelimit.SlidingWindowRateLimiter
import app.logdate.server.responses.error
import app.logdate.server.sync.MediaAccessPolicy
import app.logdate.server.sync.SyncMetricsSnapshot
import app.logdate.shared.model.sync.AssociationChange
import app.logdate.shared.model.sync.ContentChange
import app.logdate.shared.model.sync.JournalChange
import io.github.aakira.napier.Napier
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import java.util.UUID

// --- Pagination + metric names ------------------------------------------------------------------

internal const val DEFAULT_SYNC_PAGE_SIZE = 200
internal const val MAX_SYNC_PAGE_SIZE = 500
internal const val MILLIS_PER_DAY = 86_400_000L

internal const val METRIC_SYNC_STATUS = "sync.status"
internal const val METRIC_SYNC_METRICS = "sync.metrics"
internal const val METRIC_SYNC_METRICS_PROM = "sync.metrics.prometheus"
internal const val METRIC_CONTENT_UPLOAD = "sync.content.upload"
internal const val METRIC_CONTENT_CHANGES = "sync.content.changes"
internal const val METRIC_CONTENT_UPDATE = "sync.content.update"
internal const val METRIC_CONTENT_DELETE = "sync.content.delete"
internal const val METRIC_JOURNAL_UPLOAD = "sync.journal.upload"
internal const val METRIC_JOURNAL_CHANGES = "sync.journal.changes"
internal const val METRIC_JOURNAL_UPDATE = "sync.journal.update"
internal const val METRIC_JOURNAL_DELETE = "sync.journal.delete"
internal const val METRIC_ASSOCIATION_UPLOAD = "sync.association.upload"
internal const val METRIC_ASSOCIATION_CHANGES = "sync.association.changes"
internal const val METRIC_ASSOCIATION_DELETE = "sync.association.delete"
internal const val METRIC_MEDIA_UPLOAD = "sync.media.upload"
internal const val METRIC_MEDIA_DOWNLOAD = "sync.media.download"
internal const val METRIC_MEDIA_DELETE = "sync.media.delete"
internal const val METRIC_SYNC_PURGE = "sync.maintenance.purge"

// --- Per-user write-path rate-limit policies ----------------------------------------------------

/**
 * Per-user ceilings on the write paths. Tuned to let an actively-syncing device churn through its
 * pending upload queue (which typically runs a dozen or two items in a burst) without bothering a
 * well-behaved client, while still throttling a runaway loop or a compromised token.
 */
internal val MEDIA_UPLOAD_RATE_LIMIT = RateLimitPolicy(maxRequests = 120, windowSeconds = 60)
internal val BACKUP_UPLOAD_RATE_LIMIT = RateLimitPolicy(maxRequests = 6, windowSeconds = 60 * 60)

// --- Multipart parsing --------------------------------------------------------------------------

internal data class ParsedMediaMultipartUpload(
    val contentId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val data: ByteArray,
    val deviceId: String,
)

internal data class ParsedBackupMultipartUpload(
    val deviceId: String,
    val manifest: String,
    val data: ByteArray,
)

internal suspend fun ApplicationCall.respondMissingMultipartField(field: String) {
    respond(
        HttpStatusCode.BadRequest,
        error("VALIDATION_ERROR", "Missing required multipart field: $field"),
    )
}

internal suspend fun ApplicationCall.receiveMediaMultipartUpload(): ParsedMediaMultipartUpload? {
    val multipart =
        runCatching { receiveMultipart() }.getOrElse {
            respond(
                HttpStatusCode.BadRequest,
                error("VALIDATION_ERROR", "Expected multipart/form-data body"),
            )
            return null
        }

    var contentId: String? = null
    var fileName: String? = null
    var mimeType: String? = null
    var sizeBytes: Long? = null
    var deviceId: String? = null
    var data: ByteArray? = null

    multipart.forEachPart { part ->
        when (part) {
            is PartData.FormItem -> {
                when (part.name) {
                    "contentId" -> contentId = part.value.trim()
                    "fileName" -> fileName = part.value.trim()
                    "mimeType" -> mimeType = part.value.trim()
                    "sizeBytes" -> sizeBytes = part.value.trim().toLongOrNull()
                    "deviceId" -> deviceId = part.value.trim()
                }
            }

            is PartData.FileItem -> {
                if (part.name == "data") {
                    data = part.provider().readRemaining().readByteArray()
                }
            }

            else -> Unit
        }
        part.dispose()
    }

    val requiredContentId =
        contentId?.takeIf { it.isNotBlank() } ?: run {
            respondMissingMultipartField("contentId")
            return null
        }
    val requiredFileName =
        fileName?.takeIf { it.isNotBlank() } ?: run {
            respondMissingMultipartField("fileName")
            return null
        }
    val requiredMimeType =
        mimeType?.takeIf { it.isNotBlank() } ?: run {
            respondMissingMultipartField("mimeType")
            return null
        }
    val requiredSizeBytes =
        sizeBytes ?: run {
            respondMissingMultipartField("sizeBytes")
            return null
        }
    val requiredDeviceId =
        deviceId?.takeIf { it.isNotBlank() } ?: run {
            respondMissingMultipartField("deviceId")
            return null
        }
    val requiredData =
        data ?: run {
            respondMissingMultipartField("data")
            return null
        }

    if (requiredSizeBytes <= 0) {
        respond(HttpStatusCode.BadRequest, error("VALIDATION_ERROR", "sizeBytes must be greater than 0"))
        return null
    }
    if (requiredSizeBytes != requiredData.size.toLong()) {
        respond(
            HttpStatusCode.BadRequest,
            error("VALIDATION_ERROR", "sizeBytes does not match uploaded binary payload size"),
        )
        return null
    }

    return ParsedMediaMultipartUpload(
        contentId = requiredContentId,
        fileName = requiredFileName,
        mimeType = requiredMimeType,
        sizeBytes = requiredSizeBytes,
        data = requiredData,
        deviceId = requiredDeviceId,
    )
}

internal suspend fun ApplicationCall.receiveBackupMultipartUpload(): ParsedBackupMultipartUpload? {
    val multipart =
        runCatching { receiveMultipart() }.getOrElse {
            respond(
                HttpStatusCode.BadRequest,
                error("VALIDATION_ERROR", "Expected multipart/form-data body"),
            )
            return null
        }

    var deviceId: String? = null
    var manifest: String? = null
    var data: ByteArray? = null

    multipart.forEachPart { part ->
        when (part) {
            is PartData.FormItem -> {
                when (part.name) {
                    "deviceId" -> deviceId = part.value.trim()
                    "manifest" -> manifest = part.value
                }
            }

            is PartData.FileItem -> {
                if (part.name == "data") {
                    data = part.provider().readRemaining().readByteArray()
                }
            }

            else -> Unit
        }
        part.dispose()
    }

    val requiredDeviceId =
        deviceId?.takeIf { it.isNotBlank() } ?: run {
            respondMissingMultipartField("deviceId")
            return null
        }
    val requiredManifest =
        manifest?.takeIf { it.isNotBlank() } ?: run {
            respondMissingMultipartField("manifest")
            return null
        }
    val requiredData =
        data ?: run {
            respondMissingMultipartField("data")
            return null
        }
    if (requiredData.isEmpty()) {
        respond(HttpStatusCode.BadRequest, error("VALIDATION_ERROR", "Backup payload must not be empty"))
        return null
    }

    return ParsedBackupMultipartUpload(
        deviceId = requiredDeviceId,
        manifest = requiredManifest,
        data = requiredData,
    )
}

// --- Auth helpers -------------------------------------------------------------------------------

/**
 * Extracts and validates the user ID from the Authorization header. Returns null and responds with
 * 401/500 if authentication fails, so callers just early-return.
 */
internal suspend fun extractUserId(
    call: ApplicationCall,
    tokenService: TokenService?,
): UUID? {
    if (tokenService == null) {
        call.respond(
            HttpStatusCode.InternalServerError,
            error("SERVER_MISCONFIGURED", "Token service is not configured"),
        )
        return null
    }

    val authHeader = call.request.header(HttpHeaders.Authorization)
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        call.respond(
            HttpStatusCode.Unauthorized,
            error("UNAUTHORIZED", "Missing or invalid Authorization header"),
        )
        return null
    }

    val token = authHeader.removePrefix("Bearer ").trim()
    val accountId = tokenService.validateAccessToken(token)
    if (accountId == null) {
        call.respond(
            HttpStatusCode.Unauthorized,
            error("UNAUTHORIZED", "Invalid or expired token"),
        )
        return null
    }

    return try {
        UUID.fromString(accountId)
    } catch (e: IllegalArgumentException) {
        Napier.e("Invalid account ID format in token: $accountId", e)
        call.respond(
            HttpStatusCode.Unauthorized,
            error("UNAUTHORIZED", "Invalid token payload"),
        )
        null
    }
}

internal fun ApplicationCall.requiredPathParam(name: String): String =
    requireNotNull(parameters[name]) { "Missing required route parameter: $name" }

// --- Wire-shape mappers --------------------------------------------------------------------------

internal fun LogDateEntry.toContentChange(): ContentChange =
    ContentChange(
        id = id,
        type = type,
        content = content,
        mediaUri = mediaUri,
        durationMs = durationMs ?: 0L,
        createdAt = createdAt,
        lastUpdated = lastUpdated,
        serverVersion = version,
        isDeleted = false,
    )

internal fun LogDateJournal.toJournalChange(): JournalChange =
    JournalChange(
        id = id,
        title = title,
        description = description,
        createdAt = createdAt,
        lastUpdated = lastUpdated,
        serverVersion = version,
        isDeleted = false,
    )

internal fun LogDateAssociation.toAssociationChange(): AssociationChange =
    AssociationChange(
        journalId = journalId,
        contentId = entryId,
        createdAt = createdAt,
        serverVersion = version,
        isDeleted = false,
    )

// --- Download URL resolution ---------------------------------------------------------------------

internal fun buildMediaDownloadUrl(
    call: ApplicationCall,
    mediaId: String,
): String = buildBinaryDownloadUrl(call, "/api/v1/media/$mediaId/binary")

internal fun buildBackupDownloadUrl(
    call: ApplicationCall,
    backupId: String,
): String = buildBinaryDownloadUrl(call, "/api/v1/backups/$backupId/binary")

private fun buildBinaryDownloadUrl(call: ApplicationCall, path: String): String {
    val origin = call.request.local
    val defaultPort =
        (origin.scheme == "http" && origin.localPort == 80) ||
            (origin.scheme == "https" && origin.localPort == 443)
    val portPart = if (defaultPort) "" else ":${origin.localPort}"
    return "${origin.scheme}://${origin.localHost}$portPart$path"
}

internal fun resolveMediaDownloadUrl(
    call: ApplicationCall,
    record: LogDateMedia,
    mediaStorage: LogDateBlobStorage?,
    accessPolicy: MediaAccessPolicy,
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

internal fun resolveBackupDownloadUrl(
    call: ApplicationCall,
    record: LogDateBackup,
    mediaStorage: LogDateBlobStorage?,
    accessPolicy: MediaAccessPolicy,
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

// --- Prometheus export ---------------------------------------------------------------------------

internal fun SyncMetricsSnapshot.toPrometheus(): String {
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

private fun escapeLabelValue(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

// --- Error-response shapes for quota and rate-limit violations -----------------------------------

internal suspend fun respondRateLimited(
    call: ApplicationCall,
    rateLimiter: SlidingWindowRateLimiter,
    key: String,
    policy: RateLimitPolicy,
) {
    val retryAfter = rateLimiter.retryAfterSeconds(key, policy)
    AuditLogger.emit(
        AuditCategory.SYNC_RATE_LIMITED,
        mapOf(
            "key" to key,
            "retryAfterSeconds" to retryAfter.toString(),
            "maxRequests" to policy.maxRequests.toString(),
            "windowSeconds" to policy.windowSeconds.toString(),
        ),
    )
    call.response.headers.append(HttpHeaders.RetryAfter, retryAfter.toString())
    call.respond(
        HttpStatusCode.TooManyRequests,
        error(
            code = "RATE_LIMIT_EXCEEDED",
            message = "Too many uploads. Try again in $retryAfter seconds.",
            details = mapOf("retryAfterSeconds" to retryAfter.toString()),
        ),
    )
}

/**
 * Structured 402 Payment Required. `reason` is one of the [QuotaReason] enum values and
 * `limit`/`current` give the user-facing numbers so the client can render a specific "X of Y"
 * message without parsing the free-form body.
 */
internal suspend fun respondQuotaExceeded(
    call: ApplicationCall,
    denied: QuotaCheck.Denied,
) {
    val reasonMessage =
        when (denied.reason) {
            QuotaReason.STORAGE_BYTES ->
                "Storage quota exceeded: ${denied.current} of ${denied.limit} bytes in use."
            QuotaReason.BACKUP_COUNT ->
                "Backup quota exceeded: ${denied.current} of ${denied.limit} backups stored."
        }
    AuditLogger.emit(
        AuditCategory.SYNC_QUOTA_EXCEEDED,
        mapOf(
            "reason" to denied.reason.name,
            "limit" to denied.limit.toString(),
            "current" to denied.current.toString(),
        ),
    )
    call.respond(
        HttpStatusCode.PaymentRequired,
        error(
            code = "QUOTA_EXCEEDED",
            message = reasonMessage,
            details =
                mapOf(
                    "reason" to denied.reason.name,
                    "limit" to denied.limit.toString(),
                    "current" to denied.current.toString(),
                ),
        ),
    )
}
