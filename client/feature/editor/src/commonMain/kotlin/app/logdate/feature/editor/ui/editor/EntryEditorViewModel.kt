package app.logdate.feature.editor.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.domain.notes.FetchTodayNotesUseCase
import app.logdate.client.location.places.PlacesProvider
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * ViewModel for the note editor screen.
 *
 * This attempts to load the entry for the current date. If entries exist, it will add them to the
 * state for editing. If no entries exist, it will create a new entry.
 */
class EntryEditorViewModel(
    private val locationProvider: PlacesProvider,
    fetchNotesUseCase: FetchTodayNotesUseCase,
    private val addNoteUseCase: AddNoteUseCase,
) : ViewModel() {

    private val shouldExit = MutableStateFlow(false)

    // Editor state management for the current editing session
    private val _editorState = MutableStateFlow(EditorState())
    val editorState: StateFlow<EditorState> = _editorState.asStateFlow()

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = combine(
        _editorState,
        shouldExit,
    ) {
        editorState, shouldExit ->
        EditorUiState.Success(
            blocks = editorState.blocks,
            shouldExit = shouldExit,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        EditorUiState.Loading,
    )

    init {
        loadEntry()
    }

    // Loads the entry for the specified date
    private fun loadEntry() {
        viewModelScope.launch {
            _uiState.value = EditorUiState.Loading
            try {
//                val entry = entryRepository.getEntry(date)
//                _uiState.value = EditorUiState.Success(entry?.blocks ?: emptyList())
//                _editorState.value = EditorState(initialBlocks = entry?.blocks ?: emptyList())
            } catch (e: Exception) {
                _uiState.value = EditorUiState.Error(e.message ?: "Failed to load entry")
            }
        }
    }

    // Creates a new block with current location and timestamp
    fun createNewBlock(type: BlockType): EntryBlockData {
        // Capture the current location for the new block
        val location = Location(0.0, 0.0, LocationAltitude(0.0, AltitudeUnit.FEET))
        // TODO: Get actual location from the location provider
        val timestamp = Clock.System.now()

        return when (type) {
            BlockType.TEXT -> TextBlockData(
                timestamp = timestamp,
                location = location,
                content = ""
            )

            BlockType.IMAGE -> ImageBlockData(
                timestamp = timestamp,
                location = location,
                uri = "",
                caption = ""
            )

            BlockType.VIDEO -> VideoBlockData(
                timestamp = timestamp,
                location = location,
                uri = "",
                caption = "",
                thumbnailUri = ""
            )

            BlockType.AUDIO -> AudioBlockData(
                timestamp = timestamp,
                location = location,
                uri = "",
                duration = 0L,
                transcription = null
            )
        }
    }

    // Saves the current entry state
    fun saveEntry() {
        viewModelScope.launch {
            try {
                addNoteUseCase(editorState.value.toNewEntry())
                shouldExit.value = true
            } catch (e: Exception) {
                // Handle save error - could emit through a shared flow for UI events
                _uiState.value = EditorUiState.Error("Failed to save entry: ${e.message}")
            }
        }
    }

    // Updates a specific block while maintaining immutability
    fun updateBlock(updatedBlock: EntryBlockData) {
        _editorState.value.updateBlock(updatedBlock)
        // Autosave after block updates
        saveEntry()
    }
}

// UI state sealed interface for type-safe state handling
sealed interface EditorUiState {
    data object Loading : EditorUiState
    data class Success(
        val blocks: List<EntryBlockData>,
        val shouldExit: Boolean = false,
    ) : EditorUiState

    data class Error(val message: String) : EditorUiState
}

// Block types enum for creation
enum class BlockType {
    TEXT, IMAGE, VIDEO, AUDIO
}

// Extension function to help with state updates
fun EditorState.updateEditorContent(block: EntryBlockData) {
    updateBlock(block)
}


internal fun EditorState.toNewEntry(): List<JournalNote> {
    return blocks.map { block ->
        when (block) {
            is TextBlockData -> JournalNote.Text(
                uid = block.id.toString(),
                creationTimestamp = block.timestamp,
                lastUpdated = block.timestamp,
                content = block.content,
            )

            is ImageBlockData -> JournalNote.Image(
                uid = block.id.toString(),
                creationTimestamp = block.timestamp,
                lastUpdated = block.timestamp,
                mediaRef = block.uri,
            )

            is VideoBlockData -> JournalNote.Video(
                uid = block.id.toString(),
                creationTimestamp = block.timestamp,
                lastUpdated = block.timestamp,
                mediaRef = block.uri,
            )

            is AudioBlockData -> JournalNote.Audio(
                uid = block.id.toString(),
                creationTimestamp = block.timestamp,
                lastUpdated = block.timestamp,
                mediaRef = block.uri,
            )
        }
    }

}