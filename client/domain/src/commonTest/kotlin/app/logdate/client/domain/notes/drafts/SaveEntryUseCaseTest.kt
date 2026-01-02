package app.logdate.client.domain.notes.drafts

import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.domain.location.LocationRetryWorker
import app.logdate.client.domain.location.LogCurrentLocationUseCase
import app.logdate.client.domain.world.LogLocationUseCase
import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaObject
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.timeline.ActivityTimelineRepository
import app.logdate.shared.model.ActivityTimelineItem
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import app.logdate.shared.model.AltitudeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SaveEntryUseCaseTest {

    private lateinit var mockDraftRepository: MockEntryDraftRepository
    private lateinit var notesRepository: RecordingJournalNotesRepository
    private lateinit var useCase: SaveEntryUseCase

    @BeforeTest
    fun setUp() {
        mockDraftRepository = MockEntryDraftRepository()
        notesRepository = RecordingJournalNotesRepository()
        val addNoteUseCase = buildAddNoteUseCase(notesRepository)
        useCase = SaveEntryUseCase(
            draftRepository = mockDraftRepository,
            addNotes = addNoteUseCase
        )
    }

    @Test
    fun `invoke should add notes from draft and return empty list`() = runTest {
        // Given
        val testNotes = listOf(
            createTestNote("First note"),
            createTestNote("Second note"),
            createTestNote("Third note")
        )
        val testDraft = createTestDraft(notes = testNotes)
        
        // When
        val result = useCase(testDraft)
        
        // Then
        assertTrue(result.isEmpty())
        val expectedNotes: List<JournalNote> = testNotes
        assertEquals(expectedNotes, notesRepository.createdNotes)
    }

    @Test
    fun `invoke should handle draft with single note`() = runTest {
        // Given
        val singleNote = createTestNote("Single note")
        val testDraft = createTestDraft(notes = listOf(singleNote))
        
        // When
        val result = useCase(testDraft)
        
        // Then
        assertTrue(result.isEmpty())
        val expectedNotes: List<JournalNote> = listOf(singleNote)
        assertEquals(expectedNotes, notesRepository.createdNotes)
    }

    @Test
    fun `invoke should handle draft with empty notes`() = runTest {
        // Given
        val testDraft = createTestDraft(notes = emptyList())
        
        // When
        val result = useCase(testDraft)
        
        // Then
        assertTrue(result.isEmpty())
        assertEquals(emptyList<JournalNote>(), notesRepository.createdNotes)
    }

    @Test
    fun `invoke should handle multiple save operations`() = runTest {
        // Given
        val draft1 = createTestDraft(notes = listOf(createTestNote("Draft 1")))
        val draft2 = createTestDraft(notes = listOf(createTestNote("Draft 2")))
        
        // When
        val result1 = useCase(draft1)
        val result2 = useCase(draft2)
        
        // Then
        assertTrue(result1.isEmpty())
        assertTrue(result2.isEmpty())
        assertEquals(2, notesRepository.createdNotes.size)
    }

    @Test
    fun `invoke should handle AddNoteUseCase errors gracefully`() = runTest {
        // Given
        val testDraft = createTestDraft(notes = listOf(createTestNote("Test note")))
        notesRepository.shouldThrowException = true
        
        // When/Then
        try {
            useCase(testDraft)
            kotlin.test.fail("Expected exception was not thrown")
        } catch (e: Exception) {
            assertEquals("AddNote failed", e.message)
        }
    }

    private fun createTestNote(content: String) = JournalNote.Text(
        uid = Uuid.random(),
        content = content,
        creationTimestamp = Clock.System.now(),
        lastUpdated = Clock.System.now()
    )

    private fun createTestDraft(notes: List<JournalNote>) = EntryDraft(
        id = Uuid.random(),
        notes = notes,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )

    private fun buildAddNoteUseCase(
        repository: RecordingJournalNotesRepository
    ): AddNoteUseCase {
        val locationProvider = FakeLocationProvider()
        val activityRepository = FakeActivityTimelineRepository()
        val locationHistoryRepository = FakeLocationHistoryRepository()
        val retryWorker = LocationRetryWorker(
            locationProvider = locationProvider,
            locationHistoryRepository = locationHistoryRepository,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
        return AddNoteUseCase(
            repository = repository,
            journalContentRepository = FakeJournalContentRepository(),
            logLocationUseCase = LogLocationUseCase(locationProvider, activityRepository),
            logCurrentLocationUseCase = LogCurrentLocationUseCase(locationProvider, locationHistoryRepository, retryWorker),
            mediaManager = FakeMediaManager()
        )
    }

    private class MockEntryDraftRepository : EntryDraftRepository {
        override fun getDrafts(): Flow<List<EntryDraft>> = flowOf(emptyList())
        override fun getDraft(uid: Uuid): Flow<Result<EntryDraft>> = flowOf(Result.failure(NoSuchElementException()))
        override suspend fun createDraft(notes: List<JournalNote>): Uuid = Uuid.random()
        override suspend fun updateDraft(uid: Uuid, notes: List<JournalNote>): Uuid = uid
        override suspend fun deleteDraft(uid: Uuid) = Unit
    }

    private class RecordingJournalNotesRepository : JournalNotesRepository {
        val createdNotes = mutableListOf<JournalNote>()
        var shouldThrowException = false

        override val allNotesObserved: Flow<List<JournalNote>> = flowOf(emptyList())
        override fun observeNotesInJournal(journalId: Uuid) = flowOf(emptyList<JournalNote>())
        override fun observeNotesInRange(start: Instant, end: Instant) = flowOf(emptyList<JournalNote>())
        override fun observeNotesPage(pageSize: Int, offset: Int) = flowOf(emptyList<JournalNote>())
        override fun observeNotesStream(pageSize: Int) = flowOf(emptyList<JournalNote>())
        override fun observeRecentNotes(limit: Int) = flowOf(emptyList<JournalNote>())

        override suspend fun create(note: JournalNote): Uuid {
            if (shouldThrowException) {
                throw Exception("AddNote failed")
            }
            createdNotes.add(note)
            return note.uid
        }

        override suspend fun remove(note: JournalNote) {}
        override suspend fun removeById(noteId: Uuid) {}
        override suspend fun create(note: JournalNote, journalId: Uuid) {}
        override suspend fun removeFromJournal(noteId: Uuid, journalId: Uuid) {}
    }

    private class FakeJournalContentRepository : JournalContentRepository {
        override fun observeContentForJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())
        override fun observeJournalsForContent(contentId: Uuid) = flowOf(emptyList<app.logdate.shared.model.Journal>())
        override suspend fun addContentToJournal(contentId: Uuid, journalId: Uuid) {}
        override suspend fun removeContentFromJournal(contentId: Uuid, journalId: Uuid) {}
        override suspend fun addContentToJournals(contentId: Uuid, journalIds: List<Uuid>) {}
        override suspend fun removeContentFromAllJournals(contentId: Uuid) {}
    }

    private class FakeMediaManager : MediaManager {
        override suspend fun getMedia(uri: String): MediaObject = MediaObject.Image(
            uri = uri,
            size = 0,
            name = "mock",
            timestamp = Clock.System.now()
        )
        override suspend fun exists(mediaId: String): Boolean = false
        override suspend fun getRecentMedia(): Flow<List<MediaObject>> = flowOf(emptyList())
        override suspend fun queryMediaByDate(start: Instant, end: Instant): Flow<List<MediaObject>> = flowOf(emptyList())
        override suspend fun addToDefaultCollection(uri: String) {}
    }

    private class FakeLocationProvider : ClientLocationProvider {
        private val shared = MutableSharedFlow<Location>(replay = 1)
        private val location = Location(
            latitude = 37.0,
            longitude = -122.0,
            altitude = LocationAltitude(0.0, AltitudeUnit.METERS)
        )

        override val currentLocation: SharedFlow<Location> = shared
        override suspend fun getCurrentLocation(): Location = location
        override suspend fun refreshLocation() {}
    }

    private class FakeActivityTimelineRepository : ActivityTimelineRepository {
        private val location = Location(
            latitude = 37.0,
            longitude = -122.0,
            altitude = LocationAltitude(0.0, AltitudeUnit.METERS)
        )

        override val allItemsObserved: Flow<List<ActivityTimelineItem>> = flowOf(emptyList())
        override fun observeModelById(id: Uuid): Flow<ActivityTimelineItem> =
            flowOf(ActivityTimelineItem(uid = id, timestamp = Clock.System.now(), location = location))
        override suspend fun addActivity(item: ActivityTimelineItem) {}
        override suspend fun removeActivity(item: ActivityTimelineItem) {}
        override suspend fun updateActivity(item: ActivityTimelineItem) {}
        override fun fetchActivitiesByType(type: String): Flow<List<ActivityTimelineItem>> = flowOf(emptyList())
    }

    private class FakeLocationHistoryRepository : LocationHistoryRepository {
        override suspend fun getAllLocationHistory(): List<LocationHistoryItem> = emptyList()
        override fun observeLocationHistory(): Flow<List<LocationHistoryItem>> = flowOf(emptyList())
        override suspend fun getRecentLocationHistory(limit: Int): List<LocationHistoryItem> = emptyList()
        override suspend fun getLocationHistoryBetween(startTime: Instant, endTime: Instant): List<LocationHistoryItem> = emptyList()
        override suspend fun getLastLocation(): LocationHistoryItem? = null
        override fun observeLastLocation(): Flow<LocationHistoryItem?> = flowOf(null)
        override suspend fun logLocation(
            location: Location,
            userId: String,
            deviceId: String,
            confidence: Float,
            isGenuine: Boolean
        ): Result<Unit> = Result.success(Unit)
        override suspend fun deleteLocationEntry(userId: String, deviceId: String, timestamp: Instant): Result<Unit> = Result.success(Unit)
        override suspend fun deleteLocationsBetween(startTime: Instant, endTime: Instant): Result<Unit> = Result.success(Unit)
        override suspend fun getLocationCount(): Int = 0
    }
}
