package app.logdate.feature.events.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.EventInferenceStats
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.domain.events.EventInferenceLauncher
import app.logdate.client.domain.events.EventInferenceSensitivity
import app.logdate.client.domain.events.observeEventInferenceSensitivityValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Drives the auto-events settings screen.
 *
 * The in-flight signal for the Run-now button is derived from the worker's own stats:
 * when the user taps Run we remember the last-run timestamp at click time, and the
 * button stays locked until `observeEventInferenceStats()` emits a fresher timestamp.
 * The affordance unlocks on the actual completion signal — not a timer that may lie
 * about whether the worker has finished.
 */
class EventsSettingsViewModel(
    private val preferences: LogdatePreferencesDataSource,
    private val inferenceLauncher: EventInferenceLauncher,
    private val clock: () -> Instant = { Clock.System.now() },
) : ViewModel() {
    private val pendingRunSince = MutableStateFlow<Instant?>(null)

    /**
     * Emits the current instant once and then re-emits every minute. The settings status
     * card formats `lastRunAt` as a relative phrase ("5 min ago"); without a ticker the
     * label freezes at composition time and a screen left open shows a stale "just now"
     * forever. The flow only ticks while there's a subscriber via [SharingStarted.WhileSubscribed],
     * so closing the screen halts the timer (no battery cost while backgrounded).
     */
    private val nowTicker =
        flow {
            while (true) {
                emit(clock())
                delay(RELATIVE_TICK_INTERVAL)
            }
        }

    val uiState: StateFlow<EventsSettingsUiState> =
        combine(
            combine(
                preferences.observeEventsEnabled(),
                preferences.observeEventInferenceSensitivityValue(),
                preferences.observeEventInferenceAiNamingEnabled(),
                preferences.observeEventInferenceStats(),
            ) { enabled, sensitivity, smartNaming, stats ->
                Snapshot(enabled, sensitivity, smartNaming, stats)
            },
            pendingRunSince,
            nowTicker,
        ) { snapshot, pendingSince, now ->
            // The button stays locked until the worker's stats record advances past the
            // threshold the click captured. Once the worker reports a fresher run, the
            // condition is naturally false; we don't need to clear `pendingRunSince`
            // because the next click overwrites it with a newer threshold anyway.
            val stats = snapshot.stats
            val recordedRunAt = stats.lastRunAt
            val isRunInFlight =
                pendingSince != null && (recordedRunAt == null || recordedRunAt <= pendingSince)
            EventsSettingsUiState(
                isAutoEventsEnabled = snapshot.enabled,
                sensitivity = snapshot.sensitivity,
                isSmartNamingEnabled = snapshot.smartNaming,
                lastRunAt = recordedRunAt,
                lastRunAge = recordedRunAt?.let { relativeAge(it, now) },
                lastCreatedCount = stats.lastCreatedCount,
                recentCreatedCount = stats.recentCreatedCount,
                lastError = stats.lastError,
                isRunInFlight = isRunInFlight,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = EventsSettingsUiState(),
        )

    private data class Snapshot(
        val enabled: Boolean,
        val sensitivity: EventInferenceSensitivity,
        val smartNaming: Boolean,
        val stats: EventInferenceStats,
    )

    fun setAutoEventsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setEventsEnabled(enabled)
        }
    }

    fun setSensitivity(sensitivity: EventInferenceSensitivity) {
        viewModelScope.launch {
            preferences.setEventInferenceSensitivity(sensitivity.name)
        }
    }

    fun setSmartNamingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setEventInferenceAiNamingEnabled(enabled)
        }
    }

    /**
     * Triggers an immediate inference run via the platform launcher and locks the Run button
     * until the worker's next stats write advances past the captured timestamp.
     */
    fun runNow() {
        if (uiState.value.isRunInFlight) return
        // Capture the current observed lastRunAt as the threshold the worker must beat. Use
        // DISTANT_PAST when there's no prior run so the very first invocation still locks
        // the button until stats land.
        val threshold = uiState.value.lastRunAt ?: Instant.DISTANT_PAST
        pendingRunSince.value = threshold
        inferenceLauncher.runNow()
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private val RELATIVE_TICK_INTERVAL = 60.seconds

        /**
         * Coarse "just now / N min ago / Nh ago / Nd ago" age bucket for the status card.
         * Computed in the ViewModel from the [nowTicker] so the bucket transitions once per
         * minute even when the user leaves the screen open. The Composable maps the variant
         * to a localized string.
         */
        private fun relativeAge(
            instant: Instant,
            now: Instant,
        ): RelativeAge {
            val delta = now - instant
            return when {
                delta < 1.minutes -> RelativeAge.JustNow
                delta < 1.hours -> RelativeAge.Minutes(delta.inWholeMinutes)
                delta < 1.days -> RelativeAge.Hours(delta.inWholeHours)
                else -> RelativeAge.Days(delta.inWholeDays)
            }
        }
    }
}
