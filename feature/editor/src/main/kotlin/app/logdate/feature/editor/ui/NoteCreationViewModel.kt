package app.logdate.feature.editor.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.core.data.notes.JournalNotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteCreationViewModel @Inject constructor(
    private val repository: JournalNotesRepository,
//    private val locationProvider: PlacesProvider,
) : ViewModel() {

    // TODO: Load recent media
    val uiState: StateFlow<NoteCreationUiState> =
        repository.allNotesObserved
            .map { notes ->
                NoteCreationUiState.Success(
                    recentNotes = notes,
                    currentLocation = null,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                NoteCreationUiState.Loading
            )
//        locationProvider.observeCurrentPlace()
//            .combine(repository.allNotesObserved) { location, notes -> location to notes }
//            // TODO: Actually do something with the notes
//            .map { (location, notes) ->
//                NoteCreationUiState.Success(
//                    recentNotes = notes,
//                    currentLocation = location,
//                )
//            }
//            .stateIn(
//                viewModelScope,
//                SharingStarted.WhileSubscribed(5000),
//                NoteCreationUiState.Loading
//            )

    fun addNote(newEntryContent: NewEntryContent, onNoteSaved: () -> Unit) {
        viewModelScope.launch {
            repository.create(newEntryContent.toNewTextNote())
            Log.d("NoteCreationViewModel", "Added note")
            // TODO: Notify on failure
            onNoteSaved()
        }
    }

    fun addMediaAttachment(uri: Uri) {

    }

//    fun refreshLocation() {
//        viewModelScope.launch {
//            locationProvider.refreshCurrentPlace()
//        }
//    }
}