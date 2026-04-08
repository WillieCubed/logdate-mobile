package app.logdate.client.domain.events

import app.logdate.client.domain.location.LocationStop
import app.logdate.client.domain.location.LocationStopEvidenceKind
import app.logdate.client.domain.places.PlaceResolutionResult
import app.logdate.client.domain.recommendation.PlaceFamiliarityRecord
import app.logdate.client.domain.recommendation.PlaceFamiliarityRepository
import app.logdate.client.intelligence.AIError
import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.events.EventName
import app.logdate.client.repository.events.EventRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.location.LocationCapturePipeline
import app.logdate.client.repository.media.ExifMetadata
import app.logdate.client.repository.media.IndexedMedia
import app.logdate.client.repository.media.IndexedMediaRepository
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
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests for [InferEventsUseCase].
 *
 * The use case combines several signals (stops, photos, notes, place familiarity, existing
 * events) into a single decision per stop. Each test seeds exactly the inputs the branch
 * cares about and asserts both the count returned by the use case and the events the fake
 * [RecordingEventRepository] saw, so failures point at the cluster the test is interested in.
 */
class InferEventsUseCaseTest {
    @Test
    fun creates_event_when_long_stop_has_photo_and_note_signals() =
        runTest {
            val stop = stopAt(STOP_START)
            val media = listOf(image(at = STOP_START + 30.minutes))
            val notes = listOf(textNote("hello", at = STOP_START + 45.minutes))
            val eventRepo = RecordingEventRepository()
            val useCase =
                InferEventsUseCase(
                    observeLocationStops = { flowOf(listOf(stop)) },
                    indexedMediaRepository = MediaInRange(media),
                    notesRepository = NotesInRange(notes),
                    placeFamiliarity = NoFamiliarity,
                    resolvePlaceForLocation = { userPlaceResolution() },
                    eventRepository = eventRepo,
                    suggestEventName = { successName("Recital", "Time at the place") },
                    now = { NOW },
                )

            val result = useCase()

            assertEquals(1, result.getOrThrow())
            assertEquals(1, eventRepo.created.size)
            val saved = eventRepo.created.single()
            assertEquals("Recital", saved.title)
            assertEquals("Time at the place", saved.description)
            assertEquals(stop.startTime, saved.startTime)
            assertEquals(stop.endTime, saved.endTime)
        }

    @Test
    fun skips_clusters_at_familiar_places() =
        runTest {
            val stop = stopAt(STOP_START)
            val eventRepo = RecordingEventRepository()
            val useCase =
                InferEventsUseCase(
                    observeLocationStops = { flowOf(listOf(stop)) },
                    indexedMediaRepository = MediaInRange(listOf(image(at = STOP_START + 5.minutes))),
                    notesRepository = NotesInRange(listOf(textNote("note", at = STOP_START + 5.minutes))),
                    placeFamiliarity = AlwaysFamiliar,
                    resolvePlaceForLocation = { userPlaceResolution() },
                    eventRepository = eventRepo,
                    suggestEventName = { error("naming should not be called for familiar places") },
                    now = { NOW },
                )

            val result = useCase()

            assertEquals(0, result.getOrThrow())
            assertEquals(0, eventRepo.created.size)
        }

    @Test
    fun skips_clusters_with_fewer_signals_than_sensitivity_requires() =
        runTest {
            val stop = stopAt(STOP_START)
            val eventRepo = RecordingEventRepository()
            val useCase =
                InferEventsUseCase(
                    observeLocationStops = { flowOf(listOf(stop)) },
                    // One signal total, sensitivity LOW requires 4
                    indexedMediaRepository = MediaInRange(listOf(image(at = STOP_START + 10.minutes))),
                    notesRepository = NotesInRange(emptyList()),
                    placeFamiliarity = NoFamiliarity,
                    resolvePlaceForLocation = { userPlaceResolution() },
                    eventRepository = eventRepo,
                    suggestEventName = { error("should not be called when below threshold") },
                    now = { NOW },
                )

            val result = useCase(sensitivity = EventInferenceSensitivity.LOW)

            assertEquals(0, result.getOrThrow())
            assertEquals(0, eventRepo.created.size)
        }

    @Test
    fun deduplicates_against_existing_events_in_range() =
        runTest {
            val stop = stopAt(STOP_START)
            val pre =
                Event(
                    title = "Already there",
                    startTime = stop.startTime,
                    endTime = stop.endTime,
                )
            val eventRepo = RecordingEventRepository(rangeResult = listOf(pre))
            val useCase =
                InferEventsUseCase(
                    observeLocationStops = { flowOf(listOf(stop)) },
                    indexedMediaRepository = MediaInRange(listOf(image(at = STOP_START + 5.minutes))),
                    notesRepository = NotesInRange(listOf(textNote("hi", at = STOP_START + 5.minutes))),
                    placeFamiliarity = NoFamiliarity,
                    resolvePlaceForLocation = { userPlaceResolution() },
                    eventRepository = eventRepo,
                    suggestEventName = { error("dedupe should short-circuit before naming") },
                    now = { NOW },
                )

            val result = useCase()

            assertEquals(0, result.getOrThrow())
            assertEquals(0, eventRepo.created.size)
        }

