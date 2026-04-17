package app.logdate.feature.core.people.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.knowledge.InferredPeopleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class PeopleInboxViewModel(
    private val inferredPeopleRepository: InferredPeopleRepository,
) : ViewModel() {
    data class SuggestionUiState(
        val id: Uuid,
        val nameHint: String,
        val evidencePreview: List<String>,
    )

    val suggestions =
        inferredPeopleRepository
            .observeOpenClusters()
            .mapLatest { clusters ->
                clusters.map { cluster ->
                    val evidence =
                        inferredPeopleRepository
                            .observeEvidence(cluster.uid)
                            .first()
                            .mapNotNull { item -> item.label }
                            .distinct()
                            .take(2)
                    SuggestionUiState(
                        id = cluster.uid,
                        nameHint = cluster.displayNameHint,
                        evidencePreview = evidence,
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    init {
        viewModelScope.launch {
            runCatching { inferredPeopleRepository.refresh() }
        }
    }

    fun confirm(clusterId: Uuid) {
        viewModelScope.launch {
            runCatching { inferredPeopleRepository.confirmClusterAsPerson(clusterId) }
        }
    }

    fun dismiss(clusterId: Uuid) {
        viewModelScope.launch {
            runCatching { inferredPeopleRepository.rejectCluster(clusterId) }
        }
    }
}
