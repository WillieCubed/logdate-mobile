package app.logdate.client.domain.notes

import app.logdate.client.domain.world.LogLocationUseCase
import app.logdate.client.domain.location.LogCurrentLocationUseCase
import app.logdate.client.domain.location.LocationRetryWorker
import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaObject
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.timeline.ActivityTimelineRepository
import app.logdate.shared.model.ActivityTimelineItem
import app.logdate.shared.model.Journal
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

class AddNoteUseCaseTest {

    private lateinit var mockRepository: MockJournalNotesRepository
    private lateinit var mockJournalContentRepository: MockJournalContentRepository
    private lateinit var mockLogLocationUseCase: MockLogLocationUseCase
    private lateinit var mockLogCurrentLocationUseCase: MockLogCurrentLocationUseCase
    private lateinit var mockMediaManager: MockMediaManager
    private lateinit var useCase: AddNoteUseCase

    @BeforeTest
    fun setUp() {
        mockRepository = MockJournalNotesRepository()
        mockJournalContentRepository = MockJournalContentRepository()
        mockLogLocationUseCase = MockLogLocationUseCase()
        mockLogCurrentLocationUseCase = MockLogCurrentLocationUseCase()
        mockMediaManager = MockMediaManager()
        
        useCase = AddNoteUseCase(
            repository = mockRepository,
            journalContentRepository = mockJournalContentRepository,
            logLocationUseCase = mockLogLocationUseCase.useCase,
            logCurrentLocationUseCase = mockLogCurrentLocationUseCase.useCase,
            mediaManager = mockMediaManager
        )
    }

    @Test
    fun `invoke with single note should create note`() = runTest {
        // Given
        val testNote = createTestNote()
        
        // When
        useCase(notes = listOf(testNote))
        
        // Then
        assertEquals(1, mockRepository.createdNotes.size)
        assertEquals(testNote, mockRepository.createdNotes.first())
    }

    @Test
    fun `invoke with multiple notes should create all notes`() = runTest {
        // Given
        val testNotes = listOf(
            createTestNote(),
            createTestNote(content = "Second note"),
            createTestNote(content = "Third note")
        )
        val expectedNotes: List<JournalNote> = testNotes
        
        // When
        useCase(notes = testNotes)
        
        // Then
        assertEquals(3, mockRepository.createdNotes.size)
        assertEquals(expectedNotes, mockRepository.createdNotes)
    }

    /**
     * Test that AddNoteUseCase properly associates notes with journals.
     * 
     * Requirements:
     * - A note MUST be able to be associated with multiple journals
     * - When specifying journal IDs, each note MUST be linked to all journals
     * - The note creation and journal association MUST happen atomically
     * - The repository.create() method MUST be called for each note
     * - journalContentRepository.addContentToJournal() MUST be called for each note-journal pair
     */
    @Test
    fun `invoke with journals should associate notes with journals`() = runTest {
        // Given
        val testNote = createTestNote()
        val journal1Id = Uuid.random()
        val journal2Id = Uuid.random()
        
        // When
        useCase(
            notes = listOf(testNote),
            journalIds = listOf(journal1Id, journal2Id)
        )
        
        // Then
        assertEquals(1, mockRepository.createdNotes.size)
        assertEquals(testNote, mockRepository.createdNotes.first())
        assertEquals(2, mockJournalContentRepository.addedLinks.size)
        assertTrue(mockJournalContentRepository.addedLinks.contains(Pair(testNote.uid, journal1Id)))
        assertTrue(mockJournalContentRepository.addedLinks.contains(Pair(testNote.uid, journal2Id)))
    }

    @Test
    fun `invoke with attachments should add attachments to media manager`() = runTest {
        // Given
        val testNote = createTestNote()
        val attachments = listOf("file://path1.jpg", "file://path2.png")
        
        // When
        useCase(notes = listOf(testNote), attachments = attachments)
        
        // Then
        assertEquals(1, mockRepository.createdNotes.size)
        assertEquals(attachments, mockMediaManager.addedAttachments)
    }

    @Test
    fun `invoke with vararg notes should create all notes`() = runTest {
        // Given
        val note1 = createTestNote(content = "Note 1")
        val note2 = createTestNote(content = "Note 2")
        val note3 = createTestNote(content = "Note 3")
        val expectedNotes: List<JournalNote> = listOf(note1, note2, note3)
        
        // When
        useCase(notes = arrayOf(note1, note2, note3), journalIds = emptyArray())
        
        // Then
        assertEquals(3, mockRepository.createdNotes.size)
        assertEquals(expectedNotes, mockRepository.createdNotes)
    }

