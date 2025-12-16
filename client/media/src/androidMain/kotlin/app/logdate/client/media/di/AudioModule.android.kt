package app.logdate.client.media.di

import app.logdate.client.media.audio.AndroidAudioRecordingManager
import app.logdate.client.media.audio.AndroidEditorAudioRecorder
import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.media.audio.EditorAudioRecorder
import app.logdate.client.media.audio.EditorAudioRecorderConfig
import app.logdate.client.media.audio.transcription.AndroidTranscriptionService
import app.logdate.client.media.audio.transcription.TranscriptionService
import app.logdate.client.media.audio.ui.AudioRecorderController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android implementation of audio module
 */
actual val audioModule: Module = module {
    // Provide the Android implementation of AudioRecordingManager as a singleton
    single<AudioRecordingManager> { AndroidAudioRecordingManager(androidContext()) }
    
    // Provide the Android implementation of TranscriptionService
    factory<TranscriptionService> { AndroidTranscriptionService(androidContext()) }
    // Provide the Android implementation of EditorAudioRecorder
    single<EditorAudioRecorder> {
        AndroidEditorAudioRecorder(
            context = androidContext(),
            config = EditorAudioRecorderConfig()
        )
    }
    factory {
        // Create AudioRecorderController with a dedicated coroutine scope
        AudioRecorderController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )
    }

}