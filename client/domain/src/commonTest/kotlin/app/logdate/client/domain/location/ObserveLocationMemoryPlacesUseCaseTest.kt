package app.logdate.client.domain.location

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Unit tests for [ObserveLocationMemoryPlacesUseCase].
 *
 * Verifies the logic for grouping and filtering journal notes based on their
 * location metadata. It tests semantic place grouping, coordinate-based
 * proximity clustering, and time-window filtering for memory retrieval.
 */
class ObserveLocationMemoryPlacesUseCaseTest {
    @Test
    fun `invoke groups notes with the same semantic place together`() =
        runTest {
            val now = Instant.parse("2026-03-10T12:00:00Z")
            val homePlace =
                NotePlace(
                    id = Uuid.random(),
                    name = "Home",
                    latitude = 37.77,
                    longitude = -122.42,
                )
            val repository =
                FakeJournalNotesRepository(
                    notes =
                        listOf(
                            textNote(
                                createdAt = now - 1.days,
                                text = "Made coffee",
                                location = NoteLocation(place = homePlace),
                            ),
                            textNote(
                                createdAt = now - 2.days,
                                text = "Watched a movie",
                                location = NoteLocation(place = homePlace),
                            ),
                        ),
                )

            val result =
                ObserveLocationMemoryPlacesUseCase(
                    notesRepository = repository,
                    clock = fixedClock(now),
                    timeZone = TimeZone.UTC,
                ).invoke(LocationMemoryTimeFilter.Last30Days).first()

            assertEquals(1, result.size)
            assertEquals("Home", result.first().semanticName)
            assertEquals(2, result.first().memories.size)
            assertEquals(
                "Made coffee",
                result
                    .first()
                    .memories
                    .first()
                    .preview,
            )
        }

    @Test
    fun `invoke groups coordinate-only notes by rounded coordinates`() =
        runTest {
            val now = Instant.parse("2026-03-10T12:00:00Z")
            val repository =
                FakeJournalNotesRepository(
                    notes =
                        listOf(
                            textNote(
                                createdAt = now - 1.days,
                                text = "Picnic in the park",
                                location =
                                    NoteLocation(
                                        coordinates =
                                            NoteCoordinates(
                                                latitude = 37.77491,
                                                longitude = -122.41941,
                                            ),
                                    ),
                            ),
                            textNote(
                                createdAt = now - 2.days,
                                text = "Walked the dog",
                                location =
                                    NoteLocation(
                                        coordinates =
                                            NoteCoordinates(
                                                latitude = 37.77493,
                                                longitude = -122.41939,
                                            ),
                                    ),
                            ),
                        ),
                )

            val result =
                ObserveLocationMemoryPlacesUseCase(
                    notesRepository = repository,
                    clock = fixedClock(now),
                    timeZone = TimeZone.UTC,
                ).invoke(LocationMemoryTimeFilter.Last30Days).first()

            assertEquals(1, result.size)
            assertNull(result.first().semanticName)
            assertEquals(2, result.first().memories.size)
        }

    @Test
    fun `invoke filters out memories outside the selected time range`() =
        runTest {
            val now = Instant.parse("2026-03-10T12:00:00Z")
            val homePlace =
                NotePlace(
                    id = Uuid.random(),
                    name = "Home",
                    latitude = 37.77,
                    longitude = -122.42,
                )
            val repository =
                FakeJournalNotesRepository(
                    notes =
                        listOf(
                            textNote(
                                createdAt = now - 5.days,
                                text = "Recent memory",
                                location = NoteLocation(place = homePlace),
                            ),
                            textNote(
                                createdAt = now - 45.days,
                                text = "Old memory",
                                location = NoteLocation(place = homePlace),
                            ),
                        ),
                )

            val result =
                ObserveLocationMemoryPlacesUseCase(
                    notesRepository = repository,
                    clock = fixedClock(now),
                    timeZone = TimeZone.UTC,
                ).invoke(LocationMemoryTimeFilter.Last30Days).first()

            assertEquals(1, result.size)
            assertEquals(1, result.first().memories.size)
            assertEquals(
                "Recent memory",
                result
                    .first()
                    .memories
                    .first()
                    .preview,
            )
        }

