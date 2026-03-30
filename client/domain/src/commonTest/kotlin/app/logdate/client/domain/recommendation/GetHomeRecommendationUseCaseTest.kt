package app.logdate.client.domain.recommendation

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.domain.notes.HasNotesForTodayUseCase
import app.logdate.client.domain.notes.drafts.FetchMostRecentDraftUseCase
import app.logdate.client.domain.places.PlaceResolutionCache
import app.logdate.client.domain.places.ResolveLocationToPlaceUseCase
import app.logdate.client.location.places.StubExternalPlacesProvider
import app.logdate.client.location.places.StubLocationProvider
import app.logdate.client.location.places.StubReverseGeocodingProvider
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.places.UserPlacesRepository
import app.logdate.shared.model.Place
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

class GetHomeRecommendationUseCaseTest {
    private lateinit var mockNotesRepository: MockJournalNotesRepository
    private lateinit var mockDraftRepository: MockEntryDraftRepository
    private lateinit var useCase: GetHomeRecommendationUseCase

    @BeforeTest
    fun setUp() {
        mockNotesRepository = MockJournalNotesRepository()
        mockDraftRepository = MockEntryDraftRepository()
        val resolveLocationToPlaceUseCase =
            ResolveLocationToPlaceUseCase(
                userPlacesRepository = EmptyUserPlacesRepository(),
                externalPlacesProvider = StubExternalPlacesProvider(),
                reverseGeocodingProvider = StubReverseGeocodingProvider(),
            )
        useCase =
            GetHomeRecommendationUseCase(
                hasNotesForToday = HasNotesForTodayUseCase(mockNotesRepository),
                fetchMostRecentDraft = FetchMostRecentDraftUseCase(mockDraftRepository),
                getMemoryRecall = GetMemoryRecallUseCase(mockNotesRepository),
                clientLocationProvider = StubLocationProvider,
                placeResolutionCache = PlaceResolutionCache(resolveLocationToPlaceUseCase),
                memoriesSettingsRepository = DefaultMemoriesSettingsRepository(MockKeyValueStorage()),
            )
    }

    // --- None ---

    @Test
    fun `returns None when user has notes today and no drafts`() =
        runTest {
            mockNotesRepository.notesForRange = listOf(createTextNote())
            mockDraftRepository.drafts = emptyList()

            val result = useCase().first()

            assertIs<HomeRecommendation.None>(result)
        }

    // --- EmptyDay ---

    @Test
    fun `returns EmptyDay when user has no notes today and no drafts`() =
        runTest {
            mockNotesRepository.notesForRange = emptyList()
            mockDraftRepository.drafts = emptyList()

            val result = useCase().first()

            assertIs<HomeRecommendation.EmptyDay>(result)
        }

    @Test
    fun `EmptyDay carries default message`() =
        runTest {
            mockNotesRepository.notesForRange = emptyList()
            mockDraftRepository.drafts = emptyList()

            val result = useCase().first() as HomeRecommendation.EmptyDay

            assertEquals("What's going on?", result.message)
        }

    // --- CompleteYourDraft ---

    @Test
    fun `returns CompleteYourDraft when a draft exists and no notes today`() =
        runTest {
            val draft = createDraftWithText("Working on something")
            mockNotesRepository.notesForRange = emptyList()
            mockDraftRepository.drafts = listOf(draft)

            val result = useCase().first()

            assertIs<HomeRecommendation.CompleteYourDraft>(result)
        }

    @Test
    fun `CompleteYourDraft draft ID matches the draft`() =
        runTest {
            val draft = createDraftWithText("In progress")
            mockNotesRepository.notesForRange = emptyList()
            mockDraftRepository.drafts = listOf(draft)

            val result = useCase().first() as HomeRecommendation.CompleteYourDraft

            assertEquals(draft.id, result.draftId)
        }

    // --- Priority ---

    @Test
    fun `CompleteYourDraft takes priority over EmptyDay when draft exists`() =
        runTest {
            val draft = createDraftWithText("Draft content")
            // No notes today — would normally trigger EmptyDay
            mockNotesRepository.notesForRange = emptyList()
            mockDraftRepository.drafts = listOf(draft)

            val result = useCase().first()

            assertIs<HomeRecommendation.CompleteYourDraft>(result)
        }

    @Test
    fun `CompleteYourDraft takes priority even when user already has notes today`() =
        runTest {
            val draft = createDraftWithText("Draft content")
            // User has notes today — would normally trigger None
            mockNotesRepository.notesForRange = listOf(createTextNote())
            mockDraftRepository.drafts = listOf(draft)

            val result = useCase().first()

            assertIs<HomeRecommendation.CompleteYourDraft>(result)
        }

    // --- Reactivity ---

