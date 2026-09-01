package app.logdate.client.media.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import app.logdate.client.media.audio.tagging.AudioTaggingResult
import app.logdate.client.media.audio.tagging.AudioTaggingService
import app.logdate.client.media.audio.transcription.TranscriptionFailure
import app.logdate.client.media.audio.transcription.TranscriptionPersistenceRetrier
import app.logdate.client.media.audio.transcription.TranscriptionResult
import app.logdate.client.media.audio.transcription.TranscriptionService
import app.logdate.client.media.audio.transcription.TranscriptionStartResult
import app.logdate.client.media.audio.transcription.stopLiveTranscriptionWithHandoff
import app.logdate.client.media.audio.transcription.toTranscriptDocument
import app.logdate.client.media.device.AudioRouteRepository
import app.logdate.client.repository.audio.AudioTag
import app.logdate.client.repository.audio.AudioTagRepository
import app.logdate.client.repository.transcription.TranscriptDocumentStatus
import app.logdate.client.repository.transcription.TranscriptSource
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.repository.transcription.TranscriptionStatus
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/**
 * Implementation of AudioRecordingManager for Android
 */
class AndroidAudioRecordingManager(
    private val context: Context,
    private val audioStorage: AudioStorage,
    private val transcriptionRepository: TranscriptionRepository,
    private val audioTaggingService: AudioTaggingService,
    private val audioTagRepository: AudioTagRepository,
    private val audioRouteRepository: AudioRouteRepository,
) : AudioRecordingManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val audioLevelFlow = MutableStateFlow(0f)
    private val durationFlow = MutableStateFlow(0L)
    private val transcriptionFlow = MutableStateFlow<String?>(null)
    private val structuredTranscriptionFlow = MutableStateFlow<TranscriptionResult?>(null)
    private var transcriptionService: TranscriptionService? = null
    private var recordingTarget: AudioRecordingTarget? = null

    /**
     * The eventual saved-note UUID for the current recording session, captured
     * at [startRecording] time. Refined transcription emissions are written to
     * the repository under this id so the polished transcript persists across
     * the editor view-model lifecycle and shows up in the saved note later.
     * Cleared when a new session begins or recording stops without a target.
     */
    private var sessionTargetNoteId: Uuid? = null

    // Service connection
    private var recordingService: AudioRecordingService? = null
    private var recordingActive = false
    private var serviceBound = false
    private var startRequested = false
    private var serviceStateJob: Job? = null
    private var routeSyncJob: Job? = null
    private var transcriptPersistenceJob: Job? = null
    private var transcriptionCollectorJob: Job? = null
    private var transcriptionWarmUpJob: Job? = null
    private var liveTranscriptionStarted = false
    private val persistenceRetrier = TranscriptionPersistenceRetrier()

    private val recordingDurationFlow: Flow<Duration> = durationFlow.map { it.milliseconds }

    // Service binding
    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                val binder = service as AudioRecordingService.AudioServiceBinder
                recordingService = binder.getService()
                serviceBound = true

                routeSyncJob?.cancel()
                routeSyncJob =
                    scope.launch {
                        audioRouteRepository.inputDevices.collect { selection ->
                            if (recordingActive && serviceBound) {
                                recordingService?.updatePreferredInputDevice(selection.selectedDeviceId)
                            }
                        }
                    }

                serviceStateJob?.cancel()
                serviceStateJob =
                    scope.launch {
                        recordingService?.recordingState?.collect { serviceState ->
                            audioLevelFlow.value = serviceState.audioLevel
                            durationFlow.value = serviceState.durationSeconds.toLong() * 1000
                            recordingActive = serviceState.isRecording
                            // Clear startRequested whether or not recording started — if the service
                            // never transitions to isRecording=true (failure path), this prevents
                            // startRequested from getting stuck true indefinitely.
                            startRequested = false

                            if (!serviceState.isRecording && serviceBound && !startRequested) {
                                // Recording has stopped from the service side
                                unbindServiceSafely()
                            }
                        }
                    }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                serviceStateJob?.cancel()
                serviceStateJob = null
                routeSyncJob?.cancel()
                routeSyncJob = null
                recordingService = null
                serviceBound = false
            }
        }

    override val isRecording: Boolean
        get() = recordingActive

    override val currentRecordingPath: String?
        get() = recordingTarget?.path

    override fun setTranscriptionService(service: TranscriptionService) {
        this.transcriptionService = service

        // Eagerly warm up models so they're ready when the user taps Record
        transcriptionWarmUpJob?.cancel()
        transcriptionWarmUpJob =
            scope.launch(Dispatchers.IO) {
                try {
                    service.warmUp()
                } catch (e: Exception) {
                    Napier.e("Transcription model warm-up failed", e)
                }
            }

        // Listen for transcription updates. This collector lives on the
        // singleton's own scope so a Whisper refinement pass that is still
        // mid-utterance when the editor view model goes away will continue
        // to flow through here and get persisted to the database.
        transcriptionCollectorJob?.cancel()
        transcriptionCollectorJob =
            scope.launch {
                service.getTranscriptionFlow().collectLatest { result ->
                    when (result) {
                        is TranscriptionResult.Success -> {
                            transcriptionFlow.value = result.text
                            structuredTranscriptionFlow.value = result
                            scheduleTranscriptPersistence(result)
                        }
                        is TranscriptionResult.Error -> {
                            Napier.e("Transcription error: ${result.reason}")
                            if (result.reason != TranscriptionFailure.PersistenceError) {
                                liveTranscriptionStarted = false
                            }
                            structuredTranscriptionFlow.value = result
                        }
                        is TranscriptionResult.InProgress -> {
                            // Just wait for the text
                            structuredTranscriptionFlow.value = result
                        }
                        TranscriptionResult.Cancelled -> {
                            structuredTranscriptionFlow.value = result
                        }
                    }
                }
            }
    }

    /**
     * Streams the recorded file through the on-device ambient sound tagger
     * and persists each cumulative result to the audio tag repository under
     * [noteId]. The tagger emits progressively as windows complete, so the
     * note's tag set in the database fills in over the seconds after the
     * user stops recording. Ambient tagging remains outside the critical path,
     * but unavailable and failed results are logged for diagnosis.
     */
    private fun runAmbientTagging(
        audioPath: String,
        noteId: Uuid,
    ) {
        scope.launch {
            try {
                audioTaggingService.tagAudio(audioPath).collect { result ->
                    when (result) {
                        is AudioTaggingResult.Success -> {
                            val domainTags =
                                result.sounds.map { sound ->
                                    AudioTag(
                                        noteId = noteId,
                                        soundName = sound.name,
                                        confidence = sound.confidence,
                                        startMs = sound.startMs,
                                        durationMs = sound.durationMs,
                                    )
                                }
                            audioTagRepository.replaceTagsForNote(noteId, domainTags)
                        }
                        is AudioTaggingResult.Error -> {
                            Napier.w("Audio tagging failed for $noteId: ${result.message}")
                        }
                        AudioTaggingResult.Unavailable -> {
                            Napier.d("Audio tagging unavailable for $noteId because its optional model is not installed")
                        }
                    }
                }
            } catch (e: Exception) {
                Napier.e("Audio tagging coroutine failed for $noteId", e)
            }
        }
    }

    /**
     * Writes a transcription result to the repository under the current
     * session's [sessionTargetNoteId], so the polished text survives the
     * editor view model and is visible to any later viewer that loads the
     * note. Skipped when no note id was supplied to [startRecording] (e.g.
     * Wear OS, tests) or when no repository was injected.
     *
     * Status is COMPLETED once refinement finishes (or when refinement
     * isn't running because the Whisper model isn't on device); it's
     * IN_PROGRESS while utterances are still being rewritten so any UI
     * observing the note can show the right state.
     */
    private suspend fun persistRefinedTranscript(result: TranscriptionResult.Success) {
        val noteId = sessionTargetNoteId ?: return
        if (result.text.isBlank()) return
        // Skip intermediate live-streaming results. The audio note may not be
        // in the database yet while recording is active (auto-save fires after
        // the recording stops), so any updateTranscription() call here would
        // fail. Only the Whisper refinement pass — which runs after recording
        // has stopped and the note has been auto-saved — should be persisted.
        if (recordingActive) return
        val status =
            if (result.isFinal && !result.isRefining) {
                TranscriptionStatus.COMPLETED
            } else {
                TranscriptionStatus.IN_PROGRESS
            }
        val persisted =
            persistenceRetrier.persist {
                try {
                    val timedTranscript = result.timedTranscript
                    if (timedTranscript != null) {
                        transcriptionRepository.updateTranscriptDocument(
                            noteId = noteId,
                            document =
                                timedTranscript.toTranscriptDocument(
                                    status =
                                        if (status == TranscriptionStatus.COMPLETED) {
                                            TranscriptDocumentStatus.FINAL
                                        } else {
                                            TranscriptDocumentStatus.REFINING
                                        },
                                    source =
                                        if (result.isRefining) {
                                            TranscriptSource.LOCAL_REFINEMENT
                                        } else {
                                            TranscriptSource.LOCAL_LIVE
                                        },
                                ),
                            status = status,
                        )
                    } else {
                        transcriptionRepository.updateTranscription(
                            noteId = noteId,
                            text = result.text,
                            status = status,
                        )
                    }
                } catch (e: Exception) {
                    Napier.e("Transcript persistence attempt failed for $noteId", e)
                    false
                }
            }
        if (!persisted) {
            Napier.e("Transcript persistence exhausted retries for $noteId")
            structuredTranscriptionFlow.value =
                TranscriptionResult.Error(TranscriptionFailure.PersistenceError)
        }
    }

    private fun scheduleTranscriptPersistence(result: TranscriptionResult.Success) {
        if (result.text.isBlank() || recordingActive || sessionTargetNoteId == null) return
        transcriptPersistenceJob?.cancel()
        transcriptPersistenceJob =
            scope.launch(Dispatchers.IO) {
                persistRefinedTranscript(result)
            }
    }

    override suspend fun startRecording(targetNoteId: Uuid?): Boolean {
        if (recordingActive || startRequested) {
            Napier.w("Attempted to start recording while already recording or start pending")
            return false
        }

        startRequested = true
        try {
            recordingTarget = audioStorage.createRecordingTarget()
            sessionTargetNoteId = targetNoteId
            liveTranscriptionStarted = false
            transcriptionFlow.value = null
            structuredTranscriptionFlow.value = null
            // Start foreground service for recording
            context.startAudioRecordingService(
                outputFilePath = recordingTarget?.path,
                inputDeviceId = audioRouteRepository.inputDevices.value.selectedDeviceId,
            )

            // Bind to the service to get updates
            // recordingActive will be set true via onServiceConnected → service state flow
            if (!bindToService()) {
                context.stopAudioRecordingService()
                startRequested = false
                Napier.e("Recording service rejected the bind request")
                return false
            }

            // Start live transcription in parallel with recording.
            // SherpaOnnxTranscriptionService uses AudioRecord (no audio focus) so music keeps playing.
            try {
                val service = transcriptionService
                if (service == null) {
                    structuredTranscriptionFlow.value =
                        TranscriptionResult.Error(
                            TranscriptionFailure.NotAvailable,
                        )
                } else {
                    service.resetTranscription()
                    if (service.supportsLiveTranscription) {
                        when (val start = service.startLiveTranscription()) {
                            TranscriptionStartResult.Started,
                            TranscriptionStartResult.AlreadyRunning,
                            -> liveTranscriptionStarted = true
                            is TranscriptionStartResult.Failed -> {
                                structuredTranscriptionFlow.value = TranscriptionResult.Error(start.reason)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Napier.e("Transcription failed to start; audio recording will continue", e)
                structuredTranscriptionFlow.value = TranscriptionResult.Error(TranscriptionFailure.Unknown)
            }

            return true
        } catch (e: Exception) {
            startRequested = false
            Napier.e("Error starting recording service", e)
            return false
        }
    }

    private fun bindToService(): Boolean =
        try {
            val intent = Intent(context, AudioRecordingService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            // serviceBound is set to true in onServiceConnected, not here
        } catch (e: Exception) {
            Napier.e("Error binding to recording service", e)
            false
        }

    private fun unbindServiceSafely() {
        if (serviceBound) {
            try {
                context.unbindService(serviceConnection)
                serviceBound = false
                routeSyncJob?.cancel()
                routeSyncJob = null
            } catch (e: Exception) {
                Napier.e("Error unbinding from service", e)
            }
        }
    }

    override suspend fun stopRecording(): String? {
        if (!recordingActive) {
            Napier.w("Attempted to stop recording while not recording")
            return null
        }

        try {
            // Capture file path before stopping the service (service may disconnect before we can query it)
            val filePath = recordingService?.getRecordedFilePath() ?: recordingTarget?.path

            // Stop the service
            context.stopAudioRecordingService()

            // Unbind from service
            unbindServiceSafely()

            recordingActive = false
            recordingTarget = null
            routeSyncJob?.cancel()
            routeSyncJob = null

            // Stop live transcription
            transcriptionService?.let { service ->
                val liveSessionWasHealthy = liveTranscriptionStarted
                val stopResult =
                    if (service.supportsLiveTranscription) {
                        try {
                            stopLiveTranscriptionWithHandoff(service) { result ->
                                transcriptionFlow.value = result.text
                                structuredTranscriptionFlow.value = result
                                scheduleTranscriptPersistence(result)
                            }
                        } catch (e: Exception) {
                            Napier.e("Transcription failed while stopping; recovering from the recorded file", e)
                            TranscriptionResult.Error(TranscriptionFailure.Unknown)
                        }
                    } else {
                        TranscriptionResult.Error(TranscriptionFailure.NotSupported)
                    }
                liveTranscriptionStarted = false

                // File transcription is the recovery path when a live session
                // could not start or terminated with an error. A healthy live
                // session already produced the realtime transcript.
                if (
                    (!liveSessionWasHealthy || stopResult is TranscriptionResult.Error) &&
                    service.supportsFileTranscription &&
                    filePath != null
                ) {
                    scope.launch {
                        val result = service.transcribeAudioFile(filePath)
                        if (result is TranscriptionResult.Success) {
                            transcriptionFlow.value = result.text
                            scheduleTranscriptPersistence(result)
                        }
                        structuredTranscriptionFlow.value = result
                    }
                }
            }

            // Kick off ambient sound detection on the saved recording. Runs on
            // the singleton's own scope so it survives the editor view model.
            val noteIdForTagging = sessionTargetNoteId
            if (filePath != null && noteIdForTagging != null) {
                runAmbientTagging(filePath, noteIdForTagging)
            }

            return filePath
        } catch (e: Exception) {
            Napier.e("Error stopping recording", e)
            release()
            return null
        }
    }

    /**
     * Pause the current recording
     * @return True if successfully paused
     */
    override suspend fun pauseRecording(): Boolean {
        val service = recordingService ?: return false
        if (!recordingActive) return false
        return try {
            service.pauseRecording()
            val paused = service.isRecordingPaused()
            if (paused) {
                transcriptionService?.stopLiveTranscription()
            }
            paused
        } catch (e: Exception) {
            Napier.e("Error pausing recording", e)
            false
        }
    }

    /**
     * Resume a paused recording
     * @return True if successfully resumed
     */
    override suspend fun resumeRecording(): Boolean {
        val service = recordingService ?: return false
        if (!recordingActive) return false
        return try {
            service.resumeRecording()
            val resumed = !service.isRecordingPaused()
            if (resumed) {
                transcriptionService?.let { svc ->
                    if (svc.supportsLiveTranscription) {
                        when (val start = svc.startLiveTranscription()) {
                            TranscriptionStartResult.Started,
                            TranscriptionStartResult.AlreadyRunning,
                            -> liveTranscriptionStarted = true
                            is TranscriptionStartResult.Failed -> {
                                structuredTranscriptionFlow.value = TranscriptionResult.Error(start.reason)
                            }
                        }
                    }
                }
            }
            resumed
        } catch (e: Exception) {
            Napier.e("Error resuming recording", e)
            false
        }
    }

    override suspend fun resetTranscription() {
        transcriptionService?.resetTranscription()
        transcriptionFlow.value = null
        structuredTranscriptionFlow.value = null
    }

    override fun getAudioLevelFlow(): Flow<Float> = audioLevelFlow

    override fun getRecordingDurationFlow(): Flow<Duration> = recordingDurationFlow

    override fun getTranscriptionFlow(): Flow<String?> = transcriptionFlow

    override fun getStructuredTranscriptionFlow(): Flow<TranscriptionResult> = structuredTranscriptionFlow.filterNotNull()

    override fun requestStopRecording() {
        // Caller is in a context with no usable coroutine scope (typically
        // ViewModel.onCleared after viewModelScope has died). Run the stop on
        // the singleton's own scope so the foreground service is released and
        // the Whisper refinement coroutine — already launched on the long-lived
        // transcription service scope — keeps running to completion.
        if (!recordingActive && !startRequested) return
        scope.launch {
            try {
                stopRecording()
            } catch (e: Exception) {
                Napier.e("Error stopping recording from background request", e)
            }
        }
    }

    override fun release() {
        scope.cancel()
        serviceStateJob?.cancel()
        serviceStateJob = null
        transcriptPersistenceJob?.cancel()
        transcriptPersistenceJob = null
        transcriptionWarmUpJob?.cancel()
        transcriptionWarmUpJob = null
        transcriptionCollectorJob?.cancel()
        transcriptionCollectorJob = null
        try {
            val wasRecording = recordingActive
            if (recordingActive) {
                // Stop the service
                context.stopAudioRecordingService()
                recordingActive = false
            }

            routeSyncJob?.cancel()
            routeSyncJob = null

            // Unbind from service if we were previously recording
            if (wasRecording) {
                unbindServiceSafely()
            }
        } catch (e: Exception) {
            Napier.e("Error releasing recording resources", e)
        }

        // Release transcription service
        transcriptionService?.release()
        recordingTarget = null
        transcriptionFlow.value = null
        structuredTranscriptionFlow.value = null
    }
}
