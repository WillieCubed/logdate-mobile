package app.logdate.feature.events.ui.calendarsync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.calendar.DeviceCalendarReader
import app.logdate.client.datastore.DeviceCalendarSyncStats
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.domain.events.CalendarImportLauncher
import app.logdate.feature.events.ui.settings.RelativeAge
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
 * Drives the calendar sync overview screen.
 *
 * Reads the master toggle, the per-calendar selection size, the most recent worker run
 * snapshot, and the live calendar permission state, and merges them into one UI state
 * the screen renders. The "Sync now" button is locked using the same stats-driven
 * pattern as the auto-events Run-now button — capture the current `lastRunAt` at click
 * time and unlock when the worker reports a fresher run.
 *
 * Permission state is fed in from the Composable side via [setPermissionState] because
 * the runtime permission flow lives in `client/permissions` and uses
 * `rememberLauncherForActivityResult`, which can't be hoisted into the ViewModel.
 */
class CalendarSyncOverviewViewModel(
    private val preferences: LogdatePreferencesDataSource,
    private val launcher: CalendarImportLauncher,
    private val deviceCalendarReader: DeviceCalendarReader,
    private val clock: () -> Instant = { Clock.System.now() },
) : ViewModel() {
    private val pendingRunSince = MutableStateFlow<Instant?>(null)
    private val permissionState = MutableStateFlow(PermissionState.Unknown)
    private val totalCalendarCount = MutableStateFlow(0)

    /**
     * One-shot ticker that re-emits the current instant every minute, used to keep the
     * "5 min ago" status label in sync without freezing at composition time. Stops when
     * the screen unsubscribes via [SharingStarted.WhileSubscribed].
     */
    private val nowTicker =
        flow {
            while (true) {
                emit(clock())
                delay(RELATIVE_TICK_INTERVAL)
            }
        }

    val uiState: StateFlow<CalendarSyncOverviewUiState> =
        combine(
            combine(
                preferences.observeDeviceCalendarSyncEnabled(),
                preferences.observeDeviceCalendarEnabledIds(),
                preferences.observeDeviceCalendarSyncStats(),
                totalCalendarCount,
            ) { enabled, selectedIds, stats, total ->
                Snapshot(enabled, selectedIds, stats, total)
            },
            permissionState,
            pendingRunSince,
            nowTicker,
        ) { snapshot, permission, pendingSince, now ->
            val recordedRunAt = snapshot.stats.lastRunAt
            val isRunInFlight =
                pendingSince != null && (recordedRunAt == null || recordedRunAt <= pendingSince)
            CalendarSyncOverviewUiState(
                permissionState = permission,
                isSyncEnabled = snapshot.enabled,
                selectedCalendarCount = snapshot.selectedIds.size,
                totalCalendarCount = snapshot.totalCalendarCount,
                lastRunAt = recordedRunAt,
                lastRunAge = recordedRunAt?.let { relativeAge(it, now) },
                lastCreatedCount = snapshot.stats.lastCreatedCount,
                lastUpdatedCount = snapshot.stats.lastUpdatedCount,
                lastError = snapshot.stats.lastError,
                isRunInFlight = isRunInFlight,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CalendarSyncOverviewUiState(),
        )

    private data class Snapshot(
        val enabled: Boolean,
        val selectedIds: Set<String>,
        val stats: DeviceCalendarSyncStats,
        val totalCalendarCount: Int,
    )

    /**
     * Updates the permission state from the Composable side. Called whenever the runtime
     * permission flow finishes; also drives a fresh `listCalendars` read so the "N of M"
     * count reflects whatever the user just unlocked.
     */
    fun setPermissionState(state: PermissionState) {
        permissionState.value = state
        if (state == PermissionState.Granted) {
            refreshCalendarTotal()
        }
    }

    fun setSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setDeviceCalendarSyncEnabled(enabled)
        }
    }

    fun runNow() {
        if (uiState.value.isRunInFlight) return
        val threshold = uiState.value.lastRunAt ?: Instant.DISTANT_PAST
        pendingRunSince.value = threshold
        launcher.runNow()
    }

    private fun refreshCalendarTotal() {
        viewModelScope.launch {
            totalCalendarCount.value = deviceCalendarReader.listCalendars().size
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private val RELATIVE_TICK_INTERVAL = 60.seconds

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