    @Test
    fun `emits updated recommendation when notes are added`() =
        runTest {
            val notesFlow = MutableStateFlow(emptyList<JournalNote>())
            mockNotesRepository.observeRangeFlow = notesFlow
            mockDraftRepository.drafts = emptyList()

            val emissions = mutableListOf<HomeRecommendation>()
            val job = launch { useCase().collect { emissions.add(it) } }

            delay(50)
            assertIs<HomeRecommendation.EmptyDay>(emissions[0])

            notesFlow.value = listOf(createTextNote())

            delay(50)
            assertIs<HomeRecommendation.None>(emissions.last())

            job.cancel()
        }

    @Test
    fun `emits updated recommendation when draft is created`() =
        runTest {
            val draftsFlow = MutableStateFlow(emptyList<EntryDraft>())
            mockDraftRepository.draftsFlow = draftsFlow
            mockNotesRepository.notesForRange = emptyList()

            val emissions = mutableListOf<HomeRecommendation>()
            val job = launch { useCase().collect { emissions.add(it) } }

            delay(50)
            assertIs<HomeRecommendation.EmptyDay>(emissions[0])

            draftsFlow.value = listOf(createDraftWithText("New draft"))

            delay(50)
            assertIs<HomeRecommendation.CompleteYourDraft>(emissions.last())

            job.cancel()
        }

    @Test
    fun `emits None when draft is deleted and notes exist`() =
        runTest {
            val draft = createDraftWithText("Will be deleted")
            val draftsFlow = MutableStateFlow(listOf(draft))
            mockDraftRepository.draftsFlow = draftsFlow
            mockNotesRepository.notesForRange = listOf(createTextNote())

            val emissions = mutableListOf<HomeRecommendation>()
            val job = launch { useCase().collect { emissions.add(it) } }

            delay(50)
            assertIs<HomeRecommendation.CompleteYourDraft>(emissions[0])

            draftsFlow.value = emptyList()

            delay(50)
            assertIs<HomeRecommendation.None>(emissions.last())

            job.cancel()
        }

    // --- Helpers ---

    private fun createTextNote(content: String = "Test note") =
        JournalNote.Text(
            uid = Uuid.random(),
            content = content,
            creationTimestamp = Clock.System.now(),
            lastUpdated = Clock.System.now(),
        )

