package app.logdate.feature.events.ui.calendarsync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.events.ObserveImportedEventsUseCase
import app.logdate.shared.model.Event
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Drives the "recent imports" screen. Surfaces every event that came from the device
 * calendar import worker, sorted newest-first, so the user can scan what LogDate
 * mirrored and tap into the regular event detail screen if they want to edit a title
 * or attach captures.
 */
class CalendarSyncActivityViewModel(
    observeImportedEvents: ObserveImportedEventsUseCase,
) : ViewModel() {
    val events: StateFlow<List<Event>> =
        observeImportedEvents()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList(),
            )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