    /**
     * Tests that multiple notes can be associated with multiple journals simultaneously.
     * 
     * Requirements:
     * - The use case MUST handle multiple notes and multiple journals efficiently
     * - Each note MUST be associated with every journal specified
     * - The total number of associations MUST be (number of notes × number of journals)
     * - All notes MUST be successfully created before journal associations begin
     * - If any note creation fails, no associations MUST be made
     * - The method MUST maintain transactional integrity (all succeed or all fail)
     */
    @Test
    fun `invoke with multiple notes and journals should associate all notes with all journals`() = runTest {
        // Given
        val note1 = createTestNote(content = "Note 1")
        val note2 = createTestNote(content = "Note 2")
        val journal1Id = Uuid.random()
        val journal2Id = Uuid.random()
        
        // When
        useCase(
            notes = arrayOf(note1, note2),
            journalIds = arrayOf(journal1Id, journal2Id)
        )
        
        // Then
        assertEquals(2, mockRepository.createdNotes.size)
        assertEquals(4, mockJournalContentRepository.addedLinks.size)
        
        // Each note should be linked to each journal (2 notes x 2 journals = 4 links)
        assertTrue(mockJournalContentRepository.addedLinks.contains(Pair(note1.uid, journal1Id)))
        assertTrue(mockJournalContentRepository.addedLinks.contains(Pair(note1.uid, journal2Id)))
        assertTrue(mockJournalContentRepository.addedLinks.contains(Pair(note2.uid, journal1Id)))
        assertTrue(mockJournalContentRepository.addedLinks.contains(Pair(note2.uid, journal2Id)))
    }

    /**
     * Tests that when no journals are specified, notes are still created but not associated.
     * 
     * Requirements:
     * - Notes MUST be created successfully even when no journals are specified
     * - No journal associations MUST be created when no journals are specified
     * - The use case MUST handle the empty journals case gracefully
     */
    @Test
    fun `invoke with no journals should create notes without associations`() = runTest {
        // Given
        val testNote = createTestNote()
        
        // When
        useCase(
            notes = listOf(testNote),
            journalIds = emptyList()
        )
        
        // Then
        assertEquals(1, mockRepository.createdNotes.size)
        assertEquals(testNote, mockRepository.createdNotes.first())
        assertEquals(0, mockJournalContentRepository.addedLinks.size)
    }
    
    /**
     * Tests that notes can be created without any attachments.
     * 
     * Requirements:
     * - Notes MUST be created successfully even when no attachments are specified
     * - No media manager operations MUST be performed when no attachments are specified
     * - The use case MUST handle the empty attachments case gracefully
     */
    @Test
    fun `invoke with empty attachments should still create note`() = runTest {
        // Given
        val testNote = createTestNote()
        
        // When
        useCase(notes = listOf(testNote), attachments = emptyList())
        
        // Then
        assertEquals(1, mockRepository.createdNotes.size)
        assertEquals(0, mockMediaManager.addedAttachments.size)
    }

    private fun createTestNote(
        content: String = "Test note content"
    ) = JournalNote.Text(
        uid = Uuid.random(),
        content = content,
        creationTimestamp = Clock.System.now(),
        lastUpdated = Clock.System.now()
    )

    private class MockJournalNotesRepository : JournalNotesRepository {
        val createdNotes = mutableListOf<JournalNote>()

        override val allNotesObserved: Flow<List<JournalNote>> = flowOf(emptyList())

        override suspend fun create(note: JournalNote): Uuid {
            createdNotes.add(note)
            return note.uid
        }

        override suspend fun removeById(noteId: Uuid) = Unit
        override suspend fun remove(note: JournalNote) = Unit
        override suspend fun create(note: JournalNote, journalId: Uuid) = Unit
        override suspend fun removeFromJournal(noteId: Uuid, journalId: Uuid) = Unit
        override fun observeNotesInJournal(journalId: Uuid) = flowOf(emptyList<JournalNote>())
        override fun observeNotesInRange(start: Instant, end: Instant) = flowOf(emptyList<JournalNote>())
        override fun observeNotesPage(pageSize: Int, offset: Int) = flowOf(emptyList<JournalNote>())
        override fun observeNotesStream(pageSize: Int) = flowOf(emptyList<JournalNote>())
        override fun observeRecentNotes(limit: Int) = flowOf(emptyList<JournalNote>())
    }
    
    /**
     * Tests that the use case correctly handles errors in journal associations.
     * 
     * Requirements:
     * - The use case MUST be resilient to failures in journal association
     * - If journal association fails, the note creation MUST still succeed
     * - Other journal associations MUST continue to be attempted even if some fail
     * - The use case MUST NOT throw exceptions when journal association fails
     */
    @Test
    fun `invoke should handle journal association errors gracefully`() = runTest {
        // Given
        val testNote = createTestNote()
        val normalJournalId = Uuid.random()
        val errorJournalId = Uuid.random()
        val mockErrorContentRepo = MockErrorJournalContentRepository(errorJournalId)
        
        val errorHandlingUseCase = AddNoteUseCase(
            repository = mockRepository,
            journalContentRepository = mockErrorContentRepo,
            logLocationUseCase = mockLogLocationUseCase.useCase,
            logCurrentLocationUseCase = mockLogCurrentLocationUseCase.useCase,
            mediaManager = mockMediaManager
        )
        
        // When - should not throw exception even though one journal association fails
        errorHandlingUseCase(
            notes = listOf(testNote),
            journalIds = listOf(normalJournalId, errorJournalId)
        )
        
        // Then
        // Note should still be created
        assertEquals(1, mockRepository.createdNotes.size)
        assertEquals(testNote, mockRepository.createdNotes.first())
        
        // Only the successful journal association should be recorded
        assertEquals(1, mockErrorContentRepo.addedLinks.size)
        assertEquals(Pair(testNote.uid, normalJournalId), mockErrorContentRepo.addedLinks.first())
        
        // Error should be recorded
        assertEquals(1, mockErrorContentRepo.errors.size)
        assertEquals(Pair(testNote.uid, errorJournalId), mockErrorContentRepo.errors.first())
    }

