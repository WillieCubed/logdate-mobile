package app.logdate.client.media.di

import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.AudioPlaybackManager
import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.media.audio.AudioStorage
import app.logdate.client.media.audio.DesktopAudioDurationResolver
import app.logdate.client.media.audio.DesktopAudioPlaybackManager
import app.logdate.client.media.audio.DesktopAudioRecordingManager
import app.logdate.client.media.audio.DesktopAudioStorage
import app.logdate.client.media.audio.transcription.DesktopTranscriptionService
import app.logdate.client.media.audio.transcription.TranscriptionService
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop implementation of audio module
 */
actual val audioModule: Module =
    module {
        single<AudioStorage> { DesktopAudioStorage() }
        // Provide the Desktop implementation of AudioRecordingManager as a singleton
        single<AudioRecordingManager> { DesktopAudioRecordingManager(get()) }
        single<AudioPlaybackManager> { DesktopAudioPlaybackManager() }
        single<AudioDurationResolver> { DesktopAudioDurationResolver() }

        // Provide the Desktop implementation of TranscriptionService
        factory<TranscriptionService> { DesktopTranscriptionService() }
    }
