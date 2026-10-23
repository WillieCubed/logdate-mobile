package app.logdate.client.domain.restore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ArchiveMediaIntegrityTest {
    private val abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun `exact bytes are copied before publication`() =
        runTest {
            val destination = Buffer()
            ArchiveMediaIntegrity(3, abc).copyTo(Buffer().writeUtf8("abc"), destination)
            assertEquals("abc", destination.readUtf8())
        }

    @Test
    fun `truncated equal-length modified and oversized originals cannot be published`() =
        runTest {
            for (bytes in listOf("ab", "abd", "abcd")) {
                var published = false
                assertFailsWith<IllegalArgumentException> {
                    ArchiveMediaIntegrity(3, abc).copyTo(Buffer().writeUtf8(bytes), Buffer())
                    published = true
                }
                assertEquals(false, published)
            }
        }

    @Test
    fun `late read failure closes source without publishing staged bytes`() =
        runTest {
            var closed = false
            var published = false
            var reads = 0
            val input =
                object : ForwardingSource(Buffer().write(ByteArray(20_000))) {
                    override fun read(
                        sink: Buffer,
                        byteCount: Long,
                    ): Long {
                        if (++reads == 2) throw IllegalStateException("Synthetic read failure")
                        return super.read(sink, byteCount)
                    }

                    override fun close() {
                        closed = true
                        super.close()
                    }
                }
            assertFailsWith<IllegalStateException> {
                input.use { ArchiveMediaIntegrity(20_000, "0".repeat(64)).copyTo(it, Buffer()) }
                published = true
            }
            assertTrue(closed)
            assertEquals(false, published)
        }

    @Test
    fun `stream reads are bounded and cancellation stops before consuming the original`() =
        runTest {
            val job = Job()
            var total = 0L
            var maximum = 0L
            var closed = false
            val input: Source =
                object : ForwardingSource(Buffer().write(ByteArray(100_000))) {
                    override fun read(
                        sink: Buffer,
                        byteCount: Long,
                    ): Long {
                        maximum = maxOf(maximum, byteCount)
                        val count = super.read(sink, byteCount)
                        if (count > 0) {
                            total += count
                            job.cancel()
                        }
                        return count
                    }

                    override fun close() {
                        closed = true
                        super.close()
                    }
                }
            assertFailsWith<CancellationException> {
                input.use { withContext(job) { ArchiveMediaIntegrity(100_000, "0".repeat(64)).copyTo(it, Buffer()) } }
            }
            assertTrue(closed)
            assertTrue(maximum <= 8_192)
            assertTrue(total <= 8_192, "Cancelled copies must not drain the remaining original")
        }
}
