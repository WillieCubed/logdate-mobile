package app.logdate.client.sync

import android.net.Uri
import app.logdate.client.database.dao.HealthSnapshotDao
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.datalayer.AssociationDataMapper
import app.logdate.client.sync.datalayer.HealthSnapshotDataMapper
import app.logdate.client.sync.datalayer.HealthSnapshotSyncData
import app.logdate.client.sync.datalayer.JournalDataMapper
import app.logdate.client.sync.datalayer.NoteDataMapper
import app.logdate.client.sync.peer.state.PeerStateStore
import app.logdate.client.sync.v2.receipts.WatchRecordIngestorV2
import app.logdate.feature.editor.ui.camera.CameraRemoteCommand
import app.logdate.shared.model.Journal
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Shared fakes for the listener's message, ingestion and snapshot contracts. */
open class PhoneDataLayerListenerFixture {
    protected val noteDataMapper = NoteDataMapper()
    protected val journalDataMapper = JournalDataMapper()
    protected val associationDataMapper = AssociationDataMapper()
    protected val healthSnapshotDataMapper = HealthSnapshotDataMapper()

    protected fun serviceWithDependencies(
        noteIngestor: WatchNoteIngestor = mockk(relaxed = true),
        journalIngestor: WatchJournalIngestor = mockk(relaxed = true),
        healthSnapshotDao: HealthSnapshotDao = mockk(relaxed = true),
    ): TestPhoneDataLayerListenerService =
        TestPhoneDataLayerListenerService(
            serviceScope = CoroutineScope(Dispatchers.Unconfined),
            syncDependencies =
                dependencies(
                    noteIngestor = noteIngestor,
                    journalIngestor = journalIngestor,
                    healthSnapshotDao = healthSnapshotDao,
                ),
        )

    protected fun dependencies(
        noteIngestor: WatchNoteIngestor = mockk(relaxed = true),
        journalIngestor: WatchJournalIngestor = mockk(relaxed = true),
        healthSnapshotDao: HealthSnapshotDao = mockk(relaxed = true),
        peerStates: PeerStateStore = PeerStateStore(),
        recordIngestorV2: WatchRecordIngestorV2? = null,
    ): PhoneDataLayerSyncDependencies =
        PhoneDataLayerSyncDependencies(
            noteIngestor = noteIngestor,
            journalIngestor = journalIngestor,
            healthSnapshotDao = healthSnapshotDao,
            peerStates = peerStates,
            recordIngestorV2 = recordIngestorV2,
        )

    protected fun channel(path: String): ChannelClient.Channel =
        mockk {
            every { this@mockk.path } returns path
        }

    protected fun message(
        path: String,
        sourceNodeId: String = "watch-node",
        data: ByteArray = ByteArray(0),
    ): MessageEvent =
        mockk {
            every { this@mockk.path } returns path
            every { this@mockk.sourceNodeId } returns sourceNodeId
            every { this@mockk.data } returns data
        }

    protected fun textNote(
        id: Uuid = Uuid.random(),
        now: Instant = Clock.System.now(),
    ): JournalNote.Text =
        JournalNote.Text(
            uid = id,
            creationTimestamp = now,
            lastUpdated = now,
            content = "Synced from watch",
        )

    protected fun noteEvent(note: JournalNote): PhoneDataLayerSnapshotEvent =
        PhoneDataLayerSnapshotEvent(
            path = NoteDataMapper.notePath(note.uid),
            data = noteDataMapper.toDataMap(note),
        )

    protected fun noteDeleteEvent(noteId: Uuid): PhoneDataLayerSnapshotEvent =
        PhoneDataLayerSnapshotEvent(
            path = NoteDataMapper.noteDeletePath(noteId),
            data = emptyMap(),
        )

    protected fun journalEvent(journal: Journal): PhoneDataLayerSnapshotEvent =
        PhoneDataLayerSnapshotEvent(
            path = JournalDataMapper.journalPath(journal.id),
            data = journalDataMapper.toDataMap(journal),
        )

