package app.logdate.client.media.di

import app.logdate.client.media.audio.AndroidAudioDurationResolver
import app.logdate.client.media.audio.AndroidAudioPlaybackManager
import app.logdate.client.media.audio.AndroidAudioRecordingManager
import app.logdate.client.media.audio.AndroidAudioStorage
import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.AudioPlaybackManager
import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.media.audio.AudioStorage
import app.logdate.client.media.audio.transcription.SherpaOnnxTranscriptionService
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
        single<AudioRecordingManager> { AndroidAudioRecordingManager(androidContext(), get()) }
        single<AudioPlaybackManager> { AndroidAudioPlaybackManager(androidContext()) }
        single<AudioDurationResolver> { AndroidAudioDurationResolver(androidContext()) }

        // Provide Sherpa-ONNX-based transcription (on-device, no audio focus required)
        single<TranscriptionService> { SherpaOnnxTranscriptionService(androidContext()) }
    }
