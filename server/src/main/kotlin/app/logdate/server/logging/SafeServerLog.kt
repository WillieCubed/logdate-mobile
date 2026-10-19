package app.logdate.server.logging

import app.logdate.shared.model.diagnostics.DiagnosticContext
import app.logdate.shared.model.diagnostics.DiagnosticPlatform
import app.logdate.shared.model.diagnostics.DiagnosticProtocol
import app.logdate.shared.model.diagnostics.DiagnosticReportCodec
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import app.logdate.shared.model.diagnostics.diagnosticCode
import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val SAFE_SERVER_EVENT_TAG = "ServerDiagnostics"

private val serverDiagnosticContext =
    DiagnosticContext(
        platform = DiagnosticPlatform.SERVER,
        protocols = DiagnosticProtocol.entries.toList(),
        serverBuild = System.getenv("LOGDATE_SERVER_BUILD_ID")?.takeIf { Regex("[0-9a-f]{7,64}").matches(it) },
    )

internal fun recordServerDiagnostic(event: SyncDiagnosticEvent) {
    runCatching {
        DiagnosticReportCodec.validateEvent(event)
        val safe = event.copy(code = diagnosticCode(event), context = serverDiagnosticContext)
        Napier.i(tag = SAFE_SERVER_EVENT_TAG, message = Json.encodeToString(safe))
    }
}

private val approvedNotices =
    mapOf(
        "Database not available, using in-memory repositories" to "SERVER_DATABASE_IN_MEMORY",
        (
            "LOGDATE_ALLOW_INMEMORY_FALLBACK is set: running on in-memory repositories. Nothing is " +
                "persisted and every record is lost when the process exits."
        ) to "SERVER_DATABASE_IN_MEMORY",
        "Account blob cleanup queue overdue" to "SERVER_ACCOUNT_BLOB_CLEANUP_OVERDUE",
        "Account blob cleanup failed" to "SERVER_ACCOUNT_BLOB_CLEANUP_FAILED",
        "Account blob cleanup batch complete" to "SERVER_ACCOUNT_BLOB_CLEANUP_COMPLETE",
        "Database repositories initialized successfully" to "SERVER_DATABASE_READY",
        "Production database unavailable; refusing to start with in-memory fallback" to "SERVER_DATABASE_REQUIRED",
        "Database unavailable; refusing to start somewhere nothing would persist" to "SERVER_DATABASE_REQUIRED",
        KeyCustodyNotices.SIGNING_KEYS_REWRAPPED to "SERVER_SIGNING_KEYS_REWRAPPED",
        KeyCustodyNotices.SIGNING_KEYS_CURRENT to "SERVER_SIGNING_KEYS_CURRENT",
        KeyCustodyNotices.SIGNING_KEYS_UNREADABLE to "SERVER_SIGNING_KEYS_UNREADABLE",
        KeyCustodyNotices.SIGNING_KEYS_REWRAP_FAILED to "SERVER_SIGNING_KEYS_REWRAP_FAILED",
        KeyCustodyNotices.OAUTH_KEY_STAGED to "SERVER_OAUTH_KEY_STAGED",
        KeyCustodyNotices.OAUTH_KEY_PROMOTED to "SERVER_OAUTH_KEY_PROMOTED",
        KeyCustodyNotices.OAUTH_KEY_RETIRED to "SERVER_OAUTH_KEY_RETIRED",
        KeyCustodyNotices.OAUTH_KEY_ROTATION_FAILED to "SERVER_OAUTH_KEY_ROTATION_FAILED",
        DownloadUrlSigningNotices.MEDIA_SIGNING_FAILED to "SERVER_MEDIA_URL_SIGNING_FAILED",
        DownloadUrlSigningNotices.BACKUP_SIGNING_FAILED to "SERVER_BACKUP_URL_SIGNING_FAILED",
        SpacePurgeNotices.BLOBS_PURGED to "SERVER_SPACE_BLOBS_PURGED",
        SpacePurgeNotices.BLOB_DELETE_FAILED to "SERVER_SPACE_BLOB_DELETE_FAILED",
        SpacePurgeNotices.PURGE_FAILED to "SERVER_SPACE_PURGE_FAILED",
    )

