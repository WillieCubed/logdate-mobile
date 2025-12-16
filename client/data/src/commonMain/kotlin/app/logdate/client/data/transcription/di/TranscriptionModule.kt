package app.logdate.client.data.transcription.di

import app.logdate.client.data.transcription.OfflineFirstTranscriptionRepository
import app.logdate.client.media.audio.transcription.TranscriptionManager
import app.logdate.client.repository.transcription.TranscriptionRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for transcription-related dependencies.
 * TranscriptionManager is now provided by the mediaModule.
 */
val transcriptionModule = module {
    // Repository implementation
    single<TranscriptionRepository> { 
        OfflineFirstTranscriptionRepository(get(), get(), get())
    }
}