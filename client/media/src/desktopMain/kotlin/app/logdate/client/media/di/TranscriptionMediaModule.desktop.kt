package app.logdate.client.media.di

import app.logdate.client.media.audio.transcription.TranscriptionManager
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop implementation of the transcription media module.
 */
actual val platformTranscriptionMediaModule: Module = module {
    // Provide Desktop implementation of TranscriptionManager
    single<TranscriptionManager> { 
        // For desktop, we could create a platform-specific implementation
        // but for now we'll use a stub that does nothing
        object : TranscriptionManager {
            override suspend fun enqueueTranscription(noteId: kotlin.uuid.Uuid, audioUri: String): Boolean = false
            override suspend fun cancelTranscription(noteId: kotlin.uuid.Uuid): Boolean = false
            override suspend fun cancelAllTranscriptions(): Int = 0
        }
    }
}