/**
 * Fixed log messages for signing-key custody. Production logs keep only approved messages, so these
 * outcomes stay visible there as SERVER_SIGNING_KEYS_* and SERVER_OAUTH_KEY_* codes.
 */
internal object KeyCustodyNotices {
    const val SIGNING_KEYS_REWRAPPED = "Re-encrypted stored signing keys under the current key-encryption key"
    const val SIGNING_KEYS_CURRENT = "Every stored signing key already uses the current key-encryption key"
    const val SIGNING_KEYS_UNREADABLE = "Some stored signing keys can't be decrypted with either key-encryption key"
    const val SIGNING_KEYS_REWRAP_FAILED = "Re-encrypting stored signing keys failed"
    const val OAUTH_KEY_STAGED = "Staged the next OAuth signing key"
    const val OAUTH_KEY_PROMOTED = "Promoted the next OAuth signing key"
    const val OAUTH_KEY_RETIRED = "Deleted the retired OAuth signing key"
    const val OAUTH_KEY_ROTATION_FAILED = "OAuth signing key rotation check failed"
}

/**
 * Fixed log messages for when signing a direct storage download URL fails and the response falls
 * back to the server's own binary endpoint. Downloads keep working, but every one is proxied
 * through the server. On Cloud Run the usual cause is that the runtime service account isn't
 * allowed to sign as itself (`iam.serviceAccounts.signBlob`).
 */
internal object DownloadUrlSigningNotices {
    const val MEDIA_SIGNING_FAILED = "Signing a media download URL failed; serving it through the server instead"
    const val BACKUP_SIGNING_FAILED = "Signing a backup download URL failed; serving it through the server instead"
}

/**
 * Fixed log messages for the space maintenance sweep, which deletes old tombstones and blobs that no
 * record refers to. A blob's row is deleted only after its object is, so a failed object delete
 * leaves both in place and the next sweep tries that blob again. Both failures are errors: a blob
 * that keeps failing keeps its bytes in storage.
 */
internal object SpacePurgeNotices {
    const val BLOBS_PURGED = "Purged space blobs that nothing refers to"
    const val BLOB_DELETE_FAILED = "Deleting a space blob from storage failed; the next sweep retries it"
    const val PURGE_FAILED = "Purging space tombstones and blobs failed; the next sweep retries"
}

internal fun safeServerMessage(
    priority: LogLevel,
    tag: String?,
    message: String?,
): String {
    if (tag == SAFE_SERVER_EVENT_TAG) validatedEvent(message)?.let { return it }
    approvedNotices[message]?.let { return it }
    return when (priority) {
        LogLevel.ERROR, LogLevel.ASSERT -> "SERVER_ERROR"
        LogLevel.WARNING -> "SERVER_WARNING"
        else -> "SERVER_NOTICE"
    }
}

private fun validatedEvent(message: String?): String? {
    if (message == null || message.length > 4096) return null
    return runCatching {
        val event = Json.decodeFromString<SyncDiagnosticEvent>(message)
        DiagnosticReportCodec.validateEvent(event)
        Json.encodeToString(event)
    }.getOrNull()
}

/** Includes framework/library logs in the same stdout privacy boundary. */
class SafeServerMessageConverter : ClassicConverter() {
    override fun convert(event: ILoggingEvent): String {
        if (event.loggerName == SERVER_LOG_NAME) {
            val text = event.formattedMessage
            if (text in approvedNotices.values || text in setOf("SERVER_ERROR", "SERVER_WARNING", "SERVER_NOTICE")) return text
            validatedEvent(text)?.let { return it }
        }
        return "SERVER_LIBRARY_EVENT"
    }
}
