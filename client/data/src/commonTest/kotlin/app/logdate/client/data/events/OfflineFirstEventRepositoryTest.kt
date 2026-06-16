package app.logdate.client.data.events

import app.logdate.client.data.fakes.FakeEventDao
import app.logdate.client.data.fakes.FakeEventNoteLinkDao
import app.logdate.client.database.entities.EventEntity
import app.logdate.shared.model.Event
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Unit tests for [OfflineFirstEventRepository] using in-memory DAO fakes.
 *
 * Each test constructs a fresh repository (and fresh fakes) via [newRepo] so cases stay
 * isolated. The [seed] helper inserts a baseline event so most tests can focus on the
 * behavior under test instead of plumbing.
 */
class OfflineFirstEventRepositoryTest {
    /**
     * Constructs a fresh [OfflineFirstEventRepository] backed by new [FakeEventDao] and
     * [FakeEventNoteLinkDao] instances. Returned as a triple so individual tests can mutate
     * the underlying fakes directly when needed (e.g., to seed an event without going through
     * the repository API).
     */
    private fun newRepo(): Triple<OfflineFirstEventRepository, FakeEventDao, FakeEventNoteLinkDao> {
        val eventDao = FakeEventDao()
        val linkDao = FakeEventNoteLinkDao()
        return Triple(OfflineFirstEventRepository(eventDao, linkDao), eventDao, linkDao)
    }

    /**
     * Inserts a baseline event entity into [dao] and returns its id. All optional fields default
     * to non-null sentinel values so tests don't need to spell out unused metadata. Override
     * the time fields to position the event for date-range tests.
     */
    private suspend fun seed(
        dao: FakeEventDao,
        id: Uuid = Uuid.random(),
        title: String = "Recital",
        start: Instant = Instant.fromEpochSeconds(1_000_000),
        end: Instant? = Instant.fromEpochSeconds(1_003_600),
        isAllDay: Boolean = false,
    ): Uuid {
        val now = Instant.fromEpochSeconds(2_000_000)
        dao.insert(
            EventEntity(
                id = id,
                title = title,
                description = null,
                startTime = start,
                endTime = end,
                isAllDay = isAllDay,
                placeId = null,
                coverImageUri = null,
                externalCalendarId = null,
                externalCalendarSource = null,
                created = now,
                lastUpdated = now,
            ),
        )
        return id
    }

    /**
     * The all-day flag survives the entity↔domain mapping in both directions: a seeded all-day
     * entity reads back as an all-day [Event], and an event created through the repository keeps
     * the flag when fetched again. Guards the mapper wiring that the all-day display fix relies on.
     */
    @Test
    fun isAllDay_round_trips_through_repository() =
        runTest {
            val (repo, dao, _) = newRepo()
            val seededId = seed(dao, isAllDay = true)
            assertTrue(repo.getEventById(seededId)!!.isAllDay)

            val created =
                Event(
                    title = "Conference",
                    startTime = Instant.fromEpochSeconds(1_500_000),
                    endTime = null,
                    isAllDay = true,
                )
            assertTrue(repo.createEvent(created).isSuccess)
            assertTrue(repo.getEventById(created.id)!!.isAllDay)
        }

    /**
     * After calling `updateEvent`, a subsequent fetch returns the new field values. Confirms
     * the repository round-trips through the DAO instead of caching stale state.
     */
    @Test
    fun updateEvent_persists_changes() =
        runTest {
            val (repo, dao, _) = newRepo()
            val id = seed(dao, title = "Old")

            val updated =
                repo
                    .getEventById(id)!!
                    .copy(title = "New")
            val result = repo.updateEvent(updated)

            assertTrue(result.isSuccess)
            assertEquals("New", repo.getEventById(id)!!.title)
        }

    /**
     * `deleteEvent` performs a soft delete: the underlying row is preserved (so a future
     * "trash" feature could restore it) but reads via `getEventById` return null because
     * the soft-delete filter excludes it.
     */
    @Test
    fun deleteEvent_hides_event_from_subsequent_reads() =
        runTest {
            val (repo, dao, _) = newRepo()
            val id = seed(dao)

            val result = repo.deleteEvent(id)

            assertTrue(result.isSuccess)
            assertNull(repo.getEventById(id))
        }

