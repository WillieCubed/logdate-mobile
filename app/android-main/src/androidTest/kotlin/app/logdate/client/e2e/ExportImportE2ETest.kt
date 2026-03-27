package app.logdate.client.e2e

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.data.location.StubLocationHistoryRepository
import app.logdate.client.data.journals.OfflineFirstJournalContentRepository
import app.logdate.client.data.journals.OfflineFirstJournalRepository
import app.logdate.client.data.journals.RemoteJournalDataSource
import app.logdate.client.data.notes.EmptyNotePlaceResolver
import app.logdate.client.data.notes.OfflineFirstJournalNotesRepository
import app.logdate.client.data.places.StubUserPlacesRepository
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.device.AppInfo
import app.logdate.client.device.AppInfoProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.domain.export.ExportProgress
import app.logdate.client.domain.export.ExportResult
import app.logdate.client.domain.export.ExportUserDataUseCase
import app.logdate.client.domain.notes.GetAllAudioNotesUseCase
import app.logdate.client.domain.restore.RestoreBundle
import app.logdate.client.domain.restore.RestoreOptions
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.repository.journals.DraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.profile.ProfileRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.profile.LogDateProfile
import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Instrumented end-to-end test for the export/import pipeline.
 *
 * Uses two separate in-memory Room databases with real DAO and repository
 * implementations to verify data survives a complete export → import round-trip.
 */
@RunWith(AndroidJUnit4::class)
class ExportImportE2ETest {
    private lateinit var sourceDb: LogDateDatabase
    private lateinit var destDb: LogDateDatabase

    private lateinit var sourceJournalRepo: OfflineFirstJournalRepository
    private lateinit var sourceNotesRepo: OfflineFirstJournalNotesRepository
    private lateinit var sourceContentRepo: OfflineFirstJournalContentRepository
    private lateinit var exportUseCase: ExportUserDataUseCase

    private lateinit var destJournalRepo: OfflineFirstJournalRepository
    private lateinit var destNotesRepo: OfflineFirstJournalNotesRepository
    private lateinit var destContentRepo: OfflineFirstJournalContentRepository
    private lateinit var restoreUseCase: RestoreUserDataUseCase

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val dispatcher = Dispatchers.Unconfined

