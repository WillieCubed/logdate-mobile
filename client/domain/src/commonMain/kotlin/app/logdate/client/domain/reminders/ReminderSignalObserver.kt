package app.logdate.client.domain.reminders

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/** Schedules fresh reads without collecting private inputs while their consent is off. */
@OptIn(FlowPreview::class)
class ReminderSignalObserver(
    private val enabled: Flow<Boolean>,
    private val placesEnabled: Flow<Boolean>,
    private val markChanges: () -> Flow<Unit>,
    private val locationChanges: () -> Flow<Unit>,
    private val enqueue: () -> Unit,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                combine(enabled, placesEnabled) { reminders, places -> reminders to places }
                    .collectLatest { (reminders, places) ->
                        if (!reminders) return@collectLatest
                        merge(
                            resilient(markChanges),
                            if (places) resilient(locationChanges) else emptyFlow(),
                        ).sample(30_000).collect { enqueue() }
                    }
            }
    }

    private fun resilient(changes: () -> Flow<Unit>): Flow<Unit> =
        flow { emitAll(changes()) }.retryWhen { cause, _ ->
            if (cause is CancellationException) throw cause
            Napier.w("Reminder signal observation unavailable; retrying later", cause)
            delay(60_000)
            true
        }
}