    protected fun journalDeleteEvent(journalId: Uuid): PhoneDataLayerSnapshotEvent =
        PhoneDataLayerSnapshotEvent(
            path = JournalDataMapper.journalDeletePath(journalId),
            data = emptyMap(),
        )

    protected fun associationEvent(
        journalId: Uuid,
        contentId: Uuid,
    ): PhoneDataLayerSnapshotEvent =
        PhoneDataLayerSnapshotEvent(
            path = AssociationDataMapper.associationPath(journalId, contentId),
            data = associationDataMapper.toDataMap(journalId, contentId),
        )

    protected fun associationDeleteEvent(
        journalId: Uuid,
        contentId: Uuid,
    ): PhoneDataLayerSnapshotEvent =
        PhoneDataLayerSnapshotEvent(
            path = AssociationDataMapper.associationDeletePath(journalId, contentId),
            data = emptyMap(),
        )

    protected fun healthSnapshotEvent(snapshot: HealthSnapshotSyncData): PhoneDataLayerSnapshotEvent =
        PhoneDataLayerSnapshotEvent(
            path = HealthSnapshotDataMapper.healthPath(snapshot.id),
            data = healthSnapshotDataMapper.toDataMap(snapshot),
        )

    protected fun mockDataEventBuffer(vararg events: DataEvent): DataEventBuffer =
        mockk {
            every { iterator() } returns events.toMutableList().iterator()
        }

    protected fun mockDataEvent(
        dataItem: DataItem,
        type: Int = DataEvent.TYPE_CHANGED,
    ): DataEvent =
        mockk {
            every { this@mockk.dataItem } returns dataItem
            every { this@mockk.type } returns type
        }

    protected fun mockDataItem(
        uriPath: String?,
        host: String? = "watch-node",
    ): DataItem {
        val itemUri = mockk<Uri>()
        every { itemUri.path } returns uriPath
        every { itemUri.host } returns host
        return mockk { every { uri } returns itemUri }
    }

    protected fun mockDataMapItem(payload: Map<String, String>): DataMapItem {
        val dataMap =
            mockk<DataMap> {
                every { keySet() } returns payload.keys
                every { getString(any()) } answers { payload[firstArg()] }
            }
        return mockk {
            every { this@mockk.dataMap } returns dataMap
        }
    }

    protected fun instant(value: String): Instant = Instant.parse(value)

    /**
     * A test implementation of [PhoneDataLayerListenerService] for testing.
     */
    protected class TestPhoneDataLayerListenerService(
        override val serviceScope: CoroutineScope,
        private val syncBridge: PhoneWearSyncBridge? = null,
        private val syncDependencies: PhoneDataLayerSyncDependencies? = null,
        private val onLaunchCamera: () -> Unit = {},
        private val onCameraCommand: (CameraRemoteCommand) -> Unit = {},
        private val channelBytes: ByteArray = ByteArray(0),
        private val localNode: String? = "phone-node",
        private val receiptsEnabled: Boolean = false,
    ) : PhoneDataLayerListenerService() {
        val closedChannels = mutableListOf<ChannelClient.Channel>()

        suspend fun processEvents(vararg events: PhoneDataLayerSnapshotEvent) {
            processSnapshotEvents(events.toList())
        }

        override suspend fun openChannelInput(channel: ChannelClient.Channel): InputStream = ByteArrayInputStream(channelBytes)

        override suspend fun closeChannel(channel: ChannelClient.Channel) {
            closedChannels += channel
        }

        override suspend fun resolvePhoneWearSyncBridge(): PhoneWearSyncBridge? = syncBridge

        override suspend fun resolveSyncDependencies(): PhoneDataLayerSyncDependencies? = syncDependencies

        override suspend fun receiptsActive(): Boolean = receiptsEnabled

        override suspend fun localNodeId(): String? = localNode

        override fun launchCamera() {
            onLaunchCamera()
        }

        override fun sendCameraCommand(command: CameraRemoteCommand): Boolean {
            onCameraCommand(command)
            return true
        }
    }
}
