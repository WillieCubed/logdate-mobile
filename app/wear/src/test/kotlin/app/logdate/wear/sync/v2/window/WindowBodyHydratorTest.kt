package app.logdate.wear.sync.v2.window

import app.logdate.client.sync.datalayer.v2.BodyRef
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.DeliveryPriority
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.RecordOrigin
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WindowTombstoneDataMap
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.NoteEnvelopeBody
import app.logdate.wear.sync.v2.media.MediaRangeConnection
import app.logdate.wear.sync.v2.media.MediaRangeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowBodyHydratorTest {
    private val f = WindowFixture()
    private val directory = Files.createTempDirectory("hydrated-window").toFile()
    private val large = f.note.copy(content = "large text".repeat(15000))
    private val bytes = NoteEnvelopeBody.encode(large).toString().encodeToByteArray()
    private val ref =
        BodyRef(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, bytes.size.toLong())
    private val envelope =
        NoteEnvelopeBody
            .envelope(large, RecordOrigin.PHONE, DeliveryPriority.BULK)
            .copy(context = f.peer, body = null, bodyRef = ref)
    private val item =
        WindowItem(
            "phone",
            WearPaths.windowItem(RecordKind.NOTE, large.uid, f.peer),
            EnvelopeDataMap.toDataMap(envelope, 1) + (WindowTombstoneDataMap.KEY_REVISION to "2"),
        )
    private var records = listOf(item)
    private var payload = bytes
    private var beforeRead: () -> Unit = {}
    private var opened = 0
    private var closed = 0
    private val hydrator =
        WindowBodyHydrator(WindowItemSource { records }, f.peers, directory) { body ->
            assertEquals(f.peer, body.context)
            assertEquals(large.uid.toString(), body.noteId)
            assertEquals(2L, body.revision)
            MediaRangeSource { request ->
                opened++
                val input = payload.inputStream().also { it.skip(request.offset) }
                object : MediaRangeConnection {
                    override val identity = request.identity
                    override val offset = request.offset
                    override val sourceNodeId = "phone"

                    override suspend fun read(buffer: ByteArray): Int {
                        beforeRead()
                        return input.read(buffer)
                    }

                    override suspend fun close() {
                        closed++
                        input.close()
                    }
                }
            }
        }

    @AfterTest fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test fun `verified large body applies without rewriting the source or revision`() =
        runTest {
            val hydrated = hydrator.items(f.peer).single()
            assertEquals(item.sourceNodeId, hydrated.sourceNodeId)
            assertEquals(item.path, hydrated.path)
            assertEquals(item.data[WindowTombstoneDataMap.KEY_REVISION], hydrated.data[WindowTombstoneDataMap.KEY_REVISION])
            val decoded = (EnvelopeDataMap.fromDataMap(hydrated.data) as Decoded.Supported).value
            assertNull(decoded.bodyRef)
            assertEquals(large, NoteEnvelopeBody.decode(assertNotNull(decoded.body)))
            assertEquals(WindowApplied(1, 0), f.applier.reconcile(f.peer, f.response(listOf(large), 2), listOf(hydrated)))
            assertEquals(large, f.stored[large.uid])
            assertEquals(1, opened)
            assertEquals(opened, closed)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }

    @Test fun `bad hash and partial streams preserve the unresolved reference and existing notes`() =
        runTest {
            f.seed()
            for (bad in listOf(ByteArray(bytes.size) { 1 }, bytes.copyOf(10))) {
                payload = bad
                val result = hydrator.items(f.peer)
                assertEquals(listOf(item), result)
                assertNull(f.applier.reconcile(f.peer, f.response(listOf(large), 2), result))
                assertEquals(f.note, f.stored[f.note.uid])
                assertTrue(directory.listFiles().orEmpty().isEmpty())
            }
            assertEquals(opened, closed)
        }

    @Test fun `foreign source and oversized body never open a channel`() =
        runTest {
            records =
                listOf(
                    item.copy(sourceNodeId = "foreign"),
                    item.copy(
                        data =
                            EnvelopeDataMap.toDataMap(
                                envelope.copy(bodyRef = ref.copy(sizeBytes = Long.MAX_VALUE)),
                                1,
                            ) + (WindowTombstoneDataMap.KEY_REVISION to "2"),
                    ),
                )
            assertEquals(records, hydrator.items(f.peer))
            assertEquals(0, opened)
        }

    @Test fun `revocation during transfer leaves the reference unresolved`() =
        runTest {
            beforeRead = { f.current = null }
            assertEquals(listOf(item), hydrator.items(f.peer))
            assertEquals(opened, closed)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }

    @Test fun `cancellation propagates and removes temporary body`() =
        runTest {
            beforeRead = { throw CancellationException("stop") }
            assertFailsWith<CancellationException> { hydrator.items(f.peer) }
            assertEquals(1, closed)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }
}
