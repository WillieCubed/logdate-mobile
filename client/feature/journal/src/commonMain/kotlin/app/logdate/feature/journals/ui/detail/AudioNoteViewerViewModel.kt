package app.logdate.feature.journals.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.timeline.GetJournalMembershipUseCase
import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.AudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.media.audio.AudioPlaybackStatusProvider
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.feature.editor.audio.AudioContext
import app.logdate.feature.editor.audio.AudioContextProcessor
import app.logdate.feature.editor.audio.AudioLabelResolver
import app.logdate.feature.editor.audio.formatAudioLabelAsync
import app.logdate.util.formatDateLocalized
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * ViewModel that manages audio-specific rendering and playback state.
 */
class AudioNoteViewerViewModel(
    private val noteId: Uuid,
    private val notesRepository: JournalNotesRepository,
    private val audioContextProcessor: AudioContextProcessor,
    private val durationResolver: AudioDurationResolver,
    private val audioPlaybackManager: AudioPlaybackManager,
    private val getJournalMembership: GetJournalMembershipUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<AudioNoteViewerUiState>(AudioNoteViewerUiState.Loading)
    val uiState: StateFlow<AudioNoteViewerUiState> = _uiState.asStateFlow()

    private val statusProvider = audioPlaybackManager as? AudioPlaybackStatusProvider
    private var audioProcessingJob: Job? = null
    private var cachedAudioContext: AudioContext? = null
    private var cachedAudioMediaRef: String? = null
    private var cachedAudioDurationMs: Long = 0L
    private var cachedPlaybackState = AudioPlaybackUiState()
    private var cachedLocationName: String? = null
    private var cachedLatitude: Double? = null
    private var cachedLongitude: Double? = null
    private var cachedJournalNames: List<String> = emptyList()
    private val labelResolver = AudioLabelResolver()

    init {
        observeNote()
        observePlaybackStatus()
        observeJournalMembership()
    }

    private fun observeNote() {
        viewModelScope.launch {
            notesRepository.allNotesObserved
                .catch { error ->
                    _uiState.value =
                        AudioNoteViewerUiState.Error(
                            "Failed to load audio note: ${error.message}",
                        )
                }.collect { notes ->
                    val note = notes.find { it.uid == noteId }
                    if (note == null) {
                        _uiState.value = AudioNoteViewerUiState.Error("Audio note not found")
                    } else {
                        updateForNote(note)
                    }
                }
        }
    }

    private fun updateForNote(note: JournalNote) {
        val audioNote = note as? JournalNote.Audio
        if (audioNote == null) {
            _uiState.value = AudioNoteViewerUiState.Error("Note is not audio")
            return
        }
        updateForAudio(audioNote)
    }

    private fun updateForAudio(note: JournalNote.Audio) {
        viewModelScope.launch {
            val durationMs =
                if (note.durationMs > 0) {
                    note.durationMs
                } else {
                    durationResolver.resolveDurationMs(note.mediaRef) ?: 0L
                }
            val shouldProcess =
                cachedAudioMediaRef != note.mediaRef ||
                    cachedAudioDurationMs != durationMs ||
                    cachedAudioContext == null

            if (shouldProcess) {
                cachedAudioMediaRef = note.mediaRef
                cachedAudioDurationMs = durationMs
                cachedAudioContext = null
                cachedLocationName = note.location?.displayName
                cachedLatitude = note.location?.effectiveLatitude
                cachedLongitude = note.location?.effectiveLongitude
                _uiState.value = AudioNoteViewerUiState.Loading
                processAudioContext(note, durationMs)
            } else {
                _uiState.value =
                    AudioNoteViewerUiState.Ready(
                        mediaRef = note.mediaRef,
                        durationMs = durationMs,
                        createdAt = note.creationTimestamp,
                        context = cachedAudioContext ?: return@launch,
                        playbackState = cachedPlaybackState,
                    )
            }
        }
    }

    private fun processAudioContext(
        note: JournalNote.Audio,
        durationMs: Long,
    ) {
        audioProcessingJob?.cancel()
        audioProcessingJob =
            viewModelScope.launch {
                val result =
                    runCatching {
                        audioContextProcessor.process(
                            audioUri = note.mediaRef,
                            durationMs = durationMs,
                            createdAt = note.creationTimestamp,
                            latitude = note.location?.effectiveLatitude,
                            longitude = note.location?.effectiveLongitude,
                        )
                    }.getOrNull()

                if (result == null) {
                    _uiState.value = AudioNoteViewerUiState.Error("Audio context unavailable")
                    return@launch
                }

                cachedAudioContext = result
                _uiState.value =
                    AudioNoteViewerUiState.Ready(
                        mediaRef = note.mediaRef,
                        durationMs = durationMs,
                        createdAt = note.creationTimestamp,
                        context = result,
                        playbackState = cachedPlaybackState,
                    )
            }
    }

    private fun observeJournalMembership() {
        viewModelScope.launch {
            getJournalMembership(setOf(noteId)).collect { membershipMap ->
                cachedJournalNames = membershipMap[noteId]?.map { it.title }.orEmpty()
            }
        }
    }

    private fun observePlaybackStatus() {
        statusProvider ?: return
        viewModelScope.launch {
            statusProvider.playbackStatus.collect { status ->
                val durationMs =
                    if (status.duration > Duration.ZERO) {
                        status.duration.inWholeMilliseconds
                    } else {
                        cachedAudioDurationMs
                    }
                cachedAudioDurationMs = durationMs
                cachedPlaybackState =
                    cachedPlaybackState.copy(
                        progress = status.progress,
                        isPlaying = status.isPlaying,
                    )

                _uiState.update { state ->
                    val ready = state as? AudioNoteViewerUiState.Ready ?: return@update state
                    ready.copy(
                        durationMs = durationMs,
                        playbackState = cachedPlaybackState,
                    )
                }
            }
        }
    }

    /**
     * Playback toggle for audio notes.
     */
    fun togglePlayback() {
        val content = _uiState.value as? AudioNoteViewerUiState.Ready ?: return
        if (content.playbackState.isPlaying) {
            audioPlaybackManager.pausePlayback()
            cachedPlaybackState = cachedPlaybackState.copy(isPlaying = false)
            _uiState.update { state ->
                val ready = state as? AudioNoteViewerUiState.Ready ?: return@update state
                ready.copy(
                    playbackState = ready.playbackState.copy(isPlaying = false),
                )
            }
        } else {
            viewModelScope.launch { startPlayback(content) }
        }
    }

    /**
     * Seek request for audio playback progress.
     */
    fun seekTo(progress: Float) {
        val safeProgress = progress.coerceIn(0f, 1f)
        audioPlaybackManager.seekTo(safeProgress)
        cachedPlaybackState = cachedPlaybackState.copy(progress = safeProgress)
        _uiState.update { state ->
            val ready = state as? AudioNoteViewerUiState.Ready ?: return@update state
            ready.copy(
                playbackState = ready.playbackState.copy(progress = safeProgress),
            )
        }
    }

    /**
     * Skip request for audio playback by milliseconds.
     */
    fun skipByMillis(deltaMs: Long) {
        val content = _uiState.value as? AudioNoteViewerUiState.Ready ?: return
        if (content.durationMs <= 0L) return
        val currentMs = (content.playbackState.progress * content.durationMs.toFloat()).roundToLong()
        val targetMs = (currentMs + deltaMs).coerceIn(0L, content.durationMs)
        val ratio = targetMs.toFloat() / content.durationMs.toFloat()
        seekTo(ratio)
    }

    private suspend fun startPlayback(content: AudioNoteViewerUiState.Ready) {
        val subtitle =
            formatDateLocalized(
                content.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date,
            )
        val labelResult =
            labelResolver.resolve(
                createdAt = content.createdAt,
                locationName = cachedLocationName,
                latitude = cachedLatitude,
                longitude = cachedLongitude,
            )
        val title = formatAudioLabelAsync(labelResult)
        val palette = content.context.palette
        val metadata =
            AudioPlaybackMetadata(
                title = title,
                subtitle = subtitle,
                noteId = noteId,
                journalNames = cachedJournalNames,
                accentColor = palette.accentColor,
                immersiveBackground = palette.immersiveBackground,
                gradientStart = palette.waveformGradientStart,
                gradientEnd = palette.waveformGradientEnd,
            )
        audioPlaybackManager.startPlayback(
            uri = content.mediaRef,
            metadata = metadata,
            onProgressUpdated = { progress ->
                cachedPlaybackState =
                    cachedPlaybackState.copy(
                        progress = progress,
                        isPlaying = true,
                    )
                _uiState.update { state ->
                    val ready = state as? AudioNoteViewerUiState.Ready ?: return@update state
                    ready.copy(
                        playbackState =
                            ready.playbackState.copy(
                                progress = progress,
                                isPlaying = true,
                            ),
                    )
                }
            },
            onPlaybackCompleted = {
                cachedPlaybackState =
                    cachedPlaybackState.copy(
                        progress = 1f,
                        isPlaying = false,
                    )
                _uiState.update { state ->
                    val ready = state as? AudioNoteViewerUiState.Ready ?: return@update state
                    ready.copy(
                        playbackState =
                            ready.playbackState.copy(
                                progress = 1f,
                                isPlaying = false,
                            ),
                    )
                }
            },
        )
    }

    override fun onCleared() {
        super.onCleared()
        audioProcessingJob?.cancel()
        audioPlaybackManager.release()
    }
}

/**
 * Playback status for audio notes.
 */
data class AudioPlaybackUiState(
    val progress: Float = 0f,
    val isPlaying: Boolean = false,
)

/**
 * Type-safe UI state for audio note viewing.
 */
sealed interface AudioNoteViewerUiState {
    data object Loading : AudioNoteViewerUiState

    data class Error(
        val message: String,
    ) : AudioNoteViewerUiState

    /**
     * Ready state for audio notes with context and playback status.
     */
    data class Ready(
        val mediaRef: String,
        val durationMs: Long,
        val createdAt: Instant,
        val context: AudioContext,
        val playbackState: AudioPlaybackUiState,
    ) : AudioNoteViewerUiState
}
