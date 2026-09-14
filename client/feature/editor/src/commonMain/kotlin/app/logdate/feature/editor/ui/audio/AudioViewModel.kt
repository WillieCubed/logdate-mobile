package app.logdate.feature.editor.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.AudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.media.audio.AudioPlaybackStatusProvider
import app.logdate.client.media.audio.download.ModelDownloadStatus
import app.logdate.client.media.audio.tagging.AudioTaggingService
import app.logdate.client.media.audio.transcription.TranscriptionResult
import app.logdate.client.media.audio.transcription.TranscriptionService
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import app.logdate.feature.editor.ui.editor.delegate.PendingAudioResolver
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid
import app.logdate.client.media.audio.AudioRecordingManager as MediaAudioRecordingManager

/**
 * Unified ViewModel for managing both audio recording and playback functionality.
 * Handles recording state, playback, audio levels, duration, and transcription.
 * Now supports pause/resume functionality for recording.
 */
class AudioViewModel(
    private val audioRecordingManager: MediaAudioRecordingManager,
    private val audioPlaybackManager: AudioPlaybackManager,
    private val audioDurationResolver: AudioDurationResolver,
    private val transcriptionService: TranscriptionService,
    private val audioTaggingService: AudioTaggingService,
) : ViewModel(),
    PendingAudioResolver {
    private val playbackStatusProvider = audioPlaybackManager as? AudioPlaybackStatusProvider

    // StateFlow to expose immutable UI state
    private val _uiState = MutableStateFlow(AudioUiState())
    val uiState: StateFlow<AudioUiState> = _uiState.asStateFlow()
    private var audioLevelJob: Job? = null
    private var durationJob: Job? = null
    private var recordingStateJob: Job? = null
    private var structuredTranscriptionJob: Job? = null

    /**
     * Serializes every recording transition so a tap that lands while a start or stop is
     * still in flight waits its turn and then sees the settled state. Without this, two quick
     * taps on Record raced each other: the second was refused by the platform and overwrote
     * the first's "recording" state with "idle" while the microphone stayed live.
     */
    private val recordingMutex = Mutex()

    /**
     * Held across stop/restart so a [restartRecording] call still persists the
     * refined transcript under the same eventual note id.
     */
    private var lastTargetNoteId: Uuid? = null

    init {
        audioRecordingManager.setTranscriptionService(transcriptionService)
        observeEnhancedModelDownloads()
    }

    /**
     * Combines the two services' download status flows so the editor can
     * render a single banner. The flows live on each service's own
     * application-scoped state, so a download started in one editor session
     * is still visible the next time the user opens an audio note.
     */
    private fun observeEnhancedModelDownloads() {
        viewModelScope.launch {
            combine(
                transcriptionService.offlineModelDownloadStatus,
                audioTaggingService.modelDownloadStatus,
            ) { transcription, tagging ->
                deriveEnhancedAudioModelStatus(transcription, tagging)
            }.distinctUntilChanged()
                .collect { combined ->
                    _uiState.update { it.copy(enhancedModelStatus = combined) }
                }
        }
    }

    /**
     * User tap on the "Download enhanced models" CTA. Both services dedupe
     * concurrent triggers internally, so kicking both unconditionally is safe
     * and keeps the call site simple.
     */
    fun downloadEnhancedAudioModels() {
        transcriptionService.startOfflineModelDownload()
        audioTaggingService.startModelDownload()
    }

    /**
     * Starts audio recording and updates the UI state.
     *
     * @param targetNoteId The UUID that the eventual saved audio note will use.
     *   When supplied, the recording manager persists refined transcription
     *   results to the database under this id, so the polished transcript
     *   shows up the next time the note is loaded — even if this view model
     *   has long since been cleared.
     */
    fun startRecording(targetNoteId: Uuid? = null) {
        viewModelScope.launch { startRecordingInternal(targetNoteId) }
    }

    private suspend fun startRecordingInternal(targetNoteId: Uuid?) {
        recordingMutex.withLock {
            if (!admitStart()) return
            Napier.d("AudioViewModel: Starting recording")
            try {
                if (_uiState.value.isPlaying) {
                    audioPlaybackManager.stopPlayback()
                }
                lastTargetNoteId = targetNoteId
                val started = audioRecordingManager.startRecording(targetNoteId)
                if (!started) {
                    failStart("Recording could not start")
                    return
                }
                startRecordingCollectors()
                _uiState.update {
                    it.copy(
                        isRecording = true,
                        isStartingRecording = false,
                        isPaused = false,
                        isPlaying = false,
                        audioLevels = emptyList(),
                        duration = Duration.ZERO,
                        transcriptionState = initialTranscriptionState(it.transcriptionState),
                        error = null,
                        recordingFilePath = audioRecordingManager.currentRecordingPath,
                        recordingTargetNoteId = targetNoteId,
                    )
                }
                Napier.d("AudioViewModel: Recording started")
            } catch (e: Exception) {
                Napier.e("Failed to start recording: ${e.message}", e)
                failStart("Recording could not start")
            }
        }
    }

    /**
     * Marks a start as in flight unless a session is already active or starting. Runs under
     * [recordingMutex], so a second tap queued behind the first sees the first's outcome.
     */
    private fun admitStart(): Boolean {
        while (true) {
            val current = _uiState.value
            if (current.isRecording || current.isStartingRecording) {
                Napier.d("AudioViewModel: Ignoring start while a session is active or starting")
                return false
            }
            val admitted =
                current.copy(
                    isStartingRecording = true,
                    error = null,
                    failedRecordingTargetNoteId = null,
                )
            if (_uiState.compareAndSet(current, admitted)) return true
        }
    }

    private fun failStart(message: String) {
        _uiState.update {
            it.copy(
                isRecording = false,
                isStartingRecording = false,
                recordingTargetNoteId = null,
                recordingFilePath = null,
                error = message,
            )
        }
    }

    private fun initialTranscriptionState(current: AudioUiState.TranscriptionState): AudioUiState.TranscriptionState =
        when (current) {
            is AudioUiState.TranscriptionState.Error -> current
            else ->
                if (transcriptionService.supportsLiveTranscription || transcriptionService.supportsFileTranscription) {
                    AudioUiState.TranscriptionState.InProgress
                } else {
                    AudioUiState.TranscriptionState.NotRequested
                }
        }

    /**
     * Stops audio recording, saves the file, and updates the UI state.
     */
    fun stopRecording() {
        viewModelScope.launch {
            recordingMutex.withLock { stopRecordingInternal() }
        }
    }

    /** Must be called with [recordingMutex] held. A stop while idle is ignored. */
    private suspend fun stopRecordingInternal() {
        val session = _uiState.value
        if (!session.isRecording) {
            Napier.d("AudioViewModel: Ignoring stop while not recording")
            return
        }
        Napier.d("AudioViewModel: Stopping recording")
        try {
            val uri = audioRecordingManager.stopRecording()
            stopRecordingCollectors()
            val resolvedDuration =
                uri
                    ?.let { audioDurationResolver.resolveDurationMs(it) }
                    ?.milliseconds
            _uiState.update {
                it.copy(
                    isRecording = false,
                    isPaused = false,
                    recordedAudioUri = uri,
                    recordingFilePath = null,
                    recordingTargetNoteId = null,
                    duration = resolvedDuration ?: it.duration,
                    transcriptionState = transcriptionStateAfterStop(it.transcriptionState),
                    failedRecordingTargetNoteId = if (uri == null) session.recordingTargetNoteId else null,
                    error = if (uri == null) "Recording could not be saved" else it.error,
                )
            }
            Napier.d("AudioViewModel: Recording stopped, URI: $uri")
        } catch (e: Exception) {
            Napier.e("Failed to stop recording: ${e.message}", e)
            stopRecordingCollectors()
            _uiState.update {
                it.copy(
                    isRecording = false,
                    isPaused = false,
                    recordingFilePath = null,
                    recordingTargetNoteId = null,
                    failedRecordingTargetNoteId = session.recordingTargetNoteId,
                    error = "Recording could not be saved",
                )
            }
        }
    }

    private fun transcriptionStateAfterStop(current: AudioUiState.TranscriptionState): AudioUiState.TranscriptionState =
        when (current) {
            is AudioUiState.TranscriptionState.Success -> current
            else ->
                if (transcriptionService.supportsFileTranscription) {
                    AudioUiState.TranscriptionState.InProgress
                } else {
                    AudioUiState.TranscriptionState.NotRequested
                }
        }

    /**
     * Pauses the current recording.
     * Only works on platforms that support pause functionality.
     */
    fun pauseRecording() {
        viewModelScope.launch {
            Napier.d("AudioViewModel: Pausing recording")
            try {
                val success = audioRecordingManager.pauseRecording()

                if (success) {
                    _uiState.update { it.copy(isPaused = true, isRecording = true) }
                    Napier.d("Recording paused successfully")
                } else {
                    Napier.w("Failed to pause recording - functionality may not be supported on this platform")
                }
            } catch (e: Exception) {
                Napier.e("Error pausing recording: ${e.message}", e)
            }
        }
    }

    /**
     * Resumes a paused recording.
     * Only works on platforms that support resume functionality.
     */
    fun resumeRecording() {
        viewModelScope.launch {
            Napier.d("AudioViewModel: Resuming recording")
            try {
                val success = audioRecordingManager.resumeRecording()

                if (success) {
                    _uiState.update { it.copy(isPaused = false, isRecording = true) }
                    Napier.d("Recording resumed successfully")
                } else {
                    Napier.w("Failed to resume recording - functionality may not be supported on this platform")
                }
            } catch (e: Exception) {
                Napier.e("Error resuming recording: ${e.message}", e)
            }
        }
    }

    /**
     * Restarts the recording from scratch, clearing accumulated transcription.
     */
    fun restartRecording() {
        viewModelScope.launch {
            recordingMutex.withLock {
                if (!_uiState.value.isRecording) return@withLock
                stopRecordingInternal()
                audioRecordingManager.resetTranscription()
                _uiState.update {
                    it.copy(
                        transcriptionState = AudioUiState.TranscriptionState.NotRequested,
                        audioLevels = emptyList(),
                        duration = Duration.ZERO,
                        recordedAudioUri = null,
                        failedRecordingTargetNoteId = null,
                        error = null,
                    )
                }
            }
            startRecordingInternal(targetNoteId = lastTargetNoteId)
        }
    }

    /**
     * Toggles recording between recording and paused states.
     */
    fun toggleRecordingPause() {
        if (_uiState.value.isRecording) {
            if (_uiState.value.isPaused) {
                resumeRecording()
            } else {
                pauseRecording()
            }
        }
    }

    /**
     * Toggles playback between playing and paused states.
     */
    fun togglePlayback(
        uri: String,
        metadata: AudioPlaybackMetadata? = null,
    ) {
        if (_uiState.value.isPlaying) {
            pausePlayback()
        } else {
            startPlayback(uri, metadata)
        }
    }

    /**
     * Starts playing the audio from the given URI.
     */
    fun startPlayback(
        uri: String,
        metadata: AudioPlaybackMetadata? = null,
    ) {
        viewModelScope.launch {
            Napier.d("AudioViewModel: Starting playback of $uri")
            try {
                // Stop any ongoing recording and await completion before starting playback
                recordingMutex.withLock { stopRecordingInternal() }

                _uiState.update {
                    it.copy(
                        isPlaying = true,
                        currentUri = uri,
                        isRecording = false,
                        isPaused = false,
                    )
                }

                audioPlaybackManager.startPlayback(
                    uri = uri,
                    metadata = metadata,
                    onProgressUpdated = { progress ->
                        _uiState.update { it.copy(playbackProgress = progress) }
                    },
                    onPlaybackCompleted = {
                        val failure = playbackStatusProvider?.playbackStatus?.value?.errorMessage
                        _uiState.update {
                            it.copy(
                                isPlaying = false,
                                playbackProgress = 0f,
                                error = if (failure != null) "Failed to play back recording" else null,
                            )
                        }
                    },
                )
            } catch (e: Exception) {
                Napier.e("Failed to start playback: ${e.message}", e)
                _uiState.update { it.copy(isPlaying = false, error = "Failed to start playback") }
            }
        }
    }

    /**
     * Pauses the current playback.
     */
    fun pausePlayback() {
        viewModelScope.launch {
            Napier.d("AudioViewModel: Pausing playback")
            try {
                audioPlaybackManager.pausePlayback()
                _uiState.update { it.copy(isPlaying = false) }
            } catch (e: Exception) {
                Napier.e("Failed to pause playback: ${e.message}", e)
            }
        }
    }

    /**
     * Seeks to a specific position in the audio.
     */
    fun seekTo(position: Float) {
        viewModelScope.launch {
            Napier.d("AudioViewModel: Seeking to $position")
            try {
                audioPlaybackManager.seekTo(position)
                _uiState.update { it.copy(playbackProgress = position) }
            } catch (e: Exception) {
                Napier.e("Failed to seek: ${e.message}", e)
            }
        }
    }

    /**
     * Seeks to an absolute position in milliseconds.
     */
    fun seekToPositionMs(
        positionMs: Long,
        durationMs: Long,
    ) {
        viewModelScope.launch {
            Napier.d("AudioViewModel: Seeking to ${positionMs}ms")
            try {
                audioPlaybackManager.seekTo(positionMs, durationMs)
                if (durationMs > 0L) {
                    _uiState.update {
                        it.copy(
                            playbackProgress =
                                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                        )
                    }
                }
            } catch (e: Exception) {
                Napier.e("Failed to seek to ${positionMs}ms: ${e.message}", e)
            }
        }
    }

    /**
     * Clean up resources when ViewModel is cleared.
     *
     * AudioRecordingManager and the underlying TranscriptionService are
     * application-scoped singletons — they MUST outlive any individual
     * ViewModel so an in-flight Whisper refinement pass can run to completion
     * after the user navigates away from the editor. We only release things
     * that this ViewModel actually owns: the local flow collectors. If a
     * recording is still active when the screen goes away, we ask the
     * singleton to stop it on its own scope (the foreground service would
     * otherwise dangle), but we never tear the singleton itself down here.
     */
    override fun onCleared() {
        super.onCleared()
        Napier.d("AudioViewModel: Being cleared")
        try {
            stopRecordingCollectors()
            val state = _uiState.value
            if (audioRecordingManager.isRecording || state.isRecording || state.isStartingRecording) {
                audioRecordingManager.requestStopRecording()
            }
            // AudioPlaybackManager is also a process-lifetime singleton.
            // Stop the current track so the user doesn't hear it after
            // leaving the editor, but never call release() — that tears
            // down the controller and the underlying MediaSessionService
            // is meant to outlive any single view model.
            if (_uiState.value.isPlaying) {
                audioPlaybackManager.stopPlayback()
            }
        } catch (e: Exception) {
            Napier.e("Error cleaning up audio resources: ${e.message}", e)
        }
    }

    fun clearRecordedAudio() {
        _uiState.update { it.copy(recordedAudioUri = null) }
    }

    /**
     * Surfaces a pending recording so the editor's save path can absorb a URI
     * that hasn't yet been transferred to the block via the Compose-side
     * `LaunchedEffect` in `AudioBlockEditor`.
     *
     * Returns null when no recording state is associated with [blockId].
     * Drives an active recording to completion before returning.
     */
    override suspend fun resolvePending(blockId: Uuid): AudioCaptureState? {
        if (lastTargetNoteId != blockId) return null
        return recordingMutex.withLock {
            val initial = _uiState.value
            val pendingUri = initial.recordedAudioUri
            if (pendingUri != null) {
                _uiState.update { it.copy(recordedAudioUri = null) }
                return@withLock AudioCaptureState.Ready(uri = pendingUri, durationMs = initial.duration.inWholeMilliseconds)
            }
            if (!initial.isRecording) return@withLock null
            stopRecordingInternal()
            val after = _uiState.value
            val resolvedUri =
                after.recordedAudioUri
                    ?: return@withLock AudioCaptureState.Failed("Recording could not be finalized")
            _uiState.update { it.copy(recordedAudioUri = null) }
            AudioCaptureState.Ready(uri = resolvedUri, durationMs = after.duration.inWholeMilliseconds)
        }
    }

    /**
     * Resets playback state for a new block. Call when the block being displayed changes
     * (e.g. after deleting a recording and opening another one).
     */
    fun resetPlaybackState() {
        stopRecordingCollectors()
        if (_uiState.value.isPlaying) {
            audioPlaybackManager.stopPlayback()
        }
        _uiState.update {
            it.copy(
                isPlaying = false,
                playbackProgress = 0f,
                currentUri = null,
                recordedAudioUri = null,
                duration = Duration.ZERO,
                audioLevels = emptyList(),
                // Preserve InProgress transcription — a file-transcription job may still be running
                transcriptionState =
                    if (it.transcriptionState is AudioUiState.TranscriptionState.InProgress) {
                        it.transcriptionState
                    } else {
                        AudioUiState.TranscriptionState.NotRequested
                    },
                error = null,
            )
        }
    }

    private fun startRecordingCollectors() {
        audioLevelJob?.cancel()
        durationJob?.cancel()
        recordingStateJob?.cancel()
        recordingStateJob =
            viewModelScope.launch {
                audioRecordingManager
                    .getRecordingStateFlow()
                    // Ignore anything before the platform confirms this session is live; the
                    // first false after that is a stop the editor did not initiate.
                    .dropWhile { active -> !active }
                    .collect { active ->
                        if (!active && _uiState.value.isRecording) {
                            Napier.d("AudioViewModel: Recording ended outside the editor; finalizing")
                            stopRecording()
                        }
                    }
            }
        audioLevelJob =
            viewModelScope.launch {
                audioRecordingManager.getAudioLevelFlow().collect { level ->
                    _uiState.update { state ->
                        val levels = (state.audioLevels + level).takeLast(50)
                        state.copy(audioLevels = levels)
                    }
                }
            }
        durationJob =
            viewModelScope.launch {
                audioRecordingManager.getRecordingDurationFlow().collect { duration ->
                    _uiState.update { it.copy(duration = duration) }
                }
            }
        startTranscriptionCollector()
    }

    private fun stopRecordingCollectors() {
        audioLevelJob?.cancel()
        durationJob?.cancel()
        recordingStateJob?.cancel()
        structuredTranscriptionJob?.cancel()
        audioLevelJob = null
        durationJob = null
        recordingStateJob = null
        structuredTranscriptionJob = null
    }

    private fun startTranscriptionCollector() {
        if (structuredTranscriptionJob != null) return
        structuredTranscriptionJob =
            viewModelScope.launch {
                audioRecordingManager
                    .getStructuredTranscriptionFlow()
                    // Whisper refinement emits one Success per utterance, but consecutive
                    // utterances often produce identical text (or differ only in trailing
                    // partial). Skipping no-op updates avoids spurious StateFlow emissions
                    // and the AnimatedContent crossfade re-running for nothing.
                    .distinctUntilChanged()
                    .collect { result ->
                        when (result) {
                            is TranscriptionResult.Success -> {
                                _uiState.update {
                                    it.copy(
                                        transcriptionState =
                                            AudioUiState.TranscriptionState.Success(
                                                text = result.text,
                                                timedTranscript = result.timedTranscript,
                                                isFinal = result.isFinal,
                                                isRefining = result.isRefining,
                                            ),
                                    )
                                }
                            }
                            is TranscriptionResult.Error -> {
                                _uiState.update {
                                    it.copy(
                                        transcriptionState = AudioUiState.TranscriptionState.Error(result.reason),
                                    )
                                }
                            }
                            is TranscriptionResult.InProgress -> {
                                _uiState.update {
                                    if (it.transcriptionState is AudioUiState.TranscriptionState.Success) {
                                        it
                                    } else {
                                        it.copy(transcriptionState = AudioUiState.TranscriptionState.InProgress)
                                    }
                                }
                            }
                            TranscriptionResult.Cancelled -> {
                                _uiState.update {
                                    it.copy(transcriptionState = AudioUiState.TranscriptionState.NotRequested)
                                }
                            }
                        }
                    }
            }
    }
}