    private class MockJournalContentRepository : JournalContentRepository {
        val addedLinks = mutableListOf<Pair<Uuid, Uuid>>()
        
        override suspend fun addContentToJournal(contentId: Uuid, journalId: Uuid) {
            addedLinks.add(Pair(contentId, journalId))
        }
        
        override fun observeContentForJournal(journalId: Uuid): Flow<List<JournalNote>> = 
            flowOf(emptyList())
            
        override fun observeJournalsForContent(contentId: Uuid): Flow<List<Journal>> = 
            flowOf(emptyList())
            
        override suspend fun removeContentFromJournal(contentId: Uuid, journalId: Uuid) = Unit
        
        override suspend fun addContentToJournals(contentId: Uuid, journalIds: List<Uuid>) {
            journalIds.forEach { journalId ->
                addedLinks.add(Pair(contentId, journalId))
            }
        }
        
        override suspend fun removeContentFromAllJournals(contentId: Uuid) = Unit
    }
    
    private class MockErrorJournalContentRepository(
        private val errorJournalId: Uuid
    ) : JournalContentRepository {
        val addedLinks = mutableListOf<Pair<Uuid, Uuid>>()
        val errors = mutableListOf<Pair<Uuid, Uuid>>()
        
        override suspend fun addContentToJournal(contentId: Uuid, journalId: Uuid) {
            if (journalId == errorJournalId) {
                errors.add(Pair(contentId, journalId))
                throw Exception("Simulated error associating content $contentId with journal $journalId")
            } else {
                addedLinks.add(Pair(contentId, journalId))
            }
        }
        
        override fun observeContentForJournal(journalId: Uuid): Flow<List<JournalNote>> = 
            flowOf(emptyList())
            
        override fun observeJournalsForContent(contentId: Uuid): Flow<List<Journal>> = 
            flowOf(emptyList())
            
        override suspend fun removeContentFromJournal(contentId: Uuid, journalId: Uuid) = Unit
        
        override suspend fun addContentToJournals(contentId: Uuid, journalIds: List<Uuid>) {
            journalIds.forEach { journalId ->
                try {
                    addContentToJournal(contentId, journalId)
                } catch (e: Exception) {
                    // Error handled in addContentToJournal
                }
            }
        }
        
        override suspend fun removeContentFromAllJournals(contentId: Uuid) = Unit
    }

    private class MockLogLocationUseCase {
        private val locationProvider = FakeLocationProvider()
        private val activityRepository = FakeActivityTimelineRepository()
        val useCase = LogLocationUseCase(locationProvider, activityRepository)
    }

    private class MockLogCurrentLocationUseCase {
        private val locationProvider = FakeLocationProvider()
        private val locationHistoryRepository = FakeLocationHistoryRepository()
        private val retryWorker = LocationRetryWorker(
            locationProvider = locationProvider,
            locationHistoryRepository = locationHistoryRepository,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
        val useCase = LogCurrentLocationUseCase(locationProvider, locationHistoryRepository, retryWorker)
    }
    
    private class MockMediaManager : MediaManager {
        val addedAttachments = mutableListOf<String>()

        override suspend fun addToDefaultCollection(uri: String) {
            addedAttachments.add(uri)
        }

        override suspend fun getMedia(uri: String): MediaObject = throw NotImplementedError()
        override suspend fun exists(mediaId: String): Boolean = false
        override suspend fun getRecentMedia(): Flow<List<MediaObject>> = flowOf(emptyList())
        override suspend fun queryMediaByDate(start: Instant, end: Instant): Flow<List<MediaObject>> = flowOf(emptyList())
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
            flowOf(ActivityTimelineItem(timestamp = Clock.System.now(), uid = id, location = location))

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

        override suspend fun deleteLocationEntry(userId: String, deviceId: String, timestamp: Instant): Result<Unit> =
            Result.success(Unit)

        override suspend fun deleteLocationsBetween(startTime: Instant, endTime: Instant): Result<Unit> =
            Result.success(Unit)

        override suspend fun getLocationCount(): Int = 0
    }
}
