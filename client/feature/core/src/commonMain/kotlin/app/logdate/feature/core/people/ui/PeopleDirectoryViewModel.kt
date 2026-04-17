package app.logdate.feature.core.people.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.entities.GetPeopleUseCase
import app.logdate.client.repository.knowledge.InferredPeopleRepository
import app.logdate.ui.profiles.PersonUiState
import app.logdate.ui.profiles.toUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PeopleDirectoryViewModel(
    getPeopleUseCase: GetPeopleUseCase,
    private val inferredPeopleRepository: InferredPeopleRepository,
) : ViewModel() {
    val people: StateFlow<List<PersonUiState>> =
        getPeopleUseCase()
            .map { people -> people.map { it.toUiState() } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    init {
        viewModelScope.launch {
            runCatching { inferredPeopleRepository.refresh() }
        }
    }
}