        sourceDb = Room
            .inMemoryDatabaseBuilder(context, LogDateDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        destDb = Room
            .inMemoryDatabaseBuilder(context, LogDateDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Source-side wiring
        val sourceSyncMeta = InMemorySyncMetadataService()
        val sourceDraftRepo = InMemoryDraftRepository()
        val sourceRemote = NoOpRemoteJournalDataSource()

        sourceJournalRepo = OfflineFirstJournalRepository(
            journalDao = sourceDb.journalDao(),
            remoteDataSource = sourceRemote,
            draftRepository = sourceDraftRepo,
            syncMetadataService = sourceSyncMeta,
            dispatcher = dispatcher,
            externalScope = kotlinx.coroutines.CoroutineScope(dispatcher),
        )

        sourceNotesRepo = OfflineFirstJournalNotesRepository(
            textNoteDao = sourceDb.textNoteDao(),
            imageNoteDao = sourceDb.imageNoteDao(),
            audioNoteDao = sourceDb.audioNoteDao(),
            videoNoteDao = sourceDb.videoNoteDao(),
            journalContentDao = sourceDb.journalContentDao(),
            journalRepository = sourceJournalRepo,
            mediaCaptionDao = sourceDb.mediaCaptionDao(),
            notePlaceResolver = EmptyNotePlaceResolver,
            syncMetadataService = sourceSyncMeta,
            syncScope = kotlinx.coroutines.CoroutineScope(dispatcher),
        )

        sourceContentRepo = OfflineFirstJournalContentRepository(
            journalContentDao = sourceDb.journalContentDao(),
            journalRepository = sourceJournalRepo,
            journalNotesRepository = sourceNotesRepo,
            syncMetadataService = sourceSyncMeta,
            dispatcher = dispatcher,
        )

        val audioUseCase = GetAllAudioNotesUseCase(sourceNotesRepo)

        exportUseCase = ExportUserDataUseCase(
            journalRepository = sourceJournalRepo,
            journalNotesRepository = sourceNotesRepo,
            profileRepository = StubProfileRepository(),
            userPlacesRepository = StubUserPlacesRepository(),
            locationHistoryRepository = StubLocationHistoryRepository(),
            userStateRepository = StubUserStateRepository(),
            deviceIdProvider = FixedDeviceIdProvider(),
            appInfoProvider = FixedAppInfoProvider(),
            getAllAudioNotesUseCase = audioUseCase,
        )

        // Destination-side wiring
        val destSyncMeta = InMemorySyncMetadataService()
        val destDraftRepo = InMemoryDraftRepository()
        val destRemote = NoOpRemoteJournalDataSource()

        destJournalRepo = OfflineFirstJournalRepository(
            journalDao = destDb.journalDao(),
            remoteDataSource = destRemote,
            draftRepository = destDraftRepo,
            syncMetadataService = destSyncMeta,
            dispatcher = dispatcher,
            externalScope = kotlinx.coroutines.CoroutineScope(dispatcher),
        )

        destNotesRepo = OfflineFirstJournalNotesRepository(
            textNoteDao = destDb.textNoteDao(),
            imageNoteDao = destDb.imageNoteDao(),
            audioNoteDao = destDb.audioNoteDao(),
            videoNoteDao = destDb.videoNoteDao(),
            journalContentDao = destDb.journalContentDao(),
            journalRepository = destJournalRepo,
            mediaCaptionDao = destDb.mediaCaptionDao(),
            notePlaceResolver = EmptyNotePlaceResolver,
            syncMetadataService = destSyncMeta,
            syncScope = kotlinx.coroutines.CoroutineScope(dispatcher),
        )

        destContentRepo = OfflineFirstJournalContentRepository(
            journalContentDao = destDb.journalContentDao(),
            journalRepository = destJournalRepo,
            journalNotesRepository = destNotesRepo,
            syncMetadataService = destSyncMeta,
            dispatcher = dispatcher,
        )

        restoreUseCase = RestoreUserDataUseCase(
            journalRepository = destJournalRepo,
            journalNotesRepository = destNotesRepo,
            journalContentRepository = destContentRepo,
            profileRepository = StubProfileRepository(),
            userPlacesRepository = StubUserPlacesRepository(),
            locationHistoryRepository = StubLocationHistoryRepository(),
        )
    }

    @After
    fun teardown() {
        sourceDb.close()
        destDb.close()
    }

    // ── Helper ──────────────────────────────────────────────────────────

    /** Truncate to millisecond precision (Room stores epoch millis). */
    private fun Instant.truncateToMillis(): Instant =
        Instant.fromEpochMilliseconds(toEpochMilliseconds())

    private fun now(): Instant = Clock.System.now().truncateToMillis()

    private suspend fun exportAndRestore(
        options: RestoreOptions = RestoreOptions(),
    ): ExportResult {
        val result = exportUseCase.exportUserData().last()
        check(result is ExportProgress.Completed) { "Export should complete successfully but was $result" }
        val export = result.result

        val bundle = RestoreBundle(
            metadataJson = export.metadata,
            journalsJson = export.journals,
            notesJson = export.notes,
            journalNotesJson = export.journalNotes,
            draftsJson = export.drafts,
            mediaManifestJson = export.mediaManifest,
        )
        restoreUseCase.restore(bundle, options)
        return export
    }

    // ── Tests ───────────────────────────────────────────────────────────

    @Test
    fun roundTrip_journals_preservesAllFields() = runTest {
        val now = now()
        val journal = Journal(
            id = Uuid.random(),
            title = "Travel Log",
            description = "My travels",
            created = now - 48.hours,
            lastUpdated = now,
        )
        sourceJournalRepo.create(journal)

        exportAndRestore()

        val restored = destJournalRepo.getJournalById(journal.id)
        assertNotNull(restored, "Journal should exist in destination")
        assertEquals(journal.id, restored.id)
        assertEquals(journal.title, restored.title)
        assertEquals(journal.description, restored.description)
        assertEquals(journal.created, restored.created)
        assertEquals(journal.lastUpdated, restored.lastUpdated)
    }

    @Test
    fun roundTrip_textNote_preservesContent() = runTest {
        val now = now()
        val journal = Journal(id = Uuid.random(), title = "J1", created = now, lastUpdated = now)
        sourceJournalRepo.create(journal)

        val note = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = now - 1.hours,
            lastUpdated = now,
            content = "Hello, world!",
        )
        sourceNotesRepo.create(note, journal.id)

        exportAndRestore()

        val restored = destNotesRepo.getNoteById(note.uid) as? JournalNote.Text
        assertNotNull(restored, "Text note should exist in destination")
        assertEquals(note.content, restored.content)
        assertEquals(note.creationTimestamp, restored.creationTimestamp)
        assertEquals(note.lastUpdated, restored.lastUpdated)
    }

