package app.logdate.client.domain.export

import app.logdate.client.device.AppInfo
import app.logdate.client.device.AppInfoProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.domain.notes.GetAllAudioNotesUseCase
import app.logdate.client.domain.restore.MediaImporter
import app.logdate.client.domain.restore.RestoreBundle
import app.logdate.client.domain.restore.RestoreOptions
import app.logdate.client.domain.restore.RestoreStrategy
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.SerializableCameraBlock
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * End-to-end round-trip test: export data → JSON → import into fresh repositories → verify.
 *
 * Uses realistic payloads covering every note type, locations, captions, media references,
 * drafts, and journal-note relations to prove the pipeline is lossless.
 */
class ExportImportRoundTripTest {
    // Source repositories (populated with test data, used by export)
    private lateinit var sourceJournalRepo: RoundTripJournalRepository
    private lateinit var sourceNotesRepo: RoundTripJournalNotesRepository

    // Destination repositories (empty, used by import)
    private lateinit var destJournalRepo: RoundTripJournalRepository
    private lateinit var destNotesRepo: RoundTripJournalNotesRepository
    private lateinit var destContentRepo: RoundTripJournalContentRepository

    private lateinit var exportUseCase: ExportUserDataUseCase
    private lateinit var importUseCase: RestoreUserDataUseCase

    private val now = Clock.System.now()

    // -- Fixed IDs for deterministic assertions --
    private val journalDaily = Uuid.random()
    private val journalTravel = Uuid.random()

    private val noteText = Uuid.random()
    private val noteImage = Uuid.random()
    private val noteVideo = Uuid.random()
    private val noteAudio = Uuid.random()
    private val noteTextWithLocation = Uuid.random()

    private val draftId = Uuid.random()

    @BeforeTest
    fun setUp() {
        sourceJournalRepo = RoundTripJournalRepository()
        sourceNotesRepo = RoundTripJournalNotesRepository()

        destJournalRepo = RoundTripJournalRepository()
        destNotesRepo = RoundTripJournalNotesRepository()
        destContentRepo = RoundTripJournalContentRepository()

        val deviceIdProvider = StubDeviceIdProvider(Uuid.random())
        val appInfoProvider =
            StubAppInfoProvider(
                AppInfo(
                    versionName = "2.1.0",
                    versionCode = 210,
                    packageName = "app.logdate.test",
                ),
            )

        exportUseCase =
            ExportUserDataUseCase(
                journalRepository = sourceJournalRepo,
                journalNotesRepository = sourceNotesRepo,
                userStateRepository = StubUserStateRepository(),
                deviceIdProvider = deviceIdProvider,
                appInfoProvider = appInfoProvider,
                getAllAudioNotesUseCase = GetAllAudioNotesUseCase(sourceNotesRepo),
            )

        importUseCase =
            RestoreUserDataUseCase(
                journalRepository = destJournalRepo,
                journalNotesRepository = destNotesRepo,
                journalContentRepository = destContentRepo,
            )
    }

    // region Round-trip tests

    @Test
    fun `full round-trip preserves all journals`() =
        runTest {
            seedSourceData()
            val result = exportThenImport()

            assertEquals(2, result.journalsImported)
            val daily = destJournalRepo.getJournalById(journalDaily)
            assertNotNull(daily, "Daily journal should exist after import")
            assertEquals("Daily Reflections", daily.title)
            assertEquals("A place for daily thoughts", daily.description)

            val travel = destJournalRepo.getJournalById(journalTravel)
            assertNotNull(travel, "Travel journal should exist after import")
            assertEquals("Road Trip 2026", travel.title)
        }

    @Test
    fun `full round-trip preserves text note content`() =
        runTest {
            seedSourceData()
            val result = exportThenImport()

            assertEquals(5, result.notesImported)
            val text = destNotesRepo.getNoteById(noteText)
            assertTrue(text is JournalNote.Text, "Should be text note")
            assertEquals("Today I learned about export pipelines. Fascinating stuff!", text.content)
        }

    @Test
    fun `full round-trip preserves image note with caption`() =
        runTest {
            seedSourceData()
            exportThenImport()

            val image = destNotesRepo.getNoteById(noteImage)
            assertTrue(image is JournalNote.Image, "Should be image note")
            assertEquals("file:///storage/photos/sunset.jpg", image.mediaRef)
            assertEquals("Golden hour at Ocean Beach", image.caption)
        }

