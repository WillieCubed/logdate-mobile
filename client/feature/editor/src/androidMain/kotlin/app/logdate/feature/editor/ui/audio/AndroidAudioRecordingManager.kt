package app.logdate.feature.editor.ui.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import app.logdate.client.media.audio.AudioRecordingService
import app.logdate.client.media.audio.startAudioRecordingService
import app.logdate.client.media.audio.stopAudioRecordingService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android implementation of the AudioRecordingManager interface.
 * Uses a foreground service for background recording capability.
 */
class AndroidAudioRecordingManager(
    private val context: Context
) : AudioRecordingManager {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _audioLevels = MutableStateFlow<List<Float>>(emptyList())
    private val _recordingDuration = MutableStateFlow(0L)
    
    // Recording state
    private var recordingActive = false
    private var serviceBound = false
    private var recordingService: AudioRecordingService? = null
    private var recordedAudioPath: String? = null
    
    // Callback functions
    private var audioLevelCallback: ((List<Float>) -> Unit)? = null
    private var durationCallback: ((Long) -> Unit)? = null
    
    // Service connection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioRecordingService.AudioServiceBinder
            recordingService = binder.getService()
            serviceBound = true
            
            // Start observing service state
            scope.launch {
                recordingService?.recordingState?.collect { serviceState ->
                    // Only process updates while recording is active
                    if (recordingActive) {
                        // Update audio level
                        val level = serviceState.audioLevel
                        val levels = listOf(level)
                        _audioLevels.value = levels
                        audioLevelCallback?.invoke(levels)
                        
                        // Update duration
                        val durationMs = serviceState.durationSeconds * 1000L
                        _recordingDuration.value = durationMs
                        durationCallback?.invoke(durationMs)
                        
                        // Check if recording stopped on service side
                        if (!serviceState.isRecording) {
                            recordingActive = false
                            recordedAudioPath = serviceState.recordedFilePath
                            unbindFromService()
                        }
                    }
                }
            }
            
            Napier.d("Connected to audio recording service")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            recordingService = null
            serviceBound = false
            Napier.d("Disconnected from audio recording service")
        }
    }
    
    init {
        Napier.d("AndroidAudioRecordingManager initialized")
    }
    
    private fun bindToService() {
        try {
            val intent = Intent(context, AudioRecordingService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            serviceBound = true
            Napier.d("Binding to audio recording service")
        } catch (e: Exception) {
            Napier.e("Failed to bind to recording service", e)
            serviceBound = false
        }
    }
    
    private fun unbindFromService() {
        if (serviceBound) {
            try {
                context.unbindService(serviceConnection)
                serviceBound = false
                Napier.d("Unbound from audio recording service")
            } catch (e: Exception) {
                Napier.e("Error unbinding from recording service", e)
            }
        }
    }
    
    override fun startRecording(
        onAudioLevelChanged: (List<Float>) -> Unit,
        onDurationChanged: (Long) -> Unit
    ) {
        // Store callbacks
        audioLevelCallback = onAudioLevelChanged
        durationCallback = onDurationChanged
        
        try {
            Napier.d("Starting recording with foreground service")
            
            // Reset state
            recordingActive = true
            recordedAudioPath = null
            _recordingDuration.value = 0L
            
            // Start the foreground service
            context.startAudioRecordingService()
            
            // Bind to service for updates
            bindToService()
            
            Napier.d("Recording started successfully")
        } catch (e: Exception) {
            Napier.e("Error starting recording with service", e)
            recordingActive = false
            throw e
        }
    }
    
    override fun stopRecording(): String? {
        return try {
            Napier.d("Stopping recording with foreground service")
            
            if (!recordingActive) {
                Napier.w("Attempted to stop recording when not active")
                return null
            }
            
            // Get file path before stopping
            val filePath = recordingService?.getRecordedFilePath()
            
            // Stop the foreground service
            context.stopAudioRecordingService()
            
            // Give service time to stop properly
            scope.launch {
                withContext(Dispatchers.IO) {
                    delay(300) // Short delay to allow service to complete operations
                    unbindFromService()
                }
            }
            
            // Return the audio path
            recordingActive = false
            filePath ?: recordedAudioPath
        } catch (e: Exception) {
            Napier.e("Error stopping recording", e)
            recordingActive = false
            unbindFromService()
            null
        }
    }
    
    /**
     * Pause the current recording
     * @return True if successfully paused
     */
    override suspend fun pauseRecording(): Boolean {
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
    override suspend fun resumeRecording(): Boolean {
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
    
    override fun clear() {
        try {
            Napier.d("Clearing recording resources")
            
            if (recordingActive) {
                context.stopAudioRecordingService()
                recordingActive = false
            }
            
            unbindFromService()
            
            // Clear callbacks
            audioLevelCallback = null
            durationCallback = null
            
            Napier.d("Recording resources cleared")
        } catch (e: Exception) {
            Napier.e("Error clearing recording resources", e)
        }
    }
}