    /**
     * Verifies the date-range overlap predicate. Three events are seeded:
     *
     * - one entirely before the window,
     * - one that overlaps the window,
     * - one entirely after the window.
     *
     * Only the overlapping event should be returned.
     */
    @Test
    fun observeEventsForDateRange_includes_overlapping_excludes_outside() =
        runTest {
            val (repo, dao, _) = newRepo()
            val outsideBefore =
                seed(
                    dao,
                    id = Uuid.random(),
                    start = Instant.fromEpochSeconds(0),
                    end = Instant.fromEpochSeconds(100),
                )
            val overlapping =
                seed(
                    dao,
                    id = Uuid.random(),
                    start = Instant.fromEpochSeconds(900),
                    end = Instant.fromEpochSeconds(1_500),
                )
            val outsideAfter =
                seed(
                    dao,
                    id = Uuid.random(),
                    start = Instant.fromEpochSeconds(3_000),
                    end = Instant.fromEpochSeconds(3_500),
                )

            val results =
                repo
                    .observeEventsForDateRange(
                        start = Instant.fromEpochSeconds(1_000),
                        end = Instant.fromEpochSeconds(2_000),
                    ).first()

            val ids = results.map { it.id }
            assertTrue(overlapping in ids)
            assertTrue(outsideBefore !in ids)
            assertTrue(outsideAfter !in ids)
        }

    /**
     * Edge case: a point-in-time event (no end time) whose start lands exactly at the lower
     * bound of the requested window must be included. This guards the inclusive lower-bound
     * branch of the SQL `COALESCE(end_time, start_time) >= :start` clause.
     */
    @Test
    fun observeEventsForDateRange_includes_point_in_time_at_lower_bound() =
        runTest {
            val (repo, dao, _) = newRepo()
            val pointInTime =
                seed(
                    dao,
                    id = Uuid.random(),
                    start = Instant.fromEpochSeconds(1_000),
                    end = null,
                )

            val results =
                repo
                    .observeEventsForDateRange(
                        start = Instant.fromEpochSeconds(1_000),
                        end = Instant.fromEpochSeconds(2_000),
                    ).first()

            assertEquals(1, results.size)
            assertEquals(pointInTime, results[0].id)
        }

    /**
     * After linking a note to an event, observing the notes for that event emits the new
     * note id. Covers the basic write-then-read flow on the junction table.
     */
    @Test
    fun linkNoteToEvent_then_observeNotesForEvent_emits_link() =
        runTest {
            val (repo, dao, _) = newRepo()
            val eventId = seed(dao)
            val noteId = Uuid.random()

            repo.linkNoteToEvent(eventId, noteId)

            val notes = repo.observeNotesForEvent(eventId).first()
            assertEquals(listOf(noteId), notes)
        }

    /**
     * `unlinkNoteFromEvent` removes the junction row so the note no longer appears in the
     * observed list. The event itself is unaffected.
     */
    @Test
    fun unlinkNoteFromEvent_removes_link() =
        runTest {
            val (repo, dao, _) = newRepo()
            val eventId = seed(dao)
            val noteId = Uuid.random()
            repo.linkNoteToEvent(eventId, noteId)

            repo.unlinkNoteFromEvent(eventId, noteId)

            assertEquals(emptyList(), repo.observeNotesForEvent(eventId).first())
        }

    /**
     * Reverse lookup: given a note id, the repository resolves the linked event ids back into
     * full [Event] models. Exercises the `flatMapLatest` branch in the repository implementation
     * that joins junction rows to event rows.
     */
    @Test
    fun observeEventsForNote_resolves_to_event_models() =
        runTest {
            val (repo, dao, _) = newRepo()
            val eventId = seed(dao, title = "Concert")
            val noteId = Uuid.random()
            repo.linkNoteToEvent(eventId, noteId)

            val events: List<Event> = repo.observeEventsForNote(noteId).first()

            assertEquals(1, events.size)
            assertEquals("Concert", events[0].title)
            assertNotNull(events[0])
        }
}
