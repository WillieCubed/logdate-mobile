package app.logdate.feature.editor.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import app.logdate.core.data.notes.JournalNote
import app.logdate.core.data.notes.JournalNotesRepository
import app.logdate.core.media.MediaManager
import app.logdate.core.world.PlacesProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Fetches notes for the given day.
 *
 * This use case is used to populate the list of recent notes in the note creation screen.
 * It includes a buffer to include
 */
class FetchNotesForDayUseCase @Inject constructor(
    private val repository: JournalNotesRepository,
) {
    operator fun invoke(
        date: LocalDate,
    ): Flow<List<JournalNote>> {
        val start = date.atStartOfDayIn(TimeZone.currentSystemDefault())
        val end = start + 24.hours
        return repository.observeNotesInRange(start, end)
    }
}

/**
 * Fetches notes for the current day.
 *
 * This use case is used to populate the list of recent notes in the note creation screen.
 * It includes a buffer to include
 */
class FetchTodayNotesUseCase @Inject constructor(
    private val repository: JournalNotesRepository,
) {
    operator fun invoke(buffer: Duration = 4.hours): Flow<List<JournalNote>> {
        val start =
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.atStartOfDayIn(
                TimeZone.currentSystemDefault()
            )
        val end = start + 24.hours
        return repository.observeNotesInRange(start - buffer, end)
    }
}

@HiltViewModel
class NoteCreationViewModel @Inject constructor(
    private val repository: JournalNotesRepository,
    private val mediaManager: MediaManager,
    private val locationProvider: PlacesProvider,
    fetchNotesUseCase: FetchTodayNotesUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _deepLinkData =
        savedStateHandle.getStateFlow(NavController.KEY_DEEP_LINK_INTENT, Intent())
    private val _userMessage = MutableStateFlow<UserMessage?>(null)
    private val _locationState = locationProvider.observeCurrentPlace().catch {
        _userMessage.emit(
            UserMessage(
                text = "Please enable location access in settings to use this feature.",
                actionLabel = "Settings",
                actionHandler = { /* TODO: Request location permission */ },
            )
        )
        LocationUiState.Disabled
    }.map {
        LocationUiState.Enabled(currentPlace = it)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        LocationUiState.Unknown,
    )
    private val _todayNotes = fetchNotesUseCase()

    val uiState: StateFlow<NoteCreationUiState> = combine(
        _deepLinkData,
        _todayNotes,
        _locationState,
        _userMessage,
    ) { deepLinkIntent, notes, locationState, userMessage ->
        val content = deepLinkIntent.parseNewEntryContent()
        NoteCreationUiState(
            recentNotes = notes,
            recentMedia = listOf(), // TODO: Load recent media
            initialContent = content,
            locationUiState = locationState,
            userMessage = userMessage,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        NoteCreationUiState(),
    )

    internal fun handleLocationPermissionResult(granted: Boolean) {
        if (granted) {
            refreshLocation()
            _userMessage.value = UserMessage(
                text = "Location updated.",
            )
        } else {
            // TODO: Show error message
            _userMessage.value = UserMessage(
                text = "Please enable location access in settings to use this feature.",
                actionLabel = "Settings",
                actionHandler = { /* TODO: Request location permission */ },
            )
        }
    }

    fun addNote(newEntryContent: NewEntryContent, onNoteSaved: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.create(newEntryContent.toNewTextNote())
            } catch (e: Exception) {
                _userMessage.value = UserMessage(
                    text = "Failed to save note.",
                )
                return@launch
            }
            onNoteSaved()
        }
    }

    fun addMediaAttachment(uri: Uri) {

    }

    fun refreshLocation() = viewModelScope.launch {
        locationProvider.refreshCurrentPlace()
    }

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