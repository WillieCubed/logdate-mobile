package app.logdate.client.media.audio

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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Implementation of AudioRecordingManager for Android.
 *
 * All session transitions run under one mutex on [workDispatcher], off the main thread. A
 * start only reports success once the foreground service confirms the recorder is running,
 * and a stop finalizes the file through the service binder before the service is torn down,
 * so the path handed back always points at a complete recording. The singleton stays usable
 * after any failure: nothing here cancels its scope or releases the shared transcription
 * service.
 */
class AndroidAudioRecordingManager(
    private val audioStorage: AudioStorage,
    private val transcriptionRepository: TranscriptionRepository,
    private val audioTaggingService: AudioTaggingService,
    private val audioTagRepository: AudioTagRepository,
    private val audioRouteRepository: AudioRouteRepository,
    private val serviceController: RecordingServiceController,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val startTimeout: Duration = DEFAULT_START_TIMEOUT,
) : AudioRecordingManager {
    private val scope = CoroutineScope(SupervisorJob() + workDispatcher)
    private val sessionMutex = Mutex()
    private val recordingStateFlow = MutableStateFlow(false)
    private val audioLevelFlow = MutableStateFlow(0f)
    private val durationFlow = MutableStateFlow(0L)
    private val transcriptionFlow = MutableStateFlow<String?>(null)
    private val structuredTranscriptionFlow = MutableStateFlow<TranscriptionResult?>(null)
    private val recordingDurationFlow: Flow<Duration> = durationFlow.map { it.milliseconds }

    @Volatile
    private var transcriptionService: TranscriptionService? = null

    @Volatile
    private var recordingTarget: AudioRecordingTarget? = null

    /**
     * The eventual saved-note UUID for the current recording session, captured
     * at [startRecording] time. Refined transcription emissions are written to
     * the repository under this id so the polished transcript persists across
     * the editor view-model lifecycle and shows up in the saved note later.
     */
    @Volatile
    private var sessionTargetNoteId: Uuid? = null

    /**
     * File the service reported when it ended the session on its own (notification action,
     * recorder failure). Handed back by the next [stopRecording] so the editor still gets
     * the recording it was making.
     */
    @Volatile
    private var externallyRecordedPath: String? = null

    /**
     * True while [startSessionLocked] is waiting on the service. A stop requested in that
     * window (the editor closing mid-start) must queue behind the start instead of being
     * dropped, or the recorder it is about to confirm would run with nobody to stop it.
     */
    @Volatile
    private var startInFlight = false

    @Volatile
    private var liveTranscriptionStarted = false
    private var serviceStateJob: Job? = null
    private var routeSyncJob: Job? = null
    private var transcriptPersistenceJob: Job? = null
    private var transcriptionCollectorJob: Job? = null
    private var transcriptionWarmUpJob: Job? = null
    private val persistenceRetrier = TranscriptionPersistenceRetrier()

    override val isRecording: Boolean
        get() = recordingStateFlow.value

    override val currentRecordingPath: String?
        get() = recordingTarget?.path

    override fun getRecordingStateFlow(): StateFlow<Boolean> = recordingStateFlow.asStateFlow()

    override fun setTranscriptionService(service: TranscriptionService) {
        // Every audio block's view model hands over the same app-scoped service. Rebinding
        // it would restart the warm-up and drop the collector mid-refinement for nothing.
        if (service === transcriptionService && transcriptionCollectorJob?.isActive == true) return
        this.transcriptionService = service

        transcriptionWarmUpJob?.cancel()
        transcriptionWarmUpJob =
            scope.launch {
                try {
                    service.warmUp()
                } catch (e: Exception) {
                    Napier.e("Transcription model warm-up failed", e)
                }
            }

        // This collector lives on the singleton's own scope so a Whisper refinement pass
        // that is still mid-utterance when the editor view model goes away continues to
        // flow through here and gets persisted to the database.
        transcriptionCollectorJob?.cancel()
        transcriptionCollectorJob =
            scope.launch {
                service.getTranscriptionFlow().collectLatest { result -> onTranscriptionResult(result) }
            }
    }

    private fun onTranscriptionResult(result: TranscriptionResult) {
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
            is TranscriptionResult.InProgress,
            TranscriptionResult.Cancelled,
            -> structuredTranscriptionFlow.value = result
        }
    }

    override suspend fun startRecording(targetNoteId: Uuid?): Boolean =
        withContext(workDispatcher) {
            sessionMutex.withLock {
                startInFlight = true
                try {
                    startSessionLocked(targetNoteId)
                } finally {
                    startInFlight = false
                }
            }
        }

    private suspend fun startSessionLocked(targetNoteId: Uuid?): Boolean {
        if (recordingStateFlow.value) {
            Napier.w("Attempted to start recording while already recording")
            return false
        }
        val target =
            try {
                audioStorage.createRecordingTarget()
            } catch (e: Exception) {
                Napier.e("Could not create a recording target", e)
                return false
            }
        recordingTarget = target
        sessionTargetNoteId = targetNoteId
        externallyRecordedPath = null
        liveTranscriptionStarted = false
        transcriptionFlow.value = null
        structuredTranscriptionFlow.value = null

        val started = serviceController.start(target.path, audioRouteRepository.inputDevices.value.selectedDeviceId)
        val confirmed = if (started) awaitServiceStarted() else null
        if (confirmed == null) {
            serviceController.shutdown()
            recordingTarget = null
            return false
        }
        recordingStateFlow.value = true
        observeService()
        startLiveTranscription()
        return true
    }

    /**
     * Waits for the service to report that the recorder is running. Returns null when the
     * service reports an error or never connects within [startTimeout], which covers a
     * foreground start the platform refused and a recorder that failed to prepare.
     */
    private suspend fun awaitServiceStarted(): RecordingServiceState? {
        val state =
            withTimeoutOrNull(startTimeout) {
                serviceController.serviceState
                    .filterNotNull()
                    .first { it.isRecording || it.error != null }
            }
        return when {
            state == null -> {
                Napier.e("Recording service did not confirm a start within $startTimeout")
                null
            }
            !state.isRecording -> {
                Napier.e("Recording service failed to start: ${state.error}")
                null
            }
            else -> state
        }
    }

    private fun observeService() {
        serviceStateJob?.cancel()
        serviceStateJob =
            scope.launch {
                serviceController.serviceState.collect { state -> onServiceState(state) }
            }
        routeSyncJob?.cancel()
        routeSyncJob =
            scope.launch {
                audioRouteRepository.inputDevices.collect { selection ->
                    if (recordingStateFlow.value) {
                        serviceController.updatePreferredInputDevice(selection.selectedDeviceId)
                    }
                }
            }
    }

    private fun onServiceState(state: RecordingServiceState?) {
        if (state == null) {
            if (recordingStateFlow.value) {
                Napier.w("Recording service went away mid-session")
                recordingStateFlow.value = false
            }
            return
        }
        audioLevelFlow.value = state.audioLevel
        durationFlow.value = state.durationSeconds.toLong() * 1000
        if (!state.isRecording && recordingStateFlow.value) {
            // The service ended the session on its own (notification action or recorder
            // failure). Keep the file it produced for the editor's stop call.
            Napier.d("Recording service stopped on its own; file=${state.recordedFilePath} error=${state.error}")
            externallyRecordedPath = state.recordedFilePath
            recordingStateFlow.value = false
        }
    }

    private suspend fun startLiveTranscription() {
        val service = transcriptionService
        if (service == null) {
            structuredTranscriptionFlow.value = TranscriptionResult.Error(TranscriptionFailure.NotAvailable)
            return
        }
        try {
            service.resetTranscription()
            if (service.supportsLiveTranscription) {
                applyLiveStart(service.startLiveTranscription())
            }
        } catch (e: Exception) {
            Napier.e("Transcription failed to start; audio recording will continue", e)
            structuredTranscriptionFlow.value = TranscriptionResult.Error(TranscriptionFailure.Unknown)
        }
    }

    private fun applyLiveStart(start: TranscriptionStartResult) {
        when (start) {
            TranscriptionStartResult.Started,
            TranscriptionStartResult.AlreadyRunning,
            -> liveTranscriptionStarted = true
            is TranscriptionStartResult.Failed -> {
                structuredTranscriptionFlow.value = TranscriptionResult.Error(start.reason)
            }
        }
    }

    override suspend fun stopRecording(): String? =
        withContext(workDispatcher) {
            sessionMutex.withLock { stopSessionLocked() }
        }

    private suspend fun stopSessionLocked(): String? {
        if (!recordingStateFlow.value && externallyRecordedPath == null) {
            Napier.w("Attempted to stop recording while not recording")
            return null
        }
        serviceStateJob?.cancel()
        serviceStateJob = null
        routeSyncJob?.cancel()
        routeSyncJob = null

        val filePath =
            try {
                finalizeServiceRecording()
            } catch (e: Exception) {
                Napier.e("Error finalizing the recording", e)
                null
            } finally {
                serviceController.shutdown()
                recordingStateFlow.value = false
                recordingTarget = null
                externallyRecordedPath = null
            }

        val noteId = sessionTargetNoteId
        try {
            finishTranscription(filePath)
        } catch (e: Exception) {
            Napier.e("Transcription failed to stop cleanly; the recording is kept", e)
        }
        if (filePath != null && noteId != null) {
            runAmbientTagging(filePath, noteId)
        }
        return filePath
    }

    /**
     * Asks the bound service to finalize the file. When the service already ended the
     * session on its own, the path it reported then is used instead.
     */
    private fun finalizeServiceRecording(): String? {
        val stopped = serviceController.stopRecordingNow()
        if (stopped != null) return stopped
        val snapshot = serviceController.serviceState.value
        val reported = snapshot?.recordedFilePath ?: externallyRecordedPath
        if (reported == null) {
            Napier.e("Recording produced no file: ${snapshot?.error ?: "service not bound"}")
        }
        return reported
    }

    private suspend fun finishTranscription(filePath: String?) {
        val service = transcriptionService ?: return
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

        // File transcription is the recovery path when a live session could not start or
        // terminated with an error. A healthy live session already produced the transcript.
        val needsFileRecovery = !liveSessionWasHealthy || stopResult is TranscriptionResult.Error
        if (needsFileRecovery && service.supportsFileTranscription && filePath != null) {
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

    override suspend fun pauseRecording(): Boolean =
        withContext(workDispatcher) {
            sessionMutex.withLock {
                if (!recordingStateFlow.value) return@withLock false
                try {
                    val paused = serviceController.pause()
                    if (paused) transcriptionService?.stopLiveTranscription()
                    paused
                } catch (e: Exception) {
                    Napier.e("Error pausing recording", e)
                    false
                }
            }
        }

    override suspend fun resumeRecording(): Boolean =
        withContext(workDispatcher) {
            sessionMutex.withLock {
                if (!recordingStateFlow.value) return@withLock false
                try {
                    val resumed = serviceController.resume()
                    val service = transcriptionService
                    if (resumed && service != null && service.supportsLiveTranscription) {
                        applyLiveStart(service.startLiveTranscription())
                    }
                    resumed
                } catch (e: Exception) {
                    Napier.e("Error resuming recording", e)
                    false
                }
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
        if (!recordingStateFlow.value && !startInFlight && externallyRecordedPath == null) return
        scope.launch {
            try {
                stopRecording()
            } catch (e: Exception) {
                Napier.e("Error stopping recording from background request", e)
            }
        }
    }

    /**
     * Ends any session in flight and drops per-session work. The manager stays usable
     * afterwards: it is an application-scoped singleton shared by every editor, and so is
     * the transcription service it was handed, so neither is torn down here.
     */
    override fun release() {
        transcriptPersistenceJob?.cancel()
        transcriptPersistenceJob = null
        requestStopRecording()
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
     * Wear OS, tests).
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
        if (recordingStateFlow.value) return
        val status =
            if (result.isFinal && !result.isRefining) {
                TranscriptionStatus.COMPLETED
            } else {
                TranscriptionStatus.IN_PROGRESS
            }
        val persisted =
            persistenceRetrier.persist {
                try {
                    writeTranscript(noteId, result, status)
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

    private suspend fun writeTranscript(
        noteId: Uuid,
        result: TranscriptionResult.Success,
        status: TranscriptionStatus,
    ): Boolean {
        val timedTranscript = result.timedTranscript
        return if (timedTranscript != null) {
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
    }

    private fun scheduleTranscriptPersistence(result: TranscriptionResult.Success) {
        if (result.text.isBlank() || recordingStateFlow.value || sessionTargetNoteId == null) return
        transcriptPersistenceJob?.cancel()
        transcriptPersistenceJob =
            scope.launch(Dispatchers.IO) {
                persistRefinedTranscript(result)
            }
    }

    private companion object {
        /**
         * Upper bound on the wait for the foreground service to confirm the recorder is
         * running. Preparing MediaRecorder takes well under a second; the bound only has to
         * catch a service that never connects.
         */
        val DEFAULT_START_TIMEOUT: Duration = 10.seconds
    }
}
