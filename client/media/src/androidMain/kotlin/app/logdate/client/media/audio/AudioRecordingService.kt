package app.logdate.client.media.audio

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
    options: RecordingSessionOptions = RecordingSessionOptions(),
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
            putExtra(AudioRecordingService.EXTRA_MAX_DURATION_MS, options.maxDurationMs)
            putExtra(AudioRecordingService.EXTRA_PAUSE_ON_INTERRUPTION, options.pauseOnInterruption)
            putExtra(AudioRecordingService.EXTRA_HOLD_WAKE_LOCK, options.holdWakeLock)
            putExtra(AudioRecordingService.EXTRA_CRASH_SAFE, options.crashSafe)
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
        const val EXTRA_MAX_DURATION_MS = "app.logdate.extra.MAX_DURATION_MS"
        const val EXTRA_PAUSE_ON_INTERRUPTION = "app.logdate.extra.PAUSE_ON_INTERRUPTION"
        const val EXTRA_HOLD_WAKE_LOCK = "app.logdate.extra.HOLD_WAKE_LOCK"
        const val EXTRA_CRASH_SAFE = "app.logdate.extra.CRASH_SAFE"
        private const val WAKE_LOCK_TAG = "LogDate:AudioRecordingWakeLock"
        private const val WAKE_LOCK_HEADROOM_MS = 60_000L
        private const val DEFAULT_WAKE_LOCK_TIMEOUT_MS = 35 * 60_000L
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

    /** Counts start requests, so a stop that finishes late can tell a newer session now owns the service. */
    @Volatile
    private var sessionGeneration: Int = 0

    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var options = RecordingSessionOptions()

    private val audioFocusListener =
        AudioManager.OnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                Napier.d("Audio focus lost ($change); pausing the recording")
                pauseRecording(byInterruption = true)
            }
        }

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
                options =
                    RecordingSessionOptions(
                        maxDurationMs = intent.getLongExtra(EXTRA_MAX_DURATION_MS, 0L),
                        pauseOnInterruption = intent.getBooleanExtra(EXTRA_PAUSE_ON_INTERRUPTION, false),
                        holdWakeLock = intent.getBooleanExtra(EXTRA_HOLD_WAKE_LOCK, false),
                        crashSafe = intent.getBooleanExtra(EXTRA_CRASH_SAFE, false),
                    )
                sessionGeneration++
                startForegroundRecording(outputPath, inputDeviceId)
            }
            SERVICE_ACTION_STOP -> {
                Napier.d("Stopping audio recording service")
                val session = sessionGeneration
                // Finishing a crash-safe recording copies the whole file, which must not block the main thread.
                serviceScope.launch {
                    if (sessionGeneration != session) return@launch
                    stopRecording()
                    // A start that arrived while the file was being finished owns the service now.
                    if (sessionGeneration != session) return@launch
                    releaseSessionResources()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
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
        if (options.crashSafe && _recordingState.value.isRecording) {
            // Wrapping a long recording must not block the main thread. The thread outlives the service
            // and drops the wake lock only once the file is finished.
            Thread({
                stopRecording()
                releaseSessionResources()
            }, "recording-finalizer").start()
        } else {
            stopRecording()
            releaseSessionResources()
        }
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
    internal fun pauseRecording(byInterruption: Boolean = false) {
        if (!_recordingState.value.isRecording || isPaused) {
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                synchronized(recorderLock) { mediaRecorder?.pause() }
                isPaused = true
                releaseWakeLock()
                _recordingState.update { it.copy(isPaused = true, pausedByInterruption = byInterruption) }
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
                acquireWakeLock()
                _recordingState.update { it.copy(isPaused = false, pausedByInterruption = false) }
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
                recorder = createRecorder(recordingFileFor(file), inputDeviceId)
                recorder.prepare()
                recorder.start()
                mediaRecorder = recorder
                recordingStartTime = System.currentTimeMillis()
                isPaused = false
                acquireSessionResources()
                _recordingState.update {
                    it.copy(
                        isRecording = true,
                        isPaused = false,
                        pausedByInterruption = false,
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

    /** The file the recorder writes: the m4a itself, or the raw AAC that is wrapped into it when it ends. */
    private fun recordingFileFor(finalFile: File): File =
        if (options.crashSafe) CrashSafeRecording.inFlightFile(finalFile).also { it.delete() } else finalFile

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
            setOutputFormat(if (options.crashSafe) MediaRecorder.OutputFormat.AAC_ADTS else MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            if (options.maxDurationMs > 0) setMaxDuration(options.maxDurationMs.toInt())
            setOnInfoListener { _, what, _ -> onRecorderInfo(what) }
            setOnErrorListener { _, what, extra -> onRecorderError(what, extra) }
            applyPreferredInputDevice(inputDeviceId)
        }
    }

    /**
     * The recorder ended the session itself at a configured limit. Finalizing here means the
     * file is complete before the bound client learns the session is over.
     */
    private fun onRecorderInfo(what: Int) {
        val limitReached =
            what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED
        if (!limitReached) return
        Napier.d("Recorder reached its limit ($what); finalizing the recording")
        serviceScope.launch {
            stopRecording()
            releaseSessionResources()
        }
    }

    private fun onRecorderError(
        what: Int,
        extra: Int,
    ) {
        Napier.e("Recorder error what=$what extra=$extra")
        serviceScope.launch {
            stopRecording()
            releaseSessionResources()
            _recordingState.update { it.copy(error = "Recording stopped unexpectedly (error $what)") }
        }
    }

    private fun acquireSessionResources() {
        releaseSessionResources()
        acquireWakeLock()
        if (options.pauseOnInterruption) requestAudioFocus()
    }

    /**
     * The lock's timeout is the longest the session can still record. The recorder counts recorded
     * time only, so it is dropped while paused and taken again, with a fresh timeout, on resume.
     */
    private fun acquireWakeLock() {
        if (!options.holdWakeLock) return
        releaseWakeLock()
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val timeoutMs =
                if (options.maxDurationMs > 0) options.maxDurationMs + WAKE_LOCK_HEADROOM_MS else DEFAULT_WAKE_LOCK_TIMEOUT_MS
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply { acquire(timeoutMs) }
        } catch (e: Exception) {
            Napier.w("Could not acquire the recording wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            Napier.w("Error releasing the recording wake lock", e)
        }
        wakeLock = null
    }

    private fun requestAudioFocus() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val request =
                AudioFocusRequest
                    .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    ).setOnAudioFocusChangeListener(audioFocusListener)
                    .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } catch (e: Exception) {
            Napier.w("Could not request audio focus for the recording", e)
        }
    }

    private fun releaseSessionResources() {
        releaseWakeLock()
        val request = focusRequest ?: return
        focusRequest = null
        try {
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(request)
        } catch (e: Exception) {
            Napier.w("Error abandoning audio focus", e)
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
            val path = finalizeOutputFile()
            _recordingState.update {
                it.copy(isRecording = false, isPaused = false, pausedByInterruption = false, recordedFilePath = path)
            }
            path
        } catch (e: Exception) {
            Napier.e("Error stopping recording", e)
            releaseQuietly(recorder)
            _recordingState.update {
                it.copy(
                    isRecording = false,
                    isPaused = false,
                    pausedByInterruption = false,
                    recordedFilePath = null,
                    error = "Error stopping recording: ${e.message}",
                )
            }
            null
        }
    }

    /**
     * Wraps a crash-safe recording into its m4a. The raw AAC file stays beside it: its owner deletes it
     * once the recording is saved, and until then it marks the recording as unsaved for startup recovery.
     * A recording that cannot be wrapped is reported as a failed stop and recovered from the raw file at
     * the next launch.
     */
    private fun finalizeOutputFile(): String? {
        val finalFile = outputFile ?: return null
        if (!options.crashSafe) return finalFile.absolutePath
        check(AdtsToM4aRemuxer.remuxToM4a(CrashSafeRecording.inFlightFile(finalFile), finalFile)) {
            "Could not finish the recording file"
        }
        return finalFile.absolutePath
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
    val isPaused: Boolean = false,
    /** True when the pause came from losing audio focus (a call, another app) rather than the user. */
    val pausedByInterruption: Boolean = false,
    val startTime: Long = 0,
    val durationSeconds: Int = 0,
    val audioLevel: Float = 0f,
    val recordedFilePath: String? = null,
    val error: String? = null,
)
