package app.logdate.client.media.di

import android.content.ContentResolver
import app.logdate.client.media.AndroidMediaManager
import app.logdate.client.media.MediaManager
import app.logdate.client.media.audio.transcription.AndroidTranscriptionManager
import app.logdate.client.media.audio.transcription.TranscriptionManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module that exposes handles for interacting with OS-specific media library APIs.
 * 
 * Note: We don't include audioModule here anymore to avoid circular dependencies.
 * The editorAudioModule is redundant and has been replaced by the audioModule and 
 * platformEditorModule in the feature/editor package.
 */
actual val mediaModule: Module = module {
    // Media manager dependencies
    single<ContentResolver> { androidContext().contentResolver }
    single<MediaManager> { AndroidMediaManager(get(), get()) }
    
    // Transcription manager
    single<TranscriptionManager> { AndroidTranscriptionManager(androidContext()) }
}