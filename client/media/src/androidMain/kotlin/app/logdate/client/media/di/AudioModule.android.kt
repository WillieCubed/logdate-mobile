package app.logdate.client.media.di

import app.logdate.client.media.audio.AndroidAudioDurationResolver
import app.logdate.client.media.audio.AndroidAudioPlaybackManager
import app.logdate.client.media.audio.AndroidAudioRecordingManager
import app.logdate.client.media.audio.AndroidAudioStorage
import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.AudioPlaybackManager
import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.media.audio.AudioStorage
import app.logdate.client.media.audio.tagging.AudioTaggingService
import app.logdate.client.media.audio.tagging.OnDemandAudioTaggingService
import app.logdate.client.media.audio.transcription.OnDemandTranscriptionService
import app.logdate.client.media.audio.transcription.TranscriptionService
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
            )
        }
        single<AudioPlaybackManager> { AndroidAudioPlaybackManager(androidContext(), get()) }
        single<AudioDurationResolver> { AndroidAudioDurationResolver(androidContext()) }

        // On-demand transcription: loads Sherpa-ONNX from dynamic module when available,
        // falls back to Android's built-in SpeechRecognizer otherwise
        single<TranscriptionService> {
            OnDemandTranscriptionService(
                context = androidContext(),
                scope = get(),
                dataUsagePolicy = get(),
            )
        }

        // On-device ambient sound tagging. Loads CED from the speech-recognition
        // dynamic feature module when present and reports as unavailable otherwise.
        single<AudioTaggingService> { OnDemandAudioTaggingService(androidContext()) }
    }