    @Test
    fun `full round-trip preserves video note with caption`() =
        runTest {
            seedSourceData()
            exportThenImport()

            val video = destNotesRepo.getNoteById(noteVideo)
            assertTrue(video is JournalNote.Video, "Should be video note")
            assertEquals("file:///storage/videos/waves.mp4", video.mediaRef)
            assertEquals("Waves crashing on the shore", video.caption)
        }

    @Test
    fun `full round-trip preserves audio note`() =
        runTest {
            seedSourceData()
            exportThenImport()

            val audio = destNotesRepo.getNoteById(noteAudio)
            assertTrue(audio is JournalNote.Audio, "Should be audio note")
            assertEquals("file:///storage/audio/voice_memo.m4a", audio.mediaRef)
        }

    @Test
    fun `full round-trip preserves note location and place name`() =
        runTest {
            seedSourceData()
            exportThenImport()

            val note = destNotesRepo.getNoteById(noteTextWithLocation)
            assertTrue(note is JournalNote.Text, "Should be text note")
            assertNotNull(note.location, "Location should be preserved")
            assertEquals(37.7749, note.location!!.coordinates?.latitude)
            assertEquals(-122.4194, note.location!!.coordinates?.longitude)
            assertEquals("San Francisco", note.location!!.displayName)
        }

    @Test
    fun `full round-trip preserves journal-note relations`() =
        runTest {
            seedSourceData()
            val result = exportThenImport()

            assertTrue(result.journalLinksImported > 0, "Should import journal-note relations")
            val links = destContentRepo.allLinks()
            assertTrue(
                links.any { it.first == noteText && it.second == journalDaily },
                "Text note should be linked to Daily journal",
            )
            assertTrue(
                links.any { it.first == noteImage && it.second == journalTravel },
                "Image note should be linked to Travel journal",
            )
        }

    @Test
    fun `full round-trip preserves drafts`() =
        runTest {
            seedSourceData()
            val result = exportThenImport()

            assertEquals(1, result.draftsImported)
            val drafts = destJournalRepo.getAllDrafts()
            assertEquals(1, drafts.size)
            val draft = drafts.first()
            assertTrue(
                draft.blocks.any { it is SerializableTextBlock && it.content.contains("work in progress") },
                "Draft text block should be preserved",
            )
        }

    @Test
    fun `full round-trip metadata counts match source data`() =
        runTest {
            seedSourceData()
            val result = exportThenImport()

            assertEquals(2, result.metadata.stats.journalCount)
            assertEquals(5, result.metadata.stats.noteCount)
            assertEquals(1, result.metadata.stats.draftCount)
            assertTrue(result.warnings.isEmpty(), "Round-trip should produce no warnings, got: ${result.warnings}")
        }

    @Test
    fun `full round-trip with media importer resolves paths`() =
        runTest {
            seedSourceData()

            val mediaImporter =
                object : MediaImporter {
                    override suspend fun importMedia(exportPath: String): String? =
                        if (exportPath.contains("sunset")) {
                            "content://media/imported/sunset_new.jpg"
                        } else {
                            null
                        }
                }

            val exported = export()
            val bundle = exportResultToBundle(exported)
            val result = importUseCase.restore(bundle, mediaImporter = mediaImporter)

            val image = destNotesRepo.getNoteById(noteImage)
            assertTrue(image is JournalNote.Image)
            // Media importer matched the export path containing "sunset"
            assertEquals("content://media/imported/sunset_new.jpg", image.mediaRef)
            assertTrue(result.mediaImported > 0)
        }

    @Test
    fun `round-trip with REPLACE_EXISTING overwrites pre-existing data`() =
        runTest {
            seedSourceData()
            val exported = export()
            val bundle = exportResultToBundle(exported)

            // Pre-populate destination with stale data
            destJournalRepo.create(
                Journal(
                    id = journalDaily,
                    title = "Old Title",
                    description = "Old description",
                    lastUpdated = now - 30.days,
                ),
            )

            val result =
                importUseCase.restore(
                    bundle,
                    options = RestoreOptions(strategy = RestoreStrategy.REPLACE_EXISTING),
                )

            assertEquals(2, result.journalsImported)
            val updated = destJournalRepo.getJournalById(journalDaily)
            assertEquals("Daily Reflections", updated?.title, "Title should be overwritten")
        }

