package app.logdate.feature.core.restore

import app.logdate.client.sync.diagnostics.DiagnosticSource
import app.logdate.client.sync.diagnostics.DiagnosticSourceProvider
import app.logdate.client.sync.diagnostics.SyncDiagnosticRecorder
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent

/** Local restore owns completion; the cloud download worker only reports its handoff. */
interface RestoreDiagnosticEvents {
    fun source(): DiagnosticSource?

    fun record(
        event: SyncDiagnosticEvent,
        source: DiagnosticSource?,
    )
}

class DefaultRestoreDiagnosticEvents(
    private val sources: DiagnosticSourceProvider,
    private val recorder: SyncDiagnosticRecorder,
) : RestoreDiagnosticEvents {
    override fun source(): DiagnosticSource? = sources.current()

    override fun record(
        event: SyncDiagnosticEvent,
        source: DiagnosticSource?,
    ) {
        recorder.record(event, source?.scope, source?.epoch)
    }
}
