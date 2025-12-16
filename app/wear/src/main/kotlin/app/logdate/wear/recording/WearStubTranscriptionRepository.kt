package app.logdate.wear.recording

import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.repository.transcription.TranscriptionResult
import app.logdate.client.repository.transcription.TranscriptionStatus
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.uuid.Uuid

/**
 * Stub implementation of TranscriptionRepository for Wear OS.
 * 
 * Provides a minimal implementation that allows reusing the AudioViewModel
 * without implementing actual transcription functionality on the watch.
 */
class WearStubTranscriptionRepository : TranscriptionRepository {
    
    override suspend fun requestTranscription(noteId: Uuid): Boolean {
        Napier.d("Transcription not implemented on Wear OS yet")
        return false
    }
    
    override fun observeTranscription(noteId: Uuid): Flow<TranscriptionResult?> {
        // Return empty flow - no transcription support on Wear OS
        return emptyFlow()
    }
    
    override suspend fun getTranscription(noteId: Uuid): TranscriptionResult? {
        return null
    }
    
    override suspend fun cancelTranscription(noteId: Uuid): Boolean {
        return true
    }
}