    @Test
    fun `round-trip with MERGE_KEEP_NEWEST keeps newer local data`() =
        runTest {
            seedSourceData()
            val exported = export()
            val bundle = exportResultToBundle(exported)

            // Pre-populate destination with NEWER data
            destJournalRepo.create(
                Journal(
                    id = journalDaily,
                    title = "Locally Updated Title",
                    description = "Locally updated",
                    lastUpdated = now + 1.days,
                ),
            )

            val result =
                importUseCase.restore(
                    bundle,
                    options = RestoreOptions(strategy = RestoreStrategy.MERGE_KEEP_NEWEST),
                )

            val local = destJournalRepo.getJournalById(journalDaily)
            assertEquals("Locally Updated Title", local?.title, "Newer local title should be kept")
        }

    @Test
    fun `round-trip preserves note timestamps`() =
        runTest {
            seedSourceData()
            exportThenImport()

            val text = destNotesRepo.getNoteById(noteText) as JournalNote.Text
            val sourceNote = sourceNotesRepo.getNoteById(noteText) as JournalNote.Text

            assertEquals(sourceNote.creationTimestamp, text.creationTimestamp)
            assertEquals(sourceNote.lastUpdated, text.lastUpdated)
        }

    @Test
    fun `round-trip preserves image location`() =
        runTest {
            seedSourceData()
            exportThenImport()

            val image = destNotesRepo.getNoteById(noteImage) as JournalNote.Image
            assertNotNull(image.location, "Image location should be preserved")
            assertEquals(34.0522, image.location!!.coordinates?.latitude)
            assertEquals(-118.2437, image.location!!.coordinates?.longitude)
        }

    @Test
    fun `round-trip with empty data succeeds with zero counts`() =
        runTest {
            // Don't seed any data — source repos are empty
            val result = exportThenImport()

            assertEquals(0, result.journalsImported)
            assertEquals(0, result.notesImported)
            assertEquals(0, result.draftsImported)
            assertEquals(0, result.journalLinksImported)
            assertTrue(result.warnings.isEmpty())
        }

    @Test
    fun `round-trip note appears in multiple journals`() =
        runTest {
            seedSourceData()
            val result = exportThenImport()

            // noteTextWithLocation is in both journals
            val links = destContentRepo.allLinks()
            val linkedJournals = links.filter { it.first == noteTextWithLocation }.map { it.second }
            assertTrue(linkedJournals.contains(journalDaily), "Should be linked to Daily journal")
            assertTrue(linkedJournals.contains(journalTravel), "Should be linked to Travel journal")
        }

    // endregion

    // region Helpers

