package app.logdate.client.sync

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

/** Quiet polls still discover remote changes; actual transfers and long runs retain foreground protection. */
internal suspend fun <T> withSyncForegroundRun(
    statuses: StateFlow<SyncStatus>,
    showStarting: suspend () -> Boolean,
    showProgress: suspend (SyncRunProgress) -> Boolean,
    run: suspend () -> T,
): T =
    coroutineScope {
        val since = statuses.value
        val notificationMutex = Mutex()
        var shown = false
        var notificationsAllowed = true
        val updates =
            launch(start = CoroutineStart.UNDISPATCHED) {
                statuses
                    .runProgressUpdates(since, 1.seconds)
                    .takeWhile { progress ->
                        notificationMutex.withLock {
                            if (!notificationsAllowed) return@withLock false
                            notificationsAllowed = showProgress(progress)
                            shown = notificationsAllowed
                            notificationsAllowed
                        }
                    }.collect()
            }
        // Remote discovery may download before an upload denominator exists. Do not let a long
        // discovery/transfer run lose background protection, or overwrite determinate upload progress.
        val keepAlive =
            launch {
                delay(10.seconds)
                notificationMutex.withLock {
                    if (!shown && notificationsAllowed) {
                        notificationsAllowed = showStarting()
                        shown = notificationsAllowed
                    }
                }
            }
        try {
            run()
        } finally {
            updates.cancel()
            keepAlive.cancel()
        }
    }
