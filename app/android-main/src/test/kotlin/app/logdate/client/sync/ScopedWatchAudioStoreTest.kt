package app.logdate.client.sync

import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.peer.link.LinkWire
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ScopedWatchAudioStoreTest {
    private val root = Files.createTempDirectory("scoped-watch-audio").toFile()
    private val audio = File(root, "audio")
    private val incoming = File(root, "incoming")
    private val context = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { -1 }), "phone", "watch", LinkMode.PAIR)
    private val fresh = context.copy(linkId = LinkWire.encodeKey(ByteArray(32) { 2 }))
    private val id = Uuid.random()
    private val oldStore = store(context)
    private val newStore = store(fresh)

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    @Test
    fun `separate links cannot share staged audio or metadata`() =
        runTest {
            oldStore.stashMetadata(id, mapOf("old" to "capture"))
            oldStore.writeAudio(id, ByteArrayInputStream(byteArrayOf(1, 2)))
            assertNotEquals(oldStore.audioPath(id), newStore.audioPath(id))
            assertFalse(newStore.hasAudio(id))
            assertNull(newStore.stashedMetadata(id))
            assertNull(newStore.storedDigest(id))
        }

    @Test
    fun `the same verified link resumes its staging after restart`() =
        runTest {
            oldStore.stashMetadata(id, mapOf("capture" to "metadata"))
            oldStore.writeAudio(id, ByteArrayInputStream(byteArrayOf(1, 2)))
            val restarted = store(context)
            assertEquals(oldStore.audioPath(id), restarted.audioPath(id))
            assertEquals(mapOf("capture" to "metadata"), restarted.stashedMetadata(id))
            assertContentEquals(byteArrayOf(1, 2), File(restarted.audioPath(id)).readBytes())
            assertEquals(oldStore.storedDigest(id), restarted.storedDigest(id))
        }

    @Test
    fun `deletion and discard cannot reach another link's audio`() =
        runTest {
            oldStore.markDeleted(id)
            newStore.stashMetadata(id, mapOf("fresh" to "capture"))
            newStore.writeAudio(id, ByteArrayInputStream(byteArrayOf(3)))
            oldStore.discard(id)
            assertFalse(newStore.isDeleted(id))
            assertTrue(newStore.hasAudio(id))
            assertEquals(mapOf("fresh" to "capture"), newStore.stashedMetadata(id))
        }

    @Test
    fun `legacy staging cannot be adopted by a scoped transfer`() =
        runTest {
            val legacy = FileWatchAudioStore(audio, incoming)
            legacy.stashMetadata(id, mapOf("legacy" to "metadata"))
            legacy.writeAudio(id, ByteArrayInputStream(byteArrayOf(9)))
            assertFalse(oldStore.hasAudio(id))
            assertNull(oldStore.stashedMetadata(id))
            oldStore.discard(id)
            assertTrue(legacy.hasAudio(id))
        }

    @Test
    fun `invalid contexts cannot name staging directories`() {
        assertFailsWith<IllegalArgumentException> { store(context.copy(linkId = "../../escape")) }
        assertFailsWith<IllegalArgumentException> { store(context.copy(phoneNode = "")) }
        assertFalse(audio.exists())
        assertFalse(incoming.exists())
    }

    @Test
    fun `cancellation propagates and removes only the interrupted staging file`() =
        runTest {
            oldStore.writeAudio(id, ByteArrayInputStream(byteArrayOf(1)))
            val source =
                object : InputStream() {
                    override fun read(): Int = throw CancellationException("Stopped")
                }
            assertFailsWith<CancellationException> { oldStore.writeAudio(id, source) }
            assertContentEquals(byteArrayOf(1), File(oldStore.audioPath(id)).readBytes())
            assertFalse(File(oldStore.audioPath(id) + ".part").exists())
        }

    private fun store(peer: PeerDeliveryContext) = FileWatchAudioStore.scoped(audio, incoming, peer)
}
