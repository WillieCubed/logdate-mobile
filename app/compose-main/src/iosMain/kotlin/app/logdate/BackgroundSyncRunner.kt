package app.logdate

import app.logdate.client.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * iOS bridge for running background sync from Swift.
 */
class BackgroundSyncRunner : KoinComponent {
    private val syncManager: SyncManager by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun run(completion: (Boolean) -> Unit) {
        scope.launch {
            val success =
                runCatching { syncManager.fullSync().success }
                    .getOrElse { false }
            completion(success)
        }
    }
}
