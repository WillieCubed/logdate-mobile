package app.logdate.client.media.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import app.logdate.client.media.audio.transcription.TranscriptionResult
import app.logdate.client.media.audio.transcription.TranscriptionService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Implementation of AudioRecordingManager for Android
 */
class AndroidAudioRecordingManager(
    private val context: Context,
    private val audioStorage: AudioStorage,
) : AudioRecordingManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audioLevelFlow = MutableStateFlow(0f)
    private val durationFlow = MutableStateFlow(0L)
    private val transcriptionFlow = MutableStateFlow<String?>(null)
    private var transcriptionService: TranscriptionService? = null
    private var recordingTarget: AudioRecordingTarget? = null

    // Service connection
    private var recordingService: AudioRecordingService? = null
    private var recordingActive = false
    private var serviceBound = false
    private var startRequested = false
    private var serviceStateJob: Job? = null

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
                recordingService = null
                serviceBound = false
            }
        }

    override val isRecording: Boolean
        get() = recordingActive

    override fun setTranscriptionService(service: TranscriptionService) {
        this.transcriptionService = service

        // Listen for transcription updates
        scope.launch {
            service.getTranscriptionFlow().collectLatest { result ->
                when (result) {
                    is TranscriptionResult.Success -> {
                        transcriptionFlow.value = result.text
                    }
                    is TranscriptionResult.Error -> {
                        Napier.e("Transcription error: ${result.message}")
                    }
                    is TranscriptionResult.InProgress -> {
                        // Just wait for the text
                    }
                }
            }
        }
    }

    override suspend fun startRecording(): Boolean {
        if (recordingActive || startRequested) {
            Napier.w("Attempted to start recording while already recording or start pending")
            return false
        }

        startRequested = true
        try {
            recordingTarget = audioStorage.createRecordingTarget()
            // Start foreground service for recording
            context.startAudioRecordingService(recordingTarget?.path)

            // Bind to the service to get updates
            // recordingActive will be set true via onServiceConnected → service state flow
            bindToService()

            // Start live transcription in parallel with recording.
            // VoskTranscriptionService uses AudioRecord (no audio focus) so music keeps playing.
            transcriptionService?.let { service ->
                if (service.supportsLiveTranscription) {
                    scope.launch { service.startLiveTranscription() }
                }
            }

            return true
        } catch (e: Exception) {
            startRequested = false
            Napier.e("Error starting recording service", e)
            return false
        }
    }

    private fun bindToService() {
        try {
            val intent = Intent(context, AudioRecordingService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            // serviceBound is set to true in onServiceConnected, not here
        } catch (e: Exception) {
            Napier.e("Error binding to recording service", e)
        }
    }

    private fun unbindServiceSafely() {
        if (serviceBound) {
            try {
                context.unbindService(serviceConnection)
                serviceBound = false
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

            // Stop live transcription
            transcriptionService?.let { service ->
                if (service.supportsLiveTranscription) {
                    service.stopLiveTranscription()
                }

                // If file transcription is supported, transcribe the recorded file
                if (service.supportsFileTranscription && filePath != null) {
                    scope.launch {
                        val result = service.transcribeAudioFile(filePath)
                        if (result is TranscriptionResult.Success) {
                            transcriptionFlow.value = result.text
                        }
                    }
                }
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
                        scope.launch { svc.startLiveTranscription() }
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
    }

    override fun getAudioLevelFlow(): Flow<Float> = audioLevelFlow

    override fun getRecordingDurationFlow(): Flow<Duration> = recordingDurationFlow

    override fun getTranscriptionFlow(): Flow<String?> = transcriptionFlow

    override fun release() {
        serviceStateJob?.cancel()
        serviceStateJob = null
        try {
            val wasRecording = recordingActive
            if (recordingActive) {
                // Stop the service
                context.stopAudioRecordingService()
                recordingActive = false
            }

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
    }
}
