package app.logdate.client.domain.reminders

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/** Refreshes learned hours on startup, consent changes and scheduled reminder evaluations. */
@OptIn(ExperimentalCoroutinesApi::class)
class LearnedReminderHours(
    private val enabled: Flow<Boolean>,
    private val resolver: WindDownTimeResolver,
    private val clock: Clock = Clock.System,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    private val refreshes = MutableStateFlow(0L)

    fun refresh() = refreshes.update { it + 1 }

    fun observe(): Flow<WakingHours?> =
        combine(enabled.distinctUntilChanged(), refreshes) { consent, _ -> consent }
            .mapLatest { consent ->
                if (!consent) null else withTimeoutOrNull(10_000) { resolver.resolve(clock.now(), zone()) } ?: WakingHours.Fallback
            }.distinctUntilChanged()
}
