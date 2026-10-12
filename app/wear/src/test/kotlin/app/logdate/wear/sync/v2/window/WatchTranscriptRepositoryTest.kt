package app.logdate.wear.sync.v2.window

import app.logdate.client.repository.transcription.TranscriptionRepository
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

class WatchTranscriptRepositoryTest {
    @Test fun `watch transcript persistence never starts recognition or schedules phone work`() =
        runTest {
            val phoneEngine = mockk<TranscriptionRepository>(relaxed = true)
            val watch = WatchTranscriptRepository(phoneEngine)
            watch.startAutomaticTranscriptions(backgroundScope)
            assertFalse(watch.requestTranscription(Uuid.random()))
            verify(exactly = 0) { phoneEngine.startAutomaticTranscriptions(any()) }
            coVerify(exactly = 0) { phoneEngine.requestTranscription(any()) }
        }
}
