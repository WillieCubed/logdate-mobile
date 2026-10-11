package app.logdate.client.sync

import app.logdate.client.sync.datalayer.NoteDataMapper
import app.logdate.client.sync.datalayer.v2.RecordKind
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.datalayer.v2.WearPaths
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.peer.state.PhoneState
import app.logdate.client.sync.peer.state.StateDocument
import app.logdate.client.sync.peer.state.StateDocumentCodec
import app.logdate.client.sync.peer.state.WatchState
import app.logdate.client.sync.v2.receipts.WatchRecordIngestorV2
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneDataLayerListenerServiceTest : PhoneDataLayerListenerFixture() {
    @Test
    fun `process snapshot events drops work when sync dependencies are unavailable`() =
        runTest {
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    syncDependencies = null,
                )

            service.processEvents(noteDeleteEvent(Uuid.random()))
        }

    @Test
    fun `onDataChanged snapshots wearable events and processes valid payloads`() =
        runTest {
            val note = textNote()
            val ingestor = mockk<WatchNoteIngestor>(relaxed = true)
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    syncDependencies = dependencies(noteIngestor = ingestor),
                )
            val payload =
                buildMap {
                    putAll(noteDataMapper.toDataMap(note))
                    put("_ignored", "should not be read")
                }

            mockkStatic(DataMapItem::class)
            try {
                val dataItem = mockDataItem(uriPath = NoteDataMapper.notePath(note.uid))
                every { DataMapItem.fromDataItem(dataItem) } returns mockDataMapItem(payload)
                val dataEvents = mockDataEventBuffer(mockDataEvent(dataItem))

                service.onDataChanged(dataEvents)
                advanceUntilIdle()

                coVerify(exactly = 1) { ingestor.onNoteMetadata(noteDataMapper.toDataMap(note)) }
            } finally {
                unmockkStatic(DataMapItem::class)
            }
        }

    @Test
    fun `onDataChanged skips events without a path`() =
        runTest {
            val ingestor = mockk<WatchNoteIngestor>(relaxed = true)
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    syncDependencies = dependencies(noteIngestor = ingestor),
                )

            val dataEvents = mockDataEventBuffer(mockDataEvent(mockDataItem(uriPath = null)))
            service.onDataChanged(dataEvents)
            advanceUntilIdle()

            confirmVerified(ingestor)
        }

    @Test
    fun `a note the phone wrote itself is not treated as one from the watch`() =
        runTest {
            val note = textNote()
            val ingestor = mockk<WatchNoteIngestor>(relaxed = true)
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    syncDependencies = dependencies(noteIngestor = ingestor),
                )

            mockkStatic(DataMapItem::class)
            try {
                val dataItem = mockDataItem(uriPath = NoteDataMapper.notePath(note.uid), host = "phone-node")
                every { DataMapItem.fromDataItem(dataItem) } returns mockDataMapItem(noteDataMapper.toDataMap(note))

                service.onDataChanged(mockDataEventBuffer(mockDataEvent(dataItem)))
                advanceUntilIdle()

                coVerify(exactly = 0) { ingestor.onNoteMetadata(any()) }
            } finally {
                unmockkStatic(DataMapItem::class)
            }
        }

    @Test
    fun `a deleted data item is ignored without reading its contents`() =
        runTest {
            val ingestor = mockk<WatchNoteIngestor>(relaxed = true)
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    syncDependencies = dependencies(noteIngestor = ingestor),
                )

            mockkStatic(DataMapItem::class)
            try {
                val dataItem = mockDataItem(uriPath = NoteDataMapper.notePath(Uuid.random()))

                service.onDataChanged(mockDataEventBuffer(mockDataEvent(dataItem, type = DataEvent.TYPE_DELETED)))
                advanceUntilIdle()

                confirmVerified(ingestor)
                verify(exactly = 0) { DataMapItem.fromDataItem(any()) }
            } finally {
                unmockkStatic(DataMapItem::class)
            }
        }

    @Test
    fun `changes are applied when the phone's own node cannot be read`() =
        runTest {
            val note = textNote()
            val ingestor = mockk<WatchNoteIngestor>(relaxed = true)
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    syncDependencies = dependencies(noteIngestor = ingestor),
                    localNode = null,
                )

            service.processEvents(
                PhoneDataLayerSnapshotEvent(
                    path = NoteDataMapper.notePath(note.uid),
                    data = noteDataMapper.toDataMap(note),
                    sourceNodeId = "phone-node",
                ),
            )

            coVerify(exactly = 1) { ingestor.onNoteMetadata(noteDataMapper.toDataMap(note)) }
        }

    @Test
    fun `a state document from the watch is stored for the protocol gate`() =
        runTest {
            val peerStates = PeerStateStore()
            val service = TestPhoneDataLayerListenerService(serviceScope = this, syncDependencies = dependencies(peerStates = peerStates))
            val document = StateDocument(role = PeerRole.WATCH, writtenAt = 1, watch = WatchState(features = setOf(WearFeatures.RECEIPTS)))

            service.processEvents(
                PhoneDataLayerSnapshotEvent(
                    path = WearPaths.STATE,
                    data = StateDocumentCodec.toDataMap(document),
                    sourceNodeId = "watch-node",
                ),
            )

            assertEquals(setOf(WearFeatures.RECEIPTS), peerStates.features(PeerRole.WATCH).first())
        }

    @Test
    fun `the phone's own state document is never taken for a watch's`() =
        runTest {
            val peerStates = PeerStateStore()
            val service = TestPhoneDataLayerListenerService(serviceScope = this, syncDependencies = dependencies(peerStates = peerStates))
            val document = StateDocument(role = PeerRole.PHONE, writtenAt = 1, phone = PhoneState())

            service.processEvents(
                PhoneDataLayerSnapshotEvent(
                    path = WearPaths.STATE,
                    data = StateDocumentCodec.toDataMap(document),
                    sourceNodeId = "phone-node",
                ),
            )

            assertNull(peerStates.latest(PeerRole.PHONE).first())
            assertNull(peerStates.latest(PeerRole.WATCH).first())
        }

    @Test
    fun `a phone document from another node is not taken for a watch's`() =
        runTest {
            val peerStates = PeerStateStore()
            val service = TestPhoneDataLayerListenerService(serviceScope = this, syncDependencies = dependencies(peerStates = peerStates))
            val document = StateDocument(role = PeerRole.PHONE, writtenAt = 1, phone = PhoneState())

            service.processEvents(
                PhoneDataLayerSnapshotEvent(
                    path = WearPaths.STATE,
                    data = StateDocumentCodec.toDataMap(document),
                    sourceNodeId = "other-phone",
                ),
            )

            assertNull(peerStates.latest(PeerRole.WATCH).first())
            assertNull(peerStates.latest(PeerRole.PHONE).first())
        }

    @Test
    fun `v2 records reach the ingestor only while receipts are active`() =
        runTest {
            val ingestor = mockk<WatchRecordIngestorV2>(relaxed = true)
            val event =
                PhoneDataLayerSnapshotEvent(
                    WearPaths.watchRecord(RecordKind.NOTE, Uuid.random()),
                    mapOf("envelope" to "x"),
                    sourceNodeId = "watch",
                )
            val disabled = TestPhoneDataLayerListenerService(this, syncDependencies = dependencies(recordIngestorV2 = ingestor))
            disabled.processEvents(event)
            coVerify(exactly = 0) { ingestor.onRecord(any(), any(), any()) }
            val enabled =
                TestPhoneDataLayerListenerService(
                    this,
                    syncDependencies = dependencies(recordIngestorV2 = ingestor),
                    receiptsEnabled = true,
                )
            enabled.processEvents(event)
            coVerify(exactly = 1) { ingestor.onRecord("watch", event.path, event.data) }
        }

    @Test
    fun `v2 audio reaches the ingestor only while receipts are active and its channel closes`() =
        runTest {
            val ingestor = mockk<WatchRecordIngestorV2>(relaxed = true)
            val expectedPath = WearPaths.recordMedia(RecordKind.NOTE, Uuid.random())
            val transfer = channel(expectedPath)
            val disabled = TestPhoneDataLayerListenerService(this, syncDependencies = dependencies(recordIngestorV2 = ingestor))
            disabled.onChannelOpened(transfer)
            advanceUntilIdle()
            coVerify(exactly = 0) { ingestor.onMedia(any(), any(), any()) }
            assertEquals(listOf(transfer), disabled.closedChannels)
            val enabled =
                TestPhoneDataLayerListenerService(
                    this,
                    syncDependencies = dependencies(recordIngestorV2 = ingestor),
                    receiptsEnabled = true,
                )
            enabled.onChannelOpened(transfer)
            advanceUntilIdle()
            coVerify(exactly = 1) { ingestor.onMedia("watch-node", expectedPath, any()) }
            assertEquals(listOf(transfer), enabled.closedChannels)
        }
}
