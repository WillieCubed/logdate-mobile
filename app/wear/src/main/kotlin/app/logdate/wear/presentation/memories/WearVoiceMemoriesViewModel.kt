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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
 * The watch's newest-first list of voice memories. The whole list is one live query that widens by
 * [PAGE_SIZE] on request, so a recording that arrives or is deleted anywhere in it shows up at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearVoiceMemoriesViewModel(
    private val notesRepository: JournalNotesRepository,
    dataLayerClient: WearDataLayerClient,
) : ViewModel() {
    companion object {
        const val PAGE_SIZE = 30
    }

    /** How many memories the list is asked to show. */
    private val requested = MutableStateFlow(PAGE_SIZE)

    private class Window(
        val limit: Int,
        val notes: List<JournalNote.Audio>,
    )

    val uiState: StateFlow<VoiceMemoriesUiState> =
        requested
            .flatMapLatest { limit ->
                // One extra row tells whether older recordings exist, without a cursor that recordings
                // sharing a creation time could fall between.
                notesRepository.observeRecentAudioNotes(limit + 1).map { Window(limit, it) }
            }.combine(requested) { window, wanted ->
                val notes = window.notes.sortedByDescending { it.creationTimestamp }
                VoiceMemoriesUiState(
                    memories = notes.take(window.limit).map { VoiceMemoryItem(it.uid, it.creationTimestamp, it.durationMs) },
                    isLoaded = true,
                    hasMore = notes.size > window.limit,
                    isLoadingMore = wanted > window.limit,
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
        val state = uiState.value
        if (!state.hasMore || state.isLoadingMore) return
        requested.update { it + PAGE_SIZE }
    }
}