/**
 * Combines the per-model download statuses into the single
 * [EnhancedAudioModelStatus] the audio editor banner reads. The banner
 * disappears once both models are present, and otherwise reflects the
 * highest-priority state across the two — a failure wins over a download in
 * progress, which wins over an idle missing model.
 */
internal fun deriveEnhancedAudioModelStatus(
    transcription: ModelDownloadStatus,
    tagging: ModelDownloadStatus,
): EnhancedAudioModelStatus {
    val both = listOf(transcription, tagging)

    val failure = both.firstOrNull { it is ModelDownloadStatus.Failure }
    if (failure != null) return EnhancedAudioModelStatus.Failed(failure)

    val downloads = both.filterIsInstance<ModelDownloadStatus.Downloading>()
    if (downloads.isNotEmpty()) {
        // Single pass: bail to indeterminate (null) the moment we see an
        // unknown total; otherwise accumulate the sum and divide once.
        var sum = 0.0
        var hasUnknown = false
        for (download in downloads) {
            val fraction = download.fraction
            if (fraction == null) {
                hasUnknown = true
                break
            }
            sum += fraction
        }
        val combined = if (hasUnknown) null else (sum / downloads.size).toFloat()
        return EnhancedAudioModelStatus.Downloading(combined)
    }

    if (both.any { it == ModelDownloadStatus.Extracting }) {
        return EnhancedAudioModelStatus.Downloading(null)
    }

    if (both.all { it == ModelDownloadStatus.Completed }) {
        return EnhancedAudioModelStatus.Ready
    }

    return EnhancedAudioModelStatus.NotDownloaded
}