    private fun seedSourceData() {
        val dailyJournal =
            Journal(
                id = journalDaily,
                title = "Daily Reflections",
                description = "A place for daily thoughts",
                created = now - 90.days,
                lastUpdated = now - 1.hours,
            )
        val travelJournal =
            Journal(
                id = journalTravel,
                title = "Road Trip 2026",
                description = "Cross-country adventure",
                created = now - 14.days,
                lastUpdated = now - 2.days,
            )

        sourceJournalRepo.testJournals = listOf(dailyJournal, travelJournal)

        val sfLocation =
            NoteLocation(
                coordinates = NoteCoordinates(latitude = 37.7749, longitude = -122.4194),
                place =
                    NotePlace(
                        id = Uuid.random(),
                        name = "San Francisco",
                        latitude = 37.7749,
                        longitude = -122.4194,
                    ),
            )
        val laLocation =
            NoteLocation(
                coordinates = NoteCoordinates(latitude = 34.0522, longitude = -118.2437),
                place =
                    NotePlace(
                        id = Uuid.random(),
                        name = "Los Angeles",
                        latitude = 34.0522,
                        longitude = -118.2437,
                    ),
            )

        val textNote =
            JournalNote.Text(
                uid = noteText,
                creationTimestamp = now - 7.days,
                lastUpdated = now - 7.days,
                content = "Today I learned about export pipelines. Fascinating stuff!",
            )
        val imageNote =
            JournalNote.Image(
                uid = noteImage,
                creationTimestamp = now - 5.days,
                lastUpdated = now - 5.days,
                mediaRef = "file:///storage/photos/sunset.jpg",
                caption = "Golden hour at Ocean Beach",
                location = laLocation,
            )
        val videoNote =
            JournalNote.Video(
                uid = noteVideo,
                creationTimestamp = now - 3.days,
                lastUpdated = now - 3.days,
                mediaRef = "file:///storage/videos/waves.mp4",
                caption = "Waves crashing on the shore",
            )
        val audioNote =
            JournalNote.Audio(
                uid = noteAudio,
                creationTimestamp = now - 2.days,
                lastUpdated = now - 2.days,
                mediaRef = "file:///storage/audio/voice_memo.m4a",
                durationMs = 45000,
            )
        val textWithLocationNote =
            JournalNote.Text(
                uid = noteTextWithLocation,
                creationTimestamp = now - 1.days,
                lastUpdated = now - 1.days,
                content = "Sitting in Dolores Park writing in my journal",
                location = sfLocation,
            )

        sourceNotesRepo.testNotes = listOf(textNote, imageNote, videoNote, audioNote, textWithLocationNote)
        sourceNotesRepo.notesByJournal =
            mapOf(
                journalDaily to listOf(textNote, audioNote, textWithLocationNote),
                journalTravel to listOf(imageNote, videoNote, textWithLocationNote),
            )

        val draft =
            EditorDraft(
                id = draftId,
                blocks =
                    listOf(
                        SerializableTextBlock(
                            id = Uuid.random(),
                            timestamp = now,
                            locationLat = 37.7749,
                            locationLng = -122.4194,
                            content = "This is a work in progress entry about my day",
                        ),
                        SerializableCameraBlock(
                            id = Uuid.random(),
                            timestamp = now,
                            uri = "file:///storage/photos/draft_photo.jpg",
                        ),
                    ),
                selectedJournalIds = listOf(journalDaily),
                createdAt = now - 1.hours,
                lastModifiedAt = now,
            )
        sourceJournalRepo.testDrafts = listOf(draft)
    }

    private suspend fun export(): ExportResult {
        val emissions = exportUseCase.exportUserData().toList()
        val last = emissions.last()
        assertTrue(last is ExportProgress.Completed, "Export should complete, got: $last")
        return last.result
    }

    private fun exportResultToBundle(result: ExportResult): RestoreBundle =
        RestoreBundle(
            metadataJson = result.metadata,
            journalsJson = result.journals,
            notesJson = result.notes,
            journalNotesJson = result.journalNotes,
            draftsJson = result.drafts,
            mediaManifestJson = result.mediaManifest,
        )

    private suspend fun exportThenImport(): app.logdate.client.domain.restore.RestoreResult {
        val exported = export()
        val bundle = exportResultToBundle(exported)
        return importUseCase.restore(bundle)
    }

    // endregion

    // region Fake implementations

    /**
     * In-memory journal repository that supports both export-side reads and import-side writes.
     */
    private class RoundTripJournalRepository : JournalRepository {
        private val journalsFlow = MutableStateFlow<List<Journal>>(emptyList())
        private val journals = mutableMapOf<Uuid, Journal>()
        private val drafts = mutableListOf<EditorDraft>()

        var testJournals: List<Journal> = emptyList()
            set(value) {
                field = value
                value.forEach { journals[it.id] = it }
                journalsFlow.value = value
            }

        var testDrafts: List<EditorDraft> = emptyList()
            set(value) {
                field = value
                drafts.clear()
                drafts.addAll(value)
            }

        override val allJournalsObserved: Flow<List<Journal>> = journalsFlow

        override fun observeJournalById(id: Uuid): Flow<Journal> = flowOf(journals[id] ?: Journal(id = id))

        override suspend fun getJournalById(id: Uuid): Journal? = journals[id]

        override suspend fun create(journal: Journal): Uuid {
            journals[journal.id] = journal
            journalsFlow.value = journals.values.toList()
            return journal.id
        }

        override suspend fun update(journal: Journal) {
            journals[journal.id] = journal
            journalsFlow.value = journals.values.toList()
        }

        override suspend fun delete(journalId: Uuid) {
            journals.remove(journalId)
            journalsFlow.value = journals.values.toList()
        }

