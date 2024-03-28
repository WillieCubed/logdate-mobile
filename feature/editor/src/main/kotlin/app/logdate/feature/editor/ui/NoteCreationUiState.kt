package app.logdate.feature.editor.ui

import android.net.Uri
import app.logdate.core.data.notes.JournalNote
import app.logdate.model.UserPlace

sealed interface NoteCreationUiState {

    data class Success(
        val recentNotes: List<JournalNote> = listOf(),
        val recentMedia: List<Uri> = listOf(),
        val initialContent: ShareIntentData? = ShareIntentData(),
        val currentLocation: UserPlace? = null,
    ) : NoteCreationUiState

    data object Loading : NoteCreationUiState
}

data class ShareIntentData(
    val text: String = "",
    val media: List<Uri> = listOf(),
)