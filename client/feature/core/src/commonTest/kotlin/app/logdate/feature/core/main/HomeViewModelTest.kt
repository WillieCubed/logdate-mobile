package app.logdate.feature.core.main

import app.logdate.client.domain.notes.HasNotesForTodayUseCase
import app.logdate.client.domain.notes.drafts.FetchMostRecentDraftUseCase
import app.logdate.client.domain.places.PlaceResolutionCache
import app.logdate.client.domain.places.ResolveLocationToPlaceUseCase
import app.logdate.client.domain.recommendation.GetHomeRecommendationUseCase
import app.logdate.client.domain.recommendation.GetMemoryRecallUseCase
import app.logdate.client.domain.recommendation.MemoriesSettings
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.timeline.GetTimelinePageUseCase
import app.logdate.client.domain.timeline.Timeline
import app.logdate.client.domain.timeline.TimelinePageRequest
import app.logdate.client.location.places.StubExternalPlacesProvider
import app.logdate.client.location.places.StubLocationProvider
import app.logdate.client.location.places.StubReverseGeocodingProvider
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.places.UserPlacesRepository
import app.logdate.shared.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadMoreOlder appends older days and reaches end of history`() =
        runTest {
            val notesRepository = ReactiveJournalNotesRepository(notes = generateSequentialDayNotes(dayCount = 55))
            val viewModel = createViewModel(notesRepository)
            val collectionJob = launch { viewModel.uiState.collect() }

            advanceUntilIdle()
            assertEquals(50, viewModel.uiState.value.items.size)
            assertTrue(viewModel.uiState.value.hasMoreOlderContent)

            viewModel.loadMoreOlder()
            advanceUntilIdle()

            assertEquals(55, viewModel.uiState.value.items.size)
            assertFalse(viewModel.uiState.value.hasMoreOlderContent)
            assertFalse(viewModel.uiState.value.isLoadingMore)
            assertEquals(null, viewModel.uiState.value.appendError)
            collectionJob.cancel()
        }

    @Test
    fun `loadMoreOlder does not duplicate the boundary day when recent window cuts through it`() =
        runTest {
            val notesRepository = ReactiveJournalNotesRepository(notes = generateBoundarySplitNotes())
            val viewModel = createViewModel(notesRepository)
            val boundaryDay = LocalDate(2025, 1, 20)
            val collectionJob = launch { viewModel.uiState.collect() }

            advanceUntilIdle()
            assertEquals(50, viewModel.uiState.value.items.size)
            assertEquals(
                1,
                viewModel.uiState.value.items
                    .count { it.date == boundaryDay },
            )
            assertEquals(
                2,
                viewModel.uiState.value.items
                    .first { it.date == boundaryDay }
                    .notes.size,
            )

            viewModel.loadMoreOlder()
            advanceUntilIdle()

            assertEquals(51, viewModel.uiState.value.items.size)
            assertEquals(
                1,
                viewModel.uiState.value.items
                    .count { it.date == boundaryDay },
            )
            assertEquals(
                2,
                viewModel.uiState.value.items
                    .first { it.date == boundaryDay }
                    .notes.size,
            )
            assertFalse(viewModel.uiState.value.hasMoreOlderContent)
            collectionJob.cancel()
        }

    private suspend fun createViewModel(notesRepository: ReactiveJournalNotesRepository): HomeViewModel {
        val pageUseCase = GetTimelinePageUseCase(notesRepository)
        val recentTimelineFlow =
            MutableStateFlow(
                Timeline(
                    days =
                        pageUseCase(
                            TimelinePageRequest(pageSize = 50),
                        ).days,
                ),
            )
        val getHomeRecommendation =
            GetHomeRecommendationUseCase(
                hasNotesForToday = HasNotesForTodayUseCase(notesRepository),
                fetchMostRecentDraft = FetchMostRecentDraftUseCase(EmptyEntryDraftRepository),
                getMemoryRecall = GetMemoryRecallUseCase(notesRepository),
                clientLocationProvider = StubLocationProvider,
                placeResolutionCache =
                    PlaceResolutionCache(
                        ResolveLocationToPlaceUseCase(
                            userPlacesRepository = EmptyUserPlacesRepository(),
                            externalPlacesProvider = StubExternalPlacesProvider(),
                            reverseGeocodingProvider = StubReverseGeocodingProvider(),
                        ),
                    ),
                memoriesSettingsRepository = FakeMemoriesSettingsRepository(),
            )

        return HomeViewModel(
            recentTimelineFlow = recentTimelineFlow,
            loadTimelinePage = pageUseCase::invoke,
            notesRepository = notesRepository,
            getHomeRecommendation = getHomeRecommendation,
        )
    }

    private fun generateSequentialDayNotes(dayCount: Int): List<JournalNote> {
        val baseInstant = Instant.parse("2026-03-31T18:00:00Z")
        return (0 until dayCount).map { index ->
            textNote(
                content = "Day $index",
                timestamp = baseInstant - index.days,
            )
        }
    }

    private fun generateBoundarySplitNotes(): List<JournalNote> {
        val baseInstant = Instant.parse("2025-03-10T18:00:00Z")
        val newerDays =
            (0 until 49).map { index ->
                textNote(
                    content = "Recent day $index",
                    timestamp = baseInstant - index.days,
                )
            }

        return newerDays +
            listOf(
                textNote("Boundary late", Instant.parse("2025-01-20T20:00:00Z")),
                textNote("Boundary early", Instant.parse("2025-01-20T15:00:00Z")),
                textNote("Older", Instant.parse("2025-01-19T18:00:00Z")),
            )
    }

    private fun textNote(
        content: String,
        timestamp: Instant,
    ): JournalNote.Text =
        JournalNote.Text(
            uid = Uuid.random(),
            content = content,
            creationTimestamp = timestamp,
            lastUpdated = timestamp,
        )

    private class ReactiveJournalNotesRepository(
        notes: List<JournalNote>,
    ) : JournalNotesRepository {
        private val notesFlow = MutableStateFlow(notes.sortedByDescending(JournalNote::creationTimestamp))

        override val allNotesObserved: Flow<List<JournalNote>> = notesFlow

        override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeNotesInRange(
            start: Instant,
            end: Instant,
        ): Flow<List<JournalNote>> =
            notesFlow.map { notes ->
                notes.filter { note -> note.creationTimestamp >= start && note.creationTimestamp < end }
            }

        override fun observeNotesPage(
            pageSize: Int,
            offset: Int,
        ): Flow<List<JournalNote>> =
            notesFlow.map { notes ->
                notes.drop(offset).take(pageSize)
            }

        override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = notesFlow

        override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> =
            notesFlow.map { notes ->
                notes.take(limit)
            }

        override suspend fun getNoteById(noteId: Uuid): JournalNote? = notesFlow.value.firstOrNull { it.uid == noteId }

        override suspend fun create(note: JournalNote): Uuid = note.uid

        override suspend fun remove(note: JournalNote) = Unit

        override suspend fun removeById(noteId: Uuid) = Unit

        override suspend fun create(
            note: JournalNote,
            journalId: Uuid,
        ) = Unit

        override suspend fun removeFromJournal(
            noteId: Uuid,
            journalId: Uuid,
        ) = Unit
    }

    private object EmptyEntryDraftRepository : EntryDraftRepository {
        override fun getDrafts(): Flow<List<EntryDraft>> = flowOf(emptyList())

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

    private class FakeMemoriesSettingsRepository : MemoriesSettingsRepository {
        private val settings = MutableStateFlow(MemoriesSettings())

        override suspend fun getSettings(): MemoriesSettings = settings.value

        override fun observeSettings(): Flow<MemoriesSettings> = settings

        override suspend fun updateSettings(settings: MemoriesSettings) {
            this.settings.value = settings
        }

        override suspend fun setContextualRecommendationsEnabled(enabled: Boolean) {
            settings.value = settings.value.copy(contextualRecommendationsEnabled = enabled)
        }

        override suspend fun setAiRecallEnabled(enabled: Boolean) {
            settings.value = settings.value.copy(aiRecallEnabled = enabled)
        }
    }
}
