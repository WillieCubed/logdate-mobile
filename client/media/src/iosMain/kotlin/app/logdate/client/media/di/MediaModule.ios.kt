package app.logdate.client.media.di

import app.logdate.client.media.audio.transcription.IosTranscriptionManager
import app.logdate.client.media.audio.transcription.TranscriptionManager
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module that exposes handles for interacting with OS-specific media library APIs.
 */
actual val mediaModule: Module = module {
    // Include the audio module only
    includes(audioModule)
    
    // TODO: Implement iOS MediaManager
    
    // Transcription manager for iOS
    single<TranscriptionManager> { 
        IosTranscriptionManager(get())
    }
}