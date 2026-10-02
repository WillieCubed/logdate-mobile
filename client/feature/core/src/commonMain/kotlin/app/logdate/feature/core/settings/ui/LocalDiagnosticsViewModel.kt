package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.sync.diagnostics.DiagnosticReportBundle
import app.logdate.client.sync.diagnostics.DiagnosticReportBundles
import app.logdate.client.sync.diagnostics.SyncDiagnosticRecorder
import app.logdate.client.sync.diagnostics.VerboseDiagnosticMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface DiagnosticArchiveExporter {
    suspend fun export(bundle: DiagnosticReportBundle): Boolean
}

data class LocalDiagnosticsState(
    val preview: String? = null,
    val eventCount: Int = 0,
    val verboseRemainingMillis: Long = 0L,
    val feedback: LocalDiagnosticsFeedback? = null,
)

enum class LocalDiagnosticsFeedback { EXPORTED, EXPORT_FAILED, CLEARED, CLEAR_FAILED }

class LocalDiagnosticsViewModel(
    private val recorder: SyncDiagnosticRecorder,
    private val verboseMode: VerboseDiagnosticMode,
    private val exporter: DiagnosticArchiveExporter,
) : ViewModel() {
    private val _state = MutableStateFlow(LocalDiagnosticsState())
    val state: StateFlow<LocalDiagnosticsState> = _state
    private var prepared: DiagnosticReportBundle? = null

    fun refresh() {
        viewModelScope.launch {
            try {
                val report = recorder.report()
                prepared = DiagnosticReportBundles.prepare(report)
                _state.value =
                    LocalDiagnosticsState(
                        preview = prepared?.summary,
                        eventCount = report.events.size,
                        verboseRemainingMillis = verboseMode.remainingMillis(),
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                prepared = null
                _state.value = LocalDiagnosticsState(feedback = LocalDiagnosticsFeedback.EXPORT_FAILED)
            }
        }
    }

    fun export() {
        viewModelScope.launch {
            try {
                val bundle = prepared ?: DiagnosticReportBundles.prepare(recorder.report()).also { prepared = it }
                _state.value = _state.value.copy(preview = bundle.summary)
                val exported = exporter.export(bundle)
                _state.value =
                    _state.value.copy(
                        feedback =
                            if (exported) LocalDiagnosticsFeedback.EXPORTED else LocalDiagnosticsFeedback.EXPORT_FAILED,
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(feedback = LocalDiagnosticsFeedback.EXPORT_FAILED)
            }
        }
    }

    fun clear() {
        viewModelScope.launch {
            try {
                recorder.clear()
                prepared = null
                _state.value =
                    LocalDiagnosticsState(
                        verboseRemainingMillis = verboseMode.remainingMillis(),
                        feedback = LocalDiagnosticsFeedback.CLEARED,
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(feedback = LocalDiagnosticsFeedback.CLEAR_FAILED)
            }
        }
    }

    fun setVerboseEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                if (enabled) verboseMode.enable() else verboseMode.disable()
                _state.value = _state.value.copy(verboseRemainingMillis = verboseMode.remainingMillis())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(feedback = LocalDiagnosticsFeedback.CLEAR_FAILED)
            }
        }
    }
}
