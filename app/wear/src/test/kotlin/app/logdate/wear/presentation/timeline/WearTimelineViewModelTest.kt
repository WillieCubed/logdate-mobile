package app.logdate.wear.presentation.timeline

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WearTimelineViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val fixedTime = Instant.fromEpochMilliseconds(1_710_000_000_000) // 2024-03-09

    private fun createNote(
        type: String = "text",
        content: String = "test",
        hoursOffset: Int = 0,
    ): JournalNote {
        val timestamp = Instant.fromEpochMilliseconds(
            fixedTime.toEpochMilliseconds() + hoursOffset * 3_600_000L,
        )
        return when (type) {
            "audio" -> JournalNote.Audio(
                uid = Uuid.random(),
                creationTimestamp = timestamp,
                lastUpdated = timestamp,
                mediaRef = "/recording.aac",
                durationMs = 4200,
            )
            else -> JournalNote.Text(
                uid = Uuid.random(),
                creationTimestamp = timestamp,
                lastUpdated = timestamp,
                content = content,
            )
        }
    }

    private fun createViewModel(
        notes: List<JournalNote> = emptyList(),
    ): WearTimelineViewModel {
        val repository = mockk<JournalNotesRepository>()
        every { repository.observeRecentNotes(any()) } returns flowOf(notes)
        every { repository.observeNotesForDay(any()) } returns flowOf(emptyList())
        return WearTimelineViewModel(repository)
    }

    // =======================================================================
    // Initial state
    // =======================================================================

    @Test
    fun `initial state shows loading`() = runTest {
        val repository = mockk<JournalNotesRepository>()
        // Don't emit anything yet
        every { repository.observeRecentNotes(any()) } returns MutableStateFlow(emptyList())
        val vm = WearTimelineViewModel(repository)

        // Before emission, state should have no days
        val state = vm.uiState.first()
        assertTrue(state.days.isEmpty())
    }

    // =======================================================================
    // Day grouping
    // =======================================================================

    @Test
    fun `groups notes by day`() = runTest {
        val today = createNote(content = "Today's note", hoursOffset = 0)
        val yesterday = createNote(content = "Yesterday's note", hoursOffset = -24)

        val vm = createViewModel(listOf(today, yesterday))
        val state = vm.uiState.first()

        assertEquals(2, state.days.size)
    }

    @Test
    fun `multiple notes on same day grouped together`() = runTest {
        val note1 = createNote(content = "Morning", hoursOffset = 0)
        val note2 = createNote(content = "Afternoon", hoursOffset = 3)
        val note3 = createNote(content = "Evening", hoursOffset = 6)

        val vm = createViewModel(listOf(note1, note2, note3))
        val state = vm.uiState.first()

        assertEquals(1, state.days.size)
        assertEquals(3, state.days[0].entryCount)
    }

    @Test
    fun `days are sorted most recent first`() = runTest {
        val olderNote = createNote(content = "Old", hoursOffset = -48)
        val newerNote = createNote(content = "New", hoursOffset = 0)

        val vm = createViewModel(listOf(olderNote, newerNote))
        val state = vm.uiState.first()

        assertEquals(2, state.days.size)
        assertTrue(state.days[0].date > state.days[1].date)
    }

    // =======================================================================
    // Entry counts
    // =======================================================================

    @Test
    fun `entry count reflects note count per day`() = runTest {
        val notes = (0..4).map { i ->
            createNote(content = "Note $i", hoursOffset = i)
        }

        val vm = createViewModel(notes)
        val state = vm.uiState.first()

        assertEquals(1, state.days.size)
        assertEquals(5, state.days[0].entryCount)
    }

    // =======================================================================
    // Mood extraction
    // =======================================================================

    @Test
    fun `extracts mood from mood-tagged note`() = runTest {
        val moodNote = createNote(content = "#mood:good Feeling great")

        val vm = createViewModel(listOf(moodNote))
        val state = vm.uiState.first()

        assertEquals("good", state.days[0].latestMood)
    }

    @Test
    fun `null mood when no mood notes exist`() = runTest {
        val plainNote = createNote(content = "Just a regular note")

        val vm = createViewModel(listOf(plainNote))
        val state = vm.uiState.first()

        assertNull(state.days[0].latestMood)
    }

    // =======================================================================
    // Preview text
    // =======================================================================

    @Test
    fun `preview text from first text note`() = runTest {
        val note = createNote(content = "Had a great day today")

        val vm = createViewModel(listOf(note))
        val state = vm.uiState.first()

        assertEquals("Had a great day today", state.days[0].previewText)
    }

    @Test
    fun `preview text truncated to 50 chars`() = runTest {
        val longContent = "A".repeat(100)
        val note = createNote(content = longContent)

        val vm = createViewModel(listOf(note))
        val state = vm.uiState.first()

        assertTrue(state.days[0].previewText!!.length <= 50)
    }

    @Test
    fun `no preview text for audio-only day`() = runTest {
        val audioNote = createNote(type = "audio")

        val vm = createViewModel(listOf(audioNote))
        val state = vm.uiState.first()

        assertNull(state.days[0].previewText)
    }

    // =======================================================================
    // Empty state
    // =======================================================================

    @Test
    fun `empty state when no notes exist`() = runTest {
        val vm = createViewModel(emptyList())
        val state = vm.uiState.first()

        assertTrue(state.days.isEmpty())
        assertFalse(state.isLoading)
    }

    // =======================================================================
    // Day selection for detail view
    // =======================================================================

    @Test
    fun `selectDay loads notes for that day`() = runTest {
        val repository = mockk<JournalNotesRepository>()
        val targetDate = LocalDate(2024, 3, 9)
        val dayNotes = listOf(
            createNote(content = "Morning entry"),
            createNote(type = "audio"),
        )
        every { repository.observeRecentNotes(any()) } returns flowOf(
            listOf(createNote(content = "test")),
        )
        every { repository.observeNotesForDay(targetDate) } returns flowOf(dayNotes)

        val vm = WearTimelineViewModel(repository)
        vm.selectDay(targetDate)

        val detail = vm.selectedDayState.first()
        assertEquals(targetDate, detail?.date)
        assertEquals(2, detail?.entries?.size)
    }

    @Test
    fun `clearSelection resets selected day`() = runTest {
        val repository = mockk<JournalNotesRepository>()
        val targetDate = LocalDate(2024, 3, 9)
        every { repository.observeRecentNotes(any()) } returns flowOf(emptyList())
        every { repository.observeNotesForDay(targetDate) } returns flowOf(emptyList())

        val vm = WearTimelineViewModel(repository)
        vm.selectDay(targetDate)
        vm.clearSelection()

        val detail = vm.selectedDayState.first()
        assertNull(detail)
    }
}
