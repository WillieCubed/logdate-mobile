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
        "Database repositories initialized successfully" to "SERVER_DATABASE_READY",
        "Production database unavailable; refusing to start with in-memory fallback" to "SERVER_DATABASE_REQUIRED",
        "Database unavailable; refusing to start somewhere nothing would persist" to "SERVER_DATABASE_REQUIRED",
    )

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
