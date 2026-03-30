package app.logdate.client.domain.export

import app.logdate.client.device.AppInfo
import app.logdate.client.device.AppInfoProvider
import app.logdate.client.device.identity.DeviceIdProvider
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
import app.logdate.client.repository.location.LocationCapturePipeline
import app.logdate.client.repository.location.LocationCaptureSource
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.location.LocationLogRecord
import app.logdate.client.repository.places.UserPlacesRepository
import app.logdate.client.repository.profile.ProfileRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import app.logdate.shared.model.Place
import app.logdate.shared.model.SerializableAudioBlock
import app.logdate.shared.model.SerializableCameraBlock
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.SerializableVideoBlock
import app.logdate.shared.model.profile.LogDateProfile
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
    private lateinit var sourceProfileRepo: RoundTripProfileRepository
    private lateinit var sourcePlacesRepo: RoundTripUserPlacesRepository
    private lateinit var sourceLocationHistoryRepo: RoundTripLocationHistoryRepository

    // Destination repositories (empty, used by import)
    private lateinit var destJournalRepo: RoundTripJournalRepository
    private lateinit var destNotesRepo: RoundTripJournalNotesRepository
    private lateinit var destContentRepo: RoundTripJournalContentRepository
    private lateinit var destProfileRepo: RoundTripProfileRepository
    private lateinit var destPlacesRepo: RoundTripUserPlacesRepository
    private lateinit var destLocationHistoryRepo: RoundTripLocationHistoryRepository

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
        sourceProfileRepo = RoundTripProfileRepository()
        sourcePlacesRepo = RoundTripUserPlacesRepository()
        sourceLocationHistoryRepo = RoundTripLocationHistoryRepository()

        destJournalRepo = RoundTripJournalRepository()
        destNotesRepo = RoundTripJournalNotesRepository()
        destContentRepo = RoundTripJournalContentRepository()
        destProfileRepo = RoundTripProfileRepository()
        destPlacesRepo = RoundTripUserPlacesRepository()
        destLocationHistoryRepo = RoundTripLocationHistoryRepository()

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
                profileRepository = sourceProfileRepo,
                userPlacesRepository = sourcePlacesRepo,
                locationHistoryRepository = sourceLocationHistoryRepo,
                userStateRepository = StubUserStateRepository(),
                deviceIdProvider = deviceIdProvider,
                appInfoProvider = appInfoProvider,
            )

        importUseCase =
            RestoreUserDataUseCase(
                journalRepository = destJournalRepo,
                journalNotesRepository = destNotesRepo,
                journalContentRepository = destContentRepo,
                profileRepository = destProfileRepo,
                userPlacesRepository = destPlacesRepo,
                locationHistoryRepository = destLocationHistoryRepo,
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
            assertTrue(draft.blocks.any { it is SerializableImageBlock }, "Image draft block should be preserved")
            assertTrue(draft.blocks.any { it is SerializableVideoBlock }, "Video draft block should be preserved")
            assertTrue(draft.blocks.any { it is SerializableAudioBlock }, "Audio draft block should be preserved")
        }

    @Test
    fun `full round-trip preserves note sync versions`() =
        runTest {
            seedSourceData()
            exportThenImport()

            val text = destNotesRepo.getNoteById(noteText)
            assertTrue(text is JournalNote.Text)
            assertEquals(7, text.syncVersion)

            val image = destNotesRepo.getNoteById(noteImage)
            assertTrue(image is JournalNote.Image)
            assertEquals(11, image.syncVersion)
        }

    @Test
    fun `full round-trip metadata counts match source data`() =
        runTest {
            seedSourceData()
            val result = exportThenImport()

            assertEquals(2, result.metadata.stats.journalCount)
            assertEquals(5, result.metadata.stats.noteCount)
            assertEquals(1, result.metadata.stats.draftCount)
            assertEquals(1, result.metadata.stats.placeCount)
            assertEquals(1, result.metadata.stats.locationHistoryCount)
            assertTrue(result.metadata.stats.hasProfile)
            assertTrue(result.warnings.isEmpty(), "Round-trip should produce no warnings, got: ${result.warnings}")
        }

    @Test
    fun `full round-trip preserves profile places and location history`() =
        runTest {
            seedSourceData()
            exportThenImport()

            assertEquals("Willie", destProfileRepo.profile.displayName)
            assertEquals(1, destPlacesRepo.places.size)
            assertEquals("Home Base", (destPlacesRepo.places.first() as Place.UserDefined).displayName)
            assertEquals(1, destLocationHistoryRepo.entries.size)
            assertEquals("sample-1", destLocationHistoryRepo.entries.first().sampleId)
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

    @Test
    fun `round-trip preserves audio note duration`() =
        runTest {
            seedSourceData()
            exportThenImport()

            val audio = destNotesRepo.getNoteById(noteAudio)
            assertTrue(audio is JournalNote.Audio, "Should be audio note")
            assertEquals(45000L, audio.durationMs, "Audio duration should survive round-trip")
        }

    @Test
    fun `round-trip preserves location altitude and accuracy`() =
        runTest {
            seedSourceData()
            exportThenImport()

            val note = destNotesRepo.getNoteById(noteTextWithLocation)
            assertTrue(note is JournalNote.Text, "Should be text note")
            val coords = note.location?.coordinates
            assertNotNull(coords, "Coordinates should be preserved")
            assertEquals(52.3, coords.altitude, "Altitude should survive round-trip")
            assertEquals(8.5f, coords.accuracy, "Accuracy should survive round-trip")
        }

    @Test
    fun `round-trip preserves draft with multiple journal associations`() =
        runTest {
            seedSourceData()
            exportThenImport()

            val drafts = destJournalRepo.getAllDrafts()
            assertEquals(1, drafts.size, "Should have one draft")
            val draft = drafts.first()
            assertEquals(2, draft.selectedJournalIds.size, "Draft should keep both journal IDs")
            assertTrue(draft.selectedJournalIds.contains(journalDaily))
            assertTrue(draft.selectedJournalIds.contains(journalTravel))
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
                coordinates =
                    NoteCoordinates(
                        latitude = 37.7749,
                        longitude = -122.4194,
                        altitude = 52.3,
                        accuracy = 8.5f,
                    ),
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
                syncVersion = 7,
            )
        val imageNote =
            JournalNote.Image(
                uid = noteImage,
                creationTimestamp = now - 5.days,
                lastUpdated = now - 5.days,
                mediaRef = "file:///storage/photos/sunset.jpg",
                caption = "Golden hour at Ocean Beach",
                location = laLocation,
                syncVersion = 11,
            )
        val videoNote =
            JournalNote.Video(
                uid = noteVideo,
                creationTimestamp = now - 3.days,
                lastUpdated = now - 3.days,
                mediaRef = "file:///storage/videos/waves.mp4",
                caption = "Waves crashing on the shore",
                syncVersion = 13,
            )
        val audioNote =
            JournalNote.Audio(
                uid = noteAudio,
                creationTimestamp = now - 2.days,
                lastUpdated = now - 2.days,
                mediaRef = "file:///storage/audio/voice_memo.m4a",
                durationMs = 45000,
                syncVersion = 17,
            )
        val textWithLocationNote =
            JournalNote.Text(
                uid = noteTextWithLocation,
                creationTimestamp = now - 1.days,
                lastUpdated = now - 1.days,
                content = "Sitting in Dolores Park writing in my journal",
                location = sfLocation,
                syncVersion = 19,
            )

        sourceNotesRepo.testNotes = listOf(textNote, imageNote, videoNote, audioNote, textWithLocationNote)
        sourceNotesRepo.notesByJournal =
            mapOf(
                journalDaily to listOf(textNote, audioNote, textWithLocationNote),
                journalTravel to listOf(imageNote, videoNote, textWithLocationNote),
            )
        sourceProfileRepo.profile =
            LogDateProfile(
                displayName = "Willie",
                bio = "Keeps travel journals",
                createdAt = now - 30.days,
                lastUpdatedAt = now - 1.days,
            )
        sourcePlacesRepo.places =
            listOf(
                Place.UserDefined(
                    id = Uuid.random(),
                    displayName = "Home Base",
                    lat = 37.7749,
                    lng = -122.4194,
                    radiusMeters = 150.0,
                    description = "Apartment",
                ),
            )
        sourceLocationHistoryRepo.entries =
            listOf(
                LocationHistoryItem(
                    sampleId = "sample-1",
                    userId = "test-user",
                    deviceId = "device-1",
                    timestamp = now - 1.days,
                    loggedAt = now - 1.days,
                    location =
                        Location(
                            latitude = 37.7749,
                            longitude = -122.4194,
                            altitude = LocationAltitude(12.0, AltitudeUnit.METERS),
                        ),
                    confidence = 0.85f,
                    isGenuine = true,
                    capturePipeline = LocationCapturePipeline.HIGH_DETAIL,
                    captureSource = LocationCaptureSource.MANUAL,
                    accuracyMeters = 6.0f,
                ),
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
                        SerializableImageBlock(
                            id = Uuid.random(),
                            timestamp = now,
                            uri = "file:///storage/photos/draft_image.jpg",
                            caption = "Draft image",
                        ),
                        SerializableVideoBlock(
                            id = Uuid.random(),
                            timestamp = now,
                            uri = "file:///storage/videos/draft_video.mp4",
                            thumbnailUri = "file:///storage/videos/draft_video_thumb.jpg",
                            caption = "Draft video",
                        ),
                        SerializableAudioBlock(
                            id = Uuid.random(),
                            timestamp = now,
                            uri = "file:///storage/audio/draft_audio.m4a",
                            duration = 1234L,
                            transcription = "draft audio",
                        ),
                    ),
                selectedJournalIds = listOf(journalDaily, journalTravel),
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
            profileJson = result.profile,
            placesJson = result.places,
            locationHistoryJson = result.locationHistory,
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

        override fun observeJournalsForContents(contentIds: Set<Uuid>): Flow<Map<Uuid, List<Journal>>> = flowOf(emptyMap())
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

    private class RoundTripProfileRepository : ProfileRepository {
        var profile: LogDateProfile = LogDateProfile()

        override val currentProfile: Flow<LogDateProfile> = flowOf(profile)

        override suspend fun updateDisplayName(displayName: String): Result<LogDateProfile> {
            profile = profile.copy(displayName = displayName)
            return Result.success(profile)
        }

        override suspend fun updateBirthday(birthday: Instant?): Result<LogDateProfile> {
            profile = profile.copy(birthday = birthday)
            return Result.success(profile)
        }

        override suspend fun updateProfilePhoto(profilePhotoUri: String?): Result<LogDateProfile> {
            profile = profile.copy(profilePhotoUri = profilePhotoUri)
            return Result.success(profile)
        }

        override suspend fun updateBio(
            bio: String?,
            originalBio: String?,
        ): Result<LogDateProfile> {
            profile = profile.copy(bio = bio, originalBio = originalBio)
            return Result.success(profile)
        }

        override suspend fun getCurrentProfile(): LogDateProfile = profile

        override suspend fun clearProfile(): Result<Unit> {
            profile = LogDateProfile()
            return Result.success(Unit)
        }
    }

    private class RoundTripUserPlacesRepository : UserPlacesRepository {
        var places: List<Place> = emptyList()

        override suspend fun getAllPlaces(): List<Place> = places

        override fun observeAllPlaces(): Flow<List<Place>> = flowOf(places)

        override suspend fun getPlacesNear(
            latitude: Double,
            longitude: Double,
            radiusMeters: Double,
        ): List<Place> = places

        override suspend fun getPlaceById(placeId: String): Place? = places.find { it.uid.toString() == placeId }

        override suspend fun createPlace(place: Place): Result<Place> {
            places = places + place
            return Result.success(place)
        }

        override suspend fun updatePlace(place: Place): Result<Place> {
            places = places.filterNot { it.uid == place.uid } + place
            return Result.success(place)
        }

        override suspend fun deletePlace(placeId: String): Result<Unit> {
            places = places.filterNot { it.uid.toString() == placeId }
            return Result.success(Unit)
        }

        override suspend fun searchPlaces(query: String): List<Place> = places.filter { it.name.contains(query, ignoreCase = true) }
    }

    private class RoundTripLocationHistoryRepository : LocationHistoryRepository {
        var entries: List<LocationHistoryItem> = emptyList()

        override suspend fun getAllLocationHistory(): List<LocationHistoryItem> = entries

        override fun observeLocationHistory(): Flow<List<LocationHistoryItem>> = flowOf(entries)

        override suspend fun getRecentLocationHistory(limit: Int): List<LocationHistoryItem> = entries.take(limit)

        override suspend fun getLocationHistoryBetween(
            startTime: Instant,
            endTime: Instant,
        ): List<LocationHistoryItem> = entries.filter { it.timestamp in startTime..endTime }

        override suspend fun getLastLocation(): LocationHistoryItem? = entries.maxByOrNull { it.timestamp }

        override fun observeLastLocation(): Flow<LocationHistoryItem?> = flowOf(entries.maxByOrNull { it.timestamp })

        override suspend fun logLocation(
            location: Location,
            userId: String,
            deviceId: String,
            confidence: Float,
            isGenuine: Boolean,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun logLocation(record: LocationLogRecord): Result<Unit> {
            entries =
                entries +
                LocationHistoryItem(
                    sampleId = record.sampleId,
                    userId = record.userId,
                    deviceId = record.deviceId,
                    timestamp = record.timestamp,
                    loggedAt = record.loggedAt,
                    location = record.location,
                    confidence = record.confidence,
                    isGenuine = record.isGenuine,
                    capturePipeline = record.capturePipeline,
                    captureSource = record.captureSource,
                    accuracyMeters = record.accuracyMeters,
                    speedMetersPerSecond = record.speedMetersPerSecond,
                    bearingDegrees = record.bearingDegrees,
                    isMock = record.isMock,
                )
            return Result.success(Unit)
        }

        override suspend fun deleteLocationEntry(
            userId: String,
            deviceId: String,
            timestamp: Instant,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun deleteLocationsBetween(
            startTime: Instant,
            endTime: Instant,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun getLocationCount(): Int = entries.size
    }

    // endregion
}
