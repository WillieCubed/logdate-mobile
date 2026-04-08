package app.logdate.feature.events.ui.calendarsync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.calendar.DeviceCalendar
import app.logdate.client.calendar.DeviceCalendarReader
import app.logdate.client.datastore.LogdatePreferencesDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the "choose calendars" screen. Loads the device calendars on entry, tracks a
 * working selection set the user can toggle, and persists the result back to preferences
 * on save. The next periodic worker run picks up the new selection automatically.
 */
class CalendarSyncCalendarsViewModel(
    private val deviceCalendarReader: DeviceCalendarReader,
    private val preferences: LogdatePreferencesDataSource,
) : ViewModel() {
    private val _state = MutableStateFlow(CalendarSyncCalendarsUiState())
    val state: StateFlow<CalendarSyncCalendarsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val calendars = deviceCalendarReader.listCalendars()
            val initiallySelected = preferences.getDeviceCalendarEnabledIds()
            _state.value =
                CalendarSyncCalendarsUiState(
                    isLoading = false,
                    calendars = calendars,
                    selectedIds = initiallySelected,
                )
        }
    }

    fun toggleCalendar(id: String) {
        _state.update { current ->
            val nextIds =
                if (id in current.selectedIds) {
                    current.selectedIds - id
                } else {
                    current.selectedIds + id
                }
            current.copy(selectedIds = nextIds)
        }
    }

    fun save(onComplete: () -> Unit) {
        viewModelScope.launch {
            preferences.setDeviceCalendarEnabledIds(_state.value.selectedIds)
            onComplete()
        }
    }
}

data class CalendarSyncCalendarsUiState(
    val isLoading: Boolean = true,
    val calendars: List<DeviceCalendar> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
)
