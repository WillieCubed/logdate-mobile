package app.logdate.feature.events.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.events.DeleteEventUseCase
import app.logdate.client.domain.events.GetEventByIdUseCase
import app.logdate.client.domain.events.UpdateEventUseCase
import app.logdate.shared.model.Event
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * UI state for the event detail/edit screen.
 */
sealed interface EventDetailUiState {
    data object Loading : EventDetailUiState

    data object NotFound : EventDetailUiState

    data class Loaded(
        val event: Event,
        val isSaving: Boolean = false,
        val errorMessage: String? = null,
    ) : EventDetailUiState
}

class EventDetailViewModel(
    private val getEventById: GetEventByIdUseCase,
    private val updateEvent: UpdateEventUseCase,
    private val deleteEvent: DeleteEventUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<EventDetailUiState>(EventDetailUiState.Loading)
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    fun loadEvent(eventId: Uuid) {
        viewModelScope.launch {
            getEventById(eventId).collect { event ->
                _uiState.value =
                    if (event == null) EventDetailUiState.NotFound else EventDetailUiState.Loaded(event)
            }
        }
    }

    fun updateTitle(title: String) {
        _uiState.update { current ->
            if (current is EventDetailUiState.Loaded) current.copy(event = current.event.copy(title = title)) else current
        }
    }

    fun updateDescription(description: String) {
        _uiState.update { current ->
            if (current is EventDetailUiState.Loaded) {
                current.copy(event = current.event.copy(description = description.ifBlank { null }))
            } else {
                current
            }
        }
    }

    fun save() {
        val current = _uiState.value as? EventDetailUiState.Loaded ?: return
        _uiState.value = current.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            updateEvent(current.event)
                .onFailure { error ->
                    Napier.e("Failed to save event ${current.event.id}", error)
                    _uiState.update {
                        if (it is EventDetailUiState.Loaded) it.copy(isSaving = false, errorMessage = "Couldn't save event") else it
                    }
                }.onSuccess {
                    _uiState.update { if (it is EventDetailUiState.Loaded) it.copy(isSaving = false) else it }
                }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val current = _uiState.value as? EventDetailUiState.Loaded ?: return
        viewModelScope.launch {
            deleteEvent(current.event.id)
                .onSuccess { onDeleted() }
                .onFailure { error ->
                    Napier.e("Failed to delete event ${current.event.id}", error)
                    _uiState.update {
                        if (it is EventDetailUiState.Loaded) it.copy(errorMessage = "Couldn't delete event") else it
                    }
                }
        }
    }
}
