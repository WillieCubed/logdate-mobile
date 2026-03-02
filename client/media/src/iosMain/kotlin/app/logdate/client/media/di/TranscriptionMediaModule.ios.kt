package app.logdate.client.media.di

import app.logdate.client.media.audio.transcription.TranscriptionManager
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.uuid.Uuid

/**
 * iOS implementation of the transcription media module.
 */
actual val platformTranscriptionMediaModule: Module =
    module {
        // Provide iOS implementation of TranscriptionManager
        single<TranscriptionManager> {
            // For iOS, we could create a platform-specific implementation
            // but for now we'll use a stub that does nothing
            object : TranscriptionManager {
                override suspend fun enqueueTranscription(
                    noteId: Uuid,
                    audioUri: String,
                ): Boolean = false

                override suspend fun cancelTranscription(noteId: Uuid): Boolean = false

                override suspend fun cancelAllTranscriptions(): Int = 0
            }
        }
    }
