package app.logdate.client.media.storage

import app.logdate.client.media.InMemoryMediaManager
import app.logdate.client.media.MediaFileSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecoveredMediaIntegrityTest {
    private val canonical =
        "logdate-media://library/objects/sha256/ba/" +
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad.jpg"

    @Test
    fun `a complete canonical original matches its content digest`() =
        runTest {
            val media = readable("abc")
            assertTrue(media.isRecoveredOriginal(canonical, StoredMediaReferences.Unchanged))
        }

    @Test
    fun `a truncated canonical original is missing even when its current size is accurate`() =
        runTest {
            assertFalse(readable("ab").isRecoveredOriginal(canonical, StoredMediaReferences.Unchanged))
        }

    @Test
    fun `equal length corruption cannot count as a recovered original`() =
        runTest {
            assertFalse(readable("abd").isRecoveredOriginal(canonical, StoredMediaReferences.Unchanged))
        }

    @Test
    fun `a source shorter than its advertised size remains pending`() =
        runTest {
            assertFalse(readable("abc", 4).isRecoveredOriginal(canonical, StoredMediaReferences.Unchanged))
        }

    @Test
    fun `a source longer than its advertised size remains pending`() =
        runTest {
            assertFalse(readable("abc", 2).isRecoveredOriginal(canonical, StoredMediaReferences.Unchanged))
        }

    @Test
    fun `an error after the first readable byte cannot count as complete`() =
        runTest {
            val media = interrupted { throw IllegalStateException("Read interrupted") }
            assertFalse(media.isRecoveredOriginal(canonical, StoredMediaReferences.Unchanged))
        }

    @Test
    fun `cancellation during integrity verification closes the source`() =
        runTest {
            var closed = false
            val media = interrupted(onClose = { closed = true }) { throw CancellationException("Stopped") }
            assertFailsWith<CancellationException> {
                media.isRecoveredOriginal(canonical, StoredMediaReferences.Unchanged)
            }
            assertTrue(closed)
        }

    @Test
    fun `coroutine cancellation stops a source that continues returning bytes`() =
        runTest {
            var reads = 0
            var closed = false
            val verification =
                async {
                    val job = currentCoroutineContext().job
                    val media =
                        object : InMemoryMediaManager() {
                            override suspend fun exists(mediaId: String) = true

                            override suspend fun openMedia(uri: String) =
                                MediaFileSource("original.jpg", "image/jpeg", 3) {
                                    val input = Buffer().apply { write("abc".encodeToByteArray()) }
                                    object : RawSource {
                                        override fun readAtMostTo(
                                            sink: Buffer,
                                            byteCount: Long,
                                        ): Long {
                                            reads++
                                            val read = input.readAtMostTo(sink, 1)
                                            if (reads == 1) job.cancel()
                                            return read
                                        }

                                        override fun close() {
                                            closed = true
                                        }
                                    }
                                }
                        }
                    media.isRecoveredOriginal(canonical, StoredMediaReferences.Unchanged)
                }
            assertFailsWith<CancellationException> { verification.await() }
            assertEquals(1, reads)
            assertTrue(closed)
        }

    @Test
    fun `legacy originals are read completely with bounded buffers`() =
        runTest {
            val bytes = ByteArray(150_000) { 1 }
            var readBytes = 0L
            var closed = false
            val media =
                object : InMemoryMediaManager() {
                    override suspend fun exists(mediaId: String) = true

                    override suspend fun openMedia(uri: String) =
                        MediaFileSource("legacy.jpg", "image/jpeg", bytes.size.toLong()) {
                            val input = Buffer().apply { write(bytes) }
                            object : RawSource {
                                override fun readAtMostTo(
                                    sink: Buffer,
                                    byteCount: Long,
                                ): Long {
                                    assertTrue(byteCount <= 65_536, "Verification must not request the entire original")
                                    assertTrue(sink.size <= 65_536, "Verification must consume each chunk before reading more")
                                    return input.readAtMostTo(sink, byteCount).also { if (it > 0) readBytes += it }
                                }

                                override fun close() {
                                    closed = true
                                }
                            }
                        }
                }
            assertTrue(media.isRecoveredOriginal("logdate-media://library/legacy.jpg", StoredMediaReferences.Unchanged))
            assertEquals(bytes.size.toLong(), readBytes)
            assertTrue(closed)
        }

    @Test
    fun `a malformed canonical directory cannot fall back to a legacy original`() =
        runTest {
            assertFalse(readable("abc").isRecoveredOriginal(canonical.replace("/ba/", "/00/"), StoredMediaReferences.Unchanged))
            assertFalse(
                readable(
                    "abc",
                ).isRecoveredOriginal("logdate-media://library/objects/sha256/not-a-digest.jpg", StoredMediaReferences.Unchanged),
            )
        }

    private fun readable(
        value: String,
        size: Long = value.length.toLong(),
    ) = object : InMemoryMediaManager() {
        override suspend fun exists(mediaId: String) = true

        override suspend fun openMedia(uri: String) =
            MediaFileSource("original.jpg", "image/jpeg", size) {
                Buffer().apply { write(value.encodeToByteArray()) }
            }
    }

    private fun interrupted(
        onClose: () -> Unit = {},
        afterFirstByte: () -> Nothing,
    ) = object : InMemoryMediaManager() {
        override suspend fun exists(mediaId: String) = true

        override suspend fun openMedia(uri: String) =
            MediaFileSource("original.jpg", "image/jpeg", 3) {
                object : RawSource {
                    private var first = true

                    override fun readAtMostTo(
                        sink: Buffer,
                        byteCount: Long,
                    ): Long {
                        if (!first) afterFirstByte()
                        first = false
                        sink.writeByte('a'.code.toByte())
                        return 1
                    }

                    override fun close() = onClose()
                }
            }
    }
}
