package app.logdate.client.media.audio

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import app.logdate.client.media.device.AndroidAudioRouteDevices
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Extension function to start the recording service
 */
fun Context.startAudioRecordingService(
    outputFilePath: String? = null,
    inputDeviceId: String? = null,
) {
    val intent =
        Intent(this, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.SERVICE_ACTION_START
            if (outputFilePath != null) {
                putExtra(AudioRecordingService.EXTRA_OUTPUT_PATH, outputFilePath)
            }
            if (inputDeviceId != null) {
                putExtra(AudioRecordingService.EXTRA_INPUT_DEVICE_ID, inputDeviceId)
            }
        }
    startForegroundService(intent)
}

/**
 * Extension function to stop the recording service
 */
fun Context.stopAudioRecordingService() {
    val intent =
        Intent(this, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.SERVICE_ACTION_STOP
        }
    stopService(intent)
}

/**
 * Android foreground service for audio recording.
 *
 * Handles recording in the background with a persistent notification.
 * Provides binding for clients to interact with the recording.
 *
 * The recorder is prepared and started off the main thread; [recordingState] reports the
 * outcome so a bound client can wait for a confirmed start (or an error) instead of
 * assuming one. Every recorder transition is serialized on [recorderLock] because the
 * bound client, the notification actions, and [onDestroy] reach it from different threads.
 */
class AudioRecordingService : Service() {
    companion object {
        const val NOTIFICATION_ID = 1001
        const val SERVICE_ACTION_START = "app.logdate.action.START_RECORDING"
        const val SERVICE_ACTION_STOP = "app.logdate.action.STOP_RECORDING"
        const val SERVICE_ACTION_PAUSE = AndroidAudioNotificationHandler.ACTION_PAUSE
        const val SERVICE_ACTION_RESUME = AndroidAudioNotificationHandler.ACTION_RESUME
        const val EXTRA_OUTPUT_PATH = "app.logdate.extra.OUTPUT_PATH"
        const val EXTRA_INPUT_DEVICE_ID = "app.logdate.extra.INPUT_DEVICE_ID"
        private const val LEVEL_POLL_INTERVAL_MS = 100L
        private const val DURATION_TICK_MS = 1000L
        private const val MAX_AMPLITUDE = 32768f
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

    // Recording state, guarded by recorderLock
    private val recorderLock = Any()
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var recordingStartTime: Long = 0

    @Volatile
    private var isPaused: Boolean = false

    @Volatile
    private var destroyed: Boolean = false

    // State flow for UI updates
    private val _recordingState = MutableStateFlow(RecordingServiceState())
    val recordingState = _recordingState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        notificationHandler = AndroidAudioNotificationHandler(this)
        Napier.d("Audio recording service created")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            SERVICE_ACTION_START -> {
                Napier.d("Starting audio recording service")
                val outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH)
                val inputDeviceId = intent.getStringExtra(EXTRA_INPUT_DEVICE_ID)
                startForegroundRecording(outputPath, inputDeviceId)
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

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        Napier.d("Audio recording service destroyed")
        destroyed = true
        stopRecording()
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceScope.cancel() // Cancel all coroutines
        super.onDestroy()
    }

    /**
     * Enters the foreground with the recording notification, then prepares the recorder off
     * the main thread. Any failure lands in [recordingState] as an error so the bound client
     * can report it instead of waiting on a recorder that never starts.
     */
    private fun startForegroundRecording(
        outputPath: String?,
        inputDeviceId: String?,
    ) {
        try {
            val notification = notificationHandler.createRecordingNotification(true, System.currentTimeMillis())

            // Start as a foreground service with the microphone type
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Napier.e("Error starting foreground service", e)
            _recordingState.update {
                it.copy(isRecording = false, error = "Recording is not allowed right now: ${e.message}")
            }
            stopSelf()
            return
        }

        serviceScope.launch { startRecording(outputPath, inputDeviceId) }
    }

