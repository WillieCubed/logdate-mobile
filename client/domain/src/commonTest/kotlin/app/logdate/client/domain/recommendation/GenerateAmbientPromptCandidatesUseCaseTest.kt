package app.logdate.client.domain.recommendation

import app.logdate.client.domain.events.ObserveUpcomingEventsUseCase
import app.logdate.client.domain.location.ObserveLocationHistoryUseCase
import app.logdate.client.domain.location.ObserveLocationStopsUseCase
import app.logdate.client.domain.notes.HasNotesForTodayUseCase
import app.logdate.client.domain.notes.drafts.FetchMostRecentDraftUseCase
import app.logdate.client.domain.places.PlaceResolutionCache
import app.logdate.client.domain.places.ResolveLocationToPlaceUseCase
import app.logdate.client.location.places.GeocodedAddress
import app.logdate.client.location.places.PlaceSuggestion
import app.logdate.client.location.places.ReverseGeocodingProvider
import app.logdate.client.repository.events.EventRepository
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.location.LocationCapturePipeline
import app.logdate.client.repository.location.LocationCaptureSource
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.client.repository.location.LocationLogRecord
import app.logdate.client.repository.places.UserPlacesRepository
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Event
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import app.logdate.shared.model.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Verifies the behavior of [GenerateAmbientPromptCandidatesUseCase] in surfacing relevant
 * journal prompts based on the user's current environment.
 *
 * These tests simulate various "ambient" triggers—such as scheduled morning/evening checks
 * or periodic background evaluations—to ensure the use case correctly prioritizes and
 * filters candidates like:
 * - Rescue nudges for existing drafts
 * - Periodic memory recalls for past events
 * - Context-aware capture nudges for novel locations
 * - Upcoming event reminders from the calendar
 */
class GenerateAmbientPromptCandidatesUseCaseTest {
    @Test
    fun `morning schedule creates a capture nudge when today is empty`() =
        runTest {
            val now = Instant.parse("2026-04-02T15:00:00Z")
            val useCase = testHarness(now = now).createUseCase()

            val candidates = useCase(AmbientPromptTriggerContext.MORNING_SCHEDULE)

            val candidate = candidates.single()
            assertEquals(AmbientPromptFamily.CAPTURE_NUDGES, candidate.family)
            val payload = assertIs<AmbientPromptPayload.CaptureNudge>(candidate.payload)
            assertEquals(AmbientCaptureNudgeStyle.MORNING, payload.style)
        }

    @Test
    fun `draft rescue outranks scheduled capture nudges`() =
        runTest {
            val now = Instant.parse("2026-04-02T18:00:00Z")
            val draft =
                EntryDraft(
                    id = Uuid.random(),
                    notes = listOf(textNote(createdAt = now - 7.hours)),
                    createdAt = now - 7.hours,
                    updatedAt = now - 7.hours,
                )
            val harness =
                testHarness(
                    now = now,
                    drafts = listOf(draft),
                )

            val candidates = harness.createUseCase()(AmbientPromptTriggerContext.EVENING_SCHEDULE)

            val candidate = candidates.first()
            assertEquals(AmbientPromptFamily.DRAFT_RESCUE, candidate.family)
            val payload = assertIs<AmbientPromptPayload.DraftRescue>(candidate.payload)
            assertEquals(draft.id, payload.draftId)
        }

    @Test
    fun `periodic evaluation surfaces memory recall when enabled`() =
        runTest {
            val now = Instant.parse("2026-04-02T12:00:00Z")
            val recallDay = LocalDate.parse("2025-04-02")
            val harness =
                testHarness(
                    now = now,
                    notes = listOf(textNote(createdAt = Instant.parse("2025-04-02T12:30:00Z"), content = "Met old friends")),
                    settings =
                        MemoriesSettings(
                            memoryRecallNotificationsEnabled = true,
                            recallMode = RecallMode.ON_THIS_DAY,
                            widgetContentTypes = setOf(WidgetContentType.TEXT),
                        ),
                )

            val candidates = harness.createUseCase()(AmbientPromptTriggerContext.PERIODIC)

            val recallCandidate =
                candidates.first { it.family == AmbientPromptFamily.MEMORY_RECALL }
            val payload = assertIs<AmbientPromptPayload.MemoryRecall>(recallCandidate.payload)
            assertEquals(recallDay, payload.date)
        }

