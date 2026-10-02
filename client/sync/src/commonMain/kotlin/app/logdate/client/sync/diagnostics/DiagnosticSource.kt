package app.logdate.client.sync.diagnostics

import app.logdate.client.sync.metadata.UploadScope
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Request provenance is operational context only, excluded from every serialized report. */
class DiagnosticSource(
    val scope: UploadScope,
    val epoch: String?,
)

internal object SuppressDiagnosticReporting : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<SuppressDiagnosticReporting>
}

class DiagnosticSourceProvider(
    private val sessions: app.logdate.client.datastore.SessionStorage,
    private val privacyEpoch: app.logdate.shared.config.PrivacyScopeEpoch,
) {
    fun current(): DiagnosticSource? {
        val bound = sessions.getOriginBoundSession() ?: return null
        return DiagnosticSource(UploadScope(bound.session.accountId, bound.origin), privacyEpoch.value.value)
    }
}

/** Correlation contains only random references and follows the coroutine across nested requests. */
internal class DiagnosticCorrelation(
    val runId: String,
    val operationId: String? = null,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<DiagnosticCorrelation>
}
