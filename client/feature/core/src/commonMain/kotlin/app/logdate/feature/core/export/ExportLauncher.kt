package app.logdate.feature.core.export

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

/**
 * Interface for launching data export operations.
 */
interface ExportLauncher {
    /**
     * Starts the data export process with the given options.
     */
    fun startExport(options: ExportOptions = ExportOptions())

    /**
     * Cancels any ongoing export operation.
     */
    fun cancelExport()

    /**
     * Sets a callback to be notified of export completion with a path.
     *
     * @param callback Function that receives the export path or null if export failed/was cancelled
     */
    fun setExportCompletionCallback(callback: (String?) -> Unit)

    /**
     * Updates the export progress directly. Called by platform-specific workers
     * to bypass WorkManager's rate-limited setProgress() delivery.
     */
    fun updateProgress(info: ExportProgressInfo)

    /**
     * Observable progress stream for the current export operation.
     */
    val exportProgress: StateFlow<ExportProgressInfo>
}

/**
 * Progress information for an active export operation.
 */
data class ExportProgressInfo(
    val isActive: Boolean = false,
    val progressPercent: Int = 0,
    val message: String = "",
)

/**
 * Options controlling what data to include in the export.
 */
data class ExportOptions(
    val includeJournals: Boolean = true,
    val includeNotes: Boolean = true,
    val includeDrafts: Boolean = true,
    val includeMedia: Boolean = true,
    val dateRange: ExportDateRange = ExportDateRange.AllTime,
)

/**
 * Date range filter for export operations.
 */
sealed class ExportDateRange {
    data object AllTime : ExportDateRange()

    data object Last30Days : ExportDateRange()

    data object Last90Days : ExportDateRange()

    data object LastYear : ExportDateRange()

    data class Custom(
        val start: Instant,
        val end: Instant,
    ) : ExportDateRange()
}
