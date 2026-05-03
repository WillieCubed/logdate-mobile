package app.logdate

import app.logdate.client.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * iOS bridge for running background sync from Swift.
 */
class BackgroundSyncRunner : KoinComponent {
    private val syncManager: SyncManager by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeJob: Job? = null

    fun run(completion: (Boolean) -> Unit) {
        activeJob =
            scope.launch {
                val success =
                    runCatching { syncManager.fullSync().success }
                        .getOrElse { false }
                completion(success)
            }
    }

    /**
     * Cancels the in-flight sync. Safe to call from the iOS BGTask expiration handler when the
     * system warns the task is about to be killed; the cancellation lets the next scheduled refresh
     * pick up cleanly instead of leaving a half-complete sync running past its budget.
     */
    fun cancel() {
        activeJob?.cancel()
        activeJob = null
    }
}
