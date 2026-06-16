package app.logdate.client.media.di

import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.AudioPlaybackManager
import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.media.audio.AudioStorage
import app.logdate.client.media.audio.IosAudioDurationResolver
import app.logdate.client.media.audio.IosAudioPlaybackManager
import app.logdate.client.media.audio.IosAudioRecordingManager
import app.logdate.client.media.audio.IosAudioStorage
import app.logdate.client.media.audio.tagging.AudioTaggingService
import app.logdate.client.media.audio.tagging.IosSoundAnalysisTaggingService
import app.logdate.client.media.audio.transcription.IosTranscriptionService
import app.logdate.client.media.audio.transcription.TranscriptionService
import app.logdate.client.media.device.AudioRouteRepository
import app.logdate.client.media.device.SystemControlledAudioRouteRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS implementation of audio module
 */
actual val audioModule: Module =
    module {
        single<AudioStorage> { IosAudioStorage() }
        single<AudioRecordingManager> { IosAudioRecordingManager(get()) }
        single<AudioPlaybackManager> { IosAudioPlaybackManager() }
        single<AudioDurationResolver> { IosAudioDurationResolver() }
        single<AudioRouteRepository> { SystemControlledAudioRouteRepository() }

        // On-device transcription via Apple's Speech Recognition framework.
        // The system model is always present — no download required.
        single<TranscriptionService> { IosTranscriptionService() }

        // On-device ambient sound detection via Apple's Sound Analysis
        // framework (iOS 15+). Detects birds, traffic, music, rain, and
        // ~300 other categories without any model download.
        single<AudioTaggingService> { IosSoundAnalysisTaggingService() }
    }