    private fun createDraftWithText(content: String): EntryDraft {
        val now = Clock.System.now()
        return EntryDraft(
            id = Uuid.random(),
            notes =
                listOf(
                    JournalNote.Text(
                        uid = Uuid.random(),
                        content = content,
                        creationTimestamp = now,
                        lastUpdated = now,
                    ),
                ),
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun createDraftWithAudioOnly(): EntryDraft {
        val now = Clock.System.now()
        return EntryDraft(
            id = Uuid.random(),
            notes =
                listOf(
                    JournalNote.Audio(
                        uid = Uuid.random(),
                        mediaRef = "file://audio.m4a",
                        durationMs = 5000,
                        creationTimestamp = now,
                        lastUpdated = now,
                    ),
                ),
            createdAt = now,
            updatedAt = now,
        )
    }

    // --- Mocks ---

    private class MockJournalNotesRepository : JournalNotesRepository {
        var notesForRange = emptyList<JournalNote>()
        var observeRangeFlow: MutableStateFlow<List<JournalNote>>? = null

        override fun observeNotesInRange(
            start: Instant,
            end: Instant,
        ): Flow<List<JournalNote>> = observeRangeFlow ?: flowOf(notesForRange)

        override val allNotesObserved: Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeNotesInJournal(journalId: Uuid) = flowOf(emptyList<JournalNote>())

        override fun observeNotesPage(
            pageSize: Int,
            offset: Int,
        ) = flowOf(emptyList<JournalNote>())

        override fun observeNotesStream(pageSize: Int) = flowOf(emptyList<JournalNote>())

        override fun observeRecentNotes(limit: Int) = flowOf(emptyList<JournalNote>())

        override suspend fun getNoteById(noteId: Uuid): JournalNote? = null

        override suspend fun create(note: JournalNote): Uuid = note.uid

        override suspend fun create(
            note: JournalNote,
            journalId: Uuid,
        ) = Unit

        override suspend fun remove(note: JournalNote) = Unit

        override suspend fun removeById(noteId: Uuid) = Unit

        override suspend fun removeFromJournal(
            noteId: Uuid,
            journalId: Uuid,
        ) = Unit

        override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = emptyList()
    }

    private class MockEntryDraftRepository : EntryDraftRepository {
        var drafts = emptyList<EntryDraft>()
        var draftsFlow: MutableStateFlow<List<EntryDraft>>? = null

        override fun getDrafts(): Flow<List<EntryDraft>> = draftsFlow ?: flowOf(drafts)

        override fun getDraft(uid: Uuid): Flow<Result<EntryDraft>> = flowOf(Result.failure(NoSuchElementException()))

        override suspend fun createDraft(notes: List<JournalNote>): Uuid = Uuid.random()

        override suspend fun updateDraft(
            uid: Uuid,
            notes: List<JournalNote>,
        ): Uuid = uid

        override suspend fun deleteDraft(uid: Uuid) = Unit

        override suspend fun deleteAllDrafts() = Unit

        override suspend fun deleteExpiredDrafts(maxAge: Duration): Int = 0
    }

    private class EmptyUserPlacesRepository : UserPlacesRepository {
        override suspend fun getAllPlaces(): List<Place> = emptyList()

        override fun observeAllPlaces(): Flow<List<Place>> = flowOf(emptyList())

        override suspend fun getPlacesNear(
            latitude: Double,
            longitude: Double,
            radiusMeters: Double,
        ): List<Place> = emptyList()

        override suspend fun getPlaceById(placeId: String): Place? = null

        override suspend fun createPlace(place: Place): Result<Place> = Result.success(place)

        override suspend fun updatePlace(place: Place): Result<Place> = Result.success(place)

        override suspend fun deletePlace(placeId: String): Result<Unit> = Result.success(Unit)

        override suspend fun searchPlaces(query: String): List<Place> = emptyList()
    }

    private class MockKeyValueStorage : KeyValueStorage {
        private val store = mutableMapOf<String, Any?>()
        private val stringFlows = mutableMapOf<String, MutableStateFlow<String?>>()
        private val booleanFlows = mutableMapOf<String, MutableStateFlow<Boolean>>()
        private val intFlows = mutableMapOf<String, MutableStateFlow<Int>>()
        private val longFlows = mutableMapOf<String, MutableStateFlow<Long>>()
        private val floatFlows = mutableMapOf<String, MutableStateFlow<Float>>()

        override suspend fun getString(key: String): String? = store[key] as? String

        override fun getStringSync(key: String): String? = store[key] as? String

        override suspend fun getBoolean(
            key: String,
            defaultValue: Boolean,
        ): Boolean = store[key] as? Boolean ?: defaultValue

        override suspend fun putString(
            key: String,
            value: String,
        ) {
            store[key] = value
            stringFlows.getOrPut(key) { MutableStateFlow(value) }.value = value
        }

        override suspend fun putBoolean(
            key: String,
            value: Boolean,
        ) {
            store[key] = value
            booleanFlows.getOrPut(key) { MutableStateFlow(value) }.value = value
        }

        override suspend fun getInt(
            key: String,
            defaultValue: Int,
        ): Int = store[key] as? Int ?: defaultValue

        override suspend fun putInt(
            key: String,
            value: Int,
        ) {
            store[key] = value
            intFlows.getOrPut(key) { MutableStateFlow(value) }.value = value
        }

        override suspend fun getLong(
            key: String,
            defaultValue: Long,
        ): Long = store[key] as? Long ?: defaultValue

        override suspend fun putLong(
            key: String,
            value: Long,
        ) {
            store[key] = value
            longFlows.getOrPut(key) { MutableStateFlow(value) }.value = value
        }

        override suspend fun getFloat(
            key: String,
            defaultValue: Float,
        ): Float = store[key] as? Float ?: defaultValue

        override suspend fun putFloat(
            key: String,
            value: Float,
        ) {
            store[key] = value
            floatFlows.getOrPut(key) { MutableStateFlow(value) }.value = value
        }

        override suspend fun remove(key: String) {
            store.remove(key)
            stringFlows.remove(key)
            booleanFlows.remove(key)
            intFlows.remove(key)
            longFlows.remove(key)
            floatFlows.remove(key)
        }

        override suspend fun contains(key: String): Boolean = store.containsKey(key)

        override suspend fun clear() {
            store.clear()
            stringFlows.clear()
            booleanFlows.clear()
            intFlows.clear()
            longFlows.clear()
            floatFlows.clear()
        }

        override fun observeString(key: String): Flow<String?> = stringFlows.getOrPut(key) { MutableStateFlow(store[key] as? String) }

        override fun observeBoolean(
            key: String,
            defaultValue: Boolean,
        ): Flow<Boolean> =
            booleanFlows.getOrPut(key) {
                MutableStateFlow(store[key] as? Boolean ?: defaultValue)
            }

        override fun observeInt(
            key: String,
            defaultValue: Int,
        ): Flow<Int> =
            intFlows.getOrPut(key) {
                MutableStateFlow(store[key] as? Int ?: defaultValue)
            }

        override fun observeLong(
            key: String,
            defaultValue: Long,
        ): Flow<Long> =
            longFlows.getOrPut(key) {
                MutableStateFlow(store[key] as? Long ?: defaultValue)
            }

        override fun observeFloat(
            key: String,
            defaultValue: Float,
        ): Flow<Float> =
            floatFlows.getOrPut(key) {
                MutableStateFlow(store[key] as? Float ?: defaultValue)
            }
    }
}
