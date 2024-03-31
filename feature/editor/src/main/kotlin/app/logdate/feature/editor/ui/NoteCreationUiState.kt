package app.logdate.feature.editor.ui

import android.net.Uri
import app.logdate.core.data.notes.JournalNote
import app.logdate.model.UserPlace

data class NoteCreationUiState(
    val recentNotes: List<JournalNote> = listOf(),
    val recentMedia: List<Uri> = listOf(),
    val initialContent: ShareIntentData? = ShareIntentData(),
    val locationUiState: LocationUiState = LocationUiState.Unknown,
    val userMessage: UserMessage? = null
)

sealed class LocationUiState {
    data class Enabled(
        val currentPlace: UserPlace = UserPlace.Unknown,
    ) : LocationUiState()

    data object Disabled : LocationUiState()

    data object Unknown : LocationUiState()
}

data class ShareIntentData(
    val text: String = "",
    val media: List<Uri> = listOf(),
)

data class UserMessage(
    val text: String,
    val actionLabel: String? = null,
    val actionHandler: () -> Unit? = {},
)