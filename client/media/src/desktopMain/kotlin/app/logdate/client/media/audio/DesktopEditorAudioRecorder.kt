package app.logdate.client.media.audio

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Desktop implementation of EditorAudioRecorder using JavaSound API
 * for recording and playback of audio on desktop platforms.
 */
class DesktopEditorAudioRecorder(
    private val config: EditorAudioRecorderConfig = EditorAudioRecorderConfig()
) : EditorAudioRecorder {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    
    // Thread for recording
    private var recordingThread: Thread? = null
    
    // Audio format for recording
    private val audioFormat = AudioFormat(
        config.sampleRate.toFloat(),
        16, // bits per sample
        config.channels,
        true, // signed
        false // big endian
    )
    
    // Audio line for capturing
    private var line: TargetDataLine? = null
    
    // Recording timestamps
    private var recordingStartTime: Long = 0
    private var recordingPausedAt: Long = 0
    private var recordingDuration: Long = 0
    
    // Audio data buffer
    private var audioData: ByteArrayOutputStream? = null
    
    // Playback
    private var playbackThread: Thread? = null
    private var currentPlaybackPosition: Long = 0
    private var lastPlaybackUpdate: Long = 0
    private var playbackFile: File? = null
    
    // Audio levels for visualization
    private val _audioLevels = MutableStateFlow<List<Float>>(emptyList())
    
    override val recordingState: RecordingState
        get() = _recordingState.value
    
    override suspend fun startRecording(): Boolean {
        if (recordingState == RecordingState.RECORDING) {
            return false
        }
        
        try {
            // Set up audio format and data line
            val info = DataLine.Info(TargetDataLine::class.java, audioFormat)
            
            if (!AudioSystem.isLineSupported(info)) {
                Napier.e("Desktop: Audio line not supported")
                return false
            }
            
            line = AudioSystem.getLine(info) as TargetDataLine
            line?.open(audioFormat)
            line?.start()
            
            audioData = ByteArrayOutputStream()
            recordingStartTime = System.currentTimeMillis()
            
            // Start recording thread
            recordingThread = thread(start = true, name = "AudioRecordingThread") {
                val buffer = ByteArray(4096)
                var bytesRead: Int
                
                try {
                    while (_recordingState.value == RecordingState.RECORDING && line != null) {
                        bytesRead = line!!.read(buffer, 0, buffer.size)
                        if (bytesRead > 0) {
                            audioData?.write(buffer, 0, bytesRead)
                            
                            // Calculate audio levels for visualization
                            val levels = calculateAudioLevels(buffer, bytesRead)
                            scope.launch(Dispatchers.Main) {
                                _audioLevels.value = updateAudioLevels(_audioLevels.value, levels)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Napier.e("Desktop: Error recording audio", e)
                }
            }
            
            _recordingState.value = RecordingState.RECORDING
            Napier.d("Desktop: Started recording")
            return true
        } catch (e: LineUnavailableException) {
            Napier.e("Desktop: Could not start recording", e)
            return false
        } catch (e: Exception) {
            Napier.e("Desktop: Error starting recording", e)
            release()
            return false
        }
    }
    
    override suspend fun pauseRecording(): Boolean {
        if (recordingState != RecordingState.RECORDING) {
            return false
        }
        
        try {
            line?.stop()
            recordingPausedAt = System.currentTimeMillis()
            recordingDuration += (recordingPausedAt - recordingStartTime)
            _recordingState.value = RecordingState.PAUSED
            
            Napier.d("Desktop: Paused recording")
            return true
        } catch (e: Exception) {
            Napier.e("Desktop: Error pausing recording", e)
            return false
        }
    }
    
    override suspend fun resumeRecording(): Boolean {
        if (recordingState != RecordingState.PAUSED) {
            return false
        }
        
        try {
            line?.start()
            recordingStartTime = System.currentTimeMillis()
            _recordingState.value = RecordingState.RECORDING
            
            Napier.d("Desktop: Resumed recording")
            return true
        } catch (e: Exception) {
            Napier.e("Desktop: Error resuming recording", e)
            return false
        }
    }
    
    override suspend fun stopRecording(): String? {
        if (recordingState != RecordingState.RECORDING && recordingState != RecordingState.PAUSED) {
            return null
        }
        
        try {
            if (recordingState == RecordingState.RECORDING) {
                val now = System.currentTimeMillis()
                recordingDuration += (now - recordingStartTime)
            }
            
            // Stop recording thread
            _recordingState.value = RecordingState.IDLE
            recordingThread?.join(1000) // Give thread time to finish
            recordingThread = null
            
            // Stop and close the line
            line?.stop()
            line?.close()
            
            // Create output file
            val fileExtension = ".wav" // JavaSound natively supports WAV
            val outputFile = File.createTempFile("desktop_audio_", fileExtension)
            
            // Save audio data to file
            saveAudioToFile(outputFile)
            
            // Reset state
            line = null
            audioData = null
            
            Napier.d("Desktop: Stopped recording: ${outputFile.absolutePath}")
            return outputFile.absolutePath
        } catch (e: Exception) {
            Napier.e("Desktop: Error stopping recording", e)
            release()
            return null
        }
    }
    
    override suspend fun cancelRecording() {
        try {
            _recordingState.value = RecordingState.IDLE
            
            // Stop recording thread
            recordingThread?.interrupt()
            recordingThread = null
            
            // Stop and close the line
            line?.stop()
            line?.close()
            line = null
            
            // Clear audio data
            audioData = null
            recordingDuration = 0
            
            Napier.d("Desktop: Cancelled recording")
        } catch (e: Exception) {
            Napier.e("Desktop: Error cancelling recording", e)
        }
    }
    
    override suspend fun startPlayback(uri: String): Boolean {
        if (recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED) {
            return false
        }
        
        try {
            val file = File(uri.removePrefix("file://"))
            if (!file.exists()) {
                Napier.e("Desktop: Playback file not found: $uri")
                return false
            }
            
            playbackFile = file
            currentPlaybackPosition = 0
            lastPlaybackUpdate = System.currentTimeMillis()
            _recordingState.value = RecordingState.PLAYING
            
            // Start playback in a separate thread
            playbackThread = thread(start = true, name = "AudioPlaybackThread") {
                try {
                    val audioInputStream = AudioSystem.getAudioInputStream(file)
                    val format = audioInputStream.format
                    val info = DataLine.Info(javax.sound.sampled.SourceDataLine::class.java, format)
                    
                    val line = AudioSystem.getLine(info) as javax.sound.sampled.SourceDataLine
                    line.open(format)
                    line.start()
                    
                    val buffer = ByteArray(4096)
                    var bytesRead = 0
                    
                    while (bytesRead != -1 && _recordingState.value == RecordingState.PLAYING) {
                        bytesRead = audioInputStream.read(buffer, 0, buffer.size)
                        if (bytesRead > 0) {
                            line.write(buffer, 0, bytesRead)
                        }
                    }
                    
                    line.drain()
                    line.close()
                    audioInputStream.close()
                    
                    // If we completed normally, return to idle state
                    if (_recordingState.value == RecordingState.PLAYING) {
                        scope.launch(Dispatchers.Main) {
                            _recordingState.value = RecordingState.IDLE
                        }
                    }
                } catch (e: Exception) {
                    Napier.e("Desktop: Error during playback", e)
                    scope.launch(Dispatchers.Main) {
                        _recordingState.value = RecordingState.IDLE
                    }
                }
            }
            
            Napier.d("Desktop: Started playback of $uri")
            return true
        } catch (e: Exception) {
            Napier.e("Desktop: Error starting playback", e)
            return false
        }
    }
    
    override suspend fun pausePlayback(): Boolean {
        if (recordingState != RecordingState.PLAYING) {
            return false
        }
        
        try {
            _recordingState.value = RecordingState.PLAYBACK_PAUSED
            playbackThread?.interrupt()
            playbackThread = null
            
            Napier.d("Desktop: Paused playback")
            return true
        } catch (e: Exception) {
            Napier.e("Desktop: Error pausing playback", e)
            return false
        }
    }
    
    override suspend fun resumePlayback(): Boolean {
        if (recordingState != RecordingState.PLAYBACK_PAUSED) {
            return false
        }
        
        // We need to restart playback from the current position
        try {
            playbackFile?.let { file ->
                lastPlaybackUpdate = System.currentTimeMillis()
                _recordingState.value = RecordingState.PLAYING
                
                // Start playback in a separate thread from the current position
                playbackThread = thread(start = true, name = "AudioPlaybackThread") {
                    try {
                        val audioInputStream = AudioSystem.getAudioInputStream(file)
                        val format = audioInputStream.format
                        val info = DataLine.Info(javax.sound.sampled.SourceDataLine::class.java, format)
                        
                        val line = AudioSystem.getLine(info) as javax.sound.sampled.SourceDataLine
                        line.open(format)
                        line.start()
                        
                        // Skip to current position
                        val bytesPerMilli = format.frameSize * format.frameRate / 1000
                        val bytesToSkip = (bytesPerMilli * currentPlaybackPosition).toLong()
                        audioInputStream.skip(bytesToSkip)
                        
                        val buffer = ByteArray(4096)
                        var bytesRead = 0
                        
                        while (bytesRead != -1 && _recordingState.value == RecordingState.PLAYING) {
                            bytesRead = audioInputStream.read(buffer, 0, buffer.size)
                            if (bytesRead > 0) {
                                line.write(buffer, 0, bytesRead)
                            }
                        }
                        
                        line.drain()
                        line.close()
                        audioInputStream.close()
                        
                        // If we completed normally, return to idle state
                        if (_recordingState.value == RecordingState.PLAYING) {
                            scope.launch(Dispatchers.Main) {
                                _recordingState.value = RecordingState.IDLE
                            }
                        }
                    } catch (e: Exception) {
                        Napier.e("Desktop: Error during resumed playback", e)
                        scope.launch(Dispatchers.Main) {
                            _recordingState.value = RecordingState.IDLE
                        }
                    }
                }
                
                Napier.d("Desktop: Resumed playback")
                return true
            }
            
            return false
        } catch (e: Exception) {
            Napier.e("Desktop: Error resuming playback", e)
            return false
        }
    }
    
    override suspend fun stopPlayback() {
        if (recordingState != RecordingState.PLAYING && recordingState != RecordingState.PLAYBACK_PAUSED) {
            return
        }
        
        try {
            _recordingState.value = RecordingState.IDLE
            playbackThread?.interrupt()
            playbackThread = null
            currentPlaybackPosition = 0
            
            Napier.d("Desktop: Stopped playback")
        } catch (e: Exception) {
            Napier.e("Desktop: Error stopping playback", e)
        }
    }
    
    override fun setVolume(volume: Float) {
        // JavaSound doesn't have a simple volume control method
        // In a real implementation, we would need to modify the audio data
        Napier.d("Desktop: Set volume to $volume (not implemented)")
    }
    
    override suspend fun seekTo(position: Duration) {
        currentPlaybackPosition = position.inWholeMilliseconds
        
        // If currently playing, restart playback from the new position
        if (recordingState == RecordingState.PLAYING) {
            pausePlayback()
            resumePlayback()
        }
        
        Napier.d("Desktop: Seeked to position ${position.inWholeMilliseconds}ms")
    }
    
    override fun getAudioLevelFlow(): Flow<Float> = flow {
        while (recordingState == RecordingState.RECORDING) {
            // Emit current audio level
            _audioLevels.value.lastOrNull()?.let { emit(it) } ?: emit(0f)
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
            }
            
            emit(currentPlaybackPosition.milliseconds)
            delay(100)
        }
    }
    
    override suspend fun getWaveformData(uri: String, samples: Int): List<Float> {
        try {
            val file = File(uri.removePrefix("file://"))
            if (!file.exists()) {
                Napier.e("Desktop: Waveform file not found: $uri")
                return List(samples) { 0f }
            }
            
            // Get audio data for waveform visualization
            val audioInputStream = AudioSystem.getAudioInputStream(file)
            val format = audioInputStream.format
            val frameSize = format.frameSize
            val sampleSize = format.sampleSizeInBits / 8
            
            // Calculate bytes to read for each sample
            val streamLengthInBytes = file.length()
            val bytesPerSample = streamLengthInBytes / samples
            
            val waveform = mutableListOf<Float>()
            val buffer = ByteArray(bytesPerSample.toInt())
            
            for (i in 0 until samples) {
                val bytesRead = audioInputStream.read(buffer)
                if (bytesRead <= 0) break
                
                // Calculate average amplitude for this segment
                var sum = 0.0
                var count = 0
                
                for (j in 0 until bytesRead step frameSize) {
                    if (j + sampleSize <= bytesRead) {
                        // Read a single sample (supporting different bit depths)
                        val amplitude = when (format.sampleSizeInBits) {
                            8 -> buffer[j].toInt() and 0xFF
                            16 -> {
                                val low = buffer[j].toInt() and 0xFF
                                val high = buffer[j + 1].toInt() and 0xFF
                                if (format.isBigEndian) (high shl 8) or low else (low shl 8) or high
                            }
                            else -> 0
                        }
                        
                        val normalizedAmplitude = when (format.sampleSizeInBits) {
                            8 -> (amplitude / 128.0) - 1.0
                            16 -> amplitude / 32768.0
                            else -> 0.0
                        }
                        
                        sum += Math.abs(normalizedAmplitude)
                        count++
                    }
                }
                
                // Add average amplitude to waveform
                val avgAmplitude = if (count > 0) (sum / count).toFloat() else 0f
                waveform.add(avgAmplitude)
            }
            
            audioInputStream.close()
            
            // Normalize waveform (0.0 to 1.0)
            val maxAmplitude = waveform.maxOrNull() ?: 1f
            val normalizedWaveform = if (maxAmplitude > 0) {
                waveform.map { it / maxAmplitude }
            } else {
                waveform
            }
            
            Napier.d("Desktop: Generated waveform data with $samples samples from $uri")
            return normalizedWaveform
        } catch (e: Exception) {
            Napier.e("Desktop: Error generating waveform data", e)
            // Return empty waveform on error
            return List(samples) { 0f }
        }
    }
    
    override fun release() {
        try {
            // Stop recording
            if (recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED) {
                recordingThread?.interrupt()
                line?.stop()
                line?.close()
            }
            
            // Stop playback
            if (recordingState == RecordingState.PLAYING || recordingState == RecordingState.PLAYBACK_PAUSED) {
                playbackThread?.interrupt()
            }
            
            recordingThread = null
            playbackThread = null
            line = null
            audioData = null
            playbackFile = null
            recordingDuration = 0
            _recordingState.value = RecordingState.IDLE
            
            Napier.d("Desktop: Released audio recorder resources")
        } catch (e: Exception) {
            Napier.e("Desktop: Error releasing resources", e)
        }
    }
    
    /**
     * Saves the recorded audio data to a file in WAV format
     */
    private fun saveAudioToFile(outputFile: File) {
        if (audioData == null) {
            Napier.e("Desktop: No audio data to save")
            return
        }
        
        try {
            // Create an audio input stream from the recorded data
            val byteData = audioData!!.toByteArray()
            val byteArrayInputStream = java.io.ByteArrayInputStream(byteData)
            val audioInputStream = AudioInputStream(
                byteArrayInputStream,
                audioFormat,
                byteData.size.toLong() / audioFormat.frameSize
            )
            
            // Write to file
            AudioSystem.write(
                audioInputStream,
                javax.sound.sampled.AudioFileFormat.Type.WAVE,
                outputFile
            )
            
            audioInputStream.close()
            Napier.d("Desktop: Saved audio file to ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Napier.e("Desktop: Error saving audio file", e)
        }
    }
    
    /**
     * Calculates audio levels from the raw audio buffer
     */
    private fun calculateAudioLevels(buffer: ByteArray, bytesRead: Int): Float {
        if (bytesRead <= 0) return 0f
        
        var sum = 0.0
        val bytesPerSample = audioFormat.sampleSizeInBits / 8
        val frameSize = audioFormat.frameSize
        
        var sampleCount = 0
        
        for (i in 0 until bytesRead step frameSize) {
            if (i + bytesPerSample <= bytesRead) {
                // Process each sample based on bit depth
                val amplitude = when (audioFormat.sampleSizeInBits) {
                    8 -> {
                        // 8-bit audio is unsigned in Java
                        val unsignedByte = buffer[i].toInt() and 0xFF
                        unsignedByte / 128.0 - 1.0 // Convert to -1.0 to 1.0 range
                    }
                    16 -> {
                        // 16-bit audio (handle endianness)
                        val low = buffer[i].toInt() and 0xFF
                        val high = buffer[i + 1].toInt() and 0xFF
                        val sample = if (audioFormat.isBigEndian) (high shl 8) or low else (low shl 8) or high
                        sample / 32768.0 // Convert to -1.0 to 1.0 range
                    }
                    else -> 0.0
                }
                
                sum += Math.abs(amplitude)
                sampleCount++
            }
        }
        
        // Calculate average amplitude (0.0 to 1.0)
        val avgAmplitude = if (sampleCount > 0) (sum / sampleCount).toFloat() else 0f
        
        // Apply some smoothing and amplification to make the visualization more responsive
        return (avgAmplitude * 3.0f).coerceIn(0f, 1f)
    }
    
    /**
     * Updates the list of audio levels for visualization
     */
    private fun updateAudioLevels(currentLevels: List<Float>, newLevel: Float): List<Float> {
        val maxHistory = 50 // Keep last 50 levels
        val newLevels = currentLevels + newLevel
        return newLevels.takeLast(maxHistory)
    }
}