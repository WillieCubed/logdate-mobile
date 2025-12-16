package app.logdate.client.media.di

import app.logdate.client.media.DesktopMediaManager
import app.logdate.client.media.MediaManager
import app.logdate.client.media.audio.transcription.DesktopTranscriptionManager
import app.logdate.client.media.audio.transcription.TranscriptionManager
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module that exposes handles for interacting with OS-specific media library APIs.
 */
actual val mediaModule: Module = module {
    // Include the audio module only
    includes(audioModule)
    
    // Media manager
    single<MediaManager> { DesktopMediaManager() }
    
    // Transcription manager for desktop
    single<TranscriptionManager> { 
        DesktopTranscriptionManager(get())
    }
}