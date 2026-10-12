package app.logdate.client.sync.v2.window

import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.datalayer.v2.WindowTombstoneDataMap
import app.logdate.client.sync.peer.delivery.EnvelopeDataMap
import app.logdate.client.sync.peer.delivery.MomentEnvelopeBody
import app.logdate.client.sync.peer.link.LinkWire
import app.logdate.client.sync.v2.MemoryKeyValueStorage
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PhoneMomentWindowPublisherTest {
    private val peer = PeerDeliveryContext(LinkWire.encodeKey(ByteArray(32) { 1 }), "phone", "watch", LinkMode.PAIR)
    private val now = Instant.parse("2026-10-12T15:00:00Z")
    private val mark = MarkedMoment(Uuid.random(), now, "UTC", null, 72, MarkState.OPEN, "", emptySet(), "watch", now, 0)
    private val manifest = WindowManifestStore(MemoryKeyValueStorage())
    private val puts = mutableListOf<Pair<String, Map<String, String>>>()
    private val deletes = mutableListOf<String>()
    private var succeeds = true
    private val items =
        object : WindowDataItems {
            override suspend fun put(
                path: String,
                data: Map<String, String>,
                urgent: Boolean,
            ): Boolean {
                puts += path to data
                return succeeds
            }

            override suspend fun deleteOwn(path: String): Boolean {
                deletes += path
                return succeeds
            }
        }
    private val publisher = PhoneMomentWindowPublisher(manifest, items)

    private suspend fun publish(
        marks: List<MarkedMoment>,
        maxItems: Int = 10,
        maxBytes: Long = 8L * 1024 * 1024,
        interrupted: Boolean = false,
    ) = publisher.publish(peer, marks, now, TimeZone.UTC, 4, maxItems, maxBytes, interrupted) {}

    @Test fun separateMomentProjectionReturnsResolutionAndPreservesSameIdNote() =
        runTest {
            val note = PublishedItem(1, emptySet(), 1)
            manifest.write(peer, PublishedWindow(items = mapOf(mark.id.toString() to note)))
            val first = publish(listOf(mark))
            assertEquals(1, first.written)
            assertEquals(RecordKind.MOMENT, first.members.single().kind)
            assertEquals(WearPaths.windowItem(RecordKind.MOMENT, mark.id, peer), puts.single().first)
            assertEquals(mark, MomentEnvelopeBody.decode((EnvelopeDataMap.fromDataMap(puts.single().second) as Decoded.Supported).value))
            assertEquals(note, manifest.read(peer).items[mark.id.toString()])
            assertEquals(0, publish(listOf(mark)).written)
            val filled = mark.copy(state = MarkState.FILLED, caption = "resolved", resolvedNoteIds = setOf(Uuid.random()))
            val next = publish(listOf(filled))
            assertEquals(1, next.written)
            assertTrue(next.members.single().revision > first.members.single().revision)
            assertEquals(filled, MomentEnvelopeBody.decode((EnvelopeDataMap.fromDataMap(puts.last().second) as Decoded.Supported).value))
        }

    @Test fun cloudVersionOnlyChangesStillPublishTheNewEnvelope() =
        runTest {
            publish(listOf(mark))
            val result = publish(listOf(mark.copy(syncVersion = 9)))
            assertEquals(1, result.written)
            assertEquals(9L, (EnvelopeDataMap.fromDataMap(puts.last().second) as Decoded.Supported).value.syncVersion)
        }

    @Test fun removalUsesTypedTombstoneAndLargeBodiesStayBounded() =
        runTest {
            val large = mark.copy(caption = "resolved mark".repeat(15000), state = MarkState.FILLED)
            publish(listOf(large))
            val envelope = (EnvelopeDataMap.fromDataMap(puts.single().second) as Decoded.Supported).value
            assertNotNull(envelope.bodyRef)
            assertEquals(1, publish(emptyList()).removed)
            assertEquals(WearPaths.windowTombstone(RecordKind.MOMENT, mark.id, peer), puts.last().first)
            assertNotNull(puts.last().second[WindowTombstoneDataMap.KEY_REVISION])
            assertTrue(manifest.read(peer).moments.isEmpty())
            publish(listOf(large), interrupted = true)
            assertTrue(WearPaths.windowTombstone(RecordKind.MOMENT, mark.id, peer) in deletes)
        }

    @Test fun boundedOrFailedPublicationNeverClaimsAnUnwrittenMember() =
        runTest {
            assertTrue(publish(listOf(mark), maxItems = 0).members.isEmpty())
            assertTrue(publish(listOf(mark), maxBytes = 1).members.isEmpty())
            succeeds = false
            val result = publish(listOf(mark))
            assertEquals(1, result.failed)
            assertTrue(result.members.isEmpty())
            succeeds = true
            assertEquals(1, publish(listOf(mark)).written)
        }
}
