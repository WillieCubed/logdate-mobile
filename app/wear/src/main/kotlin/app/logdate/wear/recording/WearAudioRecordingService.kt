package app.logdate.wear.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import app.logdate.wear.R
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
 * Extension function to start the recording service for Wear OS
 */
fun Context.startWearAudioRecordingService() {
    val intent = Intent(this, WearAudioRecordingService::class.java).apply {
        action = WearAudioRecordingService.ACTION_START
    }
    startForegroundService(intent)
}

/**
 * Extension function to stop the recording service
 */
fun Context.stopWearAudioRecordingService() {
    val intent = Intent(this, WearAudioRecordingService::class.java).apply {
        action = WearAudioRecordingService.ACTION_STOP
    }
    stopService(intent)
}

/**
 * Foreground service for audio recording on Wear OS.
 * 
 * Optimized for wearable devices with:
 * - Simplified notification
 * - Haptic feedback
 * - Wake lock to keep recording when screen is off
 * - Battery-efficient implementation
 */
class WearAudioRecordingService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "recording_channel"
        
        const val ACTION_START = "app.logdate.wear.action.START_RECORDING"
        const val ACTION_STOP = "app.logdate.wear.action.STOP_RECORDING"
        const val ACTION_PAUSE = "app.logdate.wear.action.PAUSE_RECORDING"
        const val ACTION_RESUME = "app.logdate.wear.action.RESUME_RECORDING"
    }
    
    // Binder for clients
    inner class AudioServiceBinder : Binder() {
        fun getService(): WearAudioRecordingService = this@WearAudioRecordingService
    }
    
    private val binder = AudioServiceBinder()
    
    // Coroutine scope for service operations
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Recording state
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var recordingStartTime: Long = 0
    private var isPaused: Boolean = false
    
    // Wake lock to keep recording when the screen is off
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Vibrator for haptic feedback
    private lateinit var vibrator: Vibrator
    
    // State flow for UI updates
    private val _recordingState = MutableStateFlow(WearRecordingState())
    val recordingState = _recordingState.asStateFlow()
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        // Create notification channel
        createNotificationChannel()
        
        Napier.d("Wear OS audio recording service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Napier.d("Starting Wear OS audio recording service")
                startForegroundRecording()
                vibrateStart()
            }
            ACTION_STOP -> {
                Napier.d("Stopping Wear OS audio recording service")
                vibrateStop()
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_PAUSE -> {
                Napier.d("Pausing Wear OS audio recording")
                vibratePause()
                pauseRecording()
            }
            ACTION_RESUME -> {
                Napier.d("Resuming Wear OS audio recording")
                vibrateResume()
                resumeRecording()
            }
        }
        
        // Restart if killed
        return START_STICKY
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        Napier.d("Wear OS audio recording service destroyed")
        releaseWakeLock()
        stopRecording()
        serviceScope.cancel()
        super.onDestroy()
    }
    
    /**
     * Creates the notification channel for recording service
     */
    private fun createNotificationChannel() {
        val name = "Recording"
        val description = "Audio recording notifications"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
            this.description = description
            enableVibration(false) // We'll handle vibration manually
            setSound(null, null) // No sound as we'll use haptic feedback
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
    
    /**
     * Starts foreground recording with notification
     */
    private fun startForegroundRecording() {
        try {
            // Create a simple notification for the small screen
            val notification = createRecordingNotification()
            
            // Acquire wake lock to keep recording when screen is off
            acquireWakeLock()
            
            // Start foreground service with microphone type
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
            
            startRecording()
            
            // Update recording state in background
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
            Napier.e("Error starting Wear OS foreground service", e)
            stopSelf()
        }
    }
    
    /**
     * Creates the recording notification
     */
    private fun createRecordingNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome) // Use app icon as recording indicator
            .setContentTitle("Recording Audio")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Recording in progress on your watch"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    /**
     * Starts the actual recording process
     */
    private fun startRecording() {
        try {
            // Create output file in cache directory
            val outputDir = applicationContext.cacheDir
            outputFile = File.createTempFile("wear_audio_", ".m4a", outputDir)
            
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
                setAudioEncodingBitRate(128000) // 128kbps for good quality
                setAudioSamplingRate(44100) // 44.1kHz standard for audio
                
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
                    
                    // Monitor audio levels
                    serviceScope.launch {
                        while (_recordingState.value.isRecording) {
                            if (!isPaused) {
                                try {
                                    val amplitude = mediaRecorder?.maxAmplitude ?: 0
                                    // Convert to 0-1 range
                                    val level = (amplitude / 32768f).coerceIn(0f, 1f)
                                    
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
                    
                    Napier.d("Wear OS recording started successfully")
                } catch (e: IOException) {
                    Napier.e("Failed to start Wear OS recording", e)
                    reset()
                    release()
                    
                    _recordingState.update {
                        it.copy(
                            isRecording = false,
                            error = "Failed to start recording: ${e.message}"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Napier.e("Error setting up Wear OS recording", e)
            stopRecording()
            
            _recordingState.update {
                it.copy(
                    isRecording = false,
                    error = "Error setting up recording: ${e.message}"
                )
            }
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
            
            Napier.d("Wear OS recording paused")
        } catch (e: Exception) {
            Napier.e("Error pausing Wear OS recording", e)
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
            
            Napier.d("Wear OS recording resumed")
        } catch (e: Exception) {
            Napier.e("Error resuming Wear OS recording", e)
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
            releaseWakeLock()
            
            // Update state
            _recordingState.update {
                it.copy(
                    isRecording = false,
                    recordedFilePath = outputFile?.absolutePath
                )
            }
            
            return outputFile?.absolutePath
        } catch (e: Exception) {
            Napier.e("Error stopping Wear OS recording", e)
            
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
    
    /**
     * Acquires a wake lock to keep recording when screen is off
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "LogDate:AudioRecordingWakeLock"
            )
            wakeLock?.acquire(30 * 60 * 1000L) // 30 minutes max
            Napier.d("Wake lock acquired for audio recording")
        } catch (e: Exception) {
            Napier.e("Failed to acquire wake lock", e)
        }
    }
    
    /**
     * Releases the wake lock
     */
    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Napier.d("Wake lock released")
            }
        } catch (e: Exception) {
            Napier.e("Error releasing wake lock", e)
        }
    }
    
    /**
     * Haptic feedback for recording start
     */
    private fun vibrateStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Strong click effect for start
            val effect = VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }
    
    /**
     * Haptic feedback for recording stop
     */
    private fun vibrateStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Double click effect for stop
            val timings = longArrayOf(0, 80, 80, 80)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 80, 80, 80), -1)
        }
    }
    
    /**
     * Haptic feedback for recording pause
     */
    private fun vibratePause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Light click for pause
            val effect = VibrationEffect.createOneShot(40, VibrationEffect.EFFECT_TICK)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(40)
        }
    }
    
    /**
     * Haptic feedback for recording resume
     */
    private fun vibrateResume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Light double click for resume
            val timings = longArrayOf(0, 40, 40, 40)
            val amplitudes = intArrayOf(0, 80, 0, 80)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 40, 40, 40), -1)
        }
    }
}

/**
 * State class for Wear OS recording service
 */
data class WearRecordingState(
    val isRecording: Boolean = false,
    val startTime: Long = 0,
    val durationSeconds: Int = 0,
    val audioLevel: Float = 0f,
    val recordedFilePath: String? = null,
    val error: String? = null
)