        override suspend fun saveDraft(draft: EditorDraft) {
            drafts.removeAll { it.id == draft.id }
            drafts.add(draft)
        }

        override suspend fun getLatestDraft(): EditorDraft? = drafts.maxByOrNull { it.lastModifiedAt }

        override suspend fun getAllDrafts(): List<EditorDraft> = drafts.toList()

        override suspend fun getDraft(id: Uuid): EditorDraft? = drafts.find { it.id == id }

        override suspend fun deleteDraft(id: Uuid) {
            drafts.removeAll { it.id == id }
        }
    }

    /**
     * In-memory notes repository that supports both export-side reads and import-side writes.
     */
    private class RoundTripJournalNotesRepository : JournalNotesRepository {
        private val notesFlow = MutableStateFlow<List<JournalNote>>(emptyList())
        private val notes = mutableMapOf<Uuid, JournalNote>()
        var notesByJournal: Map<Uuid, List<JournalNote>> = emptyMap()

        var testNotes: List<JournalNote> = emptyList()
            set(value) {
                field = value
                value.forEach { notes[it.uid] = it }
                notesFlow.value = value
            }

        override val allNotesObserved: Flow<List<JournalNote>> = notesFlow

        override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(notesByJournal[journalId] ?: emptyList())

        override fun observeNotesInRange(
            start: Instant,
            end: Instant,
        ): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeNotesPage(
            pageSize: Int,
            offset: Int,
        ): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = flowOf(emptyList())

        override suspend fun getNoteById(noteId: Uuid): JournalNote? = notes[noteId]

        override suspend fun create(note: JournalNote): Uuid {
            notes[note.uid] = note
            notesFlow.value = notes.values.toList()
            return note.uid
        }

        override suspend fun remove(note: JournalNote) {
            notes.remove(note.uid)
            notesFlow.value = notes.values.toList()
        }

        override suspend fun removeById(noteId: Uuid) {
            notes.remove(noteId)
            notesFlow.value = notes.values.toList()
        }

        override suspend fun create(
            note: JournalNote,
            journalId: Uuid,
        ) {
            notes[note.uid] = note
            notesFlow.value = notes.values.toList()
        }

        override suspend fun removeFromJournal(
            noteId: Uuid,
            journalId: Uuid,
        ) {}
    }

    /**
     * In-memory content repository that tracks journal-note links.
     */
    private class RoundTripJournalContentRepository : JournalContentRepository {
        private val links = mutableListOf<Pair<Uuid, Uuid>>()

        fun allLinks(): List<Pair<Uuid, Uuid>> = links.toList()

        override fun observeContentForJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeJournalsForContent(contentId: Uuid): Flow<List<Journal>> = flowOf(emptyList())

        override suspend fun addContentToJournal(
            contentId: Uuid,
            journalId: Uuid,
        ) {
            links.add(contentId to journalId)
        }

        override suspend fun removeContentFromJournal(
            contentId: Uuid,
            journalId: Uuid,
        ) {
            links.removeAll { it.first == contentId && it.second == journalId }
        }

        override suspend fun addContentToJournals(
            contentId: Uuid,
            journalIds: List<Uuid>,
        ) {
            journalIds.forEach { links.add(contentId to it) }
        }

        override suspend fun removeContentFromAllJournals(contentId: Uuid) {
            links.removeAll { it.first == contentId }
        }
    }

    private class StubDeviceIdProvider(
        initialId: Uuid,
    ) : DeviceIdProvider {
        private val deviceId = MutableStateFlow(initialId)

        override fun getDeviceId(): MutableStateFlow<Uuid> = deviceId

        override suspend fun refreshDeviceId() {}
    }

    private class StubAppInfoProvider(
        private val appInfo: AppInfo,
    ) : AppInfoProvider {
        override fun getAppInfo(): AppInfo = appInfo
    }

    private class StubUserStateRepository : UserStateRepository {
        override val userData: Flow<UserData> = flowOf(UserData())

        override suspend fun setBirthday(birthday: Instant) {}

        override suspend fun setIsOnboardingComplete(isComplete: Boolean) {}

        override suspend fun setBiometricEnabled(isEnabled: Boolean) {}

        override suspend fun addFavoriteNote(vararg noteId: String) {}
    }

    // endregion
}
