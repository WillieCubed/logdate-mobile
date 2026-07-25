package app.logdate.feature.core.export

import app.logdate.client.domain.export.ExportError
import app.logdate.client.domain.export.ExportStage
import app.logdate.client.domain.export.ExportStats
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
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
     * Sets a callback notified when an export ends without success.
     *
     * Success is signaled separately via [ExportProgressInfo.completedFilePath] on
     * [exportProgress] (which also carries stats); this callback only reports the two
     * non-success outcomes — [ExportOutcome.Cancelled] and [ExportOutcome.Failed] — so the
     * UI can distinguish a user cancellation from a genuine failure.
     */
    fun setExportCompletionCallback(callback: (ExportOutcome) -> Unit)

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
 * Terminal outcome of an export attempt that did not succeed.
 *
 * Success is not modeled here — it is delivered through [ExportProgressInfo.completedFilePath].
 * Cancellation and failure are kept distinct so the UI can message them differently rather than
 * collapsing both into a single ambiguous "cancelled or failed" state.
 */
sealed interface ExportOutcome {
    /** The user cancelled the export (dismissed the file picker or tapped cancel). */
    data object Cancelled : ExportOutcome

    /** The export failed. [reason] is a human-readable message when one is available. */
    data class Failed(
        val reason: String? = null,
    ) : ExportOutcome
}

/**
 * Progress information for an active export operation.
 *
 * When [completedFilePath] is non-null, the export has finished and the file
 * has been written. This field is used to signal completion directly from the
 * worker, bypassing any asynchronous delivery mechanism (e.g. WorkManager
 * LiveData) that might introduce a visible delay.
 */
data class ExportProgressInfo(
    val isActive: Boolean = false,
    val progressPercent: Int = 0,
    val message: String = "",
    val completedFilePath: String? = null,
    val stats: ExportStats? = null,
)

internal val ExportStage.defaultMessage: String
    get() =
        when (this) {
            ExportStage.COLLECTING_JOURNALS -> "Collecting journals..."
            ExportStage.COLLECTING_NOTES -> "Collecting notes..."
            ExportStage.COLLECTING_DRAFTS -> "Collecting drafts..."
            ExportStage.PREPARING_DATA -> "Preparing export data..."
            ExportStage.WRITING_ARCHIVE -> "Creating ZIP archive..."
        }

internal val ExportError.defaultMessage: String
    get() =
        when (this) {
            ExportError.UNKNOWN -> "Export could not be completed."
        }

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

internal fun ExportDateRange.toCutoffInstant(now: Instant = Clock.System.now()): Instant? =
    when (this) {
        is ExportDateRange.AllTime -> null
        is ExportDateRange.Last30Days -> now - 30.days
        is ExportDateRange.Last90Days -> now - 90.days
        is ExportDateRange.LastYear -> now - 365.days
        is ExportDateRange.Custom -> start
    }
