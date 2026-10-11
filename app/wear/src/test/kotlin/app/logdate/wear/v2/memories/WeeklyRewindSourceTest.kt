package app.logdate.wear.v2.memories

import app.cash.turbine.test
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.shared.model.Rewind
import app.logdate.shared.model.RewindContent
import app.logdate.wear.v2.today.TodayWindow
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

class WeeklyRewindSourceTest {
    private val zone = TimeZone.UTC
    private val today =
        TodayWindow(
            date = LocalDate(2026, 10, 10),
            timeZone = zone,
            start = LocalDate(2026, 10, 10).atStartOfDayIn(zone),
            end = LocalDate(2026, 10, 11).atStartOfDayIn(zone),
        )
    private val rewinds = mockk<RewindRepository>()
    private val notesRepository = mockk<JournalNotesRepository>()
    private val at = Instant.fromEpochMilliseconds(1_791_000_000_000)
    private val walk = JournalNote.Audio(mediaRef = "v.m4a", durationMs = 30_000L, creationTimestamp = at, lastUpdated = at)

    init {
        every { notesRepository.allNotesObserved } returns flowOf(emptyList())
    }

    @Test
    fun `an existing rewind subscription follows edits and deletes of its lead`() =
        runTest {
            val notes = MutableStateFlow<List<JournalNote>>(listOf(walk))
            every { notesRepository.allNotesObserved } returns notes
            coEvery { notesRepository.getNoteById(any()) } answers { notes.value.firstOrNull { it.uid == firstArg() } }
            every { rewinds.getAllRewinds() } returns flowOf(listOf(rewind("A quiet week", LocalDate(2026, 10, 4), walk.uid)))
            WeeklyRewindSource(rewinds, notesRepository).observe(today).test {
                assertEquals(walk, awaitItem()?.leadMoment)
                val edited = walk.copy(transcription = "A changed memory")
                notes.value = listOf(edited)
                assertEquals(edited, awaitItem()?.leadMoment)
                notes.value = emptyList()
                assertNull(awaitItem())
            }
        }

    @Test
    fun `the newest Rewind of the past week opens its first moment on the watch`() =
        runTest {
            val missing = Uuid.random()
            val photo = Uuid.random()
            coEvery { notesRepository.getNoteById(missing) } returns null
            coEvery { notesRepository.getNoteById(walk.uid) } returns walk
            every { rewinds.getAllRewinds() } returns
                flowOf(
                    listOf(
                        rewind("Two weeks ago", endedOn = LocalDate(2026, 9, 27), walk.uid),
                        rewind(
                            "A quiet week",
                            endedOn = LocalDate(2026, 10, 4),
                            RewindContent.Image(timestamp = at, sourceId = photo, uri = "p.jpg"),
                            missing,
                            walk.uid,
                        ),
                    ),
                )

            WeeklyRewindSource(rewinds, notesRepository).observe(today).test {
                assertEquals(WeeklyRewind("A quiet week", walk), awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `no Rewind this week means no card`() =
        runTest {
            every { rewinds.getAllRewinds() } returns flowOf(listOf(rewind("Two weeks ago", endedOn = LocalDate(2026, 9, 27))))

            WeeklyRewindSource(rewinds, notesRepository).observe(today).test {
                assertNull(awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `a Rewind with none of its moments on the watch has no card`() =
        runTest {
            val gone = Uuid.random()
            coEvery { notesRepository.getNoteById(gone) } returns null
            every { rewinds.getAllRewinds() } returns flowOf(listOf(rewind("A quiet week", endedOn = LocalDate(2026, 10, 4), gone)))

            WeeklyRewindSource(rewinds, notesRepository).observe(today).test {
                assertNull(awaitItem())
                awaitComplete()
            }
        }

    private fun rewind(
        title: String,
        endedOn: LocalDate,
        vararg sources: Any,
    ) = Rewind(
        uid = Uuid.random(),
        startDate = endedOn.atStartOfDayIn(zone),
        endDate = endedOn.atStartOfDayIn(zone),
        generationDate = endedOn.atStartOfDayIn(zone),
        label = "2026#40",
        title = title,
        content =
            sources.map { source ->
                source as? RewindContent ?: RewindContent.TextNote(timestamp = at, sourceId = source as Uuid, content = "note")
            },
    )
}
