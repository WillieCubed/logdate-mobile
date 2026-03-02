package app.logdate.feature.core.restore

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Interface for launching data restore operations.
 */
interface RestoreLauncher {
    /**
     * Starts the data restore process.
     */
    fun startRestore()

    /**
     * Cancels any ongoing restore operation.
     */
    fun cancelRestore()

    /**
     * Sets a callback to be notified of restore progress and completion.
     */
    fun setRestoreCompletionCallback(callback: (RestoreOutcome) -> Unit)
}

sealed class RestoreOutcome {
    data object Started : RestoreOutcome()

    data object Cancelled : RestoreOutcome()

    data class Success(
        val summary: RestoreSummary,
    ) : RestoreOutcome()

    data class Failure(
        val message: String,
    ) : RestoreOutcome()
}

@Serializable
data class RestoreSummary(
    val source: String,
    val exportDate: Instant? = null,
    val appVersion: String? = null,
    val deviceId: String? = null,
    val journalsImported: Int,
    val notesImported: Int,
    val draftsImported: Int,
    val journalLinksImported: Int,
    val mediaImported: Int,
    val warnings: List<String> = emptyList(),
)
