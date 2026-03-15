package app.logdate.feature.core.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.export.ExportCounts
import app.logdate.client.domain.export.ExportStats
import app.logdate.client.domain.export.GetExportCountsUseCase
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class ExportState {
    data object Idle : ExportState()

    data class Configuring(
        val options: ExportOptions = ExportOptions(),
        val counts: ExportCounts? = null,
    ) : ExportState()

    data object Selecting : ExportState()

    data class Exporting(
        val progressPercent: Int = 0,
        val message: String = "",
    ) : ExportState()

    data class Completed(
        val path: String,
        val fileName: String,
        val stats: ExportStats? = null,
    ) : ExportState()

    data class Failed(
        val reason: String,
        val canRetry: Boolean = true,
    ) : ExportState()
}

class ExportViewModel(
    private val exportLauncher: ExportLauncher,
    private val getExportCountsUseCase: GetExportCountsUseCase,
) : ViewModel() {
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _isSheetVisible = MutableStateFlow(false)
    val isSheetVisible: StateFlow<Boolean> = _isSheetVisible.asStateFlow()

    private var lastExportOptions: ExportOptions = ExportOptions()

    init {
        exportLauncher.setExportCompletionCallback { path ->
            if (path == null) {
                val current = _exportState.value
                if (current is ExportState.Exporting) {
                    _exportState.update { ExportState.Failed("Export was cancelled or failed") }
                    _isSheetVisible.value = true
                } else {
                    _exportState.update { ExportState.Idle }
                    _isSheetVisible.value = false
                }
            } else {
                val fileName = path.substringAfterLast("/").substringAfterLast(":")
                _exportState.update {
                    ExportState.Completed(
                        path = path,
                        fileName = fileName,
                    )
                }
                _isSheetVisible.value = true
            }
        }

        viewModelScope.launch {
            exportLauncher.exportProgress.collect { progressInfo ->
                if (progressInfo.isActive) {
                    _exportState.update { current ->
                        if (current !is ExportState.Completed) {
                            ExportState.Exporting(
                                progressPercent = progressInfo.progressPercent,
                                message = progressInfo.message,
                            )
                        } else {
                            current
                        }
                    }
                }
            }
        }
    }

    fun showExportOptions() {
        val current = _exportState.value
        // If an export is active or showing results, just re-show the sheet
        if (current is ExportState.Exporting ||
            current is ExportState.Selecting ||
            current is ExportState.Completed ||
            current is ExportState.Failed
        ) {
            _isSheetVisible.value = true
            return
        }
        _exportState.update { ExportState.Configuring() }
        _isSheetVisible.value = true
        viewModelScope.launch {
            runCatching { getExportCountsUseCase() }
                .onSuccess { counts ->
                    _exportState.update { current ->
                        if (current is ExportState.Configuring) {
                            current.copy(counts = counts)
                        } else {
                            current
                        }
                    }
                }.onFailure { error ->
                    Napier.e("Failed to load export counts", error)
                }
        }
    }

    fun updateExportOptions(options: ExportOptions) {
        _exportState.update { current ->
            if (current is ExportState.Configuring) {
                current.copy(options = options)
            } else {
                current
            }
        }
    }

    /**
     * Dismisses the bottom sheet. If an export is in progress, it continues
     * running in the background — only [cancelExport] stops it.
     */
    fun dismissSheet() {
        val current = _exportState.value
        _isSheetVisible.value = false
        // Only reset state for pre-export or post-export states
        when (current) {
            is ExportState.Configuring,
            is ExportState.Completed,
            is ExportState.Failed,
            -> _exportState.update { ExportState.Idle }
            // Exporting/Selecting: export continues in background, state unchanged
            else -> Unit
        }
    }

    fun confirmExport() {
        val current = _exportState.value
        if (current is ExportState.Configuring) {
            lastExportOptions = current.options
        }
        _exportState.update { ExportState.Selecting }
        exportLauncher.startExport(lastExportOptions)
    }

    /**
     * Explicitly cancels the running export. This is the only way to stop
     * an in-progress export — UI dismissal does not cancel it.
     */
    fun cancelExport() {
        val current = _exportState.value
        if (current is ExportState.Exporting || current is ExportState.Selecting) {
            exportLauncher.cancelExport()
            _exportState.update { ExportState.Idle }
            _isSheetVisible.value = false
        }
    }

    fun retryExport() {
        _exportState.update { ExportState.Selecting }
        _isSheetVisible.value = true
        exportLauncher.startExport(lastExportOptions)
    }
}
