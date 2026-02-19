package app.logdate.client.media.di

import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.AudioPlaybackManager
import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.media.audio.AudioStorage
import app.logdate.client.media.audio.IosAudioDurationResolver
import app.logdate.client.media.audio.IosAudioPlaybackManager
import app.logdate.client.media.audio.IosAudioRecordingManager
import app.logdate.client.media.audio.IosAudioStorage
import app.logdate.client.media.audio.transcription.IosTranscriptionService
import app.logdate.client.media.audio.transcription.TranscriptionService
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS implementation of audio module
 */
actual val audioModule: Module = module {
    single<AudioStorage> { IosAudioStorage() }
    single<AudioRecordingManager> { IosAudioRecordingManager(get()) }
    single<AudioPlaybackManager> { IosAudioPlaybackManager() }
    single<AudioDurationResolver> { IosAudioDurationResolver() }
    
    // Provide the iOS implementation of TranscriptionService
    factory<TranscriptionService> { IosTranscriptionService() }
}
