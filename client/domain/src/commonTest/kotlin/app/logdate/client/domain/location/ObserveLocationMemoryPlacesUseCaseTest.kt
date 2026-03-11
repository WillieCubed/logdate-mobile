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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

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
                ).invoke(LocationMemoryTimeFilter.Last30Days).first()

            assertEquals(listOf("Downtown", "Beach"), result.map { it.semanticName })
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
    }
}
