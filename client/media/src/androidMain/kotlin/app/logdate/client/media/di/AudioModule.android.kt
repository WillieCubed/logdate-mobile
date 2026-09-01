package app.logdate.client.media.di

import app.logdate.client.media.audio.AndroidAudioDurationResolver
import app.logdate.client.media.audio.AndroidAudioPlaybackManager
import app.logdate.client.media.audio.AndroidAudioRecordingManager
import app.logdate.client.media.audio.AndroidAudioStorage
import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.AudioPlaybackManager
import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.media.audio.AudioStorage
import app.logdate.client.media.audio.SpeechFeatureProviderLoader
import app.logdate.client.media.audio.tagging.AudioTaggingService
import app.logdate.client.media.audio.tagging.InstallTimeAudioTaggingService
import app.logdate.client.media.audio.transcription.InstallTimeTranscriptionService
import app.logdate.client.media.audio.transcription.TranscriptionService
import app.logdate.client.media.device.AndroidAudioRouteRepository
import app.logdate.client.media.device.AudioRouteRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android implementation of audio module
 */
actual val audioModule: Module =
    module {
        single<AudioStorage> { AndroidAudioStorage(androidContext()) }
        // Provide the Android implementation of AudioRecordingManager as a singleton
        single<AudioRecordingManager> {
            AndroidAudioRecordingManager(
                context = androidContext(),
                audioStorage = get(),
                transcriptionRepository = get(),
                audioTaggingService = get(),
                audioTagRepository = get(),
                audioRouteRepository = get(),
            )
        }
        single<AudioPlaybackManager> { AndroidAudioPlaybackManager(androidContext(), get()) }
        single<AudioDurationResolver> { AndroidAudioDurationResolver(androidContext()) }
        single<AudioRouteRepository> { AndroidAudioRouteRepository(androidContext()) }

        single { SpeechFeatureProviderLoader() }

        // Core on-device speech ships as an install-time feature split. The facade
        // keeps the base module independent of Sherpa-ONNX while making recording
        // treat transcription as an always-present product capability.
        single<TranscriptionService> {
            InstallTimeTranscriptionService(
                context = androidContext(),
                scope = get(),
                providerLoader = get(),
            )
        }

        // CED remains an optional model download inside the installed speech feature.
        single<AudioTaggingService> {
            InstallTimeAudioTaggingService(
                context = androidContext(),
                providerLoader = get(),
            )
        }
    }
