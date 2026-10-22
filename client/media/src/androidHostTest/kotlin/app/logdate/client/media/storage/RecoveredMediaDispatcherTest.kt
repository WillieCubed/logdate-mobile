package app.logdate.client.media.storage

import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaFileSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveredMediaDispatcherTest {
    @Test
    fun `verification invoked on Main opens reads and closes originals off Main`() =
        runTest {
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { main ->
                Dispatchers.setMain(main)
                try {
                    withContext(Dispatchers.Main) {
                        val mainThread = Thread.currentThread()
                        val operations = AtomicInteger()

                        fun requireBackground() {
                            assertNotEquals(mainThread, Thread.currentThread(), "Original verification must not block Main")
                            operations.incrementAndGet()
                        }
                        val media =
                            object : InMemoryMediaManager() {
                                override suspend fun exists(mediaId: String): Boolean {
                                    requireBackground()
                                    return true
                                }

                                override suspend fun openMedia(uri: String): MediaFileSource {
                                    requireBackground()
                                    return MediaFileSource("original.jpg", "image/jpeg", 3) {
                                        requireBackground()
                                        val input = Buffer().apply { write("abc".encodeToByteArray()) }
                                        object : RawSource {
                                            override fun readAtMostTo(
                                                sink: Buffer,
                                                byteCount: Long,
                                            ): Long {
                                                requireBackground()
                                                return input.readAtMostTo(sink, byteCount)
                                            }

                                            override fun close() {
                                                requireBackground()
                                                input.close()
                                            }
                                        }
                                    }
                                }
                            }
                        assertTrue(media.isRecoveredOriginal("logdate-media://library/original.jpg", StoredMediaReferences.Unchanged))
                        assertEquals(6, operations.get(), "Existence, metadata, opening, both reads, and closing must run off Main")
                        assertEquals(mainThread, Thread.currentThread(), "Verification must return to its caller's context")
                    }
                } finally {
                    Dispatchers.resetMain()
                }
            }
        }
}
