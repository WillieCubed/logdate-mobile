package app.logdate.feature.editor.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.core.data.notes.JournalNotesRepository
import app.logdate.core.world.LogdateLocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteCreationViewModel @Inject constructor(
    private val repository: JournalNotesRepository,
    private val locationProvider: LogdateLocationProvider,
) : ViewModel() {

    // TODO: Load recent media
    val uiState: StateFlow<NoteCreationUiState> =
        locationProvider.observeLocation()
            .combine(repository.allNotesObserved) { location, notes -> location to notes }
            // TODO: Actually do something with the notes
            .map { (location, notes) ->
                NoteCreationUiState.Success(
                    recentNotes = notes,
                    currentLocation = location,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                NoteCreationUiState.Loading
            )

    fun addNote(newEntryContent: NewEntryContent) {
        // TODO: Add note to repository
        viewModelScope.launch {
            // TODO: Notify on failure
        }
    }

    fun addMediaAttachment(uri: Uri) {

    }
}