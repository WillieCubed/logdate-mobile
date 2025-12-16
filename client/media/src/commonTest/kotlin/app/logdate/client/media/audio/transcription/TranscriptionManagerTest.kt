package app.logdate.client.media.audio.transcription

import io.github.aakira.napier.Napier
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Tests for the [TranscriptionManager] interface and its implementations.
 */
class TranscriptionManagerTest {

    @Test
    fun testTranscriptionManagerContract() {
        // This is a simple contract test to ensure the interface behaves correctly.
        // Ideally, we would have platform-specific tests for each implementation.
        
        val mockTranscriptionService = object : TranscriptionService {
            override suspend fun transcribeAudioFile(audioUri: String): TranscriptionResult {
                return TranscriptionResult.Success("This is a mock transcription result")
            }
        }
        
        val manager = MockTranscriptionManager(mockTranscriptionService)
        
        runTest {
            // Test successful enqueue
            val noteId = Uuid.parse("00000000-0000-0000-0000-000000000001")
            val audioUri = "file:///mock/audio/path.mp3"
            
            assertTrue(manager.enqueueTranscription(noteId, audioUri))
            assertEquals(1, manager.activeJobCount)
            
            // Test cancel specific
            assertTrue(manager.cancelTranscription(noteId))
            assertEquals(0, manager.activeJobCount)
            
            // Test enqueue multiple
            val noteId2 = Uuid.parse("00000000-0000-0000-0000-000000000002")
            val audioUri2 = "file:///mock/audio/path2.mp3"
            
            assertTrue(manager.enqueueTranscription(noteId, audioUri))
            assertTrue(manager.enqueueTranscription(noteId2, audioUri2))
            assertEquals(2, manager.activeJobCount)
            
            // Test cancel all
            assertEquals(2, manager.cancelAllTranscriptions())
            assertEquals(0, manager.activeJobCount)
            
            // Test duplicate enqueue
            assertTrue(manager.enqueueTranscription(noteId, audioUri))
            assertFalse(manager.enqueueTranscription(noteId, audioUri)) // Should return false for duplicate
            assertEquals(1, manager.activeJobCount)
        }
    }
    
    /**
     * A simple mock implementation for testing the contract.
     */
    private class MockTranscriptionManager(
        private val transcriptionService: TranscriptionService
    ) : TranscriptionManager {
        private val activeJobs = mutableMapOf<Uuid, String>()
        
        val activeJobCount: Int
            get() = activeJobs.size
            
        override suspend fun enqueueTranscription(noteId: Uuid, audioUri: String): Boolean {
            if (activeJobs.containsKey(noteId)) {
                return false
            }
            
            activeJobs[noteId] = audioUri
            
            // For testing purposes, we're not actually running the job
            // but we would call transcriptionService.transcribeAudioFile(audioUri) in a real implementation
            
            return true
        }
        
        override suspend fun cancelTranscription(noteId: Uuid): Boolean {
            return activeJobs.remove(noteId) != null
        }
        
        override suspend fun cancelAllTranscriptions(): Int {
            val count = activeJobs.size
            activeJobs.clear()
            return count
        }
    }
}