package app.logdate.wear.presentation.rewind

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.shared.model.RewindContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.uuid.Uuid

data class WearRewindListItem(
    val uid: Uuid,
    val title: String,
    val label: String,
)

data class WearRewindListUiState(
    val rewinds: List<WearRewindListItem> = emptyList(),
)

data class WearRewindPlaybackState(
    val rewindTitle: String,
    val panels: List<RewindContent>,
    val currentIndex: Int,
) {
    val totalPanels: Int get() = panels.size
    val currentPanel: RewindContent? get() = panels.getOrNull(currentIndex)
    val isLastPanel: Boolean get() = currentIndex >= panels.lastIndex
    val progress: Float get() = if (panels.isEmpty()) 0f else (currentIndex + 1f) / panels.size
}

class WearRewindViewModel(
    rewindRepository: RewindRepository,
) : ViewModel() {
    private val allRewinds =
        rewindRepository
            .getAllRewinds()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val rewindListState: StateFlow<WearRewindListUiState> =
        allRewinds
            .map { rewinds ->
                WearRewindListUiState(
                    rewinds =
                        rewinds.map { rewind ->
                            WearRewindListItem(
                                uid = rewind.uid,
                                title = rewind.title,
                                label = rewind.label,
                            )
                        },
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, WearRewindListUiState())

    private val _playbackState = MutableStateFlow<WearRewindPlaybackState?>(null)
    val playbackState: StateFlow<WearRewindPlaybackState?> = _playbackState

    fun selectRewind(uid: Uuid) {
        val rewind = allRewinds.value.find { it.uid == uid } ?: return
        val panels = filterPanelsForWear(rewind.content)
        _playbackState.value =
            WearRewindPlaybackState(
                rewindTitle = rewind.title,
                panels = panels,
                currentIndex = 0,
            )
    }

    fun advance() {
        val current = _playbackState.value ?: return
        if (!current.isLastPanel) {
            _playbackState.value = current.copy(currentIndex = current.currentIndex + 1)
        }
    }

    fun previous() {
        val current = _playbackState.value ?: return
        if (current.currentIndex > 0) {
            _playbackState.value = current.copy(currentIndex = current.currentIndex - 1)
        }
    }

    fun exitPlayback() {
        _playbackState.value = null
    }

    /**
     * Filters out Image and Video panels since the watch screen is too small
     * for meaningful media viewing.
     */
    private fun filterPanelsForWear(content: List<RewindContent>): List<RewindContent> =
        content.filter { panel ->
            panel !is RewindContent.Image && panel !is RewindContent.Video
        }
}