    @Test
    fun `periodic evaluation creates a novel place capture nudge for a first visit without an entry`() =
        runTest {
            val now = Instant.parse("2026-04-02T20:00:00Z")
            val stopEnd = now - 30.minutes
            val location = location(latitude = 36.135, longitude = -115.427)
            val harness =
                testHarness(
                    now = now,
                    locationHistory =
                        listOf(
                            locationHistoryItem(timestamp = stopEnd - 50.minutes, location = location),
                            locationHistoryItem(timestamp = stopEnd - 40.minutes, location = location),
                            locationHistoryItem(timestamp = stopEnd - 30.minutes, location = location),
                            locationHistoryItem(timestamp = stopEnd - 20.minutes, location = location),
                            locationHistoryItem(timestamp = stopEnd - 10.minutes, location = location),
                            locationHistoryItem(timestamp = stopEnd, location = location),
                        ),
                    externalSuggestions =
                        mapOf(
                            locationKey(location) to
                                listOf(
                                    PlaceSuggestion(
                                        name = "Red Rock Canyon",
                                        address = "Las Vegas, NV",
                                        latitude = location.latitude,
                                        longitude = location.longitude,
                                        confidence = 98,
                                        externalId = "red-rock-canyon",
                                    ),
                                ),
                        ),
                )

            val candidates = harness.createUseCase()(AmbientPromptTriggerContext.PERIODIC)

            val candidate =
                candidates.first { it.family == AmbientPromptFamily.CAPTURE_NUDGES }
            val payload = assertIs<AmbientPromptPayload.CaptureNudge>(candidate.payload)
            assertEquals(AmbientCaptureNudgeStyle.NOVEL_PLACE, payload.style)
            assertEquals("Red Rock Canyon", payload.placeName)
        }

    @Test
    fun `periodic evaluation emits an event nudge for an upcoming uncaptured event`() =
        runTest {
            val now = Instant.parse("2026-04-02T18:00:00Z")
            val event = sampleEvent(startTime = now + 2.hours, title = "Piano recital")
            val harness = testHarness(now = now, events = listOf(event))

            val candidates = harness.createUseCase()(AmbientPromptTriggerContext.PERIODIC)

            val nudge = candidates.first { it.family == AmbientPromptFamily.EVENT_NUDGE }
            val payload = assertIs<AmbientPromptPayload.EventNudge>(nudge.payload)
            assertEquals(event.id, payload.eventId)
            assertEquals("Piano recital", payload.title)
            assertEquals("event:${event.id}", nudge.dedupeKey)
            assertEquals(90, nudge.score)
        }

    @Test
    fun `periodic evaluation skips events that already have captures attached`() =
        runTest {
            val now = Instant.parse("2026-04-02T18:00:00Z")
            val event = sampleEvent(startTime = now + 2.hours)
            val harness =
                testHarness(
                    now = now,
                    events = listOf(event),
                    notesByEvent = mapOf(event.id to listOf(Uuid.random())),
                )

            val candidates = harness.createUseCase()(AmbientPromptTriggerContext.PERIODIC)

            assertTrue(candidates.none { it.family == AmbientPromptFamily.EVENT_NUDGE })
        }

    @Test
    fun `periodic evaluation skips events when eventNudgesEnabled is false`() =
        runTest {
            val now = Instant.parse("2026-04-02T18:00:00Z")
            val event = sampleEvent(startTime = now + 2.hours)
            val harness =
                testHarness(
                    now = now,
                    events = listOf(event),
                    settings = MemoriesSettings(eventNudgesEnabled = false),
                )

            val candidates = harness.createUseCase()(AmbientPromptTriggerContext.PERIODIC)

            assertTrue(candidates.none { it.family == AmbientPromptFamily.EVENT_NUDGE })
        }

    private fun sampleEvent(
        startTime: Instant,
        endTime: Instant? = startTime + 1.hours,
        title: String = "Sample event",
    ): Event =
        Event(
            id = Uuid.random(),
            title = title,
            startTime = startTime,
            endTime = endTime,
            created = Instant.fromEpochSeconds(0),
            lastUpdated = Instant.fromEpochSeconds(0),
        )
}

