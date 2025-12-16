package app.logdate.feature.timeline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.notes.RemoveNoteUseCase
import app.logdate.client.domain.timeline.GetStreamingTimelineUseCase
import app.logdate.client.domain.timeline.GetTimelineBannerUseCase
import app.logdate.client.domain.timeline.GetTimelineUseCase
import app.logdate.client.domain.timeline.TimelineBannerResult
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.feature.editor.ui.audio.AudioPlaybackManager
import app.logdate.shared.model.Person
import app.logdate.ui.audio.AudioPlaybackState
import app.logdate.ui.audio.TranscriptionState
import app.logdate.ui.profiles.toUiState
import app.logdate.ui.timeline.AudioNoteUiState
import app.logdate.ui.timeline.HomeTimelineUiState
import app.logdate.ui.timeline.ImageNoteUiState
import app.logdate.ui.timeline.TextNoteUiState
import app.logdate.ui.timeline.TimelineDaySelection
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.TimelineLoadingState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * A view model for the timeline overview screen.
 */
class TimelineViewModel(
    getTimelineUseCase: GetTimelineUseCase,
    getStreamingTimeline: GetStreamingTimelineUseCase,
    getTimelineBannerUseCase: GetTimelineBannerUseCase,
    private val notesRepository: JournalNotesRepository, // TODO: Consolidate with GetTimelineUseCase
    private val removeNoteUseCase: RemoveNoteUseCase,
    private val userStateRepository: UserStateRepository,
    private val audioPlaybackManager: AudioPlaybackManager,
) : ViewModel() {

    // Audio playback state
    private val _audioPlaybackState = MutableStateFlow(
        AudioPlaybackState(
            currentlyPlayingId = null,
            isPlaying = false,
            progress = 0f,
            duration = Duration.ZERO,
            play = { id, uri -> play(id, uri) },
            pause = { pause() },
            stop = { stop() },
            seekTo = { position -> seekTo(position) }
        )
    )
    
    val audioPlaybackState: StateFlow<AudioPlaybackState> = _audioPlaybackState.asStateFlow()
    
    // Transcription state
    private val _transcriptionState = MutableStateFlow(
        TranscriptionState(
            requestTranscription = { noteId ->
                // Example implementation
                viewModelScope.launch {
                    Napier.d("Requesting transcription for note: $noteId")
                    // Here we would call the actual transcription service
                    // For now, we'll simulate it with delay
                    kotlinx.coroutines.delay(2000)
                }
            },
            getTranscriptionText = { noteId ->
                // Simulate some example transcriptions
                when {
                    noteId.toString().contains("1") -> "This is a sample transcription."
                    noteId.toString().contains("5") -> "Another example transcription text."
                    else -> null
                }
            },
            isTranscriptionInProgress = { noteId ->
                // Simulate in progress for certain IDs
                noteId.toString().contains("2")
            },
            getTranscriptionError = { noteId ->
                // Simulate errors for certain IDs
                if (noteId.toString().contains("3")) "Service unavailable" else null
            }
        )
    )
    
    val transcriptionState: StateFlow<TranscriptionState> = _transcriptionState.asStateFlow()
    
    // Platform-specific audio player implementation
    // On Android, use MediaPlayer or ExoPlayer
    // On iOS, use AVAudioPlayer
    // On Desktop, use a platform-specific audio player library
    
    // For a real implementation, we would have platform-specific implementations 
    // in the corresponding source sets (androidMain, iosMain, desktopMain)
    // and use expect/actual pattern to provide platform-specific implementations
    
    // Example for Android:
    // private var mediaPlayer: MediaPlayer? = null
    
    private fun play(id: Uuid, uri: String) {
        viewModelScope.launch {
            if (_audioPlaybackState.value.currentlyPlayingId != id) {
                // Stop current playback before starting new one
                stopCurrentPlayback()
                
                // Use AudioPlaybackManager from editor feature
                audioPlaybackManager.startPlayback(
                    uri = uri,
                    onProgressUpdated = { progress ->
                        _audioPlaybackState.update { currentState ->
                            currentState.copy(progress = progress)
                        }
                    },
                    onPlaybackCompleted = {
                        onPlaybackCompleted()
                    }
                )
                
                _audioPlaybackState.update { currentState ->
                    currentState.copy(
                        currentlyPlayingId = id,
                        isPlaying = true,
                        progress = 0f
                    )
                }
            } else {
                // If already playing this audio but paused, resume playback
                audioPlaybackManager.startPlayback(
                    uri = uri,
                    onProgressUpdated = { progress ->
                        _audioPlaybackState.update { currentState ->
                            currentState.copy(progress = progress)
                        }
                    },
                    onPlaybackCompleted = {
                        onPlaybackCompleted()
                    }
                )
                
                _audioPlaybackState.update { currentState ->
                    currentState.copy(isPlaying = true)
                }
            }
        }
    }
    
    private fun pause() {
        viewModelScope.launch {
            // Use AudioPlaybackManager from editor feature
            audioPlaybackManager.pausePlayback()
            
            _audioPlaybackState.update { currentState ->
                currentState.copy(isPlaying = false)
            }
        }
    }
    
    private fun stop() {
        viewModelScope.launch {
            stopCurrentPlayback()
        }
    }
    
    private fun stopCurrentPlayback() {
        // Use AudioPlaybackManager from editor feature
        audioPlaybackManager.stopPlayback()
        
        _audioPlaybackState.update { currentState ->
            currentState.copy(
                isPlaying = false,
                currentlyPlayingId = null,
                progress = 0f
            )
        }
    }
    
    private fun seekTo(position: Float) {
        viewModelScope.launch {
            // Use AudioPlaybackManager from editor feature
            audioPlaybackManager.seekTo(position)
            
            _audioPlaybackState.update { currentState ->
                currentState.copy(progress = position)
            }
        }
    }
    
    // Example of a method to start updating progress regularly
    // private fun startProgressUpdates() {
    //     viewModelScope.launch {
    //         while (_audioPlaybackState.value.isPlaying && _audioPlaybackState.value.currentlyPlayingId != null) {
    //             try {
    //                 mediaPlayer?.let { player ->
    //                     if (player.isPlaying) {
    //                         val duration = player.duration
    //                         val position = player.currentPosition
    //                         val progress = position.toFloat() / duration.toFloat()
    //                         
    //                         _audioPlaybackState.update { currentState ->
    //                             currentState.copy(progress = progress)
    //                         }
    //                     }
    //                 }
    //                 delay(100) // Update progress every 100ms
    //             } catch (e: Exception) {
    //                 // Handle error
    //                 break
    //             }
    //         }
    //     }
    // }
    
    // Would be called when audio playback completes
    private fun onPlaybackCompleted() {
        _audioPlaybackState.update { 
            it.copy(
                isPlaying = false,
                progress = 0f
            ) 
        }
    }
    
    override fun onCleared() {
        // Clean up resources when the ViewModel is cleared
        stopCurrentPlayback()
        audioPlaybackManager.release()
        super.onCleared()
    }

    private val _selectedItemUiState =
        MutableStateFlow<TimelineDaySelection>(TimelineDaySelection.NotSelected)
    
    /**
     * Sets the selected day for timeline detail view.
     * 
     * @param date The date to select, or LocalDate.fromEpochDays(0) to clear selection
     */
    fun setSelectedDay(date: LocalDate) {
        // TODO: Using epoch day 0 (1970-01-01) as a sentinel value could be problematic
        // if that's actually a valid date in the timeline. Consider using a more explicit
        // approach for selection/deselection.
        _selectedItemUiState.value = if (date.toEpochDays() == 0) {
            TimelineDaySelection.NotSelected
        } else {
            TimelineDaySelection.DateSelected(date)
        }
    }
        
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    
    // Collect the user data and map to the birthday value
    val birthday = userStateRepository.userData
        .map { it.birthday }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

    // Use streaming timeline for incremental loading with immediate first paint
    val uiState: StateFlow<HomeTimelineUiState> = getStreamingTimeline(
        GetStreamingTimelineUseCase.TimelineRequest.RecentTimeline()
    )
        .combine(notesRepository.allNotesObserved) { timeline, allNotes ->
            val loadingState = when {
                timeline.days.isEmpty() -> TimelineLoadingState.InitialLoading
                timeline.days.any { it.tldr.contains("entries") } -> TimelineLoadingState.LoadingContent
                else -> TimelineLoadingState.Loaded
            }
            
            
            // NEW APPROACH: Group notes by date components rather than exact date objects
            // This is more resilient to time zone and equality issues
            val improvedNotesByDate = mutableMapOf<String, MutableList<JournalNote>>()
            
            allNotes.forEach { note ->
                val noteDateTime = note.creationTimestamp.toLocalDateTime(TimeZone.currentSystemDefault())
                val noteDate = noteDateTime.date
                
                // Create a string key in format "YYYY-MM-DD"
                val dateKey = "${noteDate.year}-${noteDate.monthNumber}-${noteDate.dayOfMonth}"
                
                if (!improvedNotesByDate.containsKey(dateKey)) {
                    improvedNotesByDate[dateKey] = mutableListOf()
                }
                
                improvedNotesByDate[dateKey]?.add(note)
            }
            
            // Function to convert notes to UI state
            fun List<JournalNote>.toUiState(): List<app.logdate.ui.timeline.NoteUiState> {
                return this.map {
                    when (it) {
                        is JournalNote.Text -> TextNoteUiState(
                            noteId = it.uid,
                            text = it.content,
                            timestamp = it.creationTimestamp,
                        )
                        is JournalNote.Image -> ImageNoteUiState(
                            noteId = it.uid,
                            uri = it.mediaRef,
                            timestamp = it.creationTimestamp,
                        )
                        is JournalNote.Audio -> AudioNoteUiState(
                            noteId = it.uid,
                            uri = it.mediaRef,
                            timestamp = it.creationTimestamp
                        )
                        is JournalNote.Video -> TextNoteUiState(
                            noteId = it.uid,
                            text = "[Video Recording]",
                            timestamp = it.creationTimestamp,
                        )
                    }
                }
            }
            
            // Helper function to get notes for a specific day by components
            fun getNotesForDay(date: LocalDate): List<JournalNote> {
                val dateKey = "${date.year}-${date.monthNumber}-${date.dayOfMonth}"
                return improvedNotesByDate[dateKey] ?: emptyList()
            }
            
            HomeTimelineUiState(
                items = timeline.days.map { day ->
                    // Get notes for this specific day using the component-based approach
                    val dayNotes = getNotesForDay(day.date)
                    
                    TimelineDayUiState(
                        summary = day.tldr,
                        date = day.date,
                        people = day.people.map(Person::toUiState),
                        events = day.events,
                        notes = dayNotes.toUiState(),
                        isLoadingSummary = day.tldr.contains("entries"),
                        isLoadingPeople = day.people.isEmpty() && day.tldr.contains("entries")
                    )
                },
                selectedItem = _selectedItemUiState.value,
                selectedDay = when (val selection = _selectedItemUiState.value) {
                    is TimelineDaySelection.DateSelected -> {
                        // Get notes for the selected date using the component-based approach
                        val notesForSelectedDay = getNotesForDay(selection.date)
                        
                        timeline.days.find { it.date == selection.date }?.let { day ->
                            TimelineDayUiState(
                                summary = day.tldr,
                                date = day.date,
                                people = day.people.map(Person::toUiState),
                                events = day.events,
                                notes = notesForSelectedDay.toUiState(),
                                isLoadingSummary = day.tldr.contains("entries"),
                                isLoadingPeople = day.people.isEmpty() && day.tldr.contains("entries")
                            )
                        }
                    }
                    is TimelineDaySelection.Selected -> {
                        // Handle legacy Selected type
                        // Get notes for the selected date using the component-based approach
                        val notesForSelectedDay = getNotesForDay(selection.day)
                        
                        timeline.days.find { it.date == selection.day }?.let { day ->
                            TimelineDayUiState(
                                summary = day.tldr,
                                date = day.date,
                                people = day.people.map(Person::toUiState),
                                events = day.events,
                                notes = notesForSelectedDay.toUiState(),
                                isLoadingSummary = day.tldr.contains("entries"),
                                isLoadingPeople = day.people.isEmpty() && day.tldr.contains("entries")
                            )
                        }
                    }
                    TimelineDaySelection.NotSelected -> null
                },
                loadingState = loadingState,
                isLoading = loadingState != TimelineLoadingState.Loaded,
                // Default to null, will be updated by the banner flow below
                timelineSuggestion = null,
                snackbarMessage = _snackbarMessage.value
            )
        }
        .combine(getTimelineBannerUseCase()) { state, bannerResult ->
            when (bannerResult) {
                is TimelineBannerResult.ShowBanner -> state.copy(
                    // Create a TimelineSuggestionBlock from the banner data
                    timelineSuggestion = app.logdate.ui.timeline.TimelineSuggestionBlock.OngoingEvent(
                        memoryId = bannerResult.memoryId,
                        message = bannerResult.message,
                        location = bannerResult.location,
                        people = bannerResult.people,
                        mediaUris = emptyList()
                    )
                )
                TimelineBannerResult.NoBanner -> state.copy(
                    timelineSuggestion = null
                )
            }
        }
        .combine(_snackbarMessage) { state, snackbarMessage ->
            state.copy(snackbarMessage = snackbarMessage)
        }
        .stateIn(
            viewModelScope, 
            SharingStarted.WhileSubscribed(5000), 
            // Immediate first paint with skeleton
            HomeTimelineUiState(
                loadingState = TimelineLoadingState.InitialLoading,
                isLoading = true
            )
        )

    /**
     * Removes a note from the timeline.
     */
    fun deleteItem(uid: Uuid) {
        viewModelScope.launch {
            removeNoteUseCase(uid)
            // TODO: Use UI to notify user of deletion
        }
    }
    
    /**
     * Shows a snackbar message when the user clicks "Add to memories"
     */
    fun showAddToMemoriesSnackbar(memoryId: String) {
        _snackbarMessage.value = "Adding to memories coming soon!"
        
        // Automatically clear the snackbar after a few seconds
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            if (_snackbarMessage.value == "Adding to memories coming soon!") {
                _snackbarMessage.value = null
            }
        }
    }
    
    /**
     * Dismisses any currently showing snackbar
     */
    fun dismissSnackbar() {
        _snackbarMessage.value = null
    }
    
}