    /**
     * Pauses the current recording
     */
    internal fun pauseRecording() {
        if (!_recordingState.value.isRecording || isPaused) {
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                synchronized(recorderLock) { mediaRecorder?.pause() }
                isPaused = true
            } else {
                Napier.w("Pause recording not supported below Android N")
                return
            }

            // Update notification to show paused state
            notificationHandler.updateRecordingNotification(
                isRecording = false,
                startTimeMillis = recordingStartTime,
            )

            Napier.d("Recording paused")
        } catch (e: Exception) {
            Napier.e("Error pausing recording", e)
        }
    }

    /**
     * Resumes a paused recording
     */
    internal fun resumeRecording() {
        if (!_recordingState.value.isRecording || !isPaused) {
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                synchronized(recorderLock) { mediaRecorder?.resume() }
                isPaused = false
            } else {
                Napier.w("Resume recording not supported below Android N")
                return
            }

            // Update notification to show recording state
            notificationHandler.updateRecordingNotification(
                isRecording = true,
                startTimeMillis = recordingStartTime,
            )

            Napier.d("Recording resumed")
        } catch (e: Exception) {
            Napier.e("Error resuming recording", e)
        }
    }

    /**
     * Prepares and starts the recorder. A start that arrives while a recording is already
     * running finalizes that recording first so its file is playable and the old recorder
     * does not leak the microphone.
     */
    private fun startRecording(
        outputPath: String?,
        inputDeviceId: String?,
    ) {
        synchronized(recorderLock) {
            if (destroyed) {
                Napier.w("Ignoring a start that arrived after the service was destroyed")
                return
            }
            if (_recordingState.value.isRecording) {
                Napier.w("A start arrived while recording; finalizing the previous recording first")
                stopRecordingLocked()
            }
            var recorder: MediaRecorder? = null
            try {
                val file = resolveOutputFile(outputPath)
                outputFile = file
                recorder = createRecorder(file, inputDeviceId)
                recorder.prepare()
                recorder.start()
                mediaRecorder = recorder
                recordingStartTime = System.currentTimeMillis()
                isPaused = false
                _recordingState.update {
                    it.copy(
                        isRecording = true,
                        startTime = recordingStartTime,
                        recordedFilePath = null,
                        error = null,
                    )
                }
                monitorRecording()
                Napier.d("Recording started successfully")
            } catch (e: Exception) {
                Napier.e("Failed to start recording", e)
                releaseQuietly(recorder)
                mediaRecorder = null
                _recordingState.update {
                    it.copy(isRecording = false, error = "Failed to start recording: ${e.message}")
                }
            }
        }
    }

    private fun resolveOutputFile(outputPath: String?): File =
        if (outputPath != null) {
            File(outputPath).also { file ->
                file.parentFile?.mkdirs()
                if (file.exists()) file.delete()
            }
        } else {
            File.createTempFile("audio_recording_", ".m4a", applicationContext.cacheDir)
        }

    private fun createRecorder(
        file: File,
        inputDeviceId: String?,
    ): MediaRecorder {
        val recorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
        return recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            applyPreferredInputDevice(inputDeviceId)
        }
    }

    /** Polls the input level and advances the elapsed time while the recorder runs. */
    private fun monitorRecording() {
        serviceScope.launch {
            while (_recordingState.value.isRecording) {
                if (!isPaused) {
                    val amplitude =
                        try {
                            synchronized(recorderLock) { mediaRecorder?.maxAmplitude ?: 0 }
                        } catch (e: Exception) {
                            Napier.e("Error getting audio level", e)
                            0
                        }
                    val level = (amplitude / MAX_AMPLITUDE).coerceIn(0f, 1f)
                    _recordingState.update { it.copy(audioLevel = level) }
                }
                delay(LEVEL_POLL_INTERVAL_MS)
            }
        }
        serviceScope.launch {
            var elapsedTimeSeconds = 0
            while (_recordingState.value.isRecording) {
                delay(DURATION_TICK_MS)
                if (!isPaused) {
                    elapsedTimeSeconds++
                }
                _recordingState.update { it.copy(durationSeconds = elapsedTimeSeconds) }
            }
        }
    }

    /**
     * Stops the current recording and finalizes its file.
     * @return The path to the recorded file, or null if nothing was recording or the file
     *   could not be finalized
     */
    fun stopRecording(): String? = synchronized(recorderLock) { stopRecordingLocked() }

    private fun stopRecordingLocked(): String? {
        if (!_recordingState.value.isRecording) {
            return null
        }
        val recorder = mediaRecorder
        mediaRecorder = null
        return try {
            recorder?.stop()
            recorder?.release()
            val path = outputFile?.absolutePath
            _recordingState.update {
                it.copy(isRecording = false, recordedFilePath = path)
            }
            path
        } catch (e: Exception) {
            Napier.e("Error stopping recording", e)
            releaseQuietly(recorder)
            _recordingState.update {
                it.copy(isRecording = false, recordedFilePath = null, error = "Error stopping recording: ${e.message}")
            }
            null
        }
    }

    private fun releaseQuietly(recorder: MediaRecorder?) {
        if (recorder == null) return
        try {
            recorder.reset()
        } catch (e: Exception) {
            Napier.w("Error resetting recorder", e)
        }
        try {
            recorder.release()
        } catch (e: Exception) {
            Napier.w("Error releasing recorder", e)
        }
    }

    /**
     * Gets the recorded file path
     */
    fun getRecordedFilePath(): String? = outputFile?.absolutePath

    /**
     * Checks if recording is currently paused
     */
    fun isRecordingPaused(): Boolean = isPaused && _recordingState.value.isRecording

    /**
     * Re-applies the preferred microphone route while recording is active.
     *
     * This gives the service a chance to follow route changes or device hot-plugs
     * after the recording session has already started.
     */
    fun updatePreferredInputDevice(inputDeviceId: String?) {
        if (!_recordingState.value.isRecording) return
        synchronized(recorderLock) { mediaRecorder?.applyPreferredInputDevice(inputDeviceId) }
    }

    private fun MediaRecorder.applyPreferredInputDevice(inputDeviceId: String?) {
        val preferredDevice = AndroidAudioRouteDevices.findPreferredInputDevice(this@AudioRecordingService, inputDeviceId)
        if (preferredDevice == null) {
            Napier.d("Using system microphone route for recording")
            return
        }

        val applied = setPreferredDevice(preferredDevice)
        Napier.d("Preferred recording microphone ${preferredDevice.productName} applied=$applied")
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