/**
 * A test harness for [GenerateAmbientPromptCandidatesUseCase] to simplify setup
 * and provide mock/fake implementations of its dependencies.
 */
private class AmbientPromptTestHarness(
    private val now: Instant,
    notes: List<JournalNote> = emptyList(),
    drafts: List<EntryDraft> = emptyList(),
    settings: MemoriesSettings = MemoriesSettings(),
    locationHistory: List<LocationHistoryItem> = emptyList(),
    externalSuggestions: Map<String, List<PlaceSuggestion>> = emptyMap(),
    events: List<Event> = emptyList(),
    notesByEvent: Map<Uuid, List<Uuid>> = emptyMap(),
) {
    private val notesRepository = TestJournalNotesRepository(notes)
    private val draftRepository = TestEntryDraftRepository(drafts)
    private val settingsRepository = DefaultMemoriesSettingsRepository(FakeKeyValueStorage())
    private val locationRepository = TestLocationHistoryRepository(locationHistory)
    private val placeFamiliarityRepository = DefaultPlaceFamiliarityRepository(FakeKeyValueStorage())
    private val placeResolutionCache =
        PlaceResolutionCache(
            ResolveLocationToPlaceUseCase(
                userPlacesRepository = EmptyUserPlacesRepository(),
                externalPlacesProvider = TestExternalPlacesProvider(externalSuggestions),
                reverseGeocodingProvider = EmptyReverseGeocodingProvider(),
            ),
        )
    private val eventRepository: EventRepository =
        if (events.isEmpty() && notesByEvent.isEmpty()) {
            StubEventRepository
        } else {
            SeedableEventRepository(events, notesByEvent)
        }

    suspend fun createUseCase(): GenerateAmbientPromptCandidatesUseCase {
        settingsRepository.updateSettings(settings)
        return GenerateAmbientPromptCandidatesUseCase(
            memoriesSettingsRepository = settingsRepository,
            hasNotesForToday = HasNotesForTodayUseCase(notesRepository),
            fetchMostRecentDraft = FetchMostRecentDraftUseCase(draftRepository),
            getMemoryRecall = GetMemoryRecallUseCase(notesRepository, now = { now }),
            observeLocationStops = ObserveLocationStopsUseCase(ObserveLocationHistoryUseCase(locationRepository)),
            observeUpcomingEvents = ObserveUpcomingEventsUseCase(eventRepository, now = { now }),
            eventRepository = eventRepository,
            notesRepository = notesRepository,
            placeResolutionCache = placeResolutionCache,
            placeFamiliarityRepository = placeFamiliarityRepository,
            now = { now },
        )
    }

    private val settings = settings
}

private fun testHarness(
    now: Instant,
    notes: List<JournalNote> = emptyList(),
    drafts: List<EntryDraft> = emptyList(),
    settings: MemoriesSettings = MemoriesSettings(),
    locationHistory: List<LocationHistoryItem> = emptyList(),
    externalSuggestions: Map<String, List<PlaceSuggestion>> = emptyMap(),
    events: List<Event> = emptyList(),
    notesByEvent: Map<Uuid, List<Uuid>> = emptyMap(),
): AmbientPromptTestHarness =
    AmbientPromptTestHarness(
        now = now,
        events = events,
        notesByEvent = notesByEvent,
        notes = notes,
        drafts = drafts,
        settings = settings,
        locationHistory = locationHistory,
        externalSuggestions = externalSuggestions,
    )

/**
 * A test implementation of [JournalNotesRepository] for testing.
 */
