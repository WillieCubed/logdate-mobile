package app.logdate.feature.core.main

import app.logdate.client.domain.dayboundary.DayBoundarySettings
import app.logdate.client.domain.dayboundary.DayBoundarySettingsRepository
import app.logdate.client.domain.events.LinkNoteToEventUseCase
import app.logdate.client.domain.events.ObserveUpcomingEventsUseCase
import app.logdate.client.domain.notes.HasNotesForTodayUseCase
import app.logdate.client.domain.notes.drafts.FetchMostRecentDraftUseCase
import app.logdate.client.domain.places.PlaceResolutionCache
import app.logdate.client.domain.places.ResolveLocationToPlaceUseCase
import app.logdate.client.domain.recommendation.GetHomeRecommendationUseCase
import app.logdate.client.domain.recommendation.GetMemoryRecallUseCase
import app.logdate.client.domain.recommendation.MemoriesSettings
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.recommendation.RecallMode
import app.logdate.client.domain.recommendation.WidgetContentType
import app.logdate.client.domain.timeline.GetDayBoundsUseCase
import app.logdate.client.domain.timeline.GetJournalMembershipUseCase
import app.logdate.client.domain.timeline.GetTimelinePageUseCase
import app.logdate.client.domain.timeline.GroupNotesByDayBoundsUseCase
import app.logdate.client.domain.timeline.Timeline
import app.logdate.client.domain.timeline.TimelineDay
import app.logdate.client.domain.timeline.TimelineDayBuilder
import app.logdate.client.domain.timeline.TimelinePageRequest
import app.logdate.client.health.HealthDataAvailability
import app.logdate.client.health.LocalFirstHealthRepository
import app.logdate.client.health.model.DayBounds
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import app.logdate.client.location.places.UnavailableExternalPlacesProvider
import app.logdate.client.location.places.UnavailableReverseGeocodingProvider
import app.logdate.client.repository.events.EventRepository
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.places.UserPlacesRepository
import app.logdate.client.repository.transcription.TranscriptionData
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.repository.transcription.TranscriptionStatus
import app.logdate.shared.model.Event
import app.logdate.shared.model.Journal
import app.logdate.shared.model.Place
import app.logdate.ui.timeline.ImageNoteUiState
import app.logdate.ui.timeline.VideoNoteUiState
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
            val notesRepository = FakeJournalNotesRepository(notes = generateSequentialDayNotes(dayCount = 55))
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
            val notesRepository = FakeJournalNotesRepository(notes = generateBoundarySplitNotes())
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

    @Test
    fun `timeline note mapping preserves media captions and journal badges on the live Home path`() =
        runTest {
            val journalId = Uuid.random()
            val imageId = Uuid.random()
            val videoId = Uuid.random()
            val now = Instant.parse("2026-03-31T18:00:00Z")
            val notesRepository =
                FakeJournalNotesRepository(
                    notes =
                        listOf(
                            JournalNote.Image(
                                uid = imageId,
                                mediaRef = "file://image.jpg",
                                caption = "Sunset walk",
                                creationTimestamp = now,
                                lastUpdated = now,
                            ),
                            JournalNote.Video(
                                uid = videoId,
                                mediaRef = "file://video.mp4",
                                caption = "Birthday toast",
                                creationTimestamp = now - 1.days,
                                lastUpdated = now - 1.days,
                            ),
                        ),
                )
            val viewModel =
                createViewModel(
                    notesRepository = notesRepository,
                    journalContentRepository =
                        FakeJournalContentRepository(
                            mapOf(
                                imageId to listOf(Journal(id = journalId, title = "Travel")),
                                videoId to listOf(Journal(id = journalId, title = "Travel")),
                            ),
                        ),
                )
            val collectionJob = launch { viewModel.uiState.collect() }

            advanceUntilIdle()

            val imageNote =
                viewModel.uiState.value
                    .items[0]
                    .notes
                    .single()
            val image = assertIs<ImageNoteUiState>(imageNote)
            assertEquals("Sunset walk", image.caption)
            assertEquals(
                "Travel",
                image.journals
                    .single()
                    .title,
            )

            val videoNote =
                viewModel.uiState.value
                    .items[1]
                    .notes
                    .single()
            val video = assertIs<VideoNoteUiState>(videoNote)
            assertEquals("Birthday toast", video.caption)
            assertEquals(
                "Travel",
                video.journals
                    .single()
                    .title,
            )
            collectionJob.cancel()
        }

    @Test
    fun `live Home transcription state exposes completed transcriptions and queues missing visible audio`() =
        runTest {
            val audioId = Uuid.random()
            val now = Instant.parse("2026-03-31T18:00:00Z")
            val transcriptionRepository =
                FakeTranscriptionRepository(
                    initial =
                        mapOf(
                            audioId to
                                TranscriptionData(
                                    noteId = audioId,
                                    text = "Remember the launch checklist",
                                    status = TranscriptionStatus.COMPLETED,
                                    created = now,
                                    lastUpdated = now,
                                    id = Uuid.random(),
                                ),
                        ),
                )
            val viewModel =
                createViewModel(
                    notesRepository =
                        FakeJournalNotesRepository(
                            notes =
                                listOf(
                                    JournalNote.Audio(
                                        uid = audioId,
                                        mediaRef = "file://voice.m4a",
                                        durationMs = 12_000,
                                        creationTimestamp = now,
                                        lastUpdated = now,
                                    ),
                                ),
                        ),
                    transcriptionRepository = transcriptionRepository,
                )
            val collectionJob = launch { viewModel.transcriptionState.collect() }

            viewModel.updateVisibleAudioNoteIds(setOf(audioId))
            advanceUntilIdle()

            assertEquals("Remember the launch checklist", viewModel.transcriptionState.value.getTranscriptionText(audioId))
            assertFalse(viewModel.transcriptionState.value.isTranscriptionInProgress(audioId))
            assertTrue(transcriptionRepository.requestedNoteIds.isEmpty())
            collectionJob.cancel()
        }

    private suspend fun createViewModel(notesRepository: FakeJournalNotesRepository): HomeViewModel =
        createViewModel(notesRepository = notesRepository, journalContentRepository = FakeJournalContentRepository())

    private suspend fun createViewModel(
        notesRepository: FakeJournalNotesRepository,
        journalContentRepository: JournalContentRepository = FakeJournalContentRepository(),
        transcriptionRepository: TranscriptionRepository = FakeTranscriptionRepository(),
    ): HomeViewModel {
        val dayBoundarySettingsRepository = FakeDayBoundarySettingsRepository()
        val pageUseCase =
            GetTimelinePageUseCase(
                notesRepository,
                GroupNotesByDayBoundsUseCase(
                    getDayBoundsUseCase =
                        GetDayBoundsUseCase(
                            healthRepository = FakeHealthRepository(),
                            dayBoundarySettingsRepository = dayBoundarySettingsRepository,
                        ),
                    dayBoundarySettingsRepository = dayBoundarySettingsRepository,
                ),
                NoOpEventRepository,
                TimelineDayBuilder { date, entries, events ->
                    TimelineDay(
                        start = entries.minOf { it.creationTimestamp },
                        end = entries.maxOf { it.creationTimestamp },
                        tldr = "Test summary",
                        date = date,
                        events = events,
                        entries = entries.sortedByDescending { it.creationTimestamp },
                    )
                },
            )
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
                observeUpcomingEvents = ObserveUpcomingEventsUseCase(NoOpEventRepository),
                eventRepository = NoOpEventRepository,
                clientLocationProvider = TestLocationProvider,
                placeResolutionCache =
                    PlaceResolutionCache(
                        ResolveLocationToPlaceUseCase(
                            userPlacesRepository = EmptyUserPlacesRepository(),
                            externalPlacesProvider = UnavailableExternalPlacesProvider(),
                            reverseGeocodingProvider = UnavailableReverseGeocodingProvider(),
                        ),
                    ),
                memoriesSettingsRepository = FakeMemoriesSettingsRepository(),
            )

        return HomeViewModel(
            recentTimelineFlow = recentTimelineFlow,
            loadTimelinePage = pageUseCase::invoke,
            notesRepository = notesRepository,
            getHomeRecommendation = getHomeRecommendation,
            linkNoteToEvent = LinkNoteToEventUseCase(NoOpEventRepository),
            getJournalMembership = GetJournalMembershipUseCase(journalContentRepository),
            transcriptionRepository = transcriptionRepository,
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

    private class FakeJournalNotesRepository(
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

        override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = emptyList()
    }

    private object EmptyEntryDraftRepository : EntryDraftRepository {
        override fun getDrafts(): Flow<List<EntryDraft>> = flowOf(emptyList())

        override fun getDraft(uid: Uuid): Flow<Result<EntryDraft>> = flowOf(Result.failure(NoSuchElementException()))

        override suspend fun createDraft(notes: List<JournalNote>): Uuid = Uuid.random()

        override suspend fun updateDraft(
            uid: Uuid,
            notes: List<JournalNote>,
        ): Uuid = uid

        override suspend fun setPendingMedia(
            uid: Uuid,
            pendingMedia: List<app.logdate.client.repository.journals.PendingMediaRecord>,
        ) = Unit

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

    private class FakeJournalContentRepository(
        private val membership: Map<Uuid, List<Journal>> = emptyMap(),
    ) : JournalContentRepository {
        override fun observeContentForJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

        override fun observeJournalsForContent(contentId: Uuid): Flow<List<Journal>> = flowOf(membership[contentId].orEmpty())

        override fun observeJournalsForContents(contentIds: Set<Uuid>): Flow<Map<Uuid, List<Journal>>> =
            flowOf(contentIds.associateWith { id -> membership[id].orEmpty() })

        override suspend fun addContentToJournal(
            contentId: Uuid,
            journalId: Uuid,
        ) = Unit

        override suspend fun removeContentFromJournal(
            contentId: Uuid,
            journalId: Uuid,
        ) = Unit

        override suspend fun addContentToJournals(
            contentId: Uuid,
            journalIds: List<Uuid>,
        ) = Unit

        override suspend fun removeContentFromAllJournals(contentId: Uuid) = Unit
    }

    private class FakeTranscriptionRepository(
        initial: Map<Uuid, TranscriptionData?> = emptyMap(),
    ) : TranscriptionRepository {
        private val transcriptions = initial.mapValues { MutableStateFlow(it.value) }
        val requestedNoteIds = mutableListOf<Uuid>()

        override suspend fun requestTranscription(noteId: Uuid): Boolean {
            requestedNoteIds += noteId
            return true
        }

        override suspend fun getTranscription(noteId: Uuid): TranscriptionData? = transcriptions[noteId]?.value

        override fun observeTranscription(noteId: Uuid): Flow<TranscriptionData?> = transcriptions[noteId] ?: MutableStateFlow(null)

        override suspend fun getPendingTranscriptions(): List<TranscriptionData> = emptyList()

        override suspend fun updateTranscription(
            noteId: Uuid,
            text: String?,
            status: TranscriptionStatus,
            errorMessage: String?,
        ): Boolean = true

        override suspend fun deleteTranscription(noteId: Uuid): Boolean = true
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

        override suspend fun setRecallMode(mode: RecallMode) {
            settings.value = settings.value.copy(recallMode = mode)
        }

        override suspend fun setWidgetContentTypes(types: Set<WidgetContentType>) {
            settings.value = settings.value.copy(widgetContentTypes = types)
        }
    }

    private class FakeDayBoundarySettingsRepository : DayBoundarySettingsRepository {
        override suspend fun getSettings(): DayBoundarySettings = DayBoundarySettings()

        override fun observeSettings(): Flow<DayBoundarySettings> = flowOf(DayBoundarySettings())

        override suspend fun setSleepBasedBoundariesEnabled(enabled: Boolean) {}
    }

    private class FakeHealthRepository : LocalFirstHealthRepository {
        override suspend fun getHealthDataAvailability(): HealthDataAvailability = HealthDataAvailability.AVAILABLE

        override suspend fun hasSleepPermissions(): Boolean = true

        override suspend fun requestSleepPermissions(): Boolean = true

        override suspend fun getSleepSessions(
            start: Instant,
            end: Instant,
        ): List<SleepSession> = emptyList()

        override suspend fun getAverageWakeUpTime(
            timeZone: TimeZone,
            days: Int,
        ): TimeOfDay? = null

        override suspend fun getAverageSleepTime(
            timeZone: TimeZone,
            days: Int,
        ): TimeOfDay? = null

        override suspend fun isHealthDataAvailable(): Boolean = true

        override suspend fun getAvailableDataTypes(): List<String> = emptyList()

        override suspend fun getDayBoundsForDate(
            date: LocalDate,
            timeZone: TimeZone,
            sleepBasedBoundariesEnabled: Boolean,
        ): DayBounds {
            val start = LocalDateTime(date, LocalTime(4, 0)).toInstant(timeZone)
            val end = LocalDateTime(date.plus(1, DateTimeUnit.DAY), LocalTime(4, 0)).toInstant(timeZone)
            return DayBounds(start = start, end = end)
        }
    }
}

/**
 * Empty [EventRepository] used by [HomeViewModelTest] so it can wire a real
 * [LinkNoteToEventUseCase] without standing up a junction table. The home VM tests
 * never actually exercise the link path; this fake just satisfies the constructor.
 */
private object NoOpEventRepository : EventRepository {
    override fun observeAllEvents(): Flow<List<Event>> = flowOf(emptyList())

    override fun observeEvent(eventId: Uuid): Flow<Event?> = flowOf(null)

    override fun observeEventsForDateRange(
        start: Instant,
        end: Instant,
    ): Flow<List<Event>> = flowOf(emptyList())

    override suspend fun getEventById(eventId: Uuid): Event? = null

    override suspend fun findByExternalCalendarId(externalId: String): Event? = null

    override suspend fun createEvent(event: Event): Result<Unit> = Result.success(Unit)

    override suspend fun updateEvent(event: Event): Result<Unit> = Result.success(Unit)

    override suspend fun deleteEvent(eventId: Uuid): Result<Unit> = Result.success(Unit)

    override fun observeEventsForNote(noteId: Uuid): Flow<List<Event>> = flowOf(emptyList())

    override fun observeNotesForEvent(eventId: Uuid): Flow<List<Uuid>> = flowOf(emptyList())

    override suspend fun linkNoteToEvent(
        eventId: Uuid,
        noteId: Uuid,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun unlinkNoteFromEvent(
        eventId: Uuid,
        noteId: Uuid,
    ): Result<Unit> = Result.success(Unit)
}