    @Test
    fun `invoke sorts place groups by most recent memory`() =
        runTest {
            val now = Instant.parse("2026-03-10T12:00:00Z")
            val repository =
                FakeJournalNotesRepository(
                    notes =
                        listOf(
                            textNote(
                                createdAt = now - 10.days,
                                text = "Beach day",
                                location =
                                    NoteLocation(
                                        place =
                                            NotePlace(
                                                id = Uuid.random(),
                                                name = "Beach",
                                                latitude = 34.01,
                                                longitude = -118.49,
                                            ),
                                    ),
                            ),
                            textNote(
                                createdAt = now - 1.days,
                                text = "Dinner downtown",
                                location =
                                    NoteLocation(
                                        place =
                                            NotePlace(
                                                id = Uuid.random(),
                                                name = "Downtown",
                                                latitude = 37.79,
                                                longitude = -122.40,
                                            ),
                                    ),
                            ),
                        ),
                )

            val result =
                ObserveLocationMemoryPlacesUseCase(
                    notesRepository = repository,
                    clock = fixedClock(now),
                    timeZone = TimeZone.UTC,
                ).invoke(LocationMemoryTimeFilter.Last30Days).first()

            assertEquals(listOf("Downtown", "Beach"), result.map { it.semanticName })
        }

    @Test
    fun `custom range supports arbitrary inclusive day windows`() =
        runTest {
            val now = Instant.parse("2026-03-10T12:00:00Z")
            val repository =
                FakeJournalNotesRepository(
                    notes =
                        listOf(
                            textNote(
                                createdAt = Instant.parse("2026-03-03T08:00:00Z"),
                                text = "Early week memory",
                                location = coordinateLocation(37.77, -122.42),
                            ),
                            textNote(
                                createdAt = Instant.parse("2026-03-05T18:30:00Z"),
                                text = "Midweek memory",
                                location = coordinateLocation(37.77, -122.42),
                            ),
                            textNote(
                                createdAt = Instant.parse("2026-03-07T09:15:00Z"),
                                text = "Weekend memory",
                                location = coordinateLocation(37.77, -122.42),
                            ),
                        ),
                )

            val result =
                ObserveLocationMemoryPlacesUseCase(
                    notesRepository = repository,
                    clock = fixedClock(now),
                    timeZone = TimeZone.UTC,
                ).invoke(
                    LocationMemoryTimeFilter.Custom(
                        startInclusive = LocalDate(2026, 3, 4),
                        endInclusive = LocalDate(2026, 3, 6),
                    ),
                ).first()

            assertEquals(1, result.size)
            assertEquals(listOf("Midweek memory"), result.first().memories.map { it.preview })
        }

    @Test
    fun `custom range supports open ended bounds`() =
        runTest {
            val now = Instant.parse("2026-03-10T12:00:00Z")
            val repository =
                FakeJournalNotesRepository(
                    notes =
                        listOf(
                            textNote(
                                createdAt = Instant.parse("2026-02-28T23:00:00Z"),
                                text = "Before window",
                                location = coordinateLocation(37.77, -122.42),
                            ),
                            textNote(
                                createdAt = Instant.parse("2026-03-05T08:00:00Z"),
                                text = "Inside window",
                                location = coordinateLocation(37.77, -122.42),
                            ),
                        ),
                )

            val result =
                ObserveLocationMemoryPlacesUseCase(
                    notesRepository = repository,
                    clock = fixedClock(now),
                    timeZone = TimeZone.UTC,
                ).invoke(
                    LocationMemoryTimeFilter.Custom(startInclusive = LocalDate(2026, 3, 1)),
                ).first()

            assertEquals(1, result.size)
            assertEquals(listOf("Inside window"), result.first().memories.map { it.preview })
        }

    @Test
    fun `custom range rejects end before start`() {
        assertFailsWith<IllegalArgumentException> {
            LocationMemoryTimeFilter.Custom(
                startInclusive = LocalDate(2026, 3, 10),
                endInclusive = LocalDate(2026, 3, 9),
            )
        }
    }

    private fun textNote(
        createdAt: Instant,
        text: String,
        location: NoteLocation,
    ) = JournalNote.Text(
        creationTimestamp = createdAt,
        lastUpdated = createdAt,
        content = text,
        location = location,
    )

    private fun fixedClock(now: Instant): Clock =
        object : Clock {
            override fun now(): Instant = now
        }

    private fun coordinateLocation(
        latitude: Double,
        longitude: Double,
    ) = NoteLocation(
        coordinates =
            NoteCoordinates(
                latitude = latitude,
                longitude = longitude,
            ),
    )

    private class FakeJournalNotesRepository(
        notes: List<JournalNote>,
    ) : JournalNotesRepository {
        private val state = MutableStateFlow(notes)

        override val allNotesObserved: Flow<List<JournalNote>> = state

        override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = emptyFlow()

        override fun observeNotesInRange(
            start: Instant,
            end: Instant,
        ): Flow<List<JournalNote>> = emptyFlow()

        override fun observeNotesPage(
            pageSize: Int,
            offset: Int,
        ): Flow<List<JournalNote>> = emptyFlow()

        override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = emptyFlow()

        override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = emptyFlow()

        override suspend fun getNoteById(noteId: Uuid): JournalNote? = state.value.firstOrNull { it.uid == noteId }

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

        override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = emptyList()
    }
}
