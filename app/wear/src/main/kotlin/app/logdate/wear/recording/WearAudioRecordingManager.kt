package app.logdate.wear.recording

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import app.logdate.feature.editor.ui.audio.AudioRecordingManager
import app.logdate.wear.data.storage.StorageSpaceChecker
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Wear OS implementation of the AudioRecordingManager interface.
 * 
 * Manages audio recording on Wear OS devices with optimizations for:
 * - Small form factor
 * - Limited battery capacity
 * - Storage validation before recording
 */
class WearAudioRecordingManager(
    private val context: Context,
    private val storageChecker: StorageSpaceChecker
) : AudioRecordingManager {

    companion object {
        // Estimate size of 1-minute audio recording (AAC format, 128kbps)
        // 128 kilobits per second * 60 seconds / 8 bits per byte = 960 kilobytes
        private const val ONE_MINUTE_RECORDING_SIZE_BYTES = 960 * 1024L
        
        // Buffer space to leave free (0.5 MB)
        private const val BUFFER_SPACE_BYTES = 512 * 1024L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audioLevels = MutableStateFlow<List<Float>>(emptyList())
    private val recordingDuration = MutableStateFlow(0L)
    
    // Recording state
    private var recordingActive = false
    private var serviceBound = false
    private var recordingService: WearAudioRecordingService? = null
    private var recordedAudioPath: String? = null
    
    // Callbacks
    private var audioLevelCallback: ((List<Float>) -> Unit)? = null
    private var durationCallback: ((Long) -> Unit)? = null
    
    // Service connection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as WearAudioRecordingService.AudioServiceBinder
            recordingService = binder.getService()
            serviceBound = true
            
            // Observe service state
            scope.launch {
                recordingService?.recordingState?.collect { serviceState ->
                    // Process updates only while recording is active
                    if (recordingActive) {
                        // Update audio level
                        val level = serviceState.audioLevel
                        val levels = listOf(level)
                        audioLevels.value = levels
                        audioLevelCallback?.invoke(levels)
                        
                        // Update duration
                        val durationMs = serviceState.durationSeconds * 1000L
                        recordingDuration.value = durationMs
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
            
            Napier.d("Connected to Wear OS audio recording service")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            recordingService = null
            serviceBound = false
            Napier.d("Disconnected from Wear OS audio recording service")
        }
    }
    
    init {
        Napier.d("WearAudioRecordingManager initialized")
    }
    
    private fun bindToService() {
        try {
            val intent = Intent(context, WearAudioRecordingService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            serviceBound = true
            Napier.d("Binding to Wear OS audio recording service")
        } catch (e: Exception) {
            Napier.e("Failed to bind to Wear OS recording service", e)
            serviceBound = false
        }
    }
    
    private fun unbindFromService() {
        if (serviceBound) {
            try {
                context.unbindService(serviceConnection)
                serviceBound = false
                Napier.d("Unbound from Wear OS audio recording service")
            } catch (e: Exception) {
                Napier.e("Error unbinding from Wear OS recording service", e)
            }
        }
    }
    
    /**
     * Check if there's enough storage space for a 1-minute recording
     */
    private suspend fun checkStorageSpace(): Boolean {
        val requiredSpace = ONE_MINUTE_RECORDING_SIZE_BYTES + BUFFER_SPACE_BYTES
        val availableSpace = storageChecker.getAvailableStorageSpace()
        
        Napier.d("Available storage: ${availableSpace / 1024}KB, Required: ${requiredSpace / 1024}KB")
        
        return availableSpace >= requiredSpace
    }
    
    override fun startRecording(
        onAudioLevelChanged: (List<Float>) -> Unit,
        onDurationChanged: (Long) -> Unit
    ) {
        // Store callbacks
        audioLevelCallback = onAudioLevelChanged
        durationCallback = onDurationChanged
        
        scope.launch {
            try {
                // Check storage space first
                val hasEnoughSpace = checkStorageSpace()
                if (!hasEnoughSpace) {
                    Napier.w("Not enough storage space for recording")
                    throw IllegalStateException("Not enough storage space for a 1-minute recording")
                }
                
                Napier.d("Starting Wear OS recording with foreground service")
                
                // Reset state
                recordingActive = true
                recordedAudioPath = null
                recordingDuration.value = 0L
                
                // Start foreground service
                context.startWearAudioRecordingService()
                
                // Bind to service
                bindToService()
                
                Napier.d("Wear OS recording started successfully")
            } catch (e: Exception) {
                Napier.e("Error starting Wear OS recording", e)
                recordingActive = false
                throw e
            }
        }
    }
    
    override fun stopRecording(): String? {
        return try {
            Napier.d("Stopping Wear OS recording")
            
            if (!recordingActive) {
                Napier.w("Attempted to stop Wear OS recording when not active")
                return null
            }
            
            // Get file path before stopping
            val filePath = recordingService?.getRecordedFilePath()
            
            // Stop service
            context.stopWearAudioRecordingService()
            
            // Give service time to stop properly
            scope.launch {
                withContext(Dispatchers.IO) {
                    delay(300) // Short delay
                    unbindFromService()
                }
            }
            
            // Return audio path
            recordingActive = false
            filePath ?: recordedAudioPath
        } catch (e: Exception) {
            Napier.e("Error stopping Wear OS recording", e)
            recordingActive = false
            unbindFromService()
            null
        }
    }
    
    override suspend fun pauseRecording(): Boolean {
        if (!recordingActive || recordingService == null) {
            return false
        }
        
        try {
            // Send pause action to service
            val intent = Intent(context, WearAudioRecordingService::class.java).apply {
                action = WearAudioRecordingService.ACTION_PAUSE
            }
            context.startService(intent)
            
            delay(200) // Small delay
            
            return recordingService?.isRecordingPaused() == true
        } catch (e: Exception) {
            Napier.e("Error pausing Wear OS recording", e)
            return false
        }
    }
    
    override suspend fun resumeRecording(): Boolean {
        if (!recordingActive || recordingService == null) {
            return false
        }
        
        try {
            // Send resume action to service
            val intent = Intent(context, WearAudioRecordingService::class.java).apply {
                action = WearAudioRecordingService.ACTION_RESUME
            }
            context.startService(intent)
            
            delay(200) // Small delay
            
            return recordingService?.isRecordingPaused() == false
        } catch (e: Exception) {
            Napier.e("Error resuming Wear OS recording", e)
            return false
        }
    }
    
    override fun clear() {
        try {
            Napier.d("Clearing Wear OS recording resources")
            
            if (recordingActive) {
                context.stopWearAudioRecordingService()
                recordingActive = false
            }
            
            unbindFromService()
            
            // Clear callbacks
            audioLevelCallback = null
            durationCallback = null
            
            Napier.d("Wear OS recording resources cleared")
        } catch (e: Exception) {
            Napier.e("Error clearing Wear OS recording resources", e)
        }
    }
}