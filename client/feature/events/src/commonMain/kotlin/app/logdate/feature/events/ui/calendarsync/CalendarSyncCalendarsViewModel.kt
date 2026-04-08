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
 * Drives the "choose calendars" screen. Loads the device calendars on entry and writes
 * every toggle through to preferences immediately, so the row state always equals the
 * saved state and there is no save button to forget.
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
        val current = _state.value.selectedIds
        val nextIds = if (id in current) current - id else current + id
        _state.update { it.copy(selectedIds = nextIds) }
        viewModelScope.launch {
            preferences.setDeviceCalendarEnabledIds(nextIds)
        }
    }
}

data class CalendarSyncCalendarsUiState(
    val isLoading: Boolean = true,
    val calendars: List<DeviceCalendar> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
)
