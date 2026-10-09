package app.logdate.client.media.audio.transcription

import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptionData
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.repository.transcription.TranscriptionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class FileTranscriptionSchedulerTest {
    @Test
    fun `visible and new audio precede queued recovery without restarting active recognition`() =
        runTest {
            val active = CompletableDeferred<TranscriptionResult>()
            val calls = mutableListOf<String>()
            val store = TranscriptStore()
            val service =
                TestService { uri ->
                    calls += uri
                    if (uri == "active.m4a") active.await() else TranscriptionResult.Success(uri, isFinal = true)
                }
            val manager = FileTranscriptionScheduler(service, { store }, backgroundScope, { true })
            val activeId = Uuid.random()
            val visibleId = Uuid.random()
            manager.enqueueTranscription(activeId, "active.m4a", "1", TranscriptionPriority.RECOVERY)
            runCurrent()
            manager.enqueueTranscription(Uuid.random(), "older.m4a", "1", TranscriptionPriority.RECOVERY)
            manager.enqueueTranscription(visibleId, "visible.m4a", "1", TranscriptionPriority.RECOVERY)
            runCurrent()
            manager.enqueueTranscription(visibleId, "visible.m4a", "1", TranscriptionPriority.FOREGROUND)
            manager.enqueueTranscription(activeId, "active.m4a", "1", TranscriptionPriority.FOREGROUND)
            manager.enqueueTranscription(Uuid.random(), "new.m4a", "1", TranscriptionPriority.FOREGROUND)
            runCurrent()
            assertEquals(listOf("active.m4a"), calls)
            active.complete(TranscriptionResult.Success("Active words", isFinal = true))
            runCurrent()
            assertEquals(listOf("active.m4a", "visible.m4a", "new.m4a", "older.m4a"), calls)
            assertEquals(0, manager.cancelAllTranscriptions())
        }

    @Test
    fun `changed media revision replaces active work even when URI stays the same`() =
        runTest {
            val oldResult = CompletableDeferred<TranscriptionResult>()
            var calls = 0
            val store = TranscriptStore()
            val service =
                TestService {
                    calls++
                    if (calls == 1) oldResult.await() else TranscriptionResult.Success("Replacement words", isFinal = true)
                }
            val manager = FileTranscriptionScheduler(service, { store }, backgroundScope, { true })
            val id = Uuid.random()
            manager.enqueueTranscription(id, "recording.m4a", "1000")
            runCurrent()
            manager.enqueueTranscription(id, "recording.m4a", "2000")
            runCurrent()
            oldResult.complete(TranscriptionResult.Success("Obsolete words", isFinal = true))
            runCurrent()
            assertEquals(2, calls)
            assertEquals("Replacement words", store.document?.plainText)
        }

    @Test
    fun `file scheduler persists completed recognition and deduplicates active work`() =
        runTest {
            val result = CompletableDeferred<TranscriptionResult>()
            val store = TranscriptStore()
            val manager = FileTranscriptionScheduler(TestService { result.await() }, { store }, backgroundScope, { true })
            val id = Uuid.random()
            assertTrue(manager.enqueueTranscription(id, "recording.m4a"))
            assertTrue(manager.enqueueTranscription(id, "recording.m4a"))
            runCurrent()
            result.complete(TranscriptionResult.Success("Saved words", isFinal = true))
            runCurrent()
            assertEquals("Saved words", store.document?.plainText)
            assertTrue(store.document?.isFinal == true)
        }

    @Test
    fun `cancellation owns the job and cannot save its later result`() =
        runTest {
            val result = CompletableDeferred<TranscriptionResult>()
            val store = TranscriptStore()
            val manager = FileTranscriptionScheduler(TestService { result.await() }, { store }, backgroundScope, { true })
            val id = Uuid.random()
            assertTrue(manager.enqueueTranscription(id, "old.m4a"))
            runCurrent()
            assertTrue(manager.cancelTranscription(id))
            result.complete(TranscriptionResult.Success("Cancelled words", isFinal = true))
            runCurrent()
            assertEquals(null, store.document)
            assertEquals(0, manager.cancelAllTranscriptions())
        }
}

private class TranscriptStore : TranscriptionRepository {
    var document: TranscriptDocument? = null

    override suspend fun requestTranscription(noteId: Uuid): Boolean = true

    override suspend fun getTranscription(noteId: Uuid): TranscriptionData? = null

    override fun observeTranscription(noteId: Uuid): Flow<TranscriptionData?> = flowOf(null)

    override suspend fun getPendingTranscriptions(): List<TranscriptionData> = emptyList()

    override suspend fun updateTranscription(
        noteId: Uuid,
        text: String?,
        status: TranscriptionStatus,
        errorMessage: String?,
    ): Boolean = true

    override suspend fun updateTranscriptDocument(
        noteId: Uuid,
        document: TranscriptDocument,
        status: TranscriptionStatus,
        errorMessage: String?,
    ): Boolean {
        this.document = document
        return true
    }

    override suspend fun deleteTranscription(noteId: Uuid): Boolean = true
}

private class TestService(
    private val recognize: suspend (String) -> TranscriptionResult,
) : TranscriptionService {
    override fun getTranscriptionFlow() = MutableSharedFlow<TranscriptionResult>()

    override suspend fun startLiveTranscription() = TranscriptionStartResult.Failed(TranscriptionFailure.NotSupported)

    override suspend fun stopLiveTranscription() = TranscriptionResult.Cancelled

    override suspend fun transcribeAudioFile(audioUri: String) = recognize(audioUri)

    override suspend fun cancelTranscription() = Unit

    override fun getSupportedLanguages() = listOf("en-US")

    override fun setLanguage(languageCode: String) = Unit

    override val supportsLiveTranscription = false
    override val supportsFileTranscription = true

    override suspend fun resetTranscription() = Unit

    override fun release() = Unit
}
