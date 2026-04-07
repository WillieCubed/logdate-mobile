package app.logdate.client.e2e

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.data.journals.OfflineFirstJournalContentRepository
import app.logdate.client.data.journals.OfflineFirstJournalRepository
import app.logdate.client.data.journals.RemoteJournalDataSource
import app.logdate.client.data.location.StubLocationHistoryRepository
import app.logdate.client.data.notes.EmptyNotePlaceResolver
import app.logdate.client.data.notes.OfflineFirstJournalNotesRepository
import app.logdate.client.data.places.StubUserPlacesRepository
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.device.AppInfo
import app.logdate.client.device.AppInfoProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.domain.export.ExportFileStructure
import app.logdate.client.domain.export.ExportProgress
import app.logdate.client.domain.export.ExportUserDataUseCase
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
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Instrumented end-to-end test for ZIP archive round-trip fidelity.
 *
 * Unlike [ExportImportE2ETest], which exercises the use-case layer directly,
 * these tests go through the full stack: data is exported to an actual ZIP file
 * on disk using the same serialization logic as [ExportWorker], then the archive
 * is opened and parsed with [ZipFile] exactly as [RestoreWorker] does, and the
 * resulting bundle is fed to [RestoreUserDataUseCase]. This proves that the
 * JSON serialization, ZIP entry naming, and ZIP file reading all form a
 * lossless round-trip.
 */
