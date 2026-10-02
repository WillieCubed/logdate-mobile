package app.logdate.client.sync.diagnostics

import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.config.PrivacyScopeEpoch
import app.logdate.shared.model.diagnostics.DiagnosticReportCodec
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import app.logdate.shared.model.diagnostics.SyncDiagnosticReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DiagnosticConsent internal constructor(
    val destination: String,
    internal val scope: UploadScope,
    internal val epoch: String,
)

data class DiagnosticReportingSettings(
    val destination: String? = null,
    val available: Boolean = false,
    val enabled: Boolean = false,
    val pendingReports: Int = 0,
    val storageFailed: Boolean = false,
)

class DiagnosticReportingController internal constructor(
    private val storage: DiagnosticStorage,
    private val privacyEpoch: PrivacyScopeEpoch,
    private val workerScope: CoroutineScope,
    private val currentScope: () -> UploadScope?,
    private val supported: (UploadScope) -> Boolean,
    private val nowMillis: () -> Long,
    private val ready: suspend () -> Unit,
    private val send: suspend (UploadScope, SyncDiagnosticReport) -> DiagnosticDelivery,
    private val deleteUploaded: suspend (UploadScope) -> Boolean,
) {
    private val settings = MutableStateFlow(DiagnosticReportingSettings())
    val state: StateFlow<DiagnosticReportingSettings> = settings
    private val reporter = DiagnosticReporting(storage, currentScope, supported, nowMillis, { privacyEpoch.value.value }, send)

    private data class Accepted(
        val event: SyncDiagnosticEvent,
        val source: DiagnosticSource,
        val admission: String,
    )

    private val queue = Channel<Accepted>(64)

    init {
        workerScope
            .launch {
                refresh()
                launch {
                    for (accepted in queue) {
                        safely {
                            reporter.captureAdmitted(accepted.event, accepted.source.scope, accepted.admission)
                            reporter.deliverOnce()
                            updateState()
                        }
                    }
                }
                launch {
                    privacyEpoch.value.drop(1).collect {
                        safely {
                            reporter.refreshScope()
                            updateState()
                        }
                    }
                }
                while (true) {
                    delay(30_000)
                    refresh()
                    safely {
                        reporter.deliverOnce()
                        updateState()
                    }
                }
            }.invokeOnCompletion { queue.close() }
    }

    suspend fun refresh() {
        safely {
            ready()
            privacyEpoch.initialize()
            // A not-yet-restored session is not evidence of an account change.
            updateState()
        }
    }

    suspend fun prepareConsent(): DiagnosticConsent? {
        refresh()
        val selected = currentScope() ?: return null
        val epoch = privacyEpoch.value.value ?: return null
        if (!supported(selected) || settings.value.storageFailed) return null
        return DiagnosticConsent(selected.serverOrigin, selected, epoch)
    }

    suspend fun enable(consent: DiagnosticConsent): Boolean {
        if (currentScope() != consent.scope || privacyEpoch.value.value != consent.epoch || !supported(consent.scope)) {
            refresh()
            return false
        }
        return safely {
            privacyEpoch.transition(false) {
                check(currentScope() == consent.scope && privacyEpoch.value.value == consent.epoch && supported(consent.scope))
                reporter.enable(consent.scope)
                updateState()
            }
        }
    }

    suspend fun disable(): Boolean =
        safely {
            privacyEpoch.transition(true) { reporter.disable() }
            while (queue.tryReceive().isSuccess) { /* Unsent accepted events share the revoked grant. */ }
            updateState()
        }

    suspend fun deleteUploadedReports(consent: DiagnosticConsent): Boolean {
        val selected = currentScope() ?: return false
        if (selected != consent.scope || privacyEpoch.value.value != consent.epoch) return false
        if (!supported(selected)) return false
        return try {
            withContext(SuppressDiagnosticReporting) { deleteUploaded(selected) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    fun record(
        event: SyncDiagnosticEvent,
        source: DiagnosticSource?,
    ) {
        if (source == null || source.epoch == null || source.epoch != privacyEpoch.value.value) return
        if (runCatching { DiagnosticReportCodec.validateEvent(event) }.isFailure) return
        val admission = reporter.admission(source.scope) ?: return
        queue.trySend(Accepted(event, source, admission))
    }

    private suspend fun updateState() {
        val selected = currentScope()
        if (selected == null) {
            settings.value = DiagnosticReportingSettings()
            return
        }
        val status = reporter.status()
        settings.value = DiagnosticReportingSettings(selected.serverOrigin, supported(selected), status.enabled, status.pendingCount)
    }

    private suspend fun safely(action: suspend () -> Unit): Boolean =
        try {
            action()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            settings.value = settings.value.copy(storageFailed = true)
            false
        }
}
