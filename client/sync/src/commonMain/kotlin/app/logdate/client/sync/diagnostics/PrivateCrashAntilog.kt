package app.logdate.client.sync.diagnostics

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel

/** Crash collection never receives free-form application logs or exception objects. */
class PrivateCrashAntilog(
    private val breadcrumb: (String) -> Unit,
) : Antilog() {
    override fun performLog(
        priority: LogLevel,
        tag: String?,
        throwable: Throwable?,
        message: String?,
    ) {
        if (tag == SyncDiagnosticRecorder.TAG || priority < LogLevel.WARNING) return
        val category = if (priority >= LogLevel.ERROR) "APP_ERROR" else "APP_WARNING"
        runCatching { breadcrumb(category) }
    }
}
