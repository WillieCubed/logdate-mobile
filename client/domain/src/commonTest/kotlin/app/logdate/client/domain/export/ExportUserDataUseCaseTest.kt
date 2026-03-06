package app.logdate.client.domain.export

import app.logdate.client.device.AppInfo
import app.logdate.client.device.AppInfoProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.domain.notes.GetAllAudioNotesUseCase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.SerializableAudioBlock
import app.logdate.shared.model.SerializableImageBlock
import app.logdate.shared.model.SerializableTextBlock
import app.logdate.shared.model.SerializableVideoBlock
import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ExportUserDataUseCaseTest {
    private lateinit var mockJournalRepository: MockJournalRepository
    private lateinit var mockNotesRepository: MockJournalNotesRepository
    private lateinit var deviceIdProvider: FakeDeviceIdProvider
    private lateinit var appInfoProvider: FakeAppInfoProvider
    private lateinit var userStateRepository: FakeUserStateRepository
    private lateinit var useCase: ExportUserDataUseCase

    @BeforeTest
    fun setUp() {
        mockJournalRepository = MockJournalRepository()
        mockNotesRepository = MockJournalNotesRepository()
        deviceIdProvider = FakeDeviceIdProvider(Uuid.random())
        appInfoProvider =
            FakeAppInfoProvider(
                AppInfo(
                    versionName = "1.2.3",
                    versionCode = 123,
                    packageName = "app.logdate.test",
                ),
            )
        userStateRepository = FakeUserStateRepository()

        useCase =
            ExportUserDataUseCase(
                journalRepository = mockJournalRepository,
                journalNotesRepository = mockNotesRepository,
                userStateRepository = userStateRepository,
                deviceIdProvider = deviceIdProvider,
                appInfoProvider = appInfoProvider,
                getAllAudioNotesUseCase = GetAllAudioNotesUseCase(mockNotesRepository),
            )
    }

    @Test
    fun `exportUserData emits expected progress updates`() =
        runTest {
            val progressUpdates = useCase.exportUserData().toList()

            assertEquals(6, progressUpdates.size, "Should emit 6 progress updates")
            assertTrue(progressUpdates[0] is ExportProgress.Starting, "First emission should be Starting")

            assertTrue(progressUpdates[1] is ExportProgress.InProgress, "Second emission should be InProgress")
            assertEquals(0.1f, (progressUpdates[1] as ExportProgress.InProgress).percentage)

            assertTrue(progressUpdates[2] is ExportProgress.InProgress, "Third emission should be InProgress")
            assertEquals(0.3f, (progressUpdates[2] as ExportProgress.InProgress).percentage)

            assertTrue(progressUpdates[3] is ExportProgress.InProgress, "Fourth emission should be InProgress")
            assertEquals(0.5f, (progressUpdates[3] as ExportProgress.InProgress).percentage)

            assertTrue(progressUpdates[4] is ExportProgress.InProgress, "Fifth emission should be InProgress")
            assertEquals(0.7f, (progressUpdates[4] as ExportProgress.InProgress).percentage)

            assertTrue(progressUpdates[5] is ExportProgress.Completed, "Final emission should be Completed")
        }

    @Test
    fun `exportUserData includes metadata and media without duplicates`() =
        runTest {
            val testJournal =
                Journal(
                    id = Uuid.random(),
                    title = "Test Journal",
                    description = "Journal description",
                    created = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                )

            val textNote =
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                    content = "Test note content",
                )

            val audioNote =
                JournalNote.Audio(
                    uid = Uuid.random(),
                    creationTimestamp = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                    mediaRef = "file:///audio.m4a",
                )

            mockJournalRepository.testJournals = listOf(testJournal)
            mockNotesRepository.testNotes = listOf(textNote, audioNote)
            mockNotesRepository.notesByJournal = mapOf(testJournal.id to listOf(textNote))

            val progressUpdates = useCase.exportUserData().toList()
            val result = (progressUpdates.last() as ExportProgress.Completed).result

            val json = Json { ignoreUnknownKeys = true }
            val metadata = json.decodeFromString<ExportMetadata>(result.metadata)

            assertEquals(deviceIdProvider.getDeviceId().value.toString(), metadata.userId)
            assertEquals(deviceIdProvider.getDeviceId().value.toString(), metadata.deviceId)
            assertEquals(appInfoProvider.getAppInfo().versionName, metadata.appVersion)
            assertEquals(1, metadata.stats.journalCount)
            assertEquals(2, metadata.stats.noteCount)
            assertEquals(1, metadata.stats.mediaCount)

            val notesPayload = json.decodeFromString<Map<String, List<ExportNote>>>(result.notes)
            val exportedIds = notesPayload.getValue("notes").map { it.id }.toSet()
            assertTrue(exportedIds.contains(textNote.uid.toString()), "Text note should be exported")
            assertTrue(exportedIds.contains(audioNote.uid.toString()), "Audio note should be exported")

            val uniquePaths = result.mediaFiles.map { it.exportPath }.toSet()
            assertEquals(uniquePaths.size, result.mediaFiles.size, "Media export paths should be unique")

            val relationsPayload = json.decodeFromString<Map<String, List<ExportJournalNoteRelation>>>(result.journalNotes)
            val relations = relationsPayload.getValue("journal_notes")
            assertEquals(1, relations.size, "Journal-note relations should be exported")
            assertEquals(testJournal.id.toString(), relations.first().journalId)
            assertEquals(textNote.uid.toString(), relations.first().noteId)
        }

    @Test
    fun `exportUserData handles empty data gracefully`() =
        runTest {
            mockJournalRepository.testJournals = emptyList()
            mockNotesRepository.testNotes = emptyList()

            val progressUpdates = useCase.exportUserData().toList()
            val result = (progressUpdates.last() as ExportProgress.Completed).result

            val json = Json { ignoreUnknownKeys = true }
            val metadata = json.decodeFromString<ExportMetadata>(result.metadata)

            assertEquals(0, metadata.stats.journalCount)
            assertEquals(0, metadata.stats.noteCount)
            assertEquals(0, metadata.stats.mediaCount)

            val notesPayload = json.decodeFromString<Map<String, List<ExportNote>>>(result.notes)
            assertTrue(notesPayload.getValue("notes").isEmpty(), "Notes payload should be empty")

            val relationsPayload = json.decodeFromString<Map<String, List<ExportJournalNoteRelation>>>(result.journalNotes)
            assertTrue(relationsPayload.getValue("journal_notes").isEmpty(), "Journal-note relations should be empty")
        }

    @Test
    fun `exportUserData handles dependency failures`() =
        runTest {
            appInfoProvider.shouldThrow = true

            val progressUpdates = useCase.exportUserData().toList()

            assertTrue(progressUpdates.last() is ExportProgress.Failed, "Final emission should be Failed")
        }

    @Test
    fun `exportUserData includes draft media references`() =
        runTest {
            val now = Clock.System.now()
            val draft =
                EditorDraft(
                    id = Uuid.random(),
                    blocks =
                        listOf(
                            SerializableTextBlock(
                                id = Uuid.random(),
                                timestamp = now,
                                content = "Draft text",
                            ),
                            SerializableImageBlock(
                                id = Uuid.random(),
                                timestamp = now,
                                uri = "file:///image.jpg",
                                caption = "Draft image",
                            ),
                            SerializableAudioBlock(
                                id = Uuid.random(),
                                timestamp = now,
                                uri = "file:///audio.m4a",
                                duration = 1200L,
                            ),
                            SerializableVideoBlock(
                                id = Uuid.random(),
                                timestamp = now,
                                uri = "file:///video.mp4",
                                thumbnailUri = "file:///video-thumb.jpg",
                                caption = "Draft video",
                            ),
                        ),
                )

            mockJournalRepository.testDrafts = listOf(draft)

            val progressUpdates = useCase.exportUserData().toList()
            val result = (progressUpdates.last() as ExportProgress.Completed).result

            val json = Json { ignoreUnknownKeys = true }
            val draftsPayload = json.decodeFromString<Map<String, List<ExportDraft>>>(result.drafts)
            val exportDraft = draftsPayload.getValue("drafts").first()

            assertEquals(draft.id.toString(), exportDraft.id)
            assertTrue(exportDraft.mediaReferences.contains("file:///image.jpg"), "Draft should include image reference")
            assertTrue(exportDraft.mediaReferences.contains("file:///audio.m4a"), "Draft should include audio reference")
            assertTrue(exportDraft.mediaReferences.contains("file:///video.mp4"), "Draft should include video reference")
            assertTrue(exportDraft.mediaReferences.contains("file:///video-thumb.jpg"), "Draft should include video thumbnail reference")
        }

    @Test
    fun `exportUserData excludes journals when includeJournals is false`() =
        runTest {
            val testJournal =
                Journal(
                    id = Uuid.random(),
                    title = "Test Journal",
                    description = "Description",
                    created = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                )
            val textNote =
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                    content = "Note content",
                )

            mockJournalRepository.testJournals = listOf(testJournal)
            mockNotesRepository.testNotes = listOf(textNote)
            mockNotesRepository.notesByJournal = mapOf(testJournal.id to listOf(textNote))

            val progressUpdates = useCase.exportUserData(includeJournals = false).toList()
            val result = (progressUpdates.last() as ExportProgress.Completed).result

            val json = Json { ignoreUnknownKeys = true }
            val metadata = json.decodeFromString<ExportMetadata>(result.metadata)
            assertEquals(0, metadata.stats.journalCount, "Journal count should be 0")

            val relationsPayload = json.decodeFromString<Map<String, List<ExportJournalNoteRelation>>>(result.journalNotes)
            assertTrue(relationsPayload.getValue("journal_notes").isEmpty(), "Journal-note relations should be empty")
        }

    @Test
    fun `exportUserData excludes notes when includeNotes is false`() =
        runTest {
            val textNote =
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                    content = "Note content",
                )

            mockNotesRepository.testNotes = listOf(textNote)

            val progressUpdates = useCase.exportUserData(includeNotes = false).toList()
            val result = (progressUpdates.last() as ExportProgress.Completed).result

            val json = Json { ignoreUnknownKeys = true }
            val notesPayload = json.decodeFromString<Map<String, List<ExportNote>>>(result.notes)
            assertTrue(notesPayload.getValue("notes").isEmpty(), "Notes list should be empty")

            val metadata = json.decodeFromString<ExportMetadata>(result.metadata)
            assertEquals(0, metadata.stats.noteCount, "Note count should be 0")
        }

    @Test
    fun `exportUserData excludes drafts when includeDrafts is false`() =
        runTest {
            val now = Clock.System.now()
            val draft =
                EditorDraft(
                    id = Uuid.random(),
                    blocks =
                        listOf(
                            SerializableTextBlock(
                                id = Uuid.random(),
                                timestamp = now,
                                content = "Draft text",
                            ),
                        ),
                    createdAt = now,
                    lastModifiedAt = now,
                )

            mockJournalRepository.testDrafts = listOf(draft)

            val progressUpdates = useCase.exportUserData(includeDrafts = false).toList()
            val result = (progressUpdates.last() as ExportProgress.Completed).result

            val json = Json { ignoreUnknownKeys = true }
            val draftsPayload = json.decodeFromString<Map<String, List<ExportDraft>>>(result.drafts)
            assertTrue(draftsPayload.getValue("drafts").isEmpty(), "Drafts list should be empty")

            val metadata = json.decodeFromString<ExportMetadata>(result.metadata)
            assertEquals(0, metadata.stats.draftCount, "Draft count should be 0")
        }

    @Test
    fun `exportUserData excludes media when includeMedia is false`() =
        runTest {
            val audioNote =
                JournalNote.Audio(
                    uid = Uuid.random(),
                    creationTimestamp = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                    mediaRef = "file:///audio.m4a",
                )
            val imageNote =
                JournalNote.Image(
                    uid = Uuid.random(),
                    creationTimestamp = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                    mediaRef = "file:///image.jpg",
                )

            mockNotesRepository.testNotes = listOf(audioNote, imageNote)

            val progressUpdates = useCase.exportUserData(includeMedia = false).toList()
            val result = (progressUpdates.last() as ExportProgress.Completed).result

            assertTrue(result.mediaFiles.isEmpty(), "Media files should be empty")

            val json = Json { ignoreUnknownKeys = true }
            val metadata = json.decodeFromString<ExportMetadata>(result.metadata)
            assertEquals(0, metadata.stats.mediaCount, "Media count should be 0")
        }

    @Test
    fun `exportUserData filters notes by date range cutoff`() =
        runTest {
            val now = Clock.System.now()
            val cutoff = now - 30.days

            val oldNote =
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = now - 60.days,
                    lastUpdated = now - 60.days,
                    content = "Old note",
                )
            val recentNote =
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = now - 10.days,
                    lastUpdated = now - 10.days,
                    content = "Recent note",
                )

            mockNotesRepository.testNotes = listOf(oldNote, recentNote)

            val progressUpdates = useCase.exportUserData(dateRangeCutoff = cutoff).toList()
            val result = (progressUpdates.last() as ExportProgress.Completed).result

            val json = Json { ignoreUnknownKeys = true }
            val notesPayload = json.decodeFromString<Map<String, List<ExportNote>>>(result.notes)
            val exportedNotes = notesPayload.getValue("notes")

            assertEquals(1, exportedNotes.size, "Only one note should pass the cutoff filter")
            assertEquals(recentNote.uid.toString(), exportedNotes.first().id, "Only the recent note should be exported")
        }

    @Test
    fun `exportUserData filters drafts by date range cutoff`() =
        runTest {
            val now = Clock.System.now()
            val cutoff = now - 30.days

            val oldDraft =
                EditorDraft(
                    id = Uuid.random(),
                    blocks =
                        listOf(
                            SerializableTextBlock(
                                id = Uuid.random(),
                                timestamp = now - 60.days,
                                content = "Old draft",
                            ),
                        ),
                    createdAt = now - 60.days,
                    lastModifiedAt = now - 60.days,
                )
            val recentDraft =
                EditorDraft(
                    id = Uuid.random(),
                    blocks =
                        listOf(
                            SerializableTextBlock(
                                id = Uuid.random(),
                                timestamp = now - 10.days,
                                content = "Recent draft",
                            ),
                        ),
                    createdAt = now - 10.days,
                    lastModifiedAt = now - 10.days,
                )

            mockJournalRepository.testDrafts = listOf(oldDraft, recentDraft)

            val progressUpdates = useCase.exportUserData(dateRangeCutoff = cutoff).toList()
            val result = (progressUpdates.last() as ExportProgress.Completed).result

            val json = Json { ignoreUnknownKeys = true }
            val draftsPayload = json.decodeFromString<Map<String, List<ExportDraft>>>(result.drafts)
            val exportedDrafts = draftsPayload.getValue("drafts")

            assertEquals(1, exportedDrafts.size, "Only one draft should pass the cutoff filter")
            assertEquals(recentDraft.id.toString(), exportedDrafts.first().id, "Only the recent draft should be exported")
        }

    @Test
    fun `exportUserData includes journals regardless of date range cutoff`() =
        runTest {
            val now = Clock.System.now()
            val cutoff = now - 30.days

            val oldJournal =
                Journal(
                    id = Uuid.random(),
                    title = "Old Journal",
                    description = "Created long ago",
                    created = now - 365.days,
                    lastUpdated = now - 365.days,
                )
            val recentJournal =
                Journal(
                    id = Uuid.random(),
                    title = "Recent Journal",
                    description = "Created recently",
                    created = now - 5.days,
                    lastUpdated = now - 5.days,
                )

            mockJournalRepository.testJournals = listOf(oldJournal, recentJournal)

            val progressUpdates = useCase.exportUserData(dateRangeCutoff = cutoff).toList()
            val result = (progressUpdates.last() as ExportProgress.Completed).result

            val json = Json { ignoreUnknownKeys = true }
            val metadata = json.decodeFromString<ExportMetadata>(result.metadata)
            assertEquals(2, metadata.stats.journalCount, "Both journals should be exported regardless of cutoff")
        }

    @Test
    fun `exportUserData filters journal-note relations by date range cutoff`() =
        runTest {
            val now = Clock.System.now()
            val cutoff = now - 30.days

            val journal =
                Journal(
                    id = Uuid.random(),
                    title = "Test Journal",
                    description = "Description",
                    created = now - 90.days,
                    lastUpdated = now,
                )

            val oldNote =
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = now - 60.days,
                    lastUpdated = now - 60.days,
                    content = "Old note in journal",
                )
            val recentNote =
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = now - 10.days,
                    lastUpdated = now - 10.days,
                    content = "Recent note in journal",
                )

            mockJournalRepository.testJournals = listOf(journal)
            mockNotesRepository.testNotes = listOf(oldNote, recentNote)
            mockNotesRepository.notesByJournal = mapOf(journal.id to listOf(oldNote, recentNote))

            val progressUpdates = useCase.exportUserData(dateRangeCutoff = cutoff).toList()
            val result = (progressUpdates.last() as ExportProgress.Completed).result

            val json = Json { ignoreUnknownKeys = true }
            val relationsPayload = json.decodeFromString<Map<String, List<ExportJournalNoteRelation>>>(result.journalNotes)
            val relations = relationsPayload.getValue("journal_notes")

            assertEquals(1, relations.size, "Only one relation should pass the cutoff filter")
            assertEquals(recentNote.uid.toString(), relations.first().noteId, "Only the recent note relation should be exported")
        }

    @Test
    fun `exportUserData with all categories disabled exports only metadata`() =
        runTest {
            val testJournal =
                Journal(
                    id = Uuid.random(),
                    title = "Test Journal",
                    description = "Description",
                    created = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                )
            val textNote =
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                    content = "Note content",
                )

            mockJournalRepository.testJournals = listOf(testJournal)
            mockNotesRepository.testNotes = listOf(textNote)

            val progressUpdates =
                useCase
                    .exportUserData(
                        includeJournals = false,
                        includeNotes = false,
                        includeDrafts = false,
                        includeMedia = false,
                    ).toList()
            val result = (progressUpdates.last() as ExportProgress.Completed).result

            val json = Json { ignoreUnknownKeys = true }
            val metadata = json.decodeFromString<ExportMetadata>(result.metadata)
            assertEquals(0, metadata.stats.journalCount, "Journal count should be 0")
            assertEquals(0, metadata.stats.noteCount, "Note count should be 0")
            assertEquals(0, metadata.stats.draftCount, "Draft count should be 0")
            assertEquals(0, metadata.stats.mediaCount, "Media count should be 0")

            val notesPayload = json.decodeFromString<Map<String, List<ExportNote>>>(result.notes)
            assertTrue(notesPayload.getValue("notes").isEmpty(), "Notes should be empty")

            val draftsPayload = json.decodeFromString<Map<String, List<ExportDraft>>>(result.drafts)
            assertTrue(draftsPayload.getValue("drafts").isEmpty(), "Drafts should be empty")

            assertTrue(result.mediaFiles.isEmpty(), "Media files should be empty")

            // Metadata should still be valid
            assertTrue(metadata.userId.isNotEmpty(), "User ID should be present in metadata")
            assertTrue(metadata.appVersion.isNotEmpty(), "App version should be present in metadata")
        }

    @Test
    fun `exportUserData stats reflect filtered counts`() =
        runTest {
            val now = Clock.System.now()
            val cutoff = now - 30.days

            val oldNote =
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = now - 60.days,
                    lastUpdated = now - 60.days,
                    content = "Old note",
                )
            val recentNote1 =
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = now - 10.days,
                    lastUpdated = now - 10.days,
                    content = "Recent note 1",
                )
            val recentNote2 =
                JournalNote.Audio(
                    uid = Uuid.random(),
                    creationTimestamp = now - 5.days,
                    lastUpdated = now - 5.days,
                    mediaRef = "file:///recent-audio.m4a",
                )

            val oldDraft =
                EditorDraft(
                    id = Uuid.random(),
                    blocks =
                        listOf(
                            SerializableTextBlock(
                                id = Uuid.random(),
                                timestamp = now - 60.days,
                                content = "Old draft",
                            ),
                        ),
                    createdAt = now - 60.days,
                    lastModifiedAt = now - 60.days,
                )
            val recentDraft =
                EditorDraft(
                    id = Uuid.random(),
                    blocks =
                        listOf(
                            SerializableTextBlock(
                                id = Uuid.random(),
                                timestamp = now - 5.days,
                                content = "Recent draft",
                            ),
                        ),
                    createdAt = now - 5.days,
                    lastModifiedAt = now - 5.days,
                )

            val journal =
                Journal(
                    id = Uuid.random(),
                    title = "Test Journal",
                    description = "Description",
                    created = now - 90.days,
                    lastUpdated = now,
                )

            mockJournalRepository.testJournals = listOf(journal)
            mockNotesRepository.testNotes = listOf(oldNote, recentNote1, recentNote2)
            mockJournalRepository.testDrafts = listOf(oldDraft, recentDraft)

            val progressUpdates = useCase.exportUserData(dateRangeCutoff = cutoff).toList()
            val result = (progressUpdates.last() as ExportProgress.Completed).result

            val json = Json { ignoreUnknownKeys = true }
            val metadata = json.decodeFromString<ExportMetadata>(result.metadata)

            assertEquals(1, metadata.stats.journalCount, "Journal count should include all journals")
            assertEquals(2, metadata.stats.noteCount, "Note count should reflect only filtered notes")
            assertEquals(1, metadata.stats.draftCount, "Draft count should reflect only filtered drafts")
        }

    private class FakeUserStateRepository : UserStateRepository {
        override val userData: Flow<UserData> = flowOf(UserData())

        override suspend fun setBirthday(birthday: Instant) {}

        override suspend fun setIsOnboardingComplete(isComplete: Boolean) {}

        override suspend fun setBiometricEnabled(isEnabled: Boolean) {}

        override suspend fun addFavoriteNote(vararg noteId: String) {}
    }

    private class FakeDeviceIdProvider(
        initialId: Uuid,
    ) : DeviceIdProvider {
        private val deviceId = MutableStateFlow(initialId)

        override fun getDeviceId(): MutableStateFlow<Uuid> = deviceId

        override suspend fun refreshDeviceId() {}
    }

    private class FakeAppInfoProvider(
        private val appInfo: AppInfo,
    ) : AppInfoProvider {
        var shouldThrow: Boolean = false

        override fun getAppInfo(): AppInfo {
            if (shouldThrow) {
                throw RuntimeException("App info unavailable")
            }
            return appInfo
        }
    }

    private class MockJournalRepository : JournalRepository {
        private val journalsFlow = MutableStateFlow<List<Journal>>(emptyList())
        var testJournals: List<Journal> = emptyList()
            set(value) {
                field = value
                journalsFlow.value = value
            }
        var testDrafts: List<EditorDraft> = emptyList()

        override val allJournalsObserved: Flow<List<Journal>> = journalsFlow

        override suspend fun getJournalById(id: Uuid): Journal? = testJournals.find { it.id == id }

        override suspend fun create(journal: Journal): Uuid = journal.id

        override suspend fun update(journal: Journal) {}

        override suspend fun delete(journalId: Uuid) {}

        override fun observeJournalById(id: Uuid): Flow<Journal> = flowOf(testJournals.firstOrNull() ?: Journal(id = id))

        override suspend fun saveDraft(draft: app.logdate.shared.model.EditorDraft) {}

        override suspend fun getLatestDraft(): app.logdate.shared.model.EditorDraft? = null

        override suspend fun getAllDrafts(): List<app.logdate.shared.model.EditorDraft> = testDrafts

        override suspend fun getDraft(id: Uuid): app.logdate.shared.model.EditorDraft? = null

        override suspend fun deleteDraft(id: Uuid) {}
    }

    private class MockJournalNotesRepository : JournalNotesRepository {
        private val notesFlow = MutableStateFlow<List<JournalNote>>(emptyList())
        var testNotes: List<JournalNote> = emptyList()
            set(value) {
                field = value
                notesFlow.value = value
            }
        var notesByJournal: Map<Uuid, List<JournalNote>> = emptyMap()

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

        override suspend fun create(note: JournalNote): Uuid = note.uid

        override suspend fun remove(note: JournalNote) {}

        override suspend fun removeById(noteId: Uuid) {}

        override suspend fun create(
            note: JournalNote,
            journalId: Uuid,
        ) {}

        override suspend fun removeFromJournal(
            noteId: Uuid,
            journalId: Uuid,
        ) {}

        override suspend fun getNoteById(noteId: Uuid): JournalNote? = null
    }
}
