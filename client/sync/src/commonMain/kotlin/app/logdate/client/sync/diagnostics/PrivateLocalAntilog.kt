package app.logdate.client.sync.diagnostics

import app.logdate.shared.model.diagnostics.DiagnosticReportCodec
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Platform log sinks receive only validated diagnostic events or finite application codes. */
class PrivateLocalAntilog(
    private val sink: (LogLevel, String) -> Unit,
) : Antilog() {
    override fun performLog(
        priority: LogLevel,
        tag: String?,
        throwable: Throwable?,
        message: String?,
    ) {
        val event =
            if (tag == SyncDiagnosticRecorder.TAG && message != null && message.length <= 4096) {
                runCatching {
                    val decoded = Json.decodeFromString<SyncDiagnosticEvent>(message)
                    DiagnosticReportCodec.validateEvent(decoded)
                    Json.encodeToString(decoded)
                }.getOrNull()
            } else {
                null
            }
        val safe =
            event ?: when (priority) {
                LogLevel.ERROR, LogLevel.ASSERT -> "APP_ERROR"
                LogLevel.WARNING -> "APP_WARNING"
                else -> "APP_NOTICE"
            }
        runCatching { sink(priority, safe) }
    }
}