    @Test
    fun roundTrip_imageNote_preservesMediaRefAndCaption() = runTest {
        val now = now()
        val journal = Journal(id = Uuid.random(), title = "Photos", created = now, lastUpdated = now)
        sourceJournalRepo.create(journal)

        val note = JournalNote.Image(
            uid = Uuid.random(),
            creationTimestamp = now,
            lastUpdated = now,
            mediaRef = "content://media/external/images/1234",
            caption = "Sunset at the beach",
        )
        sourceNotesRepo.create(note, journal.id)

        exportAndRestore()

        val restored = destNotesRepo.getNoteById(note.uid) as? JournalNote.Image
        assertNotNull(restored, "Image note should exist in destination")
        assertEquals(note.mediaRef, restored.mediaRef)
        assertEquals(note.caption, restored.caption)
    }

    @Test
    fun roundTrip_videoNote_preservesMediaRefAndCaption() = runTest {
        val now = now()
        val journal = Journal(id = Uuid.random(), title = "Videos", created = now, lastUpdated = now)
        sourceJournalRepo.create(journal)

        val note = JournalNote.Video(
            uid = Uuid.random(),
            creationTimestamp = now,
            lastUpdated = now,
            mediaRef = "content://media/external/video/5678",
            caption = "Dog playing fetch",
        )
        sourceNotesRepo.create(note, journal.id)

        exportAndRestore()

        val restored = destNotesRepo.getNoteById(note.uid) as? JournalNote.Video
        assertNotNull(restored, "Video note should exist in destination")
        assertEquals(note.mediaRef, restored.mediaRef)
        assertEquals(note.caption, restored.caption)
    }

    @Test
    fun roundTrip_audioNote_preservesMediaRef() = runTest {
        val now = now()
        val journal = Journal(id = Uuid.random(), title = "Voice Memos", created = now, lastUpdated = now)
        sourceJournalRepo.create(journal)

        val note = JournalNote.Audio(
            uid = Uuid.random(),
            creationTimestamp = now,
            lastUpdated = now,
            mediaRef = "content://media/external/audio/9999",
            durationMs = 15000,
        )
        sourceNotesRepo.create(note, journal.id)

        exportAndRestore()

        val restored = destNotesRepo.getNoteById(note.uid) as? JournalNote.Audio
        assertNotNull(restored, "Audio note should exist in destination")
        assertEquals(note.mediaRef, restored.mediaRef)
        // durationMs is not preserved in the export format (known limitation)
        assertEquals(0L, restored.durationMs)
    }

    @Test
    fun roundTrip_noteLocation_preservesCoordinates() = runTest {
        val now = now()
        val journal = Journal(id = Uuid.random(), title = "Geo", created = now, lastUpdated = now)
        sourceJournalRepo.create(journal)

        val location = NoteLocation(
            coordinates = NoteCoordinates(latitude = 37.7749, longitude = -122.4194),
        )
        val note = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = now,
            lastUpdated = now,
            content = "In San Francisco",
            location = location,
        )
        sourceNotesRepo.create(note, journal.id)

        exportAndRestore()

        val restored = destNotesRepo.getNoteById(note.uid) as? JournalNote.Text
        assertNotNull(restored, "Note with location should exist")
        assertNotNull(restored.location, "Location should be preserved")
        val coords = restored.location?.coordinates
        assertNotNull(coords, "Coordinates should be preserved")
        assertEquals(37.7749, coords.latitude, 0.0001)
        assertEquals(-122.4194, coords.longitude, 0.0001)
    }

    @Test
    fun roundTrip_journalNoteRelations_preserved() = runTest {
        val now = now()
        val journal = Journal(id = Uuid.random(), title = "Daily", created = now, lastUpdated = now)
        sourceJournalRepo.create(journal)

        val note1 = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = now,
            lastUpdated = now,
            content = "Note 1",
        )
        val note2 = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = now,
            lastUpdated = now,
            content = "Note 2",
        )
        sourceNotesRepo.create(note1, journal.id)
        sourceNotesRepo.create(note2, journal.id)

        exportAndRestore()

        val restoredNotes = destContentRepo.observeContentForJournal(journal.id).first()
        assertEquals(2, restoredNotes.size, "Both notes should be linked to journal")
        assertTrue(restoredNotes.any { it.uid == note1.uid })
        assertTrue(restoredNotes.any { it.uid == note2.uid })
    }

    @Test
    fun roundTrip_multipleJournals_allPreserved() = runTest {
        val now = now()
        val journals = (1..3).map { i ->
            Journal(id = Uuid.random(), title = "Journal $i", created = now, lastUpdated = now)
        }
        journals.forEach { sourceJournalRepo.create(it) }

        exportAndRestore()

        journals.forEach { journal ->
            val restored = destJournalRepo.getJournalById(journal.id)
            assertNotNull(restored, "Journal '${journal.title}' should exist")
            assertEquals(journal.title, restored.title)
        }
    }

    @Test
    fun roundTrip_drafts_preservedWhenIncluded() = runTest {
        val now = now()
        val journal = Journal(id = Uuid.random(), title = "J1", created = now, lastUpdated = now)
        sourceJournalRepo.create(journal)

        val draft = EditorDraft(
            id = Uuid.random(),
            blocks = listOf(
                SerializableTextBlock(
                    id = Uuid.random(),
                    timestamp = now,
                    content = "Draft content",
                ),
            ),
            selectedJournalIds = listOf(journal.id),
            createdAt = now - 1.hours,
            lastModifiedAt = now,
        )
        sourceJournalRepo.saveDraft(draft)

        exportAndRestore(options = RestoreOptions(includeDrafts = true))

        val restoredDrafts = destJournalRepo.getAllDrafts()
        assertTrue(restoredDrafts.isNotEmpty(), "Drafts should be imported")
    }

    @Test
    fun roundTrip_drafts_skippedWhenExcluded() = runTest {
        val now = now()
        val journal = Journal(id = Uuid.random(), title = "J1", created = now, lastUpdated = now)
        sourceJournalRepo.create(journal)

        val draft = EditorDraft(
            id = Uuid.random(),
            blocks = listOf(
                SerializableTextBlock(
                    id = Uuid.random(),
                    timestamp = now,
                    content = "Should not import",
                ),
            ),
            selectedJournalIds = listOf(journal.id),
            createdAt = now,
            lastModifiedAt = now,
        )
        sourceJournalRepo.saveDraft(draft)

        exportAndRestore(options = RestoreOptions(includeDrafts = false))

        val restoredDrafts = destJournalRepo.getAllDrafts()
        assertTrue(restoredDrafts.isEmpty(), "No drafts should be imported when excluded")
    }

    @Test
    fun roundTrip_timestamps_preservedExactly() = runTest {
        val created = Instant.fromEpochMilliseconds(1700000000000L)
        val updated = Instant.fromEpochMilliseconds(1700000060000L)

        val journal = Journal(id = Uuid.random(), title = "Exact", created = created, lastUpdated = updated)
        sourceJournalRepo.create(journal)

        val note = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = created,
            lastUpdated = updated,
            content = "Timestamp test",
        )
        sourceNotesRepo.create(note, journal.id)

        exportAndRestore()

        val restoredJournal = destJournalRepo.getJournalById(journal.id)!!
        assertEquals(created, restoredJournal.created)
        assertEquals(updated, restoredJournal.lastUpdated)

        val restoredNote = destNotesRepo.getNoteById(note.uid) as JournalNote.Text
        assertEquals(created, restoredNote.creationTimestamp)
        assertEquals(updated, restoredNote.lastUpdated)
    }

    @Test
    fun roundTrip_metadata_containsDeviceAndAppInfo() = runTest {
        val now = now()
        val journal = Journal(id = Uuid.random(), title = "Meta", created = now, lastUpdated = now)
        sourceJournalRepo.create(journal)

        val result = exportUseCase.exportUserData().last()
        check(result is ExportProgress.Completed) { "Export should complete but was $result" }

        val metadata = result.result.metadata
        assertTrue(metadata.contains("00000000-0000-0000-0000-000000000001"), "Metadata should contain device ID")
        assertTrue(metadata.contains("1.0.0-test"), "Metadata should contain app version")
    }

    // ── Inline Fakes ────────────────────────────────────────────────────

    private class InMemorySyncMetadataService : SyncMetadataService {
        private val pending = mutableMapOf<EntityType, MutableMap<String, PendingUpload>>()
        private val syncTimes = mutableMapOf<EntityType, Instant>()
        private val pendingCountFlow = MutableStateFlow(0)

        override suspend fun getPendingUploads(entityType: EntityType): List<PendingUpload> =
            pending[entityType]?.values?.toList() ?: emptyList()

        override suspend fun markAsSynced(entityId: String, entityType: EntityType, syncedAt: Instant, version: Long) {
            pending[entityType]?.remove(entityId)
            updateCount()
        }

        override suspend fun getLastSyncTime(entityType: EntityType): Instant? = syncTimes[entityType]

        override suspend fun updateLastSyncTime(entityType: EntityType, syncedAt: Instant) {
            syncTimes[entityType] = syncedAt
        }

        override suspend fun enqueuePending(entityId: String, entityType: EntityType, operation: PendingOperation) {
            pending.getOrPut(entityType) { mutableMapOf() }[entityId] =
                PendingUpload(entityId, operation, 0)
            updateCount()
        }

        override suspend fun resetSyncStatus(entityId: String, entityType: EntityType) {
            pending.getOrPut(entityType) { mutableMapOf() }[entityId] =
                PendingUpload(entityId, PendingOperation.UPDATE, 0)
            updateCount()
        }

        override suspend fun getPendingCount(): Int = pending.values.sumOf { it.size }
        override fun observePendingCount(): Flow<Int> = pendingCountFlow

        override suspend fun incrementRetryCount(entityId: String, entityType: EntityType) {
            val existing = pending[entityType]?.get(entityId) ?: return
            pending[entityType]!![entityId] = existing.copy(retryCount = existing.retryCount + 1)
        }

        private fun updateCount() {
            pendingCountFlow.value = pending.values.sumOf { it.size }
        }
    }

    private class InMemoryDraftRepository : DraftRepository {
        private val drafts = mutableMapOf<Uuid, EditorDraft>()
        private val draftsFlow = MutableStateFlow<List<EditorDraft>>(emptyList())

        override suspend fun saveDraft(draft: EditorDraft) {
            drafts[draft.id] = draft
            draftsFlow.value = drafts.values.toList()
        }

        override suspend fun getLatestDraft(): EditorDraft? = drafts.values.maxByOrNull { it.lastModifiedAt }
        override suspend fun getAllDrafts(): List<EditorDraft> = drafts.values.toList()
        override val allDrafts: Flow<List<EditorDraft>> get() = draftsFlow
        override suspend fun getDraft(id: Uuid): EditorDraft? = drafts[id]

        override suspend fun deleteDraft(id: Uuid) {
            drafts.remove(id)
            draftsFlow.value = drafts.values.toList()
        }

        override suspend fun clearAllDrafts() {
            drafts.clear()
            draftsFlow.value = emptyList()
        }
    }

    private class NoOpRemoteJournalDataSource : RemoteJournalDataSource {
        override suspend fun observeAllJournals(): List<Journal> = emptyList()
        override suspend fun addJournal(journal: Journal): String = journal.id.toString()
        override suspend fun editJournal(journal: Journal) {}
        override suspend fun deleteJournal(journalId: String) {}
    }

    private class StubProfileRepository : ProfileRepository {
        private val profile = MutableStateFlow(LogDateProfile())

        override val currentProfile: Flow<LogDateProfile> = profile

        override suspend fun updateDisplayName(displayName: String): Result<LogDateProfile> =
            Result.success(profile.value.copy(displayName = displayName)).also { result ->
                result.getOrNull()?.let { profile.value = it }
            }

        override suspend fun updateBirthday(birthday: Instant?): Result<LogDateProfile> =
            Result.success(profile.value.copy(birthday = birthday)).also { result ->
                result.getOrNull()?.let { profile.value = it }
            }

        override suspend fun updateProfilePhoto(profilePhotoUri: String?): Result<LogDateProfile> =
            Result.success(profile.value.copy(profilePhotoUri = profilePhotoUri)).also { result ->
                result.getOrNull()?.let { profile.value = it }
            }

        override suspend fun updateBio(
            bio: String?,
            originalBio: String?,
        ): Result<LogDateProfile> =
            Result.success(profile.value.copy(bio = bio, originalBio = originalBio)).also { result ->
                result.getOrNull()?.let { profile.value = it }
            }

        override suspend fun getCurrentProfile(): LogDateProfile = profile.value

        override suspend fun clearProfile(): Result<Unit> =
            Result.success(Unit).also {
                profile.value = LogDateProfile()
            }
    }

    private class FixedDeviceIdProvider : DeviceIdProvider {
        private val id = Uuid.parse("00000000-0000-0000-0000-000000000001")
        private val flow = MutableStateFlow(id)
        override fun getDeviceId(): StateFlow<Uuid> = flow
        override suspend fun refreshDeviceId() {}
    }

    private class FixedAppInfoProvider : AppInfoProvider {
        override fun getAppInfo(): AppInfo = AppInfo(
            versionName = "1.0.0-test",
            versionCode = 1,
            packageName = "app.logdate.test",
        )
    }

    private class StubUserStateRepository : UserStateRepository {
        override val userData: Flow<UserData> = MutableStateFlow(UserData())
        override suspend fun setBirthday(birthday: Instant) {}
        override suspend fun setIsOnboardingComplete(isComplete: Boolean) {}
        override suspend fun setBiometricEnabled(isEnabled: Boolean) {}
        override suspend fun addFavoriteNote(vararg noteId: String) {}
    }
}
