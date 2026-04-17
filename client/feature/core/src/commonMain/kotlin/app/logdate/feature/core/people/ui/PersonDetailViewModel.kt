package app.logdate.feature.core.people.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.knowledge.InferredPeopleRepository
import app.logdate.client.repository.knowledge.PeopleProfileRepository
import app.logdate.shared.model.Event
import app.logdate.shared.model.Person
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class PersonDetailViewModel(
    private val peopleProfileRepository: PeopleProfileRepository,
    private val inferredPeopleRepository: InferredPeopleRepository,
) : ViewModel() {
    data class UiState(
        val person: Person? = null,
        val linkedEntries: List<JournalNote> = emptyList(),
        val linkedEvents: List<Event> = emptyList(),
        val isLoading: Boolean = true,
    )

    private val uiState = MutableStateFlow(UiState())
    private var loadJob: Job? = null

    fun uiState(): StateFlow<UiState> = uiState.asStateFlow()

    fun load(personId: Uuid) {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                uiState.value = uiState.value.copy(isLoading = true)
                runCatching {
                    inferredPeopleRepository.refresh()
                    peopleProfileRepository.observeProfile(personId)
                }.onSuccess { profileFlow ->
                    profileFlow.collectLatest { profile ->
                        uiState.value =
                            UiState(
                                person = profile?.person,
                                linkedEntries = profile?.linkedEntries.orEmpty(),
                                linkedEvents = profile?.linkedEvents.orEmpty(),
                                isLoading = false,
                            )
                    }
                }.onFailure { error ->
                    Napier.e("Failed to load person $personId", error)
                    uiState.value = UiState(person = null, isLoading = false)
                }
            }
    }
}
