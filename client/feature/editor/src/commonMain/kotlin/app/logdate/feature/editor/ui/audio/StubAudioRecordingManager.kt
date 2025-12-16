package app.logdate.feature.editor.ui.audio

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration
import kotlinx.datetime.Clock

/**
 * Stub implementation of AudioRecordingManager that simulates recording
 * without actually accessing hardware. Used for platforms where we don't
 * have a proper implementation yet.
 * 
 * IMPORTANT: This is NOT a real implementation and should be replaced with
 * a platform-specific implementation as soon as possible.
 */
class StubAudioRecordingManager : AudioRecordingManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isRecording = false
    private var startTime = 0L
    private val audioLevels = MutableStateFlow(listOf(0f))
    private val duration = MutableStateFlow(0L)
    
    init {
        Napier.w("⚠️ STUB IMPLEMENTATION: StubAudioRecordingManager initialized - this is not a real implementation!")
        Napier.w("⚠️ Audio recording functionality will be simulated, not actually performed")
        Napier.w("⚠️ Replace with a real platform implementation for production use")
    }
    
    override fun startRecording(
        onAudioLevelChanged: (List<Float>) -> Unit,
        onDurationChanged: (Long) -> Unit
    ) {
        Napier.w("⚠️ STUB IMPLEMENTATION: Starting simulated recording (not a real recording)")
        isRecording = true
        startTime = Clock.System.now().toEpochMilliseconds()
        
        // Simulate audio levels changing
        scope.launch {
            while (isActive && isRecording) {
                val randomLevel = Random.nextFloat() * 0.8f + 0.1f
                val levels = audioLevels.value.toMutableList()
                levels.add(randomLevel)
                if (levels.size > 50) {
                    levels.removeAt(0)
                }
                audioLevels.value = levels
                onAudioLevelChanged(levels)
                delay(100)
            }
        }
        
        // Simulate duration changing
        scope.launch {
            while (isActive && isRecording) {
                val currentDuration = Clock.System.now().toEpochMilliseconds() - startTime
                duration.value = currentDuration
                onDurationChanged(currentDuration)
                delay(100)
            }
        }
        
        Napier.d("StubAudioRecordingManager: Simulated recording started")
    }
    
    override fun stopRecording(): String? {
        Napier.w("⚠️ STUB IMPLEMENTATION: Stopping simulated recording (not a real recording)")
        isRecording = false
        
        // Simulate a recording file
        val simulatedFilePath = "file://stub_recording_${Clock.System.now().toEpochMilliseconds()}.mp3"
        Napier.d("StubAudioRecordingManager: Simulated recording saved to $simulatedFilePath")
        Napier.w("⚠️ This is a fake file path. No actual audio file was created.")
        
        return simulatedFilePath
    }
    
    override fun clear() {
        Napier.w("⚠️ STUB IMPLEMENTATION: Clearing simulated recording resources")
        isRecording = false
    }
    
    /**
     * Simulated audio level flow for testing
     */
    fun getAudioLevelFlow(): Flow<Float> = flow {
        while (isRecording) {
            emit(Random.nextFloat())
            delay(100)
        }
    }
    
    /**
     * Simulated duration flow for testing
     */
    fun getRecordingDurationFlow(): Flow<Duration> = flow {
        while (isRecording) {
            emit(kotlin.time.Duration.parse("${duration.value}ms"))
            delay(100)
        }
    }
}