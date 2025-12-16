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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of AudioRecordingManager for Android
 */
class AndroidAudioRecordingManager(
    private val context: Context
) : AudioRecordingManager {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audioLevelFlow = MutableStateFlow(0f)
    private val durationFlow = MutableStateFlow(0L)
    private val transcriptionFlow = MutableStateFlow<String?>(null)
    private var transcriptionService: TranscriptionService? = null
    
    // Service connection
    private var recordingService: AudioRecordingService? = null
    private var recordingActive = false
    private var serviceBound = false
    
    // Service binding
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioRecordingService.AudioServiceBinder
            recordingService = binder.getService()
            serviceBound = true
            
            // Start observing service state
            scope.launch {
                recordingService?.recordingState?.collect { serviceState ->
                    // Update flows
                    audioLevelFlow.value = serviceState.audioLevel
                    durationFlow.value = serviceState.durationSeconds.toLong() * 1000
                    recordingActive = serviceState.isRecording
                    
                    if (!serviceState.isRecording && serviceBound) {
                        // Recording has stopped from the service side
                        unbindServiceSafely()
                    }
                }
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
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
        if (recordingActive) {
            Napier.w("Attempted to start recording while already recording")
            return false
        }
        
        try {
            // Start foreground service for recording
            context.startAudioRecordingService()
            
            // Bind to the service to get updates
            bindToService()
            
            // Set state to recording
            recordingActive = true
            
            // Start transcription if service is available
            transcriptionService?.let { service ->
                if (service.supportsLiveTranscription) {
                    scope.launch {
                        service.startLiveTranscription()
                    }
                }
            }
            
            return true
        } catch (e: Exception) {
            Napier.e("Error starting recording service", e)
            return false
        }
    }
    
    private fun bindToService() {
        try {
            val intent = Intent(context, AudioRecordingService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            serviceBound = true
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
            // Stop the service
            context.stopAudioRecordingService()
            
            // Get file path from service before unbinding
            val filePath = recordingService?.getRecordedFilePath()
            
            // Unbind from service
            unbindServiceSafely()
            
            recordingActive = false
            
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
    suspend fun pauseRecording(): Boolean {
        if (!recordingActive || recordingService == null) {
            return false
        }
        
        try {
            // Send pause action to service
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = AudioRecordingService.SERVICE_ACTION_PAUSE
            }
            context.startService(intent)
            
            delay(200) // Small delay to allow the service to process
            
            return recordingService?.isRecordingPaused() == true
        } catch (e: Exception) {
            Napier.e("Error pausing recording", e)
            return false
        }
    }
    
    /**
     * Resume a paused recording
     * @return True if successfully resumed
     */
    suspend fun resumeRecording(): Boolean {
        if (!recordingActive || recordingService == null) {
            return false
        }
        
        try {
            // Send resume action to service
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = AudioRecordingService.SERVICE_ACTION_RESUME
            }
            context.startService(intent)
            
            delay(200) // Small delay to allow the service to process
            
            return recordingService?.isRecordingPaused() == false
        } catch (e: Exception) {
            Napier.e("Error resuming recording", e)
            return false
        }
    }
    
    override fun getAudioLevelFlow(): Flow<Float> = audioLevelFlow
    
    override fun getRecordingDurationFlow(): Flow<Duration> = flow {
        while (recordingActive) {
            emit(durationFlow.value.milliseconds)
            delay(100) // Update every 100ms
        }
    }
    
    override fun getTranscriptionFlow(): Flow<String?> = transcriptionFlow
    
    override fun release() {
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
    }
}