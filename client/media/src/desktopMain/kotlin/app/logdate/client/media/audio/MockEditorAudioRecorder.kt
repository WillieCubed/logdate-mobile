package app.logdate.client.media.audio

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.io.File
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Mock implementation of EditorAudioRecorder for desktop platforms
 * This simulates recording behavior for development and testing
 */
class MockEditorAudioRecorder(
    private val config: EditorAudioRecorderConfig = EditorAudioRecorderConfig()
) : EditorAudioRecorder {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    private var recordingStartTime: Long = 0
    private var recordingPausedAt: Long = 0
    private var recordingDuration: Long = 0
    private var simulatedDuration: Long = 0
    private var currentPlaybackPosition: Long = 0
    private var lastPlaybackUpdate: Long = 0
    
    override val recordingState: RecordingState
        get() = _recordingState.value
    
    override suspend fun startRecording(): Boolean {
        if (recordingState == RecordingState.RECORDING) {
            return false
        }
        
        recordingStartTime = System.currentTimeMillis()
        _recordingState.value = RecordingState.RECORDING
        
        Napier.d("Mock: Started simulated recording")
        return true
    }
    
    override suspend fun pauseRecording(): Boolean {
        if (recordingState != RecordingState.RECORDING) {
            return false
        }
        
        recordingPausedAt = System.currentTimeMillis()
        recordingDuration += (recordingPausedAt - recordingStartTime)
        _recordingState.value = RecordingState.PAUSED
        
        Napier.d("Mock: Paused simulated recording")
        return true
    }
    
    override suspend fun resumeRecording(): Boolean {
        if (recordingState != RecordingState.PAUSED) {
            return false
        }
        
        recordingStartTime = System.currentTimeMillis()
        _recordingState.value = RecordingState.RECORDING
        
        Napier.d("Mock: Resumed simulated recording")
        return true
    }
    
    override suspend fun stopRecording(): String? {
        if (recordingState != RecordingState.RECORDING && recordingState != RecordingState.PAUSED) {
            return null
        }
        
        if (recordingState == RecordingState.RECORDING) {
            val now = System.currentTimeMillis()
            recordingDuration += (now - recordingStartTime)
        }
        
        // Create a temp file to simulate recording
        val fileExtension = when (config.fileFormat) {
            AudioFileFormat.M4A -> ".m4a"
            AudioFileFormat.MP3 -> ".mp3"
            AudioFileFormat.WAV -> ".wav"
            AudioFileFormat.AAC -> ".aac"
        }
        
        val tempFile = File.createTempFile("mock_audio_", fileExtension)
        simulatedDuration = recordingDuration
        recordingDuration = 0
        _recordingState.value = RecordingState.IDLE
        
        Napier.d("Mock: Stopped simulated recording: ${tempFile.absolutePath}")
        return tempFile.absolutePath
    }
    
    override suspend fun cancelRecording() {
        recordingDuration = 0
        _recordingState.value = RecordingState.IDLE
        
        Napier.d("Mock: Cancelled simulated recording")
    }
    
    override suspend fun startPlayback(uri: String): Boolean {
        if (recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED) {
            return false
        }
        
        currentPlaybackPosition = 0
        lastPlaybackUpdate = System.currentTimeMillis()
        _recordingState.value = RecordingState.PLAYING
        
        Napier.d("Mock: Started simulated playback of $uri")
        return true
    }
    
    override suspend fun pausePlayback(): Boolean {
        if (recordingState != RecordingState.PLAYING) {
            return false
        }
        
        _recordingState.value = RecordingState.PLAYBACK_PAUSED
        
        Napier.d("Mock: Paused simulated playback")
        return true
    }
    
    override suspend fun resumePlayback(): Boolean {
        if (recordingState != RecordingState.PLAYBACK_PAUSED) {
            return false
        }
        
        lastPlaybackUpdate = System.currentTimeMillis()
        _recordingState.value = RecordingState.PLAYING
        
        Napier.d("Mock: Resumed simulated playback")
        return true
    }
    
    override suspend fun stopPlayback() {
        if (recordingState != RecordingState.PLAYING && recordingState != RecordingState.PLAYBACK_PAUSED) {
            return
        }
        
        currentPlaybackPosition = 0
        _recordingState.value = RecordingState.IDLE
        
        Napier.d("Mock: Stopped simulated playback")
    }
    
    override fun setVolume(volume: Float) {
        Napier.d("Mock: Set simulated volume to $volume")
    }
    
    override suspend fun seekTo(position: Duration) {
        currentPlaybackPosition = position.inWholeMilliseconds
        
        Napier.d("Mock: Seeked to simulated position ${position.inWholeMilliseconds}ms")
    }
    
    override fun getAudioLevelFlow(): Flow<Float> = flow {
        while (recordingState == RecordingState.RECORDING) {
            // Generate random audio levels that look somewhat realistic
            val baseLevel = 0.1f + (Random.nextFloat() * 0.2f)
            val spike = if (Random.nextFloat() > 0.8f) Random.nextFloat() * 0.7f else 0f
            val level = (baseLevel + spike).coerceIn(0f, 1f)
            
            emit(level)
            delay(100)
        }
    }
    
    override fun getRecordingDurationFlow(): Flow<Duration> = flow {
        while (recordingState == RecordingState.RECORDING) {
            val currentTime = System.currentTimeMillis()
            val elapsedSinceStart = currentTime - recordingStartTime
            val totalDuration = recordingDuration + elapsedSinceStart
            emit(totalDuration.milliseconds)
            delay(100)
        }
    }
    
    override fun getPlaybackPositionFlow(): Flow<Duration> = flow {
        while (recordingState == RecordingState.PLAYING || recordingState == RecordingState.PLAYBACK_PAUSED) {
            if (recordingState == RecordingState.PLAYING) {
                val now = System.currentTimeMillis()
                val elapsed = now - lastPlaybackUpdate
                lastPlaybackUpdate = now
                
                if (recordingState == RecordingState.PLAYING) {
                    currentPlaybackPosition += elapsed
                }
                
                // Loop back to start if we reach the end of the simulated duration
                if (simulatedDuration > 0 && currentPlaybackPosition > simulatedDuration) {
                    currentPlaybackPosition = 0
                    _recordingState.value = RecordingState.IDLE
                }
            }
            
            emit(currentPlaybackPosition.milliseconds)
            delay(100)
        }
    }
    
    override suspend fun getWaveformData(uri: String, samples: Int): List<Float> {
        // Generate dummy waveform data
        val waveform = mutableListOf<Float>()
        
        for (i in 0 until samples) {
            // Generate a pattern that looks somewhat like a waveform
            val position = i.toFloat() / samples
            val amplitude = (0.2f + 0.5f * kotlin.math.sin(position * 10) + 
                           0.3f * kotlin.math.sin(position * 20)).coerceIn(0f, 1f)
            waveform.add(amplitude)
        }
        
        Napier.d("Mock: Generated simulated waveform data with $samples samples")
        return waveform
    }
    
    override fun release() {
        _recordingState.value = RecordingState.IDLE
        recordingDuration = 0
        currentPlaybackPosition = 0
        
        Napier.d("Mock: Released simulated audio recorder resources")
    }
}