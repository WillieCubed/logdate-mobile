package app.logdate.client.media.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Android implementation of EditorAudioRecorder using MediaRecorder and MediaPlayer
 */
class AndroidEditorAudioRecorder(
    private val context: Context,
    private val config: EditorAudioRecorderConfig = EditorAudioRecorderConfig(),
    private val ioDispatcher: CoroutineContext = Dispatchers.IO
) : EditorAudioRecorder {
    
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var recordingFile: File? = null
    
    private var recordingStartTime: Long = 0
    private var recordingPausedAt: Long = 0
    private var recordingDuration: Long = 0
    
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    
    override val recordingState: RecordingState
        get() = _recordingState.value
    
    override suspend fun startRecording(): Boolean {
        if (recordingState == RecordingState.RECORDING) {
            Napier.w("Attempted to start recording while already recording")
            return false
        }
        
        try {
            // Create output file
            val outputDir = context.cacheDir
            val fileExtension = when (config.fileFormat) {
                AudioFileFormat.M4A -> ".m4a"
                AudioFileFormat.MP3 -> ".mp3"
                AudioFileFormat.WAV -> ".wav"
                AudioFileFormat.AAC -> ".aac"
            }
            recordingFile = withContext(ioDispatcher) {
                File.createTempFile("editor_audio_", fileExtension, outputDir)
            }
            
            // Initialize MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                
                // Set output format based on config
                when (config.fileFormat) {
                    AudioFileFormat.M4A -> setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    AudioFileFormat.AAC -> setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                    AudioFileFormat.MP3 -> setOutputFormat(MediaRecorder.OutputFormat.MPEG_4) // MP3 not directly supported
                    AudioFileFormat.WAV -> setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP) // WAV not directly supported
                }
                
                // Set encoder based on config
                when (config.fileFormat) {
                    AudioFileFormat.M4A, AudioFileFormat.MP3 -> setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    AudioFileFormat.AAC -> setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    AudioFileFormat.WAV -> setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                }
                
                setOutputFile(recordingFile?.absolutePath)
                setAudioEncodingBitRate(config.bitRate)
                setAudioSamplingRate(config.sampleRate)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAudioChannels(config.channels)
                }
                
                try {
                    prepare()
                    start()
                    recordingStartTime = System.currentTimeMillis()
                    _recordingState.value = RecordingState.RECORDING
                    
                    Napier.d("Android: Started recording to ${recordingFile?.absolutePath}")
                    return true
                } catch (e: IOException) {
                    Napier.e("Failed to start recording", e)
                    release()
                    return false
                }
            }
            
            return false
        } catch (e: Exception) {
            Napier.e("Error setting up recording", e)
            release()
            return false
        }
    }
    
    override suspend fun pauseRecording(): Boolean {
        if (recordingState != RecordingState.RECORDING) {
            return false
        }
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.pause()
                recordingPausedAt = System.currentTimeMillis()
                recordingDuration += (recordingPausedAt - recordingStartTime)
                _recordingState.value = RecordingState.PAUSED
                
                Napier.d("Android: Paused recording")
                true
            } else {
                // Pause not supported below Android N
                Napier.w("Pause recording not supported on this Android version")
                false
            }
        } catch (e: Exception) {
            Napier.e("Error pausing recording", e)
            false
        }
    }
    
    override suspend fun resumeRecording(): Boolean {
        if (recordingState != RecordingState.PAUSED) {
            return false
        }
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.resume()
                recordingStartTime = System.currentTimeMillis()
                _recordingState.value = RecordingState.RECORDING
                
                Napier.d("Android: Resumed recording")
                true
            } else {
                // Resume not supported below Android N
                Napier.w("Resume recording not supported on this Android version")
                false
            }
        } catch (e: Exception) {
            Napier.e("Error resuming recording", e)
            false
        }
    }
    
    override suspend fun stopRecording(): String? {
        if (recordingState != RecordingState.RECORDING && recordingState != RecordingState.PAUSED) {
            return null
        }
        
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            
            mediaRecorder = null
            _recordingState.value = RecordingState.IDLE
            
            val path = recordingFile?.absolutePath
            Napier.d("Android: Stopped recording to $path")
            
            // Return file path if successful
            return path
        } catch (e: Exception) {
            Napier.e("Error stopping recording", e)
            release()
            return null
        }
    }
    
    override suspend fun cancelRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            
            // Delete the file if it exists
            recordingFile?.let {
                if (it.exists()) {
                    it.delete()
                }
            }
            
            mediaRecorder = null
            recordingFile = null
            _recordingState.value = RecordingState.IDLE
            
            Napier.d("Android: Cancelled recording")
        } catch (e: Exception) {
            Napier.e("Error canceling recording", e)
            release()
        }
    }
    
    override suspend fun startPlayback(uri: String): Boolean {
        if (recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED) {
            Napier.w("Cannot start playback while recording")
            return false
        }
        
        try {
            // Release existing player if any
            mediaPlayer?.release()
            
            // Create new player
            mediaPlayer = MediaPlayer().apply {
                setDataSource(uri)
                prepare()
                
                // Set completion listener
                setOnCompletionListener {
                    _recordingState.value = RecordingState.IDLE
                }
                
                // Start playback
                start()
                _recordingState.value = RecordingState.PLAYING
            }
            
            Napier.d("Android: Started playback of $uri")
            return true
        } catch (e: Exception) {
            Napier.e("Error starting playback", e)
            return false
        }
    }
    
    override suspend fun pausePlayback(): Boolean {
        if (recordingState != RecordingState.PLAYING) {
            return false
        }
        
        return try {
            mediaPlayer?.pause()
            _recordingState.value = RecordingState.PLAYBACK_PAUSED
            
            Napier.d("Android: Paused playback")
            true
        } catch (e: Exception) {
            Napier.e("Error pausing playback", e)
            false
        }
    }
    
    override suspend fun resumePlayback(): Boolean {
        if (recordingState != RecordingState.PLAYBACK_PAUSED) {
            return false
        }
        
        return try {
            mediaPlayer?.start()
            _recordingState.value = RecordingState.PLAYING
            
            Napier.d("Android: Resumed playback")
            true
        } catch (e: Exception) {
            Napier.e("Error resuming playback", e)
            false
        }
    }
    
    override suspend fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            _recordingState.value = RecordingState.IDLE
            
            Napier.d("Android: Stopped playback")
        } catch (e: Exception) {
            Napier.e("Error stopping playback", e)
        }
    }
    
    override fun setVolume(volume: Float) {
        try {
            val clampedVolume = volume.coerceIn(0f, 1f)
            mediaPlayer?.setVolume(clampedVolume, clampedVolume)
        } catch (e: Exception) {
            Napier.e("Error setting volume", e)
        }
    }
    
    override suspend fun seekTo(position: Duration) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mediaPlayer?.seekTo(position.inWholeMilliseconds, MediaPlayer.SEEK_PREVIOUS_SYNC)
            } else {
                mediaPlayer?.seekTo(position.inWholeMilliseconds.toInt())
            }
        } catch (e: Exception) {
            Napier.e("Error seeking to position", e)
        }
    }
    
    override fun getAudioLevelFlow(): Flow<Float> = flow {
        while (recordingState == RecordingState.RECORDING) {
            try {
                val amplitude = mediaRecorder?.maxAmplitude ?: 0
                // Convert to a 0-1 scale (approximate)
                val normalizedLevel = (amplitude / 32768f).coerceIn(0f, 1f)
                emit(normalizedLevel)
            } catch (e: Exception) {
                Napier.e("Error getting audio level", e)
            }
            delay(100) // Update every 100ms
        }
    }
    
    override fun getRecordingDurationFlow(): Flow<Duration> = flow {
        while (recordingState == RecordingState.RECORDING) {
            val currentTime = System.currentTimeMillis()
            val elapsedSinceStart = currentTime - recordingStartTime
            val totalDuration = recordingDuration + elapsedSinceStart
            emit(totalDuration.milliseconds)
            delay(100) // Update every 100ms
        }
    }
    
    override fun getPlaybackPositionFlow(): Flow<Duration> = flow {
        while (recordingState == RecordingState.PLAYING || recordingState == RecordingState.PLAYBACK_PAUSED) {
            try {
                val position = mediaPlayer?.currentPosition ?: 0
                emit(position.milliseconds)
            } catch (e: Exception) {
                Napier.e("Error getting playback position", e)
            }
            delay(100) // Update every 100ms
        }
    }
    
    override suspend fun getWaveformData(uri: String, samples: Int): List<Float> {
        // This is a basic implementation that generates simplified waveform data
        // For a production app, you'd want to use a dedicated audio processing library
        try {
            val player = MediaPlayer()
            player.setDataSource(uri)
            player.prepare()
            
            val duration = player.duration
            player.release()
            
            // For now, generate dummy waveform data based on the duration
            // In a real implementation, you'd analyze the actual audio file
            val waveform = mutableListOf<Float>()
            for (i in 0 until samples) {
                // Generate a pattern that looks somewhat like a waveform
                val position = i.toFloat() / samples
                val amplitude = (0.2f + 0.5f * sin(position * 10) +
                               0.3f * sin(position * 20)).coerceIn(0f, 1f)
                waveform.add(amplitude)
            }
            
            return waveform
        } catch (e: Exception) {
            Napier.e("Error generating waveform data", e)
            // Return empty waveform on error
            return List(samples) { 0f }
        }
    }
    
    override fun release() {
        try {
            mediaRecorder?.apply {
                try {
                    if (recordingState == RecordingState.RECORDING) {
                        stop()
                    }
                } catch (e: Exception) {
                    Napier.e("Error stopping recorder during release", e)
                }
                release()
            }
            
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            
            mediaRecorder = null
            mediaPlayer = null
            _recordingState.value = RecordingState.IDLE
            
            Napier.d("Android: Released resources")
        } catch (e: Exception) {
            Napier.e("Error releasing resources", e)
        }
    }
}