package app.logdate.feature.events.ui.calendarsync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.calendar.DeviceCalendarReader
import app.logdate.client.datastore.LogdatePreferencesDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the calendar sync overview screen, exposing permission state, the master toggle,
 * and the count of selected calendars (so the "Which calendars" row can show "N of M
 * selected").
 *
 * Permission state is fed in from the Composable side via [setPermissionState] because
 * the runtime permission flow uses `rememberLauncherForActivityResult`, which can't be
 * hoisted into the ViewModel.
 */
class CalendarSyncOverviewViewModel(
    private val preferences: LogdatePreferencesDataSource,
    private val deviceCalendarReader: DeviceCalendarReader,
) : ViewModel() {
    private val permissionState = MutableStateFlow(PermissionState.Unknown)
    private val totalCalendarCount = MutableStateFlow(0)

    val uiState: StateFlow<CalendarSyncOverviewUiState> =
        combine(
            preferences.observeDeviceCalendarSyncEnabled(),
            preferences.observeDeviceCalendarEnabledIds(),
            permissionState,
            totalCalendarCount,
        ) { enabled, selectedIds, permission, total ->
            CalendarSyncOverviewUiState(
                permissionState = permission,
                isSyncEnabled = enabled,
                selectedCalendarCount = selectedIds.size,
                totalCalendarCount = total,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CalendarSyncOverviewUiState(),
        )

    /**
     * Called whenever the runtime permission flow finishes. Refreshes the cached calendar
     * count on a transition into [PermissionState.Granted] so the "N of M" row reflects
     * what the user just unlocked.
     */
    fun setPermissionState(state: PermissionState) {
        if (permissionState.value == state) return
        val transitionedToGranted =
            state == PermissionState.Granted && permissionState.value != PermissionState.Granted
        permissionState.value = state
        if (transitionedToGranted) {
            refreshCalendarTotal()
        }
    }

    fun setSyncEnabled(enabled: Boolean) {
        if (uiState.value.isSyncEnabled == enabled) return
        viewModelScope.launch {
            preferences.setDeviceCalendarSyncEnabled(enabled)
        }
    }

    private fun refreshCalendarTotal() {
        viewModelScope.launch {
            totalCalendarCount.value = deviceCalendarReader.listCalendars().size
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
