package app.logdate.feature.rewind.ui

import android.net.Uri
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

sealed interface RewindUiState {
    data class Loaded(
        val ready: Boolean = false,
        val data: RewindData,
        val isLoading: Boolean = false,
    ) : RewindUiState

    data object Loading : RewindUiState
}

data class RewindListUiState(
    val items: List<RewindData>,
)

data class RewindData(
    val id: Uuid,
    val title: String,
    val label: String,
    val media: List<Uri>,
    val issueDate: Instant,
    val places: List<String>,
    val people: List<String>,
)