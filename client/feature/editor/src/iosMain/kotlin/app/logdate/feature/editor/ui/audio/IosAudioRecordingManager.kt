package app.logdate.feature.editor.ui.audio

import app.logdate.feature.editor.ui.audio.iosbackground.IosAudioSessionController
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptions
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSURLResponse
import platform.Foundation.timeIntervalSinceDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * iOS implementation of AudioRecordingManager that supports background audio recording
 * using AVFoundation and proper audio session configuration.
 */
class IosAudioRecordingManager : AudioRecordingManager {
    private var recorder: AVAudioRecorder? = null
    private var recordingURL: NSURL? = null
    private var recordingStartTime: NSDate? = null
    private val audioSessionController = IosAudioSessionController()
    
    private val _recordingDuration = MutableStateFlow(0L)
    private val _audioLevels = MutableStateFlow(listOf<Float>())
    
    private var isRecording = false
    private var isPaused = false
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Callback functions
    private var audioLevelCallback: ((List<Float>) -> Unit)? = null
    private var durationCallback: ((Long) -> Unit)? = null
    
    init {
        Napier.d("IosAudioRecordingManager initialized")
    }

    override fun startRecording(
        onAudioLevelChanged: (List<Float>) -> Unit,
        onDurationChanged: (Long) -> Unit
    ) {
        try {
            Napier.d("Starting iOS recording")
            
            // Store callbacks
            audioLevelCallback = onAudioLevelChanged
            durationCallback = onDurationChanged
            
            // Configure audio session for recording
            if (!audioSessionController.setupAudioSessionForRecording()) {
                throw IllegalStateException("Failed to configure audio session for recording")
            }
            
            // Create recording URL in application documents directory
            val fileManager = platform.Foundation.NSFileManager.defaultManager
            val documentsDirectory = fileManager.URLForDirectory(
                platform.Foundation.NSDocumentDirectory,
                platform.Foundation.NSUserDomainMask,
                null,
                true,
                null
            )
            
            val recordingFileName = "recording_${System.currentTimeMillis()}.m4a"
            recordingURL = documentsDirectory?.URLByAppendingPathComponent(recordingFileName)
            
            // Mark file as excluded from backup
            recordingURL?.let {
                val resourceValues = NSMutableDictionary()
                resourceValues.setObject(NSNumber.numberWithBool(true), NSURLIsExcludedFromBackupKey)
                it.setResourceValues(resourceValues, null)
            }
            
            // Setup recorder settings
            val settings = mutableMapOf<Any?, Any?>()
            settings[platform.AVFAudio.AVFormatIDKey] = platform.CoreAudioTypes.kAudioFormatMPEG4AAC
            settings[platform.AVFAudio.AVSampleRateKey] = 44100.0
            settings[platform.AVFAudio.AVNumberOfChannelsKey] = 2
            settings[platform.AVFAudio.AVEncoderAudioQualityKey] = platform.AVFAudio.AVAudioQualityHigh
            
            // Create recorder
            var recorderError: NSError? = null
            recordingURL?.let { url ->
                recorder = AVAudioRecorder(
                    uRL = url,
                    settings = settings,
                    error = { recorderError = it }
                )
            }
            
            if (recorderError != null) {
                throw Exception("Failed to create audio recorder: ${recorderError?.localizedDescription}")
            }
            
            // Prepare and start recording
            recorder?.let { rec ->
                rec.prepareToRecord()
                val success = rec.record()
                
                if (!success) {
                    throw Exception("Failed to start recording")
                }
                
                isRecording = true
                isPaused = false
                recordingStartTime = NSDate()
                
                // Start monitoring audio levels and duration
                startMonitoring()
                
                Napier.d("iOS recording started successfully")
            } ?: throw Exception("Recorder not initialized")
            
        } catch (e: Exception) {
            Napier.e("Error starting iOS recording: ${e.message}", e)
            audioSessionController.endAudioSession()
            throw e
        }
    }

    override fun stopRecording(): String? {
        if (!isRecording) {
            return null
        }
        
        try {
            Napier.d("Stopping iOS recording")
            
            // Stop recording
            recorder?.stop()
            
            // End audio session
            audioSessionController.endAudioSession()
            
            isRecording = false
            isPaused = false
            
            // Get file path
            val path = recordingURL?.path
            
            // Clean up
            recorder = null
            
            Napier.d("iOS recording stopped, path: $path")
            return path
        } catch (e: Exception) {
            Napier.e("Error stopping iOS recording: ${e.message}", e)
            audioSessionController.endAudioSession()
            recorder = null
            isRecording = false
            return null
        }
    }
    
    /**
     * Pauses the current recording.
     * @return True if successfully paused
     */
    fun pauseRecording(): Boolean {
        if (!isRecording || isPaused) {
            return false
        }
        
        try {
            recorder?.pause()
            isPaused = true
            Napier.d("iOS recording paused")
            return true
        } catch (e: Exception) {
            Napier.e("Error pausing iOS recording: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Resumes a paused recording.
     * @return True if successfully resumed
     */
    fun resumeRecording(): Boolean {
        if (!isRecording || !isPaused) {
            return false
        }
        
        try {
            recorder?.record()
            isPaused = false
            Napier.d("iOS recording resumed")
            return true
        } catch (e: Exception) {
            Napier.e("Error resuming iOS recording: ${e.message}", e)
            return false
        }
    }

    override fun clear() {
        try {
            Napier.d("Clearing iOS recording resources")
            
            if (isRecording) {
                recorder?.stop()
            }
            
            audioSessionController.endAudioSession()
            
            recorder = null
            isRecording = false
            isPaused = false
            
            // Clear callbacks
            audioLevelCallback = null
            durationCallback = null
            
            Napier.d("iOS recording resources cleared")
        } catch (e: Exception) {
            Napier.e("Error clearing iOS recording resources: ${e.message}", e)
        }
    }
    
    /**
     * Starts monitoring audio levels and duration
     */
    private fun startMonitoring() {
        scope.launch {
            while (isRecording) {
                if (!isPaused) {
                    // Update duration
                    recordingStartTime?.let { startTime ->
                        val duration = (NSDate().timeIntervalSinceDate(startTime) * 1000).toLong()
                        _recordingDuration.update { duration }
                        durationCallback?.invoke(duration)
                    }
                    
                    // Update audio levels
                    recorder?.let { rec ->
                        rec.updateMeters()
                        val level = (rec.averagePowerForChannel(0) + 160.0) / 160.0
                        val normalizedLevel = level.coerceIn(0.0, 1.0).toFloat()
                        val levels = listOf(normalizedLevel)
                        _audioLevels.update { levels }
                        audioLevelCallback?.invoke(levels)
                    }
                }
                
                delay(100) // Update every 100ms
            }
        }
    }
}