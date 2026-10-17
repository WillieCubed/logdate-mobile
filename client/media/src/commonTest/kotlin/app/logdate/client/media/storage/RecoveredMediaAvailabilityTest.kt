package app.logdate.client.media.storage

import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaFileSource
import app.logdate.client.media.MediaPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecoveredMediaAvailabilityTest {
    @Test
    fun `a provider metadata query cannot prove an original is readable`() =
        runTest {
            val media =
                object : InMemoryMediaManager() {
                    override suspend fun exists(mediaId: String) = true

                    override suspend fun openMedia(uri: String): MediaFileSource = throw SecurityException("Grant revoked")
                }
            assertFalse(media.isRecoveredOriginal("content://media/external/images/media/123", StoredMediaReferences.Unchanged))
            assertFalse(media.isRecoveredOriginal("logdate-media://library/original.jpg", StoredMediaReferences.Unchanged))
        }

    @Test
    fun `private canonical originals remain usable without library permission`() =
        runTest {
            val media = InMemoryMediaManager()
            val local = media.saveMedia(MediaPayload("original.jpg", "image/jpeg", 1, byteArrayOf(1)))
            val references = StoredMediaReferences { if (it == local) "logdate-media://library/original.jpg" else it }
            assertTrue(media.isRecoveredOriginal(local, references))
            assertFalse(media.isRecoveredOriginal("logdate-unavailable://archive/original", references))
        }

    @Test
    fun `cancelling the readability check cancels recovery`() =
        runTest {
            val media =
                object : InMemoryMediaManager() {
                    override suspend fun exists(mediaId: String) = true

                    override suspend fun openMedia(uri: String): MediaFileSource = throw CancellationException("Stopped")
                }
            assertFailsWith<CancellationException> {
                media.isRecoveredOriginal("logdate-media://library/original.jpg", StoredMediaReferences.Unchanged)
            }
        }
}
