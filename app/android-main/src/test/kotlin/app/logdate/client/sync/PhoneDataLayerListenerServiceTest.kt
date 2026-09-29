package app.logdate.client.sync

import android.net.Uri
import app.logdate.client.database.dao.HealthSnapshotDao
import app.logdate.client.database.dao.journals.JournalContentDao
import app.logdate.client.database.entities.HealthSnapshotEntity
import app.logdate.client.database.entities.journals.JournalContentEntityLink
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableJournalContentRepository
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.client.sync.datalayer.AssociationDataMapper
import app.logdate.client.sync.datalayer.HealthSnapshotDataMapper
import app.logdate.client.sync.datalayer.HealthSnapshotSyncData
import app.logdate.client.sync.datalayer.JournalDataMapper
import app.logdate.client.sync.datalayer.NoteDataMapper
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.feature.editor.ui.camera.CameraRemoteCommand
import app.logdate.shared.model.Journal
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests the [PhoneDataLayerListenerService] handling of incoming Wear OS events.
 *
 * This suite validates the service's ability to decode Wearable Data Layer messages
 * and data events, ensuring they trigger the correct side effects—such as launching
 * the camera, syncing health snapshots, or updating local journal repositories.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneDataLayerListenerServiceTest {
    private val noteDataMapper = NoteDataMapper()
    private val journalDataMapper = JournalDataMapper()
    private val associationDataMapper = AssociationDataMapper()
    private val healthSnapshotDataMapper = HealthSnapshotDataMapper()

    @Test
    fun `camera open message launches camera`() = runTest {
        var launched = false
        val service =
            TestPhoneDataLayerListenerService(
                serviceScope = this,
                onLaunchCamera = { launched = true },
            )

        service.onMessageReceived(message(path = "/logdate/camera/open"))
        advanceUntilIdle()

        assertTrue(launched)
    }

    @Test
    fun `camera capture message sends capture command and does not sync work`() = runTest {
        var launched = false
        val cameraCommands = mutableListOf<CameraRemoteCommand>()
        val syncBridge = mockk<PhoneWearSyncBridge>(relaxed = true)
        val service =
            TestPhoneDataLayerListenerService(
                serviceScope = this,
                syncBridge = syncBridge,
                onLaunchCamera = { launched = true },
                onCameraCommand = { cameraCommands.add(it) },
            )

        service.onMessageReceived(message(path = "/logdate/camera/capture"))
        advanceUntilIdle()

        assertFalse(launched)
        assertTrue(cameraCommands.contains(CameraRemoteCommand.Capture))
        coVerify(exactly = 0) { syncBridge.publishNotesToWatch(any()) }
        coVerify(exactly = 0) { syncBridge.streamAudioToWatch(any(), any()) }
    }

    @Test
    fun `camera close message sends close command and does not sync work`() = runTest {
        var launched = false
        val cameraCommands = mutableListOf<CameraRemoteCommand>()
        val syncBridge = mockk<PhoneWearSyncBridge>(relaxed = true)
        val service =
            TestPhoneDataLayerListenerService(
                serviceScope = this,
                syncBridge = syncBridge,
                onLaunchCamera = { launched = true },
                onCameraCommand = { cameraCommands.add(it) },
            )

        service.onMessageReceived(message(path = "/logdate/camera/close"))
        advanceUntilIdle()

        assertFalse(launched)
        assertTrue(cameraCommands.contains(CameraRemoteCommand.Close))
        coVerify(exactly = 0) { syncBridge.publishNotesToWatch(any()) }
        coVerify(exactly = 0) { syncBridge.streamAudioToWatch(any(), any()) }
    }

    @Test
    fun `camera switch message sends switch command`() = runTest {
        val cameraCommands = mutableListOf<CameraRemoteCommand>()
        val service =
            TestPhoneDataLayerListenerService(
                serviceScope = this,
                onCameraCommand = { cameraCommands.add(it) },
            )

        service.onMessageReceived(message(path = "/logdate/camera/switch"))
        advanceUntilIdle()

        assertTrue(cameraCommands.contains(CameraRemoteCommand.SwitchBuiltInCamera))
    }

    @Test
    fun `camera select message sends built-in category command`() = runTest {
        val cameraCommands = mutableListOf<CameraRemoteCommand>()
        val service =
            TestPhoneDataLayerListenerService(
                serviceScope = this,
                onCameraCommand = { cameraCommands.add(it) },
            )

        service.onMessageReceived(
            message(
                path = "/logdate/camera/select",
                data = "front".encodeToByteArray(),
            ),
        )
        advanceUntilIdle()

        assertTrue(
            cameraCommands.contains(
                CameraRemoteCommand.SelectCameraCategory(MediaDeviceCategory.FRONT_CAMERA),
            ),
        )
    }

    @Test
    fun `camera select message sends exact device command`() = runTest {
        val cameraCommands = mutableListOf<CameraRemoteCommand>()
        val service =
            TestPhoneDataLayerListenerService(
                serviceScope = this,
                onCameraCommand = { cameraCommands.add(it) },
            )

        service.onMessageReceived(
            message(
                path = "/logdate/camera/select",
                data = "device:usb-camera-1".encodeToByteArray(),
            ),
        )
        advanceUntilIdle()

        assertTrue(
            cameraCommands.contains(
                CameraRemoteCommand.SelectCameraDevice("usb-camera-1"),
            ),
        )
    }

    @Test
    fun `sync request message publishes notes to watch`() = runTest {
        val syncBridge = mockk<PhoneWearSyncBridge>(relaxed = true)
        val service =
            TestPhoneDataLayerListenerService(
                serviceScope = this,
                syncBridge = syncBridge,
            )

        service.onMessageReceived(message(path = WearAudioRequestPaths.SYNC_REQUEST_PATH, sourceNodeId = "watch-node"))
        advanceUntilIdle()

        coVerify(exactly = 1) { syncBridge.publishNotesToWatch("watch-node") }
    }

    @Test
    fun `audio request message streams requested note to watch`() = runTest {
        val noteId = Uuid.random()
        val syncBridge = mockk<PhoneWearSyncBridge>(relaxed = true)
        val service =
            TestPhoneDataLayerListenerService(
                serviceScope = this,
                syncBridge = syncBridge,
            )

        service.onMessageReceived(
            message(
                path = WearAudioRequestPaths.audioRequestPath(noteId),
                sourceNodeId = "watch-node",
            ),
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            syncBridge.streamAudioToWatch(
                noteId = noteId,
                sourceNodeId = "watch-node",
            )
        }
    }

    @Test
    fun `unknown message path is ignored`() = runTest {
        var launched = false
        val syncBridge = mockk<PhoneWearSyncBridge>(relaxed = true)
        val service =
            TestPhoneDataLayerListenerService(
                serviceScope = this,
                syncBridge = syncBridge,
                onLaunchCamera = { launched = true },
            )

        service.onMessageReceived(message(path = "/logdate/unknown"))
        advanceUntilIdle()

        assertFalse(launched)
        coVerify(exactly = 0) { syncBridge.publishNotesToWatch(any()) }
        coVerify(exactly = 0) { syncBridge.streamAudioToWatch(any(), any()) }
    }

    @Test
    fun `invalid audio request path does not invoke sync bridge`() = runTest {
        val syncBridge = mockk<PhoneWearSyncBridge>(relaxed = true)
        val service =
            TestPhoneDataLayerListenerService(
                serviceScope = this,
                syncBridge = syncBridge,
            )

        service.onMessageReceived(
            message(
                path = "/logdate/notes/not-a-uuid/audio/request",
                sourceNodeId = "watch-node",
            ),
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { syncBridge.streamAudioToWatch(any(), any()) }
        coVerify(exactly = 0) { syncBridge.publishNotesToWatch(any()) }
    }

    @Test
    fun `note sync hands the note to the ingestor`() = runTest {
        val note = textNote()
        val ingestor = mockk<WatchNoteIngestor>(relaxed = true)
        val service = serviceWithDependencies(noteIngestor = ingestor)

        service.processEvents(noteEvent(note))

        coVerify(exactly = 1) { ingestor.onNoteMetadata(noteDataMapper.toDataMap(note)) }
    }

    @Test
    fun `note sync survives an undecodable note`() = runTest {
        val ingestor = mockk<WatchNoteIngestor>()
        coEvery { ingestor.onNoteMetadata(any()) } throws IllegalArgumentException("no payload")
        val service = serviceWithDependencies(noteIngestor = ingestor)

        service.processEvents(PhoneDataLayerSnapshotEvent(path = NoteDataMapper.notePath(Uuid.random()), data = emptyMap()))

        coVerify(exactly = 1) { ingestor.onNoteMetadata(emptyMap()) }
    }

    @Test
    fun `note delete is handed to the ingestor`() = runTest {
        val noteId = Uuid.random()
        val ingestor = mockk<WatchNoteIngestor>(relaxed = true)
        val service = serviceWithDependencies(noteIngestor = ingestor)

        service.processEvents(noteDeleteEvent(noteId))

        coVerify(exactly = 1) { ingestor.onNoteDeleted(noteId) }
    }

    @Test
    fun `invalid note delete path does not reach the ingestor`() = runTest {
        val ingestor = mockk<WatchNoteIngestor>(relaxed = true)
        val service = serviceWithDependencies(noteIngestor = ingestor)

        service.processEvents(PhoneDataLayerSnapshotEvent(path = "/logdate/notes/not-a-uuid/delete", data = emptyMap()))

        coVerify(exactly = 0) { ingestor.onNoteDeleted(any()) }
        confirmVerified(ingestor)
    }

    @Test
    fun `audio channel from the watch streams its bytes to the ingestor and closes`() = runTest {
        val noteId = Uuid.random()
        val ingestor = mockk<WatchNoteIngestor>(relaxed = true)
        val received = mutableListOf<ByteArray>()
        coEvery { ingestor.onAudioBytes(noteId, any()) } coAnswers { received += secondArg<InputStream>().readBytes() }
        val service =
            TestPhoneDataLayerListenerService(
                serviceScope = this,
                syncDependencies = dependencies(noteIngestor = ingestor),
                channelBytes = byteArrayOf(4, 5, 6),
            )

        service.onChannelOpened(channel(WearAudioRequestPaths.audioTransferPath(noteId)))
        advanceUntilIdle()

        assertEquals(1, received.size)
        assertContentEquals(byteArrayOf(4, 5, 6), received.single())
        assertEquals(1, service.closedChannels.size)
    }

    @Test
    fun `channel at an unknown path is ignored`() = runTest {
        val ingestor = mockk<WatchNoteIngestor>(relaxed = true)
        val service =
            TestPhoneDataLayerListenerService(
                serviceScope = this,
                syncDependencies = dependencies(noteIngestor = ingestor),
            )

        service.onChannelOpened(channel("/logdate/other/thing"))
        advanceUntilIdle()

        confirmVerified(ingestor)
        assertTrue(service.closedChannels.isEmpty())
    }

    @Test
    fun `audio channel is closed when the ingestor fails`() = runTest {
        val noteId = Uuid.random()
        val ingestor = mockk<WatchNoteIngestor>()
        coEvery { ingestor.onAudioBytes(any(), any()) } throws IllegalStateException("disk full")
        val service =
            TestPhoneDataLayerListenerService(
                serviceScope = this,
                syncDependencies = dependencies(noteIngestor = ingestor),
            )

        service.onChannelOpened(channel(WearAudioRequestPaths.audioTransferPath(noteId)))
        advanceUntilIdle()

        assertEquals(1, service.closedChannels.size)
    }

    @Test
    fun `audio channel is closed and dropped when dependencies are unavailable`() = runTest {
        val noteId = Uuid.random()
        val service = TestPhoneDataLayerListenerService(serviceScope = this, syncDependencies = null)

        service.onChannelOpened(channel(WearAudioRequestPaths.audioTransferPath(noteId)))
        advanceUntilIdle()

        assertEquals(1, service.closedChannels.size)
    }

    @Test
    fun `journal sync uses sync-aware repository`() = runTest {
        val journal = Journal(id = Uuid.random(), title = "Travel")
        val journalRepository = mockk<SyncableJournalRepository>(relaxed = true)
        val service = serviceWithDependencies(journalRepository = journalRepository)

        service.processEvents(journalEvent(journal))

        coVerify(exactly = 1) { journalRepository.createFromSync(journal) }
    }

    @Test
    fun `journal sync falls back to create for non-syncable repository`() = runTest {
        val journal = Journal(id = Uuid.random(), title = "Home")
        val journalRepository = mockk<JournalRepository>(relaxed = true)
        coEvery { journalRepository.create(journal) } returns journal.id
        val service = serviceWithDependencies(journalRepository = journalRepository)

        service.processEvents(journalEvent(journal))

        coVerify(exactly = 1) { journalRepository.create(journal) }
    }

    @Test
    fun `journal delete uses sync-aware repository when available`() = runTest {
        val journalId = Uuid.random()
        val journalRepository = mockk<SyncableJournalRepository>(relaxed = true)
        val service = serviceWithDependencies(journalRepository = journalRepository)

        service.processEvents(journalDeleteEvent(journalId))

        coVerify(exactly = 1) { journalRepository.deleteFromSync(journalId) }
    }

    @Test
    fun `journal delete falls back to repository delete for non-syncable repository`() = runTest {
        val journalId = Uuid.random()
        val journalRepository = mockk<JournalRepository>(relaxed = true)
        val service = serviceWithDependencies(journalRepository = journalRepository)

        service.processEvents(journalDeleteEvent(journalId))

        coVerify(exactly = 1) { journalRepository.delete(journalId) }
    }

    @Test
    fun `association sync uses syncable content repository when available`() = runTest {
        val journalId = Uuid.random()
        val contentId = Uuid.random()
        val contentRepository = mockk<SyncableJournalContentRepository>(relaxed = true)
        val journalContentDao = mockk<JournalContentDao>(relaxed = true)
        val service =
            serviceWithDependencies(
                contentRepository = contentRepository,
                journalContentDao = journalContentDao,
            )

        service.processEvents(associationEvent(journalId, contentId))

        coVerify(exactly = 1) { contentRepository.addContentToJournalFromSync(contentId, journalId) }
        coVerify(exactly = 0) { journalContentDao.addContentToJournal(any()) }
    }

    @Test
    fun `association sync falls back to dao when syncable content repository unavailable`() = runTest {
        val journalId = Uuid.random()
        val contentId = Uuid.random()
        val journalContentDao = mockk<JournalContentDao>(relaxed = true)
        val service =
            serviceWithDependencies(
                contentRepository = null,
                journalContentDao = journalContentDao,
            )

        service.processEvents(associationEvent(journalId, contentId))

        coVerify(exactly = 1) {
            journalContentDao.addContentToJournal(
                match { link ->
                    link == JournalContentEntityLink(journalId = journalId, contentId = contentId)
                },
            )
        }
    }

    @Test
    fun `association delete uses syncable content repository when available`() = runTest {
        val journalId = Uuid.random()
        val contentId = Uuid.random()
        val contentRepository = mockk<SyncableJournalContentRepository>(relaxed = true)
        val journalContentDao = mockk<JournalContentDao>(relaxed = true)
        val service =
            serviceWithDependencies(
                contentRepository = contentRepository,
                journalContentDao = journalContentDao,
            )

        service.processEvents(associationDeleteEvent(journalId, contentId))

        coVerify(exactly = 1) { contentRepository.removeContentFromJournalFromSync(contentId, journalId) }
        coVerify(exactly = 0) { journalContentDao.removeContentFromJournal(any(), any()) }
    }

    @Test
    fun `association delete falls back to dao when syncable content repository unavailable`() = runTest {
        val journalId = Uuid.random()
        val contentId = Uuid.random()
        val journalContentDao = mockk<JournalContentDao>(relaxed = true)
        val service =
            serviceWithDependencies(
                contentRepository = null,
                journalContentDao = journalContentDao,
            )

        service.processEvents(associationDeleteEvent(journalId, contentId))

        coVerify(exactly = 1) { journalContentDao.removeContentFromJournal(journalId, contentId) }
    }

    @Test
    fun `invalid association delete path does not touch content dependencies`() = runTest {
        val contentRepository = mockk<SyncableJournalContentRepository>(relaxed = true)
        val journalContentDao = mockk<JournalContentDao>(relaxed = true)
        val service =
            serviceWithDependencies(
                contentRepository = contentRepository,
                journalContentDao = journalContentDao,
            )

        service.processEvents(PhoneDataLayerSnapshotEvent(path = "/logdate/associations/not-valid/delete", data = emptyMap()))

        coVerify(exactly = 0) { contentRepository.removeContentFromJournalFromSync(any(), any()) }
        coVerify(exactly = 0) { journalContentDao.removeContentFromJournal(any(), any()) }
    }

    @Test
    fun `health snapshot sync inserts mapped entity`() = runTest {
        val snapshot =
            HealthSnapshotSyncData(
                id = Uuid.random(),
                noteId = Uuid.random(),
                heartRateBpm = 72,
                heartRateVariabilityMs = 18.5f,
                stepCount = 1234,
                stressLevel = 0.25f,
                cumulativeCalories = 44.5f,
                timestamp = instant("2026-03-31T12:00:00Z"),
                source = "wear",
            )
        val healthSnapshotDao = mockk<HealthSnapshotDao>(relaxed = true)
        val service = serviceWithDependencies(healthSnapshotDao = healthSnapshotDao)

        service.processEvents(healthSnapshotEvent(snapshot))

        coVerify(exactly = 1) {
            healthSnapshotDao.insert(
                HealthSnapshotEntity(
                    id = snapshot.id,
                    noteId = snapshot.noteId,
                    heartRateBpm = snapshot.heartRateBpm,
                    heartRateVariabilityMs = snapshot.heartRateVariabilityMs,
                    stepCount = snapshot.stepCount,
                    stressLevel = snapshot.stressLevel,
                    cumulativeCalories = snapshot.cumulativeCalories,
                    timestamp = snapshot.timestamp,
                    source = snapshot.source,
                ),
            )
        }
    }

    @Test
    fun `process snapshot events drops work when sync dependencies are unavailable`() = runTest {
        val service =
            TestPhoneDataLayerListenerService(
                serviceScope = this,
                syncDependencies = null,
            )

        service.processEvents(noteDeleteEvent(Uuid.random()))
    }

    @Test
    fun `onDataChanged snapshots wearable events and processes valid payloads`() = runTest {
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
    fun `onDataChanged skips events without a path`() = runTest {
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

    private fun serviceWithDependencies(
        noteIngestor: WatchNoteIngestor = mockk(relaxed = true),
        journalRepository: JournalRepository = mockk<SyncableJournalRepository>(relaxed = true),
        contentRepository: SyncableJournalContentRepository? = mockk(relaxed = true),
        journalContentDao: JournalContentDao? = mockk(relaxed = true),
        healthSnapshotDao: HealthSnapshotDao = mockk(relaxed = true),
    ): TestPhoneDataLayerListenerService =
        TestPhoneDataLayerListenerService(
            serviceScope = CoroutineScope(Dispatchers.Unconfined),
            syncDependencies =
                dependencies(
                    noteIngestor = noteIngestor,
                    journalRepository = journalRepository,
                    contentRepository = contentRepository,
                    journalContentDao = journalContentDao,
                    healthSnapshotDao = healthSnapshotDao,
                ),
        )

    private fun dependencies(
        noteIngestor: WatchNoteIngestor = mockk(relaxed = true),
        journalRepository: JournalRepository = mockk<SyncableJournalRepository>(relaxed = true),
        contentRepository: SyncableJournalContentRepository? = mockk(relaxed = true),
        journalContentDao: JournalContentDao? = mockk(relaxed = true),
        healthSnapshotDao: HealthSnapshotDao = mockk(relaxed = true),
    ): PhoneDataLayerSyncDependencies =
        PhoneDataLayerSyncDependencies(
            noteIngestor = noteIngestor,
            journalRepository = journalRepository,
            contentRepository = contentRepository,
            journalContentDao = journalContentDao,
            healthSnapshotDao = healthSnapshotDao,
        )

    private fun channel(path: String): ChannelClient.Channel =
        mockk {
            every { this@mockk.path } returns path
        }

    private fun message(
        path: String,
        sourceNodeId: String = "watch-node",
        data: ByteArray = ByteArray(0),
    ): MessageEvent =
        mockk {
            every { this@mockk.path } returns path
            every { this@mockk.sourceNodeId } returns sourceNodeId
            every { this@mockk.data } returns data
        }

    private fun textNote(
        id: Uuid = Uuid.random(),
        now: Instant = Clock.System.now(),
    ): JournalNote.Text =
        JournalNote.Text(
            uid = id,
            creationTimestamp = now,
            lastUpdated = now,
            content = "Synced from watch",
        )

    private fun noteEvent(note: JournalNote): PhoneDataLayerSnapshotEvent =
        PhoneDataLayerSnapshotEvent(
            path = NoteDataMapper.notePath(note.uid),
            data = noteDataMapper.toDataMap(note),
        )

    private fun noteDeleteEvent(noteId: Uuid): PhoneDataLayerSnapshotEvent =
        PhoneDataLayerSnapshotEvent(
            path = NoteDataMapper.noteDeletePath(noteId),
            data = emptyMap(),
        )

    private fun journalEvent(journal: Journal): PhoneDataLayerSnapshotEvent =
        PhoneDataLayerSnapshotEvent(
            path = JournalDataMapper.journalPath(journal.id),
            data = journalDataMapper.toDataMap(journal),
        )

    private fun journalDeleteEvent(journalId: Uuid): PhoneDataLayerSnapshotEvent =
        PhoneDataLayerSnapshotEvent(
            path = JournalDataMapper.journalDeletePath(journalId),
            data = emptyMap(),
        )

    private fun associationEvent(
        journalId: Uuid,
        contentId: Uuid,
    ): PhoneDataLayerSnapshotEvent =
        PhoneDataLayerSnapshotEvent(
            path = AssociationDataMapper.associationPath(journalId, contentId),
            data = associationDataMapper.toDataMap(journalId, contentId),
        )

    private fun associationDeleteEvent(
        journalId: Uuid,
        contentId: Uuid,
    ): PhoneDataLayerSnapshotEvent =
        PhoneDataLayerSnapshotEvent(
            path = AssociationDataMapper.associationDeletePath(journalId, contentId),
            data = emptyMap(),
        )

    private fun healthSnapshotEvent(snapshot: HealthSnapshotSyncData): PhoneDataLayerSnapshotEvent =
        PhoneDataLayerSnapshotEvent(
            path = HealthSnapshotDataMapper.healthPath(snapshot.id),
            data = healthSnapshotDataMapper.toDataMap(snapshot),
        )

    private fun mockDataEventBuffer(vararg events: DataEvent): DataEventBuffer =
        mockk {
            every { iterator() } returns events.toMutableList().iterator()
        }

    private fun mockDataEvent(dataItem: DataItem): DataEvent =
        mockk {
            every { this@mockk.dataItem } returns dataItem
        }

    private fun mockDataItem(uriPath: String?): DataItem =
        mockk {
            val uri = mockk<Uri> { every { path } returns uriPath }
            every { this@mockk.uri } returns uri
        }

    private fun mockDataMapItem(payload: Map<String, String>): DataMapItem {
        val dataMap =
            mockk<DataMap> {
                every { keySet() } returns payload.keys
                every { getString(any()) } answers { payload[firstArg()] }
            }
        return mockk {
            every { this@mockk.dataMap } returns dataMap
        }
    }

    private fun instant(value: String): Instant = Instant.parse(value)

    /**
     * A test implementation of [PhoneDataLayerListenerService] for testing.
     */
    private class TestPhoneDataLayerListenerService(
        override val serviceScope: CoroutineScope,
        private val syncBridge: PhoneWearSyncBridge? = null,
        private val syncDependencies: PhoneDataLayerSyncDependencies? = null,
        private val onLaunchCamera: () -> Unit = {},
        private val onCameraCommand: (CameraRemoteCommand) -> Unit = {},
        private val channelBytes: ByteArray = ByteArray(0),
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

        override fun launchCamera() {
            onLaunchCamera()
        }

        override fun sendCameraCommand(command: CameraRemoteCommand): Boolean {
            onCameraCommand(command)
            return true
        }
    }
}
