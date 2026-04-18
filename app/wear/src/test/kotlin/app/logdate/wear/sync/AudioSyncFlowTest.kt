package app.logdate.wear.sync

import app.logdate.client.database.dao.HealthSnapshotDao
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
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
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * End-to-end unit test simulating the critical path:
 *
 * Recording stops on watch → JournalNote.Audio created → outbox enqueued →
 * WearDataLayerSyncManager uploads → DataItem put + file sent → marked synced
 *
 * This test verifies the entire watch-side sync pipeline using mocks at the
 * transport boundary (WearDataLayerClient) while exercising real logic in
 * WearDataLayerSyncManager and NoteDataMapper.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioSyncFlowTest {
    private lateinit var dataLayerClient: WearDataLayerClient
    private lateinit var syncMetadataService: SyncMetadataService
    private lateinit var retryScheduleStore: SyncRetryScheduleStore
    private lateinit var deadLetterStore: SyncDeadLetterStore
    private lateinit var notesRepository: JournalNotesRepository
    private lateinit var journalRepository: JournalRepository
    private lateinit var healthSnapshotDao: HealthSnapshotDao
    private lateinit var syncManager: WearDataLayerSyncManager

    private val noteDataMapper = NoteDataMapper()
    private val fixedTime = Instant.fromEpochMilliseconds(1_710_000_000_000)

    @Before
    fun setUp() {
        dataLayerClient = mockk(relaxed = true)
        syncMetadataService = mockk(relaxed = true)
        retryScheduleStore = mockk(relaxed = true)
        deadLetterStore = mockk(relaxed = true)
        notesRepository = mockk(relaxed = true)
        journalRepository = mockk(relaxed = true)
        healthSnapshotDao = mockk(relaxed = true)

        coEvery { dataLayerClient.isPhoneConnected(any()) } returns true
        coEvery { dataLayerClient.putDataItem(any(), any()) } returns true
        coEvery { dataLayerClient.sendFile(any(), any()) } returns true
        every { syncMetadataService.observePendingCount() } returns MutableStateFlow(0)
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
                journalDataMapper = JournalDataMapper(),
                associationDataMapper = AssociationDataMapper(),
                healthSnapshotDataMapper = HealthSnapshotDataMapper(),
            )
    }

    // =======================================================================
    // Scenario: User records 4-second audio note on watch, syncs to phone
    // =======================================================================

    @Test
    fun `full audio sync flow - record and sync to phone`() =
        runTest {
            // --- Arrange: Simulate what happens after recording stops ---
            val noteId = Uuid.random()
            val audioFilePath = "/data/data/app.logdate.wear/files/recordings/rec_$noteId.aac"
            val audioNote =
                JournalNote.Audio(
                    uid = noteId,
                    creationTimestamp = fixedTime,
                    lastUpdated = fixedTime,
                    mediaRef = audioFilePath,
                    durationMs = 4200,
                )

            // The repository has the note (just created by WearRecordingViewModel)
            coEvery { notesRepository.getNoteById(noteId) } returns audioNote

            // The outbox has a pending CREATE for this note
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )

            // Capture what gets sent to the Data Layer
            val capturedPath = slot<String>()
            val capturedData = slot<Map<String, String>>()
            val capturedFilePath = slot<String>()
            val capturedChannelPath = slot<String>()

            coEvery { dataLayerClient.putDataItem(capture(capturedPath), capture(capturedData)) } returns true
            coEvery { dataLayerClient.sendFile(capture(capturedChannelPath), capture(capturedFilePath)) } returns true

            // --- Act: Trigger sync (normally called by OfflineFirstJournalNotesRepository) ---
            val result = syncManager.syncContent()

            // --- Assert: Verify the complete pipeline ---

            // 1. Sync succeeded
            assertTrue(result.success, "Sync should succeed")
            assertEquals(1, result.uploadedItems, "One note should be uploaded")

            // 2. Note metadata was put at the correct path
            assertEquals(NoteDataMapper.notePath(noteId), capturedPath.captured)

            // 3. The data map contains valid, deserializable note data
            val serializedNote = noteDataMapper.fromDataMap(capturedData.captured)
            assertEquals(noteId, serializedNote.uid, "UID should match")
            assertTrue(serializedNote is JournalNote.Audio, "Should be an Audio note")
            assertEquals(audioFilePath, serializedNote.mediaRef, "Media ref should match")
            assertEquals(4200, serializedNote.durationMs, "Duration should match")

            // 4. Audio file was sent via channel at the correct path
            assertEquals("${NoteDataMapper.notePath(noteId)}/audio", capturedChannelPath.captured)
            assertEquals(audioFilePath, capturedFilePath.captured)

            // 5. Note was marked as synced in the outbox
            coVerify {
                syncMetadataService.markAsSynced(
                    entityId = noteId.toString(),
                    entityType = EntityType.NOTE,
                    syncedAt = any(),
                    version = any(),
                )
            }

            // 6. Result has a last sync time
            assertNotNull(result.lastSyncTime)
        }

    // =======================================================================
    // Scenario: User records audio, but phone is not nearby
    // =======================================================================

    @Test
    fun `audio sync fails gracefully when phone not connected`() =
        runTest {
            val noteId = Uuid.random()
            val audioNote =
                JournalNote.Audio(
                    uid = noteId,
                    creationTimestamp = fixedTime,
                    lastUpdated = fixedTime,
                    mediaRef = "/storage/recording.aac",
                    durationMs = 3000,
                )

            coEvery { dataLayerClient.isPhoneConnected(any()) } returns false
            coEvery { notesRepository.getNoteById(noteId) } returns audioNote
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )

            val result = syncManager.syncContent()

            // Sync fails but doesn't crash
            assertTrue(!result.success)
            assertEquals(0, result.uploadedItems)
            assertTrue(result.errors.isNotEmpty())
            assertTrue(result.errors.first().retryable, "Error should be retryable")

            // Note stays in outbox (not marked as synced)
            coVerify(exactly = 0) {
                syncMetadataService.markAsSynced(any(), any(), any(), any())
            }

            // No data sent
            coVerify(exactly = 0) { dataLayerClient.putDataItem(any(), any()) }
            coVerify(exactly = 0) { dataLayerClient.sendFile(any(), any()) }
        }

    // =======================================================================
    // Scenario: Audio metadata syncs but file transfer fails (Bluetooth flaky)
    // =======================================================================

    @Test
    fun `audio metadata syncs even when file transfer fails`() =
        runTest {
            val noteId = Uuid.random()
            val audioNote =
                JournalNote.Audio(
                    uid = noteId,
                    creationTimestamp = fixedTime,
                    lastUpdated = fixedTime,
                    mediaRef = "/storage/recording.aac",
                    durationMs = 5000,
                )

            coEvery { notesRepository.getNoteById(noteId) } returns audioNote
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )
            // Metadata succeeds but file transfer fails
            coEvery { dataLayerClient.putDataItem(any(), any()) } returns true
            coEvery { dataLayerClient.sendFile(any(), any()) } returns false

            val result = syncManager.syncContent()

            // Metadata sync still counts as success — phone can request file later
            assertTrue(result.success)
            assertEquals(1, result.uploadedItems)

            // Note is marked synced so we don't re-send metadata
            coVerify {
                syncMetadataService.markAsSynced(noteId.toString(), EntityType.NOTE, any(), any())
            }
        }

    // =======================================================================
    // Scenario: Multiple audio notes queued (user recorded several before reconnecting)
    // =======================================================================

    @Test
    fun `batch upload of multiple audio notes`() =
        runTest {
            val notes =
                (1..5).map { i ->
                    val id = Uuid.random()
                    id to
                        JournalNote.Audio(
                            uid = id,
                            creationTimestamp = Instant.fromEpochMilliseconds(fixedTime.toEpochMilliseconds() + i * 60_000),
                            lastUpdated = Instant.fromEpochMilliseconds(fixedTime.toEpochMilliseconds() + i * 60_000),
                            mediaRef = "/storage/recording_$i.aac",
                            durationMs = (i * 1000).toLong(),
                        )
                }

            for ((id, note) in notes) {
                coEvery { notesRepository.getNoteById(id) } returns note
            }
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                notes.map { (id, _) ->
                    PendingUpload(id.toString(), PendingOperation.CREATE)
                }

            val result = syncManager.syncContent()

            assertTrue(result.success)
            assertEquals(5, result.uploadedItems)

            // 5 metadata puts + 5 file sends
            coVerify(exactly = 5) { dataLayerClient.putDataItem(any(), any()) }
            coVerify(exactly = 5) { dataLayerClient.sendFile(any(), any()) }

            // All 5 marked as synced
            coVerify(exactly = 5) {
                syncMetadataService.markAsSynced(any(), EntityType.NOTE, any(), any())
            }
        }

    // =======================================================================
    // Scenario: Note deleted on watch before sync completes
    // =======================================================================

    @Test
    fun `sync handles note deleted from DB between enqueue and upload`() =
        runTest {
            val noteId = Uuid.random()

            // Note was deleted from DB but still in outbox
            coEvery { notesRepository.getNoteById(noteId) } returns null
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )

            val result = syncManager.syncContent()

            // Should not fail — just skip the missing note
            assertTrue(result.success)
            assertEquals(0, result.uploadedItems)

            // Should clear the orphaned outbox entry
            coVerify {
                syncMetadataService.markAsSynced(noteId.toString(), EntityType.NOTE, any(), any())
            }

            // Should not attempt any data transfer
            coVerify(exactly = 0) { dataLayerClient.putDataItem(any(), any()) }
        }

    // =======================================================================
    // Scenario: Delete then re-create (conflict resolution at outbox level)
    // =======================================================================

    @Test
    fun `sync correctly handles delete operation in outbox`() =
        runTest {
            val noteId = Uuid.random()

            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.DELETE),
                )

            val result = syncManager.syncContent()

            assertTrue(result.success)
            assertEquals(1, result.uploadedItems)

            // Should send delete signal, not a create
            coVerify {
                dataLayerClient.putDataItem(NoteDataMapper.noteDeletePath(noteId), any())
            }
            coVerify(exactly = 0) { dataLayerClient.sendFile(any(), any()) }
        }

    // =======================================================================
    // Scenario: Verify data integrity end-to-end
    // =======================================================================

    @Test
    fun `data sent to phone can reconstruct the exact original note`() =
        runTest {
            val noteId = Uuid.random()
            val originalNote =
                JournalNote.Audio(
                    uid = noteId,
                    creationTimestamp = Instant.fromEpochMilliseconds(1_710_000_123_456),
                    lastUpdated = Instant.fromEpochMilliseconds(1_710_000_123_789),
                    mediaRef = "/data/data/app.logdate.wear/files/recordings/voice_memo.aac",
                    durationMs = 58_000,
                    syncVersion = 0,
                )

            coEvery { notesRepository.getNoteById(noteId) } returns originalNote
            coEvery { syncMetadataService.getPendingUploads(EntityType.NOTE) } returns
                listOf(
                    PendingUpload(noteId.toString(), PendingOperation.CREATE),
                )

            // Capture the serialized data
            val capturedData = slot<Map<String, String>>()
            coEvery { dataLayerClient.putDataItem(any(), capture(capturedData)) } returns true

            syncManager.syncContent()

            // Simulate what PhoneDataLayerListenerService does: deserialize the data map
            val receivedNote = noteDataMapper.fromDataMap(capturedData.captured)

            // Verify exact field-by-field equality
            assertTrue(receivedNote is JournalNote.Audio)
            assertEquals(originalNote.uid, receivedNote.uid)
            assertEquals(originalNote.creationTimestamp, receivedNote.creationTimestamp)
            assertEquals(originalNote.lastUpdated, receivedNote.lastUpdated)
            assertEquals(originalNote.mediaRef, receivedNote.mediaRef)
            assertEquals(originalNote.durationMs, receivedNote.durationMs)
            assertEquals(originalNote.syncVersion, receivedNote.syncVersion)
            assertEquals(originalNote.location, receivedNote.location)

            // Full object equality
            assertEquals(originalNote, receivedNote)
        }
}
