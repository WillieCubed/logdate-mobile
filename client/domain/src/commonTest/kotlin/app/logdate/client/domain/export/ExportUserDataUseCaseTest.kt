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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
        appInfoProvider = FakeAppInfoProvider(
            AppInfo(
                versionName = "1.2.3",
                versionCode = 123,
                packageName = "app.logdate.test"
            )
        )
        userStateRepository = FakeUserStateRepository()

        useCase = ExportUserDataUseCase(
            journalRepository = mockJournalRepository,
            journalNotesRepository = mockNotesRepository,
            userStateRepository = userStateRepository,
            deviceIdProvider = deviceIdProvider,
            appInfoProvider = appInfoProvider,
            getAllAudioNotesUseCase = GetAllAudioNotesUseCase(mockNotesRepository)
        )
    }

    @Test
    fun `exportUserData emits expected progress updates`() = runTest {
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
    fun `exportUserData includes metadata and media without duplicates`() = runTest {
        val testJournal = Journal(
            id = Uuid.random(),
            title = "Test Journal",
            description = "Journal description",
            created = Clock.System.now(),
            lastUpdated = Clock.System.now()
        )

        val textNote = JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = Clock.System.now(),
            lastUpdated = Clock.System.now(),
            content = "Test note content"
        )

        val audioNote = JournalNote.Audio(
            uid = Uuid.random(),
            creationTimestamp = Clock.System.now(),
            lastUpdated = Clock.System.now(),
            mediaRef = "file:///audio.m4a"
        )

        mockJournalRepository.testJournals = listOf(testJournal)
        mockNotesRepository.testNotes = listOf(textNote, audioNote)

        val progressUpdates = useCase.exportUserData().toList()
        val result = (progressUpdates.last() as ExportProgress.Completed).result

        val json = Json { ignoreUnknownKeys = true }
        val metadata = json.decodeFromString<ExportUserDataUseCase.ExportMetadata>(result.metadata)

        assertEquals(deviceIdProvider.getDeviceId().value.toString(), metadata.userId)
        assertEquals(deviceIdProvider.getDeviceId().value.toString(), metadata.deviceId)
        assertEquals(appInfoProvider.getAppInfo().versionName, metadata.appVersion)
        assertEquals(1, metadata.stats.journalCount)
        assertEquals(2, metadata.stats.noteCount)
        assertEquals(1, metadata.stats.mediaCount)

        val notesPayload = json.decodeFromString<Map<String, List<ExportUserDataUseCase.ExportNote>>>(result.notes)
        val exportedIds = notesPayload.getValue("notes").map { it.id }.toSet()
        assertTrue(exportedIds.contains(textNote.uid.toString()), "Text note should be exported")
        assertTrue(exportedIds.contains(audioNote.uid.toString()), "Audio note should be exported")

        val uniquePaths = result.mediaFiles.map { it.exportPath }.toSet()
        assertEquals(uniquePaths.size, result.mediaFiles.size, "Media export paths should be unique")
    }

    @Test
    fun `exportUserData handles empty data gracefully`() = runTest {
        mockJournalRepository.testJournals = emptyList()
        mockNotesRepository.testNotes = emptyList()

        val progressUpdates = useCase.exportUserData().toList()
        val result = (progressUpdates.last() as ExportProgress.Completed).result

        val json = Json { ignoreUnknownKeys = true }
        val metadata = json.decodeFromString<ExportUserDataUseCase.ExportMetadata>(result.metadata)

        assertEquals(0, metadata.stats.journalCount)
        assertEquals(0, metadata.stats.noteCount)
        assertEquals(0, metadata.stats.mediaCount)

        val notesPayload = json.decodeFromString<Map<String, List<ExportUserDataUseCase.ExportNote>>>(result.notes)
        assertTrue(notesPayload.getValue("notes").isEmpty(), "Notes payload should be empty")
    }

    @Test
    fun `exportUserData handles dependency failures`() = runTest {
        appInfoProvider.shouldThrow = true

        val progressUpdates = useCase.exportUserData().toList()

        assertTrue(progressUpdates.last() is ExportProgress.Failed, "Final emission should be Failed")
    }

    @Test
    fun `exportUserData includes draft media references`() = runTest {
        val now = Clock.System.now()
        val draft = EditorDraft(
            id = Uuid.random(),
            blocks = listOf(
                SerializableTextBlock(
                    id = Uuid.random(),
                    timestamp = now,
                    content = "Draft text"
                ),
                SerializableImageBlock(
                    id = Uuid.random(),
                    timestamp = now,
                    uri = "file:///image.jpg",
                    caption = "Draft image"
                ),
                SerializableAudioBlock(
                    id = Uuid.random(),
                    timestamp = now,
                    uri = "file:///audio.m4a",
                    duration = 1200L
                ),
                SerializableVideoBlock(
                    id = Uuid.random(),
                    timestamp = now,
                    uri = "file:///video.mp4",
                    thumbnailUri = "file:///video-thumb.jpg",
                    caption = "Draft video"
                )
            )
        )

        mockJournalRepository.testDrafts = listOf(draft)

        val progressUpdates = useCase.exportUserData().toList()
        val result = (progressUpdates.last() as ExportProgress.Completed).result

        val json = Json { ignoreUnknownKeys = true }
        val draftsPayload = json.decodeFromString<Map<String, List<ExportUserDataUseCase.ExportDraft>>>(result.drafts)
        val exportDraft = draftsPayload.getValue("drafts").first()

        assertEquals(draft.id.toString(), exportDraft.id)
        assertTrue(exportDraft.mediaReferences.contains("file:///image.jpg"), "Draft should include image reference")
        assertTrue(exportDraft.mediaReferences.contains("file:///audio.m4a"), "Draft should include audio reference")
        assertTrue(exportDraft.mediaReferences.contains("file:///video.mp4"), "Draft should include video reference")
        assertTrue(exportDraft.mediaReferences.contains("file:///video-thumb.jpg"), "Draft should include video thumbnail reference")
    }

    private class FakeUserStateRepository : UserStateRepository {
        override val userData: Flow<UserData> = flowOf(UserData())

        override suspend fun setBirthday(birthday: Instant) {}
        override suspend fun setIsOnboardingComplete(isComplete: Boolean) {}
        override suspend fun setBiometricEnabled(isEnabled: Boolean) {}
        override suspend fun addFavoriteNote(vararg noteId: String) {}
    }

    private class FakeDeviceIdProvider(initialId: Uuid) : DeviceIdProvider {
        private val deviceId = MutableStateFlow(initialId)

        override fun getDeviceId(): MutableStateFlow<Uuid> = deviceId
        override suspend fun refreshDeviceId() {}
    }

    private class FakeAppInfoProvider(private val appInfo: AppInfo) : AppInfoProvider {
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
        override fun observeJournalById(id: Uuid): Flow<Journal> =
            flowOf(testJournals.firstOrNull() ?: Journal(id = id))

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

        override val allNotesObserved: Flow<List<JournalNote>> = notesFlow

        override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())
        override fun observeNotesInRange(start: Instant, end: Instant): Flow<List<JournalNote>> = flowOf(emptyList())
        override fun observeNotesPage(pageSize: Int, offset: Int): Flow<List<JournalNote>> = flowOf(emptyList())
        override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = flowOf(emptyList())
        override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = flowOf(emptyList())
        override suspend fun create(note: JournalNote): Uuid = note.uid
        override suspend fun remove(note: JournalNote) {}
        override suspend fun removeById(noteId: Uuid) {}
        override suspend fun create(note: JournalNote, journalId: Uuid) {}
        override suspend fun removeFromJournal(noteId: Uuid, journalId: Uuid) {}
    }
}
