package app.logdate.client.media.di

import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.media.audio.MockEditorAudioRecorder
import app.logdate.client.media.audio.transcription.IosTranscriptionService
import app.logdate.client.media.audio.transcription.TranscriptionService
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS implementation of audio module
 */
actual val audioModule: Module = module {
    // For backward compatibility, use MockEditorAudioRecorder as AudioRecordingManager too
    // In production code, you'd implement a proper AudioRecordingManager for iOS
    single<AudioRecordingManager> { 
        // Adapt MockEditorAudioRecorder to AudioRecordingManager interface
        object : AudioRecordingManager {
            private val mockRecorder = MockEditorAudioRecorder()
            
            override val isRecording: Boolean
                get() = mockRecorder.recordingState.toString() == "RECORDING"
                
            override suspend fun startRecording() = mockRecorder.startRecording()
            
            override suspend fun stopRecording() = mockRecorder.stopRecording()
            
            override fun getAudioLevelFlow() = mockRecorder.getAudioLevelFlow()
            
            override fun getRecordingDurationFlow() = mockRecorder.getRecordingDurationFlow()
            
            override fun getTranscriptionFlow() = mockRecorder.getTranscriptionFlow()
            
            override fun setTranscriptionService(service: TranscriptionService) {
                // Not supported in mock
            }
            
            override fun release() {
                mockRecorder.release()
            }
        }
    }
    
    // Provide the iOS implementation of TranscriptionService
    factory<TranscriptionService> { IosTranscriptionService() }
}