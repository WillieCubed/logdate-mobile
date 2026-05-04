package app.logdate.client.sync

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Coalesces bursts of "needs sync" signals into a single delayed action.
 *
 * Repository write paths used to launch a fresh sync coroutine on every CRUD op, so a user
 * editing five journals in thirty seconds kicked off five back-to-back sync passes that
 * each had to wait on the previous one's mutex. [SyncDebouncer] holds the latest trigger
 * for [debounceWindow] and runs [action] once the burst settles, which collapses N rapid
 * triggers into a single sync round. Failures are logged and swallowed — the on-disk
 * pending-op queue is the durable record of what still needs to upload, so a single failed
 * action just means the next trigger (or the next foreground / silent-push wakeup) will
 * pick up the same work.
 */
@OptIn(FlowPreview::class)
class SyncDebouncer(
    scope: CoroutineScope,
    debounceWindow: Duration = DEFAULT_WINDOW,
    private val action: suspend () -> Unit,
) {
    private val triggers =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    init {
        scope.launch {
            triggers
                .debounce(debounceWindow)
                .collect {
                    runCatching { action() }
                        .onFailure { Napier.w("SyncDebouncer action failed", it) }
                }
        }
    }

    /** Schedule the [action] to run after [DEFAULT_WINDOW] of quiet. Safe to call from any thread. */
    fun trigger() {
        triggers.tryEmit(Unit)
    }

    companion object {
        val DEFAULT_WINDOW: Duration = 2_500.milliseconds
    }
}
