package app.logdate.wear.sync

import app.logdate.client.database.dao.HealthSnapshotDao
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.datalayer.AssociationDataMapper
import app.logdate.client.sync.datalayer.HealthSnapshotDataMapper
import app.logdate.client.sync.datalayer.JournalDataMapper
import app.logdate.client.sync.datalayer.NoteDataMapper
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.SyncRetryScheduleStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class WearDataLayerSyncManagerTest {
    private lateinit var dataLayerClient: WearDataLayerClient
    private lateinit var syncMetadataService: SyncMetadataService
    private lateinit var retryScheduleStore: SyncRetryScheduleStore
    private lateinit var deadLetterStore: SyncDeadLetterStore
    private lateinit var notesRepository: JournalNotesRepository
    private lateinit var journalRepository: JournalRepository
    private lateinit var healthSnapshotDao: HealthSnapshotDao
    private lateinit var noteDataMapper: NoteDataMapper
    private lateinit var journalDataMapper: JournalDataMapper
    private lateinit var associationDataMapper: AssociationDataMapper
    private lateinit var healthSnapshotDataMapper: HealthSnapshotDataMapper
    private lateinit var syncManager: WearDataLayerSyncManager

    private val fixedTime = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val noteId = Uuid.parse("550e8400-e29b-41d4-a716-446655440000")

    private val textNote =
        JournalNote.Text(
            uid = noteId,
            creationTimestamp = fixedTime,
            lastUpdated = fixedTime,
            content = "Hello from watch",
        )

    private val audioNote =
        JournalNote.Audio(
            uid = noteId,
            creationTimestamp = fixedTime,
            lastUpdated = fixedTime,
            mediaRef = "/storage/audio/recording.aac",
            durationMs = 4200,
        )

    private val audioNoteWithLocation =
        JournalNote.Audio(
            uid = noteId,
            creationTimestamp = fixedTime,
            lastUpdated = fixedTime,
            mediaRef = "/storage/audio/recording_with_loc.aac",
            durationMs = 10000,
            location =
                NoteLocation(
                    coordinates = NoteCoordinates(latitude = 37.7749, longitude = -122.4194),
                ),
        )

    @Before
    fun setUp() {
        dataLayerClient = mockk(relaxed = true)
        syncMetadataService = mockk(relaxed = true)
        retryScheduleStore = mockk(relaxed = true)
        deadLetterStore = mockk(relaxed = true)
        notesRepository = mockk(relaxed = true)
        journalRepository = mockk(relaxed = true)
        healthSnapshotDao = mockk(relaxed = true)
        noteDataMapper = NoteDataMapper()
        journalDataMapper = JournalDataMapper()
        associationDataMapper = AssociationDataMapper()
        healthSnapshotDataMapper = HealthSnapshotDataMapper()

        coEvery { dataLayerClient.isPhoneConnected(any()) } returns true
        coEvery { dataLayerClient.putDataItem(any(), any()) } returns true
        coEvery { dataLayerClient.deleteDataItem(any()) } returns true
        coEvery { dataLayerClient.sendFile(any(), any()) } returns true
        every { syncMetadataService.observePendingCount() } returns MutableStateFlow(0)
        coEvery { syncMetadataService.getPendingUploads(EntityType.JOURNAL) } returns emptyList()
        coEvery { syncMetadataService.getPendingUploads(EntityType.ASSOCIATION) } returns emptyList()
        coEvery { syncMetadataService.getPendingUploads(EntityType.MEDIA) } returns emptyList()
        coEvery { healthSnapshotDao.getAfter(any()) } returns emptyList()
        coEvery { retryScheduleStore.nextAttemptAt(any(), any()) } returns null
        coEvery { deadLetterStore.list() } returns emptyList()

        syncManager =
            WearDataLayerSyncManager(
                dataLayerClient = dataLayerClient,
                syncMetadataService = syncMetadataService,
                retryScheduleStore = retryScheduleStore,
                deadLetterStore = deadLetterStore,
                notesRepository = notesRepository,
                journalRepository = journalRepository,
                healthSnapshotDao = healthSnapshotDao,
                noteDataMapper = noteDataMapper,
                journalDataMapper = journalDataMapper,
                associationDataMapper = associationDataMapper,
                healthSnapshotDataMapper = healthSnapshotDataMapper,
            )
    }

    // =======================================================================
    // getSyncStatus
    // =======================================================================

    @Test
    fun `sync status reports enabled when phone connected`() =
        runTest {
            coEvery { dataLayerClient.isPhoneConnected(any()) } returns true
            coEvery { syncMetadataService.getPendingCount() } returns 0

            val status = syncManager.getSyncStatus()

            assertTrue(status.isEnabled)
        }

    @Test
    fun `sync status reports disabled when phone not connected`() =
        runTest {
            coEvery { dataLayerClient.isPhoneConnected(any()) } returns false
            coEvery { syncMetadataService.getPendingCount() } returns 0

            val status = syncManager.getSyncStatus()

            assertFalse(status.isEnabled)
        }

    @Test
    fun `sync status reports pending upload count`() =
        runTest {
            coEvery { syncMetadataService.getPendingCount() } returns 5

            val status = syncManager.getSyncStatus()

            assertEquals(5, status.pendingUploads)
        }

    @Test
    fun `sync status reports not syncing when idle`() =
        runTest {
            coEvery { syncMetadataService.getPendingCount() } returns 0

            val status = syncManager.getSyncStatus()

            assertFalse(status.isSyncing)
        }

    @Test
    fun `sync status has no errors initially`() =
        runTest {
            coEvery { syncMetadataService.getPendingCount() } returns 0

            val status = syncManager.getSyncStatus()

            assertFalse(status.hasErrors)
            assertNull(status.lastError)
        }

    // =======================================================================
    // uploadPendingChanges -- text notes
    // =======================================================================

    @Test
    fun `upload sends pending text note via data layer`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns textNote

            val result = syncManager.uploadPendingChanges()

            assertTrue(result.success)
            assertEquals(1, result.uploadedItems)
            coVerify {
                dataLayerClient.putDataItem(
                    NoteDataMapper.notePath(noteId),
                    any(),
                )
            }
        }

    @Test
    fun `upload marks note as synced after successful put`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns textNote

            syncManager.uploadPendingChanges()

            coVerify {
                syncMetadataService.markAsSynced(
                    entityId = noteId.toString(),
                    entityType = EntityType.NOTE,
                    syncedAt = any(),
                    version = any(),
                )
            }
        }

    @Test
    fun `upload handles multiple pending notes`() =
        runTest {
            val noteId2 = Uuid.parse("660e8400-e29b-41d4-a716-446655440000")
            val note2 =
                JournalNote.Text(
                    uid = noteId2,
                    creationTimestamp = fixedTime,
                    lastUpdated = fixedTime,
                    content = "Second note",
                )

            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                    PendingUpload(noteId2.toString(), PendingOperation.CREATE),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns textNote
            coEvery { notesRepository.getNoteById(noteId2) } returns note2

            val result = syncManager.uploadPendingChanges()

            assertTrue(result.success)
            assertEquals(2, result.uploadedItems)
        }

    @Test
    fun `upload sends UPDATE as data item put`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.UPDATE),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns textNote

            val result = syncManager.uploadPendingChanges()

            assertTrue(result.success)
            assertEquals(1, result.uploadedItems)
            coVerify { dataLayerClient.putDataItem(NoteDataMapper.notePath(noteId), any()) }
        }

    @Test
    fun `upload does not send file for text notes`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns textNote

            syncManager.uploadPendingChanges()

            coVerify(exactly = 0) { dataLayerClient.sendFile(any(), any()) }
        }

    // =======================================================================
    // uploadPendingChanges -- audio notes (the critical watch→phone path)
    // =======================================================================

    @Test
    fun `upload sends audio note metadata via data item`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns audioNote

            syncManager.uploadPendingChanges()

            coVerify { dataLayerClient.putDataItem(NoteDataMapper.notePath(noteId), any()) }
        }

    @Test
    fun `upload sends audio file via channel`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns audioNote

            syncManager.uploadPendingChanges()

            coVerify { dataLayerClient.sendFile(any(), audioNote.mediaRef) }
        }

    @Test
    fun `upload succeeds for audio note even when file transfer fails`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns audioNote
            coEvery { dataLayerClient.sendFile(any(), any()) } returns false

            val result = syncManager.uploadPendingChanges()

            // Metadata was sent successfully, so the upload counts as success
            assertTrue(result.success)
            assertEquals(1, result.uploadedItems)
        }

    @Test
    fun `upload fails for audio note when metadata put fails`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns audioNote
            coEvery { dataLayerClient.putDataItem(any(), any()) } returns false

            val result = syncManager.uploadPendingChanges()

            assertFalse(result.success)
            assertEquals(0, result.uploadedItems)
        }

    @Test
    fun `upload does not attempt file transfer when metadata put fails`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns audioNote
            coEvery { dataLayerClient.putDataItem(any(), any()) } returns false

            syncManager.uploadPendingChanges()

            coVerify(exactly = 0) { dataLayerClient.sendFile(any(), any()) }
        }

    @Test
    fun `upload sends audio file at correct channel path`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns audioNote

            syncManager.uploadPendingChanges()

            val expectedPath = "${NoteDataMapper.notePath(noteId)}/audio"
            coVerify { dataLayerClient.sendFile(expectedPath, audioNote.mediaRef) }
        }

    @Test
    fun `upload sends audio note with location metadata`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns audioNoteWithLocation

            val result = syncManager.uploadPendingChanges()

            assertTrue(result.success)
            coVerify { dataLayerClient.putDataItem(NoteDataMapper.notePath(noteId), any()) }
            coVerify { dataLayerClient.sendFile(any(), audioNoteWithLocation.mediaRef) }
        }

    @Test
    fun `upload handles mix of text and audio notes`() =
        runTest {
            val textId = Uuid.parse("110e8400-e29b-41d4-a716-446655440000")
            val audioId = Uuid.parse("220e8400-e29b-41d4-a716-446655440000")
            val textNote =
                JournalNote.Text(
                    uid = textId,
                    creationTimestamp = fixedTime,
                    lastUpdated = fixedTime,
                    content = "text",
                )
            val audioNote =
                JournalNote.Audio(
                    uid = audioId,
                    creationTimestamp = fixedTime,
                    lastUpdated = fixedTime,
                    mediaRef = "/audio.aac",
                    durationMs = 3000,
                )

            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(textId.toString(), PendingOperation.CREATE),
                    PendingUpload(audioId.toString(), PendingOperation.CREATE),
                )
            coEvery { notesRepository.getNoteById(textId) } returns textNote
            coEvery { notesRepository.getNoteById(audioId) } returns audioNote

            val result = syncManager.uploadPendingChanges()

            assertTrue(result.success)
            assertEquals(2, result.uploadedItems)
            // File sent only for audio, not text
            coVerify(exactly = 1) { dataLayerClient.sendFile(any(), any()) }
            coVerify(exactly = 2) { dataLayerClient.putDataItem(any(), any()) }
        }

    // =======================================================================
    // uploadPendingChanges -- delete operations
    // =======================================================================

    @Test
    fun `upload sends delete signal for deleted notes`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.DELETE),
                )

            val result = syncManager.uploadPendingChanges()

            assertTrue(result.success)
            coVerify { dataLayerClient.putDataItem(NoteDataMapper.noteDeletePath(noteId), any()) }
        }

    @Test
    fun `delete upload does not look up note from repository`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.DELETE),
                )

            syncManager.uploadPendingChanges()

            coVerify(exactly = 0) { notesRepository.getNoteById(any()) }
        }

    @Test
    fun `delete upload does not send file`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.DELETE),
                )

            syncManager.uploadPendingChanges()

            coVerify(exactly = 0) { dataLayerClient.sendFile(any(), any()) }
        }

    @Test
    fun `delete upload marks as synced on success`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.DELETE),
                )

            syncManager.uploadPendingChanges()

            coVerify {
                syncMetadataService.markAsSynced(noteId.toString(), EntityType.NOTE, any(), any())
            }
        }

    // =======================================================================
    // uploadPendingChanges -- error handling
    // =======================================================================

    @Test
    fun `upload returns error when phone not connected`() =
        runTest {
            coEvery { dataLayerClient.isPhoneConnected(any()) } returns false
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )

            val result = syncManager.uploadPendingChanges()

            assertFalse(result.success)
            assertTrue(result.errors.isNotEmpty())
            assertEquals(SyncErrorType.NETWORK_ERROR, result.errors.first().type)
        }

    @Test
    fun `upload does not attempt any puts when phone not connected`() =
        runTest {
            coEvery { dataLayerClient.isPhoneConnected(any()) } returns false

            syncManager.uploadPendingChanges()

            coVerify(exactly = 0) { dataLayerClient.putDataItem(any(), any()) }
            coVerify(exactly = 0) { dataLayerClient.sendFile(any(), any()) }
        }

    @Test
    fun `upload returns success with zero items when nothing pending`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns emptyList()

            val result = syncManager.uploadPendingChanges()

            assertTrue(result.success)
            assertEquals(0, result.uploadedItems)
        }

    @Test
    fun `upload increments retry count on individual note failure`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns textNote
            coEvery { dataLayerClient.putDataItem(any(), any()) } returns false

            syncManager.uploadPendingChanges()

            coVerify {
                syncMetadataService.incrementRetryCount(noteId.toString(), EntityType.NOTE)
            }
        }

    @Test
    fun `upload skips note when not found in repository`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns null

            val result = syncManager.uploadPendingChanges()

            assertTrue(result.success)
            assertEquals(0, result.uploadedItems)
            coVerify(exactly = 0) { dataLayerClient.putDataItem(any(), any()) }
        }

    @Test
    fun `upload clears outbox for missing notes`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns null

            syncManager.uploadPendingChanges()

            // Missing note should be marked as synced (cleared from outbox)
            coVerify {
                syncMetadataService.markAsSynced(noteId.toString(), EntityType.NOTE, any(), any())
            }
        }

    @Test
    fun `upload continues processing after one note fails`() =
        runTest {
            val noteId2 = Uuid.parse("660e8400-e29b-41d4-a716-446655440000")
            val note2 =
                JournalNote.Text(
                    uid = noteId2,
                    creationTimestamp = fixedTime,
                    lastUpdated = fixedTime,
                    content = "ok",
                )

            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                    PendingUpload(noteId2.toString(), PendingOperation.CREATE),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns textNote
            coEvery { notesRepository.getNoteById(noteId2) } returns note2
            // First put fails, second succeeds
            coEvery { dataLayerClient.putDataItem(NoteDataMapper.notePath(noteId), any()) } returns false
            coEvery { dataLayerClient.putDataItem(NoteDataMapper.notePath(noteId2), any()) } returns true

            val result = syncManager.uploadPendingChanges()

            // One succeeded, one failed
            assertEquals(1, result.uploadedItems)
            assertEquals(1, result.errors.size)
        }

    @Test
    fun `upload handles invalid uuid in pending upload gracefully`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload("not-a-valid-uuid", PendingOperation.CREATE),
                )

            val result = syncManager.uploadPendingChanges()

            // Should skip, not crash
            assertTrue(result.success)
            assertEquals(0, result.uploadedItems)
        }

    // =======================================================================
    // syncContent delegates to upload
    // =======================================================================

    @Test
    fun `syncContent uploads pending notes`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns textNote

            val result = syncManager.syncContent()

            assertTrue(result.success)
            coVerify { dataLayerClient.putDataItem(any(), any()) }
        }

    // =======================================================================
    // fullSync
    // =======================================================================

    @Test
    fun `fullSync uploads all pending entity types`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(any()) } returns emptyList()

            val result = syncManager.fullSync()

            assertTrue(result.success)
            coVerify { syncMetadataService.getPendingUploads(EntityType.NOTE) }
        }

    // =======================================================================
    // sync (non-blocking)
    // =======================================================================

    @Test
    fun `sync does not throw`() {
        syncManager.sync(startNow = false)
        syncManager.sync(startNow = true)
    }

    // =======================================================================
    // downloadRemoteChanges
    // =======================================================================

    @Test
    fun `downloadRemoteChanges returns success`() =
        runTest {
            val result = syncManager.downloadRemoteChanges()

            assertTrue(result.success)
        }

    // =======================================================================
    // Last sync time tracking
    // =======================================================================

    @Test
    fun `sync status includes last sync time after upload`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns textNote
            coEvery { syncMetadataService.getLastSyncTime(any()) } returns fixedTime
            coEvery { syncMetadataService.getPendingCount() } returns 0

            syncManager.uploadPendingChanges()
            val status = syncManager.getSyncStatus()

            assertNotNull(status.lastSyncTime)
        }

    @Test
    fun `upload result includes last sync time`() =
        runTest {
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns textNote

            val result = syncManager.uploadPendingChanges()

            assertNotNull(result.lastSyncTime)
        }

    // =======================================================================
    // Data integrity: serialized data map contains correct note data
    // =======================================================================

    @Test
    fun `uploaded data map can be deserialized back to original note`() =
        runTest {
            val capturedMaps = mutableListOf<Map<String, String>>()
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )
            coEvery { notesRepository.getNoteById(noteId) } returns audioNote
            coEvery { dataLayerClient.putDataItem(any(), capture(capturedMaps)) } returns true

            syncManager.uploadPendingChanges()

            assertTrue(capturedMaps.isNotEmpty())
            val restoredNote = noteDataMapper.fromDataMap(capturedMaps.first())
            assertEquals(audioNote, restoredNote)
        }
}