    @Test
    fun falls_back_to_heuristic_name_when_extractor_fails() =
        runTest {
            val stop = stopAt(STOP_START)
            val eventRepo = RecordingEventRepository()
            val useCase =
                InferEventsUseCase(
                    observeLocationStops = { flowOf(listOf(stop)) },
                    indexedMediaRepository =
                        MediaInRange(
                            listOf(
                                image(at = STOP_START + 5.minutes),
                                image(at = STOP_START + 10.minutes),
                            ),
                        ),
                    notesRepository = NotesInRange(emptyList()),
                    placeFamiliarity = NoFamiliarity,
                    resolvePlaceForLocation = { userPlaceResolution(name = "Stubb's") },
                    eventRepository = eventRepo,
                    suggestEventName = { AIResult.Error(AIError.InvalidResponse) },
                    now = { NOW },
                )

            val result = useCase()

            assertEquals(1, result.getOrThrow())
            val saved = eventRepo.created.single()
            assertTrue(
                saved.title.endsWith("at Stubb's"),
                "Expected heuristic title to mention place, was '${saved.title}'",
            )
            assertTrue(saved.description?.contains("Stubb's") == true)
        }

    @Test
    fun returns_zero_when_no_qualifying_stops_exist() =
        runTest {
            val eventRepo = RecordingEventRepository()
            val useCase =
                InferEventsUseCase(
                    observeLocationStops = { flowOf(emptyList()) },
                    indexedMediaRepository = MediaInRange(emptyList()),
                    notesRepository = NotesInRange(emptyList()),
                    placeFamiliarity = NoFamiliarity,
                    resolvePlaceForLocation = { error("no stops, no resolution call") },
                    eventRepository = eventRepo,
                    suggestEventName = { error("no stops, no naming call") },
                    now = { NOW },
                )

            val result = useCase()

            assertEquals(0, result.getOrThrow())
            assertEquals(0, eventRepo.created.size)
        }

    @Test
    fun skips_stops_resolved_to_unknown_locations() =
        runTest {
            val stop = stopAt(STOP_START)
            val eventRepo = RecordingEventRepository()
            val useCase =
                InferEventsUseCase(
                    observeLocationStops = { flowOf(listOf(stop)) },
                    indexedMediaRepository = MediaInRange(listOf(image(at = STOP_START + 5.minutes))),
                    notesRepository = NotesInRange(listOf(textNote("hi", at = STOP_START + 5.minutes))),
                    placeFamiliarity = NoFamiliarity,
                    resolvePlaceForLocation = { PlaceResolutionResult.UnknownLocation(stop.location) },
                    eventRepository = eventRepo,
                    suggestEventName = { error("naming should not be called for unknown places") },
                    now = { NOW },
                )

            val result = useCase()

            assertEquals(0, result.getOrThrow())
            assertEquals(0, eventRepo.created.size)
        }

    @Test
    fun does_not_call_extractor_when_ai_naming_disabled() =
        runTest {
            var extractorCalls = 0
            val stop = stopAt(STOP_START)
            val eventRepo = RecordingEventRepository()
            val useCase =
                InferEventsUseCase(
                    observeLocationStops = { flowOf(listOf(stop)) },
                    indexedMediaRepository = MediaInRange(listOf(image(at = STOP_START + 5.minutes))),
                    notesRepository = NotesInRange(listOf(textNote("hi", at = STOP_START + 5.minutes))),
                    placeFamiliarity = NoFamiliarity,
                    resolvePlaceForLocation = { userPlaceResolution(name = "Park") },
                    eventRepository = eventRepo,
                    suggestEventName = {
                        extractorCalls += 1
                        successName("Should not show up", "Nope")
                    },
                    now = { NOW },
                )

            val result = useCase(aiNamingEnabled = false)

            assertEquals(1, result.getOrThrow())
            assertEquals(0, extractorCalls)
            assertTrue(
                eventRepo.created
                    .single()
                    .title
                    .contains("Park"),
            )
        }

    private fun stopAt(start: Instant): LocationStop =
        LocationStop(
            id = "stop:$start",
            location =
                Location(
                    latitude = 30.0,
                    longitude = -97.0,
                    altitude = LocationAltitude(0.0, AltitudeUnit.METERS),
                ),
            startTime = start,
            endTime = start + 90.minutes,
            sampleCount = 10,
            maxInternalGap = 1.minutes,
            hasReliableDuration = true,
            evidenceKind = LocationStopEvidenceKind.STAY,
            primaryPipeline = LocationCapturePipeline.OPTIMIZED_BACKGROUND,
        )

