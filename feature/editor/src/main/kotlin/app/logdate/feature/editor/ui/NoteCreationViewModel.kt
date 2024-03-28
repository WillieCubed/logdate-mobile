package app.logdate.feature.editor.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import app.logdate.core.data.notes.JournalNotesRepository
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
    private val repository: JournalNotesRepository, private val savedStateHandle: SavedStateHandle
//    private val locationProvider: PlacesProvider,
) : ViewModel() {

    val uiState: StateFlow<NoteCreationUiState> = repository.allNotesObserved.combine(
        savedStateHandle.getStateFlow(
            NavController.KEY_DEEP_LINK_INTENT, Intent()
        )
    ) { notes, intent ->
        notes to intent
    }.map { (notes, intent) ->
        NoteCreationUiState.Success(
            recentNotes = notes,
            recentMedia = listOf(), // TODO: Load recent media
            initialContent = intent.parseNewEntryContent(),
            currentLocation = null,
        )
    }
        // TODO: Use location provider
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), NoteCreationUiState.Loading
        )

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

internal fun Intent.parseNewEntryContent(): ShareIntentData? {
    if (action != Intent.ACTION_SEND) return null
    val text = getStringExtra(Intent.EXTRA_TEXT) ?: ""
    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION") getParcelableExtra(Intent.EXTRA_STREAM) as Uri?
    }
    return ShareIntentData(
        text = text,
        media = listOfNotNull(uri),
    )
}

private const val MIME_TYPE_TEXT = "text/"
private const val MIME_TYPE_IMAGE = "image/"
internal fun Intent.isTextMimeType() = type?.startsWith(MIME_TYPE_TEXT) == true
internal fun Intent.isImageMimeType() = type?.startsWith(MIME_TYPE_IMAGE) == true