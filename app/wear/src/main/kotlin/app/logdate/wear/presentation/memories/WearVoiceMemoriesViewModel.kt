package app.logdate.wear.presentation.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import app.logdate.wear.sync.WearDataLayerClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** One voice memory in the list. */
data class VoiceMemoryItem(
    val noteId: Uuid,
    val createdAt: Instant,
    val durationMs: Long,
)

data class VoiceMemoriesUiState(
    val memories: List<VoiceMemoryItem> = emptyList(),
    val isLoaded: Boolean = false,
    /** True when recordings older than the last one shown can still be loaded. */
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
)

/**
 * The watch's newest-first list of voice memories. The newest [PAGE_SIZE] stay live as recordings
 * arrive or go; older ones are paged in on request.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearVoiceMemoriesViewModel(
    private val notesRepository: JournalNotesRepository,
    dataLayerClient: WearDataLayerClient,
) : ViewModel() {
    companion object {
        const val PAGE_SIZE = 30
    }

    private val olderNotes = MutableStateFlow<List<JournalNote.Audio>>(emptyList())
    private val loadingMore = MutableStateFlow(false)

    val uiState: StateFlow<VoiceMemoriesUiState> =
        combine(notesRepository.observeRecentAudioNotes(PAGE_SIZE), olderNotes, loadingMore) { recent, older, loading ->
            Triple(recent, older, loading)
        }.mapLatest { (recent, older, loading) ->
            val notes = (recent + older).distinctBy { it.uid }.sortedByDescending { it.creationTimestamp }
            VoiceMemoriesUiState(
                memories = notes.map { VoiceMemoryItem(it.uid, it.creationTimestamp, it.durationMs) },
                isLoaded = true,
                hasMore = notes.lastOrNull()?.let { notesRepository.hasAudioNotesBefore(it.creationTimestamp) } ?: false,
                isLoadingMore = loading,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, VoiceMemoriesUiState())

    init {
        viewModelScope.launch {
            if (!dataLayerClient.sendMessage(WearAudioRequestPaths.SYNC_REQUEST_PATH)) {
                Napier.w { "Failed to ask the phone for voice memories" }
            }
        }
    }

    fun loadMore() {
        val oldest = uiState.value.memories.lastOrNull() ?: return
        if (loadingMore.value) return
        loadingMore.value = true
        viewModelScope.launch {
            try {
                val page = notesRepository.getAudioNotesBefore(oldest.createdAt, PAGE_SIZE)
                olderNotes.update { it + page }
            } finally {
                loadingMore.value = false
            }
        }
    }
}