    private fun image(at: Instant): IndexedMedia.Image =
        IndexedMedia.Image(
            uid = Uuid.random(),
            uri = "file:///media/${at.toEpochMilliseconds()}.jpg",
            timestamp = at,
        )

    private fun textNote(
        content: String,
        at: Instant,
    ): JournalNote.Text =
        JournalNote.Text(
            creationTimestamp = at,
            lastUpdated = at,
            content = content,
        )

    private fun userPlaceResolution(name: String = "The Place"): PlaceResolutionResult =
        PlaceResolutionResult.UserDefinedPlace(
            place =
                Place.UserDefined(
                    id = Uuid.random(),
                    displayName = name,
                    lat = 30.0,
                    lng = -97.0,
                ),
        )

    private fun successName(
        title: String,
        description: String,
    ): AIResult<EventName> = AIResult.Success(EventName(title = title, description = description))

    /** Backing fakes used by every test. */

    private object NoFamiliarity : PlaceFamiliarityRepository {
        override suspend fun get(placeKey: String): PlaceFamiliarityRecord? = null

        override suspend fun recordVisit(
            placeKey: String,
            displayName: String?,
            visitedAt: Instant,
        ) = Unit
    }

    private object AlwaysFamiliar : PlaceFamiliarityRepository {
        override suspend fun get(placeKey: String): PlaceFamiliarityRecord? =
            PlaceFamiliarityRecord(
                placeKey = placeKey,
                displayName = null,
                visitCount = 99,
                lastSeenAt = NOW,
            )

        override suspend fun recordVisit(
            placeKey: String,
            displayName: String?,
            visitedAt: Instant,
        ) = Unit
    }

    private class MediaInRange(
        private val media: List<IndexedMedia>,
    ) : IndexedMediaRepository {
        override fun getForPeriod(
            startTime: Instant,
            endTime: Instant,
        ): Flow<List<IndexedMedia>> = flowOf(media.filter { it.timestamp in startTime..endTime })

        override suspend fun indexImage(
            uri: String,
            timestamp: Instant,
        ): IndexedMedia.Image = IndexedMedia.Image(Uuid.random(), uri, timestamp)

        override suspend fun indexVideo(
            uri: String,
            timestamp: Instant,
            duration: Duration,
        ): IndexedMedia.Video = IndexedMedia.Video(Uuid.random(), uri, timestamp, duration = duration)

        override suspend fun getByUid(uid: Uuid): IndexedMedia? = null

        override suspend fun isIndexed(uri: String): Boolean = false

        override suspend fun remove(uid: Uuid): Boolean = false

        override suspend fun updateCaption(
            uid: Uuid,
            caption: String?,
        ): IndexedMedia? = null

        override fun observeAllMedia(): Flow<List<IndexedMedia>> = flowOf(media)

        override fun getMediaCount(): Flow<Int> = flowOf(media.size)

        override suspend fun getExifMetadata(uid: Uuid): ExifMetadata? = null
    }

    private class NotesInRange(
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

        override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = flowOf(emptyList())

        override suspend fun getNoteById(noteId: Uuid): JournalNote? = null

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

        override suspend fun getNotesForDay(day: LocalDate): List<JournalNote> = emptyList()

        override suspend fun getDatesWithEntries(
            start: LocalDate,
            end: LocalDate,
        ): Set<LocalDate> = emptySet()
    }

    private class RecordingEventRepository(
        private val rangeResult: List<Event> = emptyList(),
    ) : EventRepository {
        val created: MutableList<Event> = mutableListOf()
        private val state = MutableStateFlow<List<Event>>(emptyList())

        override fun observeAllEvents(): Flow<List<Event>> = state

        override fun observeEvent(eventId: Uuid): Flow<Event?> = flowOf(null)

        override fun observeEventsForDateRange(
            start: Instant,
            end: Instant,
        ): Flow<List<Event>> = flowOf(rangeResult)

        override suspend fun getEventById(eventId: Uuid): Event? = null

        override suspend fun findByExternalCalendarId(externalId: String): Event? = null

        override suspend fun createEvent(event: Event): Result<Unit> {
            created += event
            return Result.success(Unit)
        }

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

    companion object {
        // Pin "now" to a deterministic instant so the time-of-day phrase in the heuristic
        // fallback is stable across runs without depending on the test machine's clock.
        private val NOW: Instant = Instant.fromEpochSeconds(1_700_000_000)
        private val STOP_START: Instant = NOW - 6.hours
    }
}