private class TestJournalNotesRepository(
    private val notes: List<JournalNote>,
) : JournalNotesRepository {
    override val allNotesObserved: Flow<List<JournalNote>> = flowOf(notes)

    override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = flowOf(emptyList())

    override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = emptyList()

    override fun observeNotesInRange(
        start: Instant,
        end: Instant,
    ): Flow<List<JournalNote>> = flowOf(notes.filter { it.creationTimestamp in start..end })

    override fun observeNotesPage(
        pageSize: Int,
        offset: Int,
    ): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = flowOf(emptyList())

    override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = flowOf(notes.take(limit))

    override suspend fun getNoteById(noteId: Uuid): JournalNote? = notes.firstOrNull { it.uid == noteId }

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

/**
 * A test implementation of [EntryDraftRepository] for testing.
 */
private class TestEntryDraftRepository(
    drafts: List<EntryDraft>,
) : EntryDraftRepository {
    private val state = MutableStateFlow(drafts)

    override fun getDrafts(): Flow<List<EntryDraft>> = state

    override fun getDraft(uid: Uuid): Flow<Result<EntryDraft>> =
        flowOf(state.value.firstOrNull { it.id == uid }?.let(Result.Companion::success) ?: Result.failure(NoSuchElementException()))

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

/**
 * A test implementation of [LocationHistoryRepository] for testing.
 */
private class TestLocationHistoryRepository(
    history: List<LocationHistoryItem>,
) : LocationHistoryRepository {
    private val state = MutableStateFlow(history)

    override suspend fun getAllLocationHistory(): List<LocationHistoryItem> = state.value

    override fun observeLocationHistory(): Flow<List<LocationHistoryItem>> = state

    override suspend fun getRecentLocationHistory(limit: Int): List<LocationHistoryItem> = state.value.take(limit)

    override suspend fun getLocationHistoryBetween(
        startTime: Instant,
        endTime: Instant,
    ): List<LocationHistoryItem> = state.value.filter { it.timestamp in startTime..endTime }

    override suspend fun getLastLocation(): LocationHistoryItem? = state.value.maxByOrNull(LocationHistoryItem::timestamp)

    override fun observeLastLocation(): Flow<LocationHistoryItem?> = flowOf(state.value.maxByOrNull(LocationHistoryItem::timestamp))

    override suspend fun logLocation(
        location: Location,
        userId: String,
        deviceId: String,
        confidence: Float,
        isGenuine: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun logLocation(record: LocationLogRecord): Result<Unit> = Result.success(Unit)

    override suspend fun deleteLocationEntry(
        userId: String,
        deviceId: String,
        timestamp: Instant,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteLocationsBetween(
        startTime: Instant,
        endTime: Instant,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun getLocationCount(): Int = state.value.size
}

/**
 * A [UserPlacesRepository] that returns no places, used for testing purposes.
 */
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

/**
 * A [ReverseGeocodingProvider] that always returns null, used for testing.
 */
private class EmptyReverseGeocodingProvider : ReverseGeocodingProvider {
    override suspend fun reverseGeocode(location: Location): GeocodedAddress? = null
}

/**
 * A test implementation of [app.logdate.client.location.places.ExternalPlacesProvider] that uses a map for lookups.
 */
private class TestExternalPlacesProvider(
    private val suggestionsByLocationKey: Map<String, List<PlaceSuggestion>>,
) : app.logdate.client.location.places.ExternalPlacesProvider {
    override suspend fun searchNearbyPlaces(location: Location): List<PlaceSuggestion> =
        suggestionsByLocationKey[locationKey(location)].orEmpty()
}

private fun textNote(
    createdAt: Instant,
    content: String = "Test note",
): JournalNote.Text =
    JournalNote.Text(
        uid = Uuid.random(),
        content = content,
        creationTimestamp = createdAt,
        lastUpdated = createdAt,
    )

private fun locationHistoryItem(
    timestamp: Instant,
    location: Location,
): LocationHistoryItem =
    LocationHistoryItem(
        userId = "user",
        deviceId = "device",
        timestamp = timestamp,
        location = location,
        confidence = 1.0f,
        isGenuine = true,
        capturePipeline = LocationCapturePipeline.OPTIMIZED_BACKGROUND,
        captureSource = LocationCaptureSource.BACKGROUND_PERIODIC,
    )

private fun location(
    latitude: Double,
    longitude: Double,
): Location =
    Location(
        latitude = latitude,
        longitude = longitude,
        altitude = LocationAltitude(0.0, AltitudeUnit.METERS),
    )

private fun locationKey(location: Location): String = "${location.latitude},${location.longitude}"
