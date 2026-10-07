package app.logdate.client.media.audio

import app.logdate.client.media.audio.tagging.AudioTaggingResult
import app.logdate.client.media.audio.tagging.AudioTaggingService
import app.logdate.client.media.audio.transcription.TranscriptionFailure
import app.logdate.client.media.audio.transcription.TranscriptionResult
import app.logdate.client.media.audio.transcription.TranscriptionService
import app.logdate.client.media.audio.transcription.TranscriptionStartResult
import app.logdate.client.media.audio.transcription.stopLiveTranscriptionWithHandoff
import app.logdate.client.media.device.AudioRouteRepository
import app.logdate.client.repository.audio.AudioTag
import app.logdate.client.repository.audio.AudioTagRepository
import app.logdate.client.repository.transcription.TranscriptionRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
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
    private val recordingPausedFlow = MutableStateFlow(false)
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
    private val recordingStartingFlow = MutableStateFlow(false)

    @Volatile
    private var sessionGeneration = 0L

    @Volatile
    private var liveTranscriptionStarted = false

    /**
     * The in-flight (or most recently finished) call to [startLiveTranscription]. Recording
     * is confirmed to the caller as soon as the platform recorder is running; transcription
     * setup -- a second, separate microphone acquisition -- runs alongside it instead of
     * serially after it, since the app treats transcription as best-effort and recording
     * must not wait on it. [stopSessionLocked] and [pauseRecording] join this job first so
     * they never race a still-initializing transcription session.
     */
    private var transcriptionStartJob: Job? = null
    private var transcriptionSessionInitialized = false
    private var serviceStateJob: Job? = null
    private var routeSyncJob: Job? = null
    private val transcriptPersistenceLock = Any()
    private val transcriptionInputRouter =
        TranscriptionInputRouter {
            structuredTranscriptionFlow.value = TranscriptionResult.Error(TranscriptionFailure.AudioError)
        }
    private val transcriptPersistenceJobs = mutableMapOf<Uuid, Job>()
    private var transcriptionCollectorJob: Job? = null
    private var transcriptionWarmUpJob: Job? = null
    private val transcriptWriter = RecordingTranscriptWriter(transcriptionRepository)

    override val isRecording: Boolean
        get() = recordingStateFlow.value

    override val isStartingRecording: Boolean
        get() = startInFlight

    override fun getRecordingStartingFlow(): StateFlow<Boolean> = recordingStartingFlow.asStateFlow()

    override val currentRecordingPath: String?
        get() = recordingTarget?.path

    override val currentRecordingTargetNoteId: Uuid?
        get() = recordingTarget?.let { sessionTargetNoteId }

    override val currentRecordingPaused: Boolean
        get() = recordingPausedFlow.value

    override fun getRecordingPausedFlow(): StateFlow<Boolean> = recordingPausedFlow.asStateFlow()

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
        val noteId = sessionTargetNoteId
        val inputHealth = transcriptionInputRouter.health
        transcriptionCollectorJob =
            scope.launch {
                service.getTranscriptionFlow().collectLatest { result -> onTranscriptionResult(result, noteId, inputHealth) }
            }
    }

    private fun onTranscriptionResult(
        result: TranscriptionResult,
        noteId: Uuid?,
        inputHealth: TranscriptionInputHealth,
    ) {
        if (result is TranscriptionResult.Success && !inputHealth.isHealthy) return
        if (result is TranscriptionResult.Error && result.reason == TranscriptionFailure.AudioError) inputHealth.isHealthy = false
        if (result is TranscriptionResult.Success) scheduleTranscriptPersistence(result, noteId)
        if (noteId != sessionTargetNoteId) return
        when (result) {
            is TranscriptionResult.Success -> {
                transcriptionFlow.value = result.text
                structuredTranscriptionFlow.value = result
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
                recordingStartingFlow.value = true
                try {
                    startSessionLocked(targetNoteId)
                } finally {
                    startInFlight = false
                    recordingStartingFlow.value = false
                }
            }
        }

    private suspend fun startSessionLocked(targetNoteId: Uuid?): Boolean {
        if (recordingStateFlow.value || externallyRecordedPath != null) {
            Napier.w("Attempted to start recording while a session is active or awaiting handoff")
            return false
        }
        sessionGeneration++
        val target =
            try {
                audioStorage.createRecordingTarget()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Could not create a recording target", e)
                return false
            }
        recordingTarget = target
        sessionTargetNoteId = targetNoteId
        externallyRecordedPath = null
        liveTranscriptionStarted = false
        transcriptionInputRouter.reset()
        transcriptionFlow.value = null
        structuredTranscriptionFlow.value = null

        var confirmed = false
        try {
            val started = serviceController.start(target.path, audioRouteRepository.requestedInputDeviceIds.first())
            confirmed = started && awaitServiceStarted() != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Could not start the recording service", e)
        } finally {
            if (!confirmed) {
                try {
                    serviceController.shutdown()
                } finally {
                    recordingTarget = null
                    sessionTargetNoteId = null
                }
            }
        }
        if (!confirmed) return false
        recordingStateFlow.value = true
        observeService()
        transcriptionStartJob = scope.launch { startLiveTranscription() }
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
        val generation = sessionGeneration
        routeSyncJob =
            observeRecordingInputRoutes(
                scope,
                audioRouteRepository,
                serviceController,
                sessionMutex,
                isCurrentSession = { generation == sessionGeneration && recordingStateFlow.value },
                onInputChanged = { updateTranscriptionInput(it) },
            )
    }

    private fun updateTranscriptionInput(
        deviceId: String?,
        verifyActiveInput: Boolean = false,
    ) = transcriptionInputRouter.update(transcriptionService, deviceId, verifyActiveInput)

    private fun onServiceState(state: RecordingServiceState?) {
        audioRouteRepository.updateRecordingInputDevice(state?.routedInputDeviceId, state?.isRecording == true, state?.inputRoutingError)
        if (state?.isRecording == true && state.routedInputDeviceId != null) updateTranscriptionInput(state.routedInputDeviceId)
        if (state == null) {
            recordingPausedFlow.value = false
            if (recordingStateFlow.value) {
                Napier.w("Recording service went away mid-session")
                recordingStateFlow.value = false
            }
            return
        }
        recordingPausedFlow.value = state.isRecording && state.isPaused
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
            if (transcriptionSessionInitialized) service.cancelTranscription()
            transcriptionCollectorJob?.cancelAndJoin()
            val noteId = sessionTargetNoteId
            val inputHealth = transcriptionInputRouter.health
            val results = service.getTranscriptionFlow()
            val outgoingReplayCount = results.replayCache.size
            transcriptionCollectorJob =
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    results.drop(outgoingReplayCount).collectLatest { result -> onTranscriptionResult(result, noteId, inputHealth) }
                }
            transcriptionSessionInitialized = true
            service.resetTranscription()
            if (service.supportsLiveTranscription) {
                updateTranscriptionInput(
                    serviceController.serviceState.value?.routedInputDeviceId ?: audioRouteRepository.requestedInputDeviceIds.first(),
                )
                applyLiveStart(service.startLiveTranscription())
                updateTranscriptionInput(
                    serviceController.serviceState.value?.routedInputDeviceId ?: audioRouteRepository.requestedInputDeviceIds.first(),
                    verifyActiveInput = true,
                )
            }
        } catch (e: CancellationException) {
            throw e
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
        sessionMutex.withLock {
            val filePath = withContext(workDispatcher) { stopSessionLocked() }
            recordingTarget = null
            externallyRecordedPath = null
            filePath
        }

    private suspend fun stopSessionLocked(): String? {
        if (!recordingStateFlow.value && externallyRecordedPath == null) {
            Napier.w("Attempted to stop recording while not recording")
            return null
        }
        // A stop that lands while the transcription session is still spinning up (start and
        // stop tapped in immediate succession) must wait for that setup to settle before
        // tearing it down, or the AudioRecord it is about to open would never get closed.
        try {
            transcriptionStartJob?.join()
        } catch (e: CancellationException) {
            withContext(NonCancellable) { preserveCancelledRecording() }
            throw e
        }
        transcriptionStartJob = null
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
                recordingPausedFlow.value = false
                audioRouteRepository.updateRecordingInputDevice(null, false)
            }
        externallyRecordedPath = filePath
        if (filePath == null) recordingTarget = null

        val noteId = sessionTargetNoteId
        try {
            finishTranscription(filePath, noteId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Transcription failed to stop cleanly; the recording is kept", e)
        }
        if (filePath != null && noteId != null) {
            runAmbientTagging(filePath, noteId)
        }
        return filePath
    }

    private suspend fun preserveCancelledRecording() {
        transcriptionStartJob?.cancel()
        serviceStateJob?.cancel()
        serviceStateJob = null
        routeSyncJob?.cancel()
        routeSyncJob = null
        try {
            externallyRecordedPath = finalizeServiceRecording()
        } catch (e: Exception) {
            Napier.e("Error preserving a cancelled recording", e)
        } finally {
            serviceController.shutdown()
            recordingStateFlow.value = false
            recordingPausedFlow.value = false
            audioRouteRepository.updateRecordingInputDevice(null, false)
            if (externallyRecordedPath == null) recordingTarget = null
        }
        transcriptionStartJob?.join()
        transcriptionStartJob = null
        try {
            if (transcriptionService?.supportsLiveTranscription == true) {
                transcriptionService?.stopLiveTranscription()
            }
        } catch (e: Exception) {
            Napier.e("Error releasing transcription after a cancelled stop", e)
        }
        liveTranscriptionStarted = false
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

    private suspend fun finishTranscription(
        filePath: String?,
        noteId: Uuid?,
    ) {
        val service = transcriptionService ?: return
        val liveSessionWasHealthy = liveTranscriptionStarted && transcriptionInputRouter.health.isHealthy
        val stopResult =
            if (service.supportsLiveTranscription) {
                try {
                    stopLiveTranscriptionWithHandoff(service) { result ->
                        if (liveSessionWasHealthy) {
                            transcriptionFlow.value = result.text
                            structuredTranscriptionFlow.value = result
                            scheduleTranscriptPersistence(result, noteId)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
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
                    scheduleTranscriptPersistence(result, noteId)
                }
                if (sessionTargetNoteId == noteId) {
                    if (result is TranscriptionResult.Success) transcriptionFlow.value = result.text
                    structuredTranscriptionFlow.value = result
                }
            }
        }
    }

    override suspend fun pauseRecording(): Boolean =
        withContext(workDispatcher) {
            sessionMutex.withLock {
                if (!recordingStateFlow.value) return@withLock false
                try {
                    // Same ordering guard as stop: don't ask a still-initializing
                    // transcription session to stop before it has finished starting.
                    transcriptionStartJob?.join()
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
                    if (resumed) {
                        applyRecordingInputRoute(
                            audioRouteRepository,
                            serviceController,
                            audioRouteRepository.requestedInputDeviceIds.first(),
                        )
                    }
                    val service = transcriptionService
                    if (resumed && service != null && service.supportsLiveTranscription) {
                        updateTranscriptionInput(
                            serviceController.serviceState.value?.routedInputDeviceId
                                ?: audioRouteRepository.requestedInputDeviceIds.first(),
                        )
                        applyLiveStart(service.startLiveTranscription())
                        updateTranscriptionInput(
                            serviceController.serviceState.value?.routedInputDeviceId
                                ?: audioRouteRepository.requestedInputDeviceIds.first(),
                            verifyActiveInput = true,
                        )
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
        val requestedSession = sessionGeneration
        scope.launch {
            try {
                sessionMutex.withLock {
                    if (sessionGeneration != requestedSession) return@withLock
                    transcriptionStartJob?.cancel()
                    stopSessionLocked()
                }
            } catch (e: CancellationException) {
                throw e
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
        val pendingWrite = synchronized(transcriptPersistenceLock) { transcriptPersistenceJobs.remove(sessionTargetNoteId) }
        pendingWrite?.cancel()
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
     * Writes a transcription result to the repository under its captured
     * session note ID, so the polished text survives the
     * editor view model and is visible to any later viewer that loads the
     * note. Skipped when no note id was supplied to [startRecording] (e.g.
     * Wear OS, tests).
     *
     * Status is COMPLETED once refinement finishes (or when refinement
     * isn't running because the Whisper model isn't on device); it's
     * IN_PROGRESS while utterances are still being rewritten so any UI
     * observing the note can show the right state.
     */
    private suspend fun persistRefinedTranscript(
        result: TranscriptionResult.Success,
        noteId: Uuid,
    ) {
        if (!transcriptWriter.persist(result, noteId)) {
            Napier.e("Transcript persistence exhausted retries for $noteId")
            structuredTranscriptionFlow.value = TranscriptionResult.Error(TranscriptionFailure.PersistenceError)
        }
    }

    private fun scheduleTranscriptPersistence(
        result: TranscriptionResult.Success,
        noteId: Uuid? = sessionTargetNoteId,
    ) {
        if (result.text.isBlank() || noteId == null) return
        // The active note is saved after recording stops; completed older sessions can
        // already persist while a new recording runs.
        if (recordingStateFlow.value && noteId == sessionTargetNoteId) return
        synchronized(transcriptPersistenceLock) {
            transcriptPersistenceJobs.remove(noteId)?.cancel()
            val job =
                scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                    persistRefinedTranscript(result, noteId)
                }
            transcriptPersistenceJobs[noteId] = job
            job.invokeOnCompletion {
                synchronized(transcriptPersistenceLock) {
                    if (transcriptPersistenceJobs[noteId] === job) transcriptPersistenceJobs.remove(noteId)
                }
            }
            job.start()
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
