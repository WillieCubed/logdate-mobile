package app.logdate.wear.presentation.memories

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import app.logdate.wear.sync.WearDataLayerClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests [WearVoiceMemoriesViewModel]: the newest-first list of voice memories on the watch, the way
 * older ones are paged in, and the request that asks the phone for any it has not sent yet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearVoiceMemoriesViewModelTest {
    private val recent = MutableStateFlow<List<JournalNote.Audio>>(emptyList())
    private val notesRepository = mockk<JournalNotesRepository>()
    private val dataLayerClient = mockk<WearDataLayerClient>(relaxed = true)
    private val base = Instant.fromEpochMilliseconds(1_710_000_000_000)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { notesRepository.observeRecentAudioNotes(any()) } returns recent
        coEvery { notesRepository.hasAudioNotesBefore(any()) } returns false
        coEvery { notesRepository.getAudioNotesBefore(any(), any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = WearVoiceMemoriesViewModel(notesRepository, dataLayerClient)

    @Test
    fun `nothing is loaded until the first list arrives`() =
        runTest {
            every { notesRepository.observeRecentAudioNotes(any()) } returns kotlinx.coroutines.flow.emptyFlow()

            assertFalse(viewModel().uiState.value.isLoaded)
        }

    @Test
    fun `an empty watch reports loaded with no memories`() =
        runTest {
            val state = viewModel().uiState.value

            assertTrue(state.isLoaded)
            assertTrue(state.memories.isEmpty())
            assertFalse(state.hasMore)
        }

    @Test
    fun `memories list the recordings newest first`() =
        runTest {
            val older = audioNote(base - 2.hours, durationMs = 12_000)
            val newer = audioNote(base - 1.hours, durationMs = 65_000)
            recent.value = listOf(older, newer)

            val memories = viewModel().uiState.value.memories

            assertEquals(listOf(newer.uid, older.uid), memories.map { it.noteId })
            assertEquals(65_000L, memories.first().durationMs)
            assertEquals(base - 1.hours, memories.first().createdAt)
        }

    @Test
    fun `a new recording appears at the top`() =
        runTest {
            val existing = audioNote(base - 2.hours)
            recent.value = listOf(existing)
            val viewModel = viewModel()

            val fresh = audioNote(base)
            recent.value = listOf(fresh, existing)
            runCurrent()

            assertEquals(listOf(fresh.uid, existing.uid), viewModel.uiState.value.memories.map { it.noteId })
        }

    @Test
    fun `has more is true when an older recording exists`() =
        runTest {
            val note = audioNote(base)
            recent.value = listOf(note)
            coEvery { notesRepository.hasAudioNotesBefore(note.creationTimestamp) } returns true

            assertTrue(viewModel().uiState.value.hasMore)
        }

    @Test
    fun `loading more appends the older page`() =
        runTest {
            val newest = audioNote(base)
            val olderA = audioNote(base - 1.hours)
            val olderB = audioNote(base - 2.hours)
            recent.value = listOf(newest)
            coEvery { notesRepository.hasAudioNotesBefore(newest.creationTimestamp) } returns true
            coEvery { notesRepository.getAudioNotesBefore(newest.creationTimestamp, WearVoiceMemoriesViewModel.PAGE_SIZE) } returns
                listOf(olderA, olderB)
            val viewModel = viewModel()

            viewModel.loadMore()
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(listOf(newest.uid, olderA.uid, olderB.uid), state.memories.map { it.noteId })
            assertFalse(state.isLoadingMore)
            assertFalse(state.hasMore)
        }

    @Test
    fun `loading more keeps paging from the oldest memory shown`() =
        runTest {
            val newest = audioNote(base)
            val older = audioNote(base - 1.hours)
            val oldest = audioNote(base - 2.hours)
            recent.value = listOf(newest)
            coEvery { notesRepository.hasAudioNotesBefore(any()) } returns true
            coEvery { notesRepository.getAudioNotesBefore(newest.creationTimestamp, any()) } returns listOf(older)
            coEvery { notesRepository.getAudioNotesBefore(older.creationTimestamp, any()) } returns listOf(oldest)
            val viewModel = viewModel()

            viewModel.loadMore()
            runCurrent()
            viewModel.loadMore()
            runCurrent()

            assertEquals(listOf(newest.uid, older.uid, oldest.uid), viewModel.uiState.value.memories.map { it.noteId })
        }

    @Test
    fun `loading more with no memories does nothing`() =
        runTest {
            val viewModel = viewModel()

            viewModel.loadMore()
            runCurrent()

            coVerify(exactly = 0) { notesRepository.getAudioNotesBefore(any(), any()) }
        }

    @Test
    fun `a memory in both the live list and an older page is shown once`() =
        runTest {
            val newest = audioNote(base)
            val other = audioNote(base - 1.hours)
            recent.value = listOf(newest, other)
            coEvery { notesRepository.hasAudioNotesBefore(any()) } returns true
            coEvery { notesRepository.getAudioNotesBefore(other.creationTimestamp, any()) } returns listOf(other)
            val viewModel = viewModel()

            viewModel.loadMore()
            runCurrent()

            assertEquals(listOf(newest.uid, other.uid), viewModel.uiState.value.memories.map { it.noteId })
        }

    @Test
    fun `opening the list asks the phone for recordings it has not sent`() =
        runTest {
            viewModel()

            coVerify(exactly = 1) { dataLayerClient.sendMessage(WearAudioRequestPaths.SYNC_REQUEST_PATH, any()) }
        }

    private fun audioNote(
        created: Instant,
        durationMs: Long = 5_000,
    ) = JournalNote.Audio(
        uid = Uuid.random(),
        creationTimestamp = created,
        lastUpdated = created,
        mediaRef = "/phone/${Uuid.random()}.m4a",
        durationMs = durationMs,
    )
}
