package app.logdate.client.media.di

import app.logdate.client.media.audio.transcription.AndroidTranscriptionManager
import app.logdate.client.media.audio.transcription.TranscriptionManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android implementation of the transcription media module.
 */
actual val platformTranscriptionMediaModule: Module = module {
    // Provide Android implementation of TranscriptionManager
    single<TranscriptionManager> {
        AndroidTranscriptionManager(androidContext())
    }
}