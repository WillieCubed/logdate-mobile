package app.logdate.feature.editor.ui

import android.location.Location
import android.net.Uri
import app.logdate.core.data.notes.JournalNote

sealed interface NoteCreationUiState {

    data class Success(
        val recentNotes: List<JournalNote> = listOf(),
        val recentMedia: List<Uri> = listOf(),
        val currentLocation: Location,
    ) : NoteCreationUiState

    data object Loading : NoteCreationUiState
}