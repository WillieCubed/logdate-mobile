package app.logdate.wear.sync.v2.media

import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.peer.link.LinkWire
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MediaStreamClientTest {
    private val directory = Files.createTempDirectory("media-stream").toFile()
    private val partial = File(directory, "audio.part")
    private val bytes = "phone audio".toByteArray()
    private val identity =
        WatchMediaIdentity(
            PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR),
            Uuid.random(),
            1L,
            bytes.size.toLong(),
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
        )
    private val offsets = mutableListOf<Long>()
    private val connections = mutableListOf<Connection>()
    private var current = true
    private var transform: (Connection) -> Unit = {}
    private val source =
        MediaRangeSource { request ->
            offsets += request.offset
            Connection(request).also {
                transform(it)
                connections += it
            }
        }
    private val client get() = MediaStreamClient(source, { current })

    @After fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test fun `copies verified bytes through the cache write guard`() =
        runTest {
            val writes = mutableListOf<Int>()
            assertTrue(client.copyTo(identity, partial) { writes += it })
            assertEquals("phone audio", partial.readText())
            assertEquals(bytes.size, writes.sum())
            assertEquals(listOf(0L), offsets)
            assertTrue(connections.all { it.closed })
        }

    @Test fun `interrupted reads resume at the received offset`() =
        runTest {
            transform = { it.failAt = if (connections.isEmpty()) 5 else null }
            assertTrue(client.copyTo(identity, partial) {})
            assertEquals(listOf(0L, 5L), offsets)
            assertEquals("phone audio", partial.readText())
            assertTrue(connections.all { it.closed })
        }

    @Test fun `foreign source and mismatched content cannot write bytes`() =
        runTest {
            for (wrong in listOf<(Connection) -> Unit>(
                { it.sourceNodeId = "other" },
                { it.identity = identity.copy(contentVersion = 2) },
                { it.offset = 1L },
            )) {
                transform = wrong
                assertFailsWith<IOException> { client.copyTo(identity, partial) { error("Must not write") } }
                assertEquals(0L, partial.length())
                assertTrue(connections.all { it.closed })
            }
        }

    @Test fun `a stalled read fails after fifteen seconds and closes the connection`() =
        runTest {
            transform = { it.stall = true }
            val started = testScheduler.currentTime
            assertFailsWith<IOException> { client.copyTo(identity, partial) {} }
            assertEquals(15_000L, testScheduler.currentTime - started)
            assertTrue(connections.single().closed)
        }

    @Test fun `exchange changes during a read cannot publish or write its bytes`() =
        runTest {
            transform = { it.afterRead = { current = false } }
            assertFailsWith<IOException> { client.copyTo(identity, partial) { error("Must not write") } }
            assertEquals(0L, partial.length())
            assertTrue(connections.single().closed)
        }

    @Test fun `wrong digest and oversized media fail verification`() =
        runTest {
            assertFailsWith<IOException> { client.copyTo(identity.copy(sha256 = "0".repeat(64)), partial) {} }
            transform = { it.extra = true }
            assertFailsWith<IOException> { client.copyTo(identity, partial) {} }
            assertTrue(connections.all { it.closed })
        }

    @Test fun `cancelled reads close the connection and propagate cancellation`() =
        runTest {
            transform = { it.cancel = true }
            assertFailsWith<kotlinx.coroutines.CancellationException> { client.copyTo(identity, partial) {} }
            assertTrue(connections.single().closed)
        }

    @Test fun `a rejected cache write does not reopen the phone stream`() =
        runTest {
            assertFailsWith<IOException> {
                client.copyTo(identity, partial) { throw IOException("The cache lease was evicted") }
            }
            assertEquals(listOf(0L), offsets)
            assertEquals(0L, partial.length())
            assertTrue(connections.single().closed)
        }

    @Test fun `opening a channel has the same inactivity limit`() =
        runTest {
            val stalled = MediaStreamClient(MediaRangeSource { awaitCancellation() }, { true })
            val started = testScheduler.currentTime
            assertFailsWith<IOException> { stalled.copyTo(identity, partial) {} }
            assertEquals(15_000L, testScheduler.currentTime - started)
        }

    @Test fun `zero byte reads cannot indefinitely extend the inactivity limit`() =
        runTest {
            transform = { it.zero = true }
            val started = testScheduler.currentTime
            assertFailsWith<IOException> { client.copyTo(identity, partial) {} }
            assertEquals(15_000L, testScheduler.currentTime - started)
            assertTrue(connections.single().closed)
        }

    @Test fun `progress resets inactivity so a long active transfer can finish`() =
        runTest {
            transform = { it.readDelay = 10_000L }
            assertTrue(client.copyTo(identity, partial) {})
            assertTrue(testScheduler.currentTime > 15_000L)
            assertEquals("phone audio", partial.readText())
        }

    @Test fun `short media stops after the bounded resume attempts`() =
        runTest {
            transform = { it.endAt = 5 }
            assertFailsWith<IOException> { client.copyTo(identity, partial) {} }
            assertEquals(listOf(0L, 5L, 5L), offsets)
            assertTrue(connections.all { it.closed })
        }

    private inner class Connection(
        request: MediaRangeRequest,
    ) : MediaRangeConnection {
        override var identity = request.identity
        override var offset = request.offset
        override var sourceNodeId = request.identity.peer.phoneNode
        var failAt: Int? = null
        var stall = false
        var zero = false
        var readDelay = 0L
        var endAt: Int? = null
        var cancel = false
        var extra = false
        var closed = false
        var afterRead: () -> Unit = {}
        private var position = request.offset.toInt()

        override suspend fun read(buffer: ByteArray): Int {
            if (stall) awaitCancellation()
            if (zero) return 0
            kotlinx.coroutines.delay(readDelay)
            if (position == endAt) return -1
            if (cancel) throw kotlinx.coroutines.CancellationException("superseded")
            if (position == failAt) throw IOException("Link interrupted")
            if (position == bytes.size) {
                if (extra) {
                    extra = false
                    buffer[0] = 1
                    return 1
                }
                return -1
            }
            val count = minOf(5, bytes.size - position)
            bytes.copyInto(buffer, 0, position, position + count)
            position += count
            afterRead()
            return count
        }

        override suspend fun close() {
            closed = true
        }
    }
}
