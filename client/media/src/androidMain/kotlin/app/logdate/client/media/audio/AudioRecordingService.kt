package app.logdate.client.media.audio

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * Extension function to start the recording service
 */
fun Context.startAudioRecordingService() {
    val intent = Intent(this, AudioRecordingService::class.java).apply {
        action = AudioRecordingService.SERVICE_ACTION_START
    }
    startForegroundService(intent)
}

/**
 * Extension function to stop the recording service
 */
fun Context.stopAudioRecordingService() {
    val intent = Intent(this, AudioRecordingService::class.java).apply {
        action = AudioRecordingService.SERVICE_ACTION_STOP
    }
    stopService(intent)
}

/**
 * Android foreground service for audio recording.
 *
 * Handles recording in the background with a persistent notification.
 * Provides binding for clients to interact with the recording.
 */
class AudioRecordingService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val SERVICE_ACTION_START = "app.logdate.action.START_RECORDING"
        const val SERVICE_ACTION_STOP = "app.logdate.action.STOP_RECORDING"
        const val SERVICE_ACTION_PAUSE = AndroidAudioNotificationHandler.ACTION_PAUSE
        const val SERVICE_ACTION_RESUME = AndroidAudioNotificationHandler.ACTION_RESUME
    }

    // Service binder for clients
    inner class AudioServiceBinder : Binder() {
        fun getService(): AudioRecordingService = this@AudioRecordingService
    }

    private val binder = AudioServiceBinder()

    // Coroutine scope for service operations
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Notification handler
    private lateinit var notificationHandler: AndroidAudioNotificationHandler

    // Recording state
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var recordingStartTime: Long = 0
    private var isPaused: Boolean = false

    // State flow for UI updates
    private val _recordingState = MutableStateFlow(RecordingServiceState())
    val recordingState = _recordingState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        notificationHandler = AndroidAudioNotificationHandler(this)
        Napier.d("Audio recording service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            SERVICE_ACTION_START -> {
                Napier.d("Starting audio recording service")
                startForegroundRecording()
            }
            SERVICE_ACTION_STOP -> {
                Napier.d("Stopping audio recording service")
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            SERVICE_ACTION_PAUSE -> {
                Napier.d("Pausing audio recording")
                pauseRecording()
            }
            SERVICE_ACTION_RESUME -> {
                Napier.d("Resuming audio recording")
                resumeRecording()
            }
        }

        // Restart if the service is killed
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        Napier.d("Audio recording service destroyed")
        stopRecording()
        serviceScope.cancel() // Cancel all coroutines
        super.onDestroy()
    }

    /**
     * Starts foreground recording with notification
     */
    private fun startForegroundRecording() {
        try {
            val notification = notificationHandler.createRecordingNotification(true, System.currentTimeMillis())
            
            // Start as a foreground service with the microphone type
            startForeground(
                NOTIFICATION_ID, 
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
            
            startRecording()

            // Update recording state
            serviceScope.launch {
                var elapsedTimeSeconds = 0
                while (_recordingState.value.isRecording) {
                    kotlinx.coroutines.delay(1000)
                    if (!isPaused) {
                        elapsedTimeSeconds++
                    }

                    _recordingState.update {
                        it.copy(durationSeconds = elapsedTimeSeconds)
                    }
                }
            }
        } catch (e: Exception) {
            Napier.e("Error starting foreground service", e)
            stopSelf()
        }
    }
    
    /**
     * Pauses the current recording
     */
    private fun pauseRecording() {
        if (!_recordingState.value.isRecording || isPaused) {
            return
        }
        
        try {
            mediaRecorder?.pause()
            isPaused = true
            
            // Update notification to show paused state
            notificationHandler.updateRecordingNotification(
                isRecording = false,
                startTimeMillis = recordingStartTime
            )
            
            Napier.d("Recording paused")
        } catch (e: Exception) {
            Napier.e("Error pausing recording", e)
        }
    }
    
    /**
     * Resumes a paused recording
     */
    private fun resumeRecording() {
        if (!_recordingState.value.isRecording || !isPaused) {
            return
        }
        
        try {
            mediaRecorder?.resume()
            isPaused = false
            
            // Update notification to show recording state
            notificationHandler.updateRecordingNotification(
                isRecording = true,
                startTimeMillis = recordingStartTime
            )
            
            Napier.d("Recording resumed")
        } catch (e: Exception) {
            Napier.e("Error resuming recording", e)
        }
    }

    /**
     * Starts the actual recording process
     */
    private fun startRecording() {
        try {
            // Create output file
            val outputDir = applicationContext.cacheDir
            outputFile = File.createTempFile("audio_recording_", ".m4a", outputDir)

            // Initialize MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile?.absolutePath)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)

                try {
                    prepare()
                    start()
                    recordingStartTime = System.currentTimeMillis()
                    isPaused = false

                    // Update state
                    _recordingState.update {
                        it.copy(
                            isRecording = true,
                            startTime = recordingStartTime
                        )
                    }

                    // Start monitoring audio levels
                    serviceScope.launch {
                        while (_recordingState.value.isRecording) {
                            if (!isPaused) {
                                try {
                                    val amplitude = mediaRecorder?.maxAmplitude ?: 0
                                    // Convert amplitude to a 0-1 range
                                    val level = (amplitude / 32768f).coerceIn(0f, 1f)

                                    // Update the audio level in the state
                                    _recordingState.update {
                                        it.copy(audioLevel = level)
                                    }
                                } catch (e: Exception) {
                                    Napier.e("Error getting audio level", e)
                                }
                            }
                            kotlinx.coroutines.delay(100)
                        }
                    }

                    Napier.d("Recording started successfully")
                } catch (e: IOException) {
                    Napier.e("Failed to start recording", e)
                    reset()
                    release()

                    // Update state with error
                    _recordingState.update {
                        it.copy(
                            isRecording = false,
                            error = "Failed to start recording: ${e.message}"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Napier.e("Error setting up recording", e)
            stopRecording()

            // Update state with error
            _recordingState.update {
                it.copy(
                    isRecording = false,
                    error = "Error setting up recording: ${e.message}"
                )
            }
        }
    }

    /**
     * Stops the current recording
     * @return The path to the recorded file, or null if recording failed
     */
    fun stopRecording(): String? {
        if (!_recordingState.value.isRecording) {
            return null
        }

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }

            mediaRecorder = null

            // Update state
            _recordingState.update {
                it.copy(
                    isRecording = false,
                    recordedFilePath = outputFile?.absolutePath
                )
            }

            // Return the path to the recorded file
            return outputFile?.absolutePath
        } catch (e: Exception) {
            Napier.e("Error stopping recording", e)

            // Update state with error
            _recordingState.update {
                it.copy(
                    isRecording = false,
                    error = "Error stopping recording: ${e.message}"
                )
            }

            return null
        }
    }

    /**
     * Gets the recorded file path
     */
    fun getRecordedFilePath(): String? {
        return outputFile?.absolutePath
    }
    
    /**
     * Checks if recording is currently paused
     */
    fun isRecordingPaused(): Boolean {
        return isPaused && _recordingState.value.isRecording
    }
}

/**
 * Represents the current state of the recording service
 */
data class RecordingServiceState(
    val isRecording: Boolean = false,
    val startTime: Long = 0,
    val durationSeconds: Int = 0,
    val audioLevel: Float = 0f,
    val recordedFilePath: String? = null,
    val error: String? = null,
)