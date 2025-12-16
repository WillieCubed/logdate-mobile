package app.logdate.client.media.di

import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.media.audio.DesktopAudioRecordingManager
import app.logdate.client.media.audio.transcription.DesktopTranscriptionService
import app.logdate.client.media.audio.transcription.TranscriptionService
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop implementation of audio module
 */
actual val audioModule: Module = module {
    // Provide the Desktop implementation of AudioRecordingManager as a singleton
    single<AudioRecordingManager> { DesktopAudioRecordingManager() }
    
    // Provide the Desktop implementation of TranscriptionService
    factory<TranscriptionService> { DesktopTranscriptionService() }
}