@RunWith(AndroidJUnit4::class)
class ArchiveRoundTripTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

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
        val dispatcher = Dispatchers.Unconfined

        sourceDb = Room
            .inMemoryDatabaseBuilder(context, LogDateDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        destDb = Room
            .inMemoryDatabaseBuilder(context, LogDateDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val sourceSyncMeta = InMemorySyncMetadataService()
        val sourceDraftRepo = InMemoryDraftRepository()

        sourceJournalRepo = OfflineFirstJournalRepository(
            journalDao = sourceDb.journalDao(),
            remoteDataSource = NoOpRemoteJournalDataSource(),
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

        exportUseCase = ExportUserDataUseCase(
            journalRepository = sourceJournalRepo,
            journalNotesRepository = sourceNotesRepo,
            profileRepository = StubProfileRepository(),
            userPlacesRepository = StubUserPlacesRepository(),
            locationHistoryRepository = StubLocationHistoryRepository(),
            userStateRepository = StubUserStateRepository(),
            deviceIdProvider = FixedDeviceIdProvider(),
            appInfoProvider = FixedAppInfoProvider(),
        )

        val destSyncMeta = InMemorySyncMetadataService()
        val destDraftRepo = InMemoryDraftRepository()

        destJournalRepo = OfflineFirstJournalRepository(
            journalDao = destDb.journalDao(),
            remoteDataSource = NoOpRemoteJournalDataSource(),
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

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun Instant.truncateToMillis(): Instant =
        Instant.fromEpochMilliseconds(toEpochMilliseconds())

    private fun now(): Instant = Clock.System.now().truncateToMillis()

    /**
     * Exports from the source database to a real ZIP archive on disk, then reads
     * that archive back and restores into the destination database — the exact path
     * [ExportWorker] and [RestoreWorker] take in production.
     */
    private suspend fun archiveRoundTrip(options: RestoreOptions = RestoreOptions()) {
        // includeMedia=false: test media refs are bare content URIs without file
        // extensions; ExportUserDataUseCase throws on extensionless URIs when building
        // the media manifest. The round-trip test proves JSON serialization fidelity,
        // not media file copying.
        val progress = exportUseCase.exportUserData(includeMedia = false).last()
        check(progress is ExportProgress.Completed) { "Export did not complete: $progress" }
        val export = progress.result

        val archiveFile = File.createTempFile("roundtrip_test", ".zip", context.cacheDir)
        try {
            // Write — same logic as ExportWorker.writeExportToZip
            ZipOutputStream(FileOutputStream(archiveFile)).use { zip ->
                fun entry(name: String, content: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                entry(ExportFileStructure.METADATA_FILE, export.serializeMetadata())
                entry(ExportFileStructure.JOURNALS_FILE, export.serializeJournals())
                entry(ExportFileStructure.NOTES_FILE, export.serializeNotes())
                entry(ExportFileStructure.JOURNAL_NOTES_FILE, export.serializeJournalNotes())
                entry(ExportFileStructure.DRAFTS_FILE, export.serializeDrafts())
                export.serializeProfile()?.let { entry(ExportFileStructure.PROFILE_FILE, it) }
                export.serializePlaces()?.let { entry(ExportFileStructure.PLACES_FILE, it) }
                export.serializeLocationHistory()?.let { entry(ExportFileStructure.LOCATION_HISTORY_FILE, it) }
                export.serializeMediaManifest()?.let { entry(ExportFileStructure.MEDIA_MANIFEST_FILE, it) }
            }

            // Read — same logic as RestoreWorker.readRequiredEntry / readOptionalEntry
            val bundle = ZipFile(archiveFile).use { zip ->
                fun required(name: String): String {
                    val e = zip.getEntry(name)
                        ?: error("Required archive entry missing: $name")
                    return zip.getInputStream(e).bufferedReader(Charsets.UTF_8).readText()
                }
                fun optional(name: String): String? {
                    val e = zip.getEntry(name) ?: return null
                    return zip.getInputStream(e).bufferedReader(Charsets.UTF_8).readText()
                }
                RestoreBundle(
                    metadataJson = required(ExportFileStructure.METADATA_FILE),
                    journalsJson = required(ExportFileStructure.JOURNALS_FILE),
                    notesJson = required(ExportFileStructure.NOTES_FILE),
                    journalNotesJson = required(ExportFileStructure.JOURNAL_NOTES_FILE),
                    draftsJson = required(ExportFileStructure.DRAFTS_FILE),
                    profileJson = optional(ExportFileStructure.PROFILE_FILE),
                    placesJson = optional(ExportFileStructure.PLACES_FILE),
                    locationHistoryJson = optional(ExportFileStructure.LOCATION_HISTORY_FILE),
                    mediaManifestJson = optional(ExportFileStructure.MEDIA_MANIFEST_FILE),
                )
            }

            restoreUseCase.restore(bundle, options)
        } finally {
            archiveFile.delete()
        }
    }

    // ── Tests ────────────────────────────────────────────────────────────

    @Test
    fun archive_allContentTypes_surviveRoundTrip() =
        runTest {
            val now = now()

            val journal1 = Journal(id = Uuid.random(), title = "Daily", created = now, lastUpdated = now)
            val journal2 = Journal(id = Uuid.random(), title = "Travel", created = now - 48.hours, lastUpdated = now)
            sourceJournalRepo.create(journal1)
            sourceJournalRepo.create(journal2)

            val textNote = JournalNote.Text(
                uid = Uuid.random(),
                creationTimestamp = now - 2.hours,
                lastUpdated = now,
                content = "A regular text entry",
            )
            val imageNote = JournalNote.Image(
                uid = Uuid.random(),
                creationTimestamp = now - 1.hours,
                lastUpdated = now,
                mediaRef = "content://media/external/images/1",
                caption = "Sunrise",
            )
            val audioNote = JournalNote.Audio(
                uid = Uuid.random(),
                creationTimestamp = now,
                lastUpdated = now,
                mediaRef = "content://media/external/audio/1",
                durationMs = 30000,
            )
            val videoNote = JournalNote.Video(
                uid = Uuid.random(),
                creationTimestamp = now,
                lastUpdated = now,
                mediaRef = "content://media/external/video/1",
                caption = "Timelapse",
            )
            val geoNote = JournalNote.Text(
                uid = Uuid.random(),
                creationTimestamp = now,
                lastUpdated = now,
                content = "At the park",
                location = NoteLocation(coordinates = NoteCoordinates(latitude = 51.5074, longitude = -0.1278)),
            )

            sourceNotesRepo.create(textNote, journal1.id)
            sourceNotesRepo.create(imageNote, journal1.id)
            sourceNotesRepo.create(audioNote, journal2.id)
            sourceNotesRepo.create(videoNote, journal2.id)
            sourceNotesRepo.create(geoNote, journal1.id)

            archiveRoundTrip()

            // Journals
            assertNotNull(destJournalRepo.getJournalById(journal1.id), "journal1 missing after archive round-trip")
            assertNotNull(destJournalRepo.getJournalById(journal2.id), "journal2 missing after archive round-trip")

            // Notes
            val rt = destNotesRepo.getNoteById(textNote.uid) as? JournalNote.Text
            assertNotNull(rt, "text note missing")
            assertEquals(textNote.content, rt.content)

            val ri = destNotesRepo.getNoteById(imageNote.uid) as? JournalNote.Image
            assertNotNull(ri, "image note missing")
            assertEquals(imageNote.mediaRef, ri.mediaRef)
            assertEquals(imageNote.caption, ri.caption)

            val ra = destNotesRepo.getNoteById(audioNote.uid) as? JournalNote.Audio
            assertNotNull(ra, "audio note missing")
            assertEquals(audioNote.mediaRef, ra.mediaRef)
            assertEquals(audioNote.durationMs, ra.durationMs)

            val rv = destNotesRepo.getNoteById(videoNote.uid) as? JournalNote.Video
            assertNotNull(rv, "video note missing")
            assertEquals(videoNote.mediaRef, rv.mediaRef)
            assertEquals(videoNote.caption, rv.caption)

            val rg = destNotesRepo.getNoteById(geoNote.uid) as? JournalNote.Text
            assertNotNull(rg, "geo note missing")
            val coords = rg.location?.coordinates
            assertNotNull(coords, "location coordinates missing after archive round-trip")
            assertEquals(51.5074, coords.latitude, 0.0001)
            assertEquals(-0.1278, coords.longitude, 0.0001)

            // Journal-note links
            val j1Notes = destContentRepo.observeContentForJournal(journal1.id).first()
            assertEquals(3, j1Notes.size, "journal1 should have 3 notes after archive round-trip")

            val j2Notes = destContentRepo.observeContentForJournal(journal2.id).first()
            assertEquals(2, j2Notes.size, "journal2 should have 2 notes after archive round-trip")
        }

    @Test
    fun archive_unicodeAndSpecialCharacters_preservedExactly() =
        runTest {
            val now = now()
            val journal = Journal(
                id = Uuid.random(),
                title = "日記 📖",
                created = now,
                lastUpdated = now,
            )
            sourceJournalRepo.create(journal)

            val note = JournalNote.Text(
                uid = Uuid.random(),
                creationTimestamp = now,
                lastUpdated = now,
                content = "こんにちは 🌸 \"quoted\" \\ newline\nend",
            )
            sourceNotesRepo.create(note, journal.id)

            archiveRoundTrip()

            val restoredJournal = destJournalRepo.getJournalById(journal.id)
            assertNotNull(restoredJournal)
            assertEquals(journal.title, restoredJournal.title, "Unicode journal title corrupted in archive")

            val restoredNote = destNotesRepo.getNoteById(note.uid) as? JournalNote.Text
            assertNotNull(restoredNote)
            assertEquals(note.content, restoredNote.content, "Unicode/special-char content corrupted in archive")
        }

    @Test
    fun archive_timestamps_preservedWithMillisecondPrecision() =
        runTest {
            val created = Instant.fromEpochMilliseconds(1_700_000_000_123L)
            val updated = Instant.fromEpochMilliseconds(1_700_000_060_456L)

            val journal = Journal(id = Uuid.random(), title = "Precision", created = created, lastUpdated = updated)
            sourceJournalRepo.create(journal)

            val note = JournalNote.Text(
                uid = Uuid.random(),
                creationTimestamp = created,
                lastUpdated = updated,
                content = "Timestamp precision test",
            )
            sourceNotesRepo.create(note, journal.id)

            archiveRoundTrip()

            val rj = destJournalRepo.getJournalById(journal.id)!!
            assertEquals(created, rj.created, "Journal created timestamp lost precision in archive")
            assertEquals(updated, rj.lastUpdated, "Journal updated timestamp lost precision in archive")

            val rn = destNotesRepo.getNoteById(note.uid) as JournalNote.Text
            assertEquals(created, rn.creationTimestamp, "Note creation timestamp lost precision in archive")
            assertEquals(updated, rn.lastUpdated, "Note updated timestamp lost precision in archive")
        }

    @Test
    fun archive_manyItems_noneDropped() =
        runTest {
            val now = now()
            val journal = Journal(id = Uuid.random(), title = "Bulk", created = now, lastUpdated = now)
            sourceJournalRepo.create(journal)

            val noteIds = (1..50).map { i ->
                val note = JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = now - i.hours,
                    lastUpdated = now,
                    content = "Entry number $i",
                )
                sourceNotesRepo.create(note, journal.id)
                note.uid
            }

            archiveRoundTrip()

            noteIds.forEachIndexed { index, id ->
                assertNotNull(
                    destNotesRepo.getNoteById(id),
                    "Note ${index + 1} of ${noteIds.size} was dropped during archive round-trip",
                )
            }
        }

    @Test
    fun archive_drafts_survivedWhenIncluded() =
        runTest {
            val now = now()
            val journal = Journal(id = Uuid.random(), title = "J", created = now, lastUpdated = now)
            sourceJournalRepo.create(journal)

            val draft = EditorDraft(
                id = Uuid.random(),
                blocks = listOf(
                    SerializableTextBlock(id = Uuid.random(), timestamp = now, content = "Draft text 📝"),
                ),
                selectedJournalIds = listOf(journal.id),
                createdAt = now - 1.hours,
                lastModifiedAt = now,
            )
            sourceJournalRepo.saveDraft(draft)

            archiveRoundTrip(RestoreOptions(includeDrafts = true))

            val restoredDrafts = destJournalRepo.getAllDrafts()
            assertTrue(restoredDrafts.isNotEmpty(), "Draft was lost during archive round-trip")
            assertEquals(
                "Draft text 📝",
                (restoredDrafts.first().blocks.first() as SerializableTextBlock).content,
                "Draft content corrupted in archive",
            )
        }

    @Test
    fun archive_drafts_absentWhenExcluded() =
        runTest {
            val now = now()
            val journal = Journal(id = Uuid.random(), title = "J", created = now, lastUpdated = now)
            sourceJournalRepo.create(journal)

            sourceJournalRepo.saveDraft(
                EditorDraft(
                    id = Uuid.random(),
                    blocks = listOf(SerializableTextBlock(id = Uuid.random(), timestamp = now, content = "Skip me")),
                    selectedJournalIds = listOf(journal.id),
                    createdAt = now,
                    lastModifiedAt = now,
                ),
            )

            archiveRoundTrip(RestoreOptions(includeDrafts = false))

            assertTrue(destJournalRepo.getAllDrafts().isEmpty(), "Excluded drafts appeared after archive round-trip")
        }

    @Test
    fun archive_emptyDatabase_producesValidArchiveAndRestoresCleanly() =
        runTest {
            // No data seeded — archive must still be well-formed and restore must succeed.
            archiveRoundTrip()

            assertTrue(
                destJournalRepo.allJournalsObserved.first().isEmpty(),
                "Empty export should restore to an empty destination",
            )
        }

    // ── Inline fakes ─────────────────────────────────────────────────────

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
            pending.getOrPut(entityType) { mutableMapOf() }[entityId] = PendingUpload(entityId, operation, 0)
            updateCount()
        }

        override suspend fun resetSyncStatus(entityId: String, entityType: EntityType) {
            pending.getOrPut(entityType) { mutableMapOf() }[entityId] = PendingUpload(entityId, PendingOperation.UPDATE, 0)
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
            Result.success(profile.value.copy(displayName = displayName)).also { profile.value = it.getOrThrow() }

        override suspend fun updateBirthday(birthday: Instant?): Result<LogDateProfile> =
            Result.success(profile.value.copy(birthday = birthday)).also { profile.value = it.getOrThrow() }

        override suspend fun updateProfilePhoto(profilePhotoUri: String?): Result<LogDateProfile> =
            Result.success(profile.value.copy(profilePhotoUri = profilePhotoUri)).also { profile.value = it.getOrThrow() }

        override suspend fun updateBio(bio: String?, originalBio: String?): Result<LogDateProfile> =
            Result.success(profile.value.copy(bio = bio, originalBio = originalBio)).also { profile.value = it.getOrThrow() }

        override suspend fun getCurrentProfile(): LogDateProfile = profile.value

        override suspend fun clearProfile(): Result<Unit> =
            Result.success(Unit).also { profile.value = LogDateProfile() }
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
