package app.logdate.wear.presentation.memories

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import app.logdate.wear.sync.WearDataLayerClient
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
 * the list widens a page at a time, and the request that asks the phone for any it has not sent yet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearVoiceMemoriesViewModelTest {
    private val all = MutableStateFlow<List<JournalNote.Audio>>(emptyList())
    private val notesRepository = mockk<JournalNotesRepository>()
    private val dataLayerClient = mockk<WearDataLayerClient>(relaxed = true)
    private val base = Instant.fromEpochMilliseconds(1_710_000_000_000)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { notesRepository.observeRecentAudioNotes(any()) } answers {
            val limit = firstArg<Int>()
            all.map { notes -> notes.sortedByDescending { it.creationTimestamp }.take(limit) }
        }
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
            all.value = listOf(older, newer)

            val memories = viewModel().uiState.value.memories

            assertEquals(listOf(newer.uid, older.uid), memories.map { it.noteId })
            assertEquals(65_000L, memories.first().durationMs)
            assertEquals(base - 1.hours, memories.first().createdAt)
        }

    @Test
    fun `a new recording appears at the top`() =
        runTest {
            val existing = audioNote(base - 2.hours)
            all.value = listOf(existing)
            val viewModel = viewModel()

            val fresh = audioNote(base)
            all.value = listOf(fresh, existing)
            runCurrent()

            assertEquals(listOf(fresh.uid, existing.uid), viewModel.uiState.value.memories.map { it.noteId })
        }

    @Test
    fun `the live list asks for one more than a page to know whether older recordings exist`() =
        runTest {
            viewModel()

            verify { notesRepository.observeRecentAudioNotes(WearVoiceMemoriesViewModel.PAGE_SIZE + 1) }
        }

    @Test
    fun `has more is true when more than a page of recordings exists`() =
        runTest {
            all.value = recordings(WearVoiceMemoriesViewModel.PAGE_SIZE + 1)

            val state = viewModel().uiState.value

            assertEquals(WearVoiceMemoriesViewModel.PAGE_SIZE, state.memories.size)
            assertTrue(state.hasMore)
        }

    @Test
    fun `has more is false when exactly a page of recordings exists`() =
        runTest {
            all.value = recordings(WearVoiceMemoriesViewModel.PAGE_SIZE)

            val state = viewModel().uiState.value

            assertEquals(WearVoiceMemoriesViewModel.PAGE_SIZE, state.memories.size)
            assertFalse(state.hasMore)
        }

    @Test
    fun `loading more widens the live list by a page`() =
        runTest {
            val extra = 5
            all.value = recordings(WearVoiceMemoriesViewModel.PAGE_SIZE + extra)
            val viewModel = viewModel()

            viewModel.loadMore()
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(WearVoiceMemoriesViewModel.PAGE_SIZE + extra, state.memories.size)
            assertFalse(state.hasMore)
            assertFalse(state.isLoadingMore)
        }

    @Test
    fun `a recording deleted from an older page leaves the list`() =
        runTest {
            val notes = recordings(WearVoiceMemoriesViewModel.PAGE_SIZE + 5)
            all.value = notes
            val viewModel = viewModel()
            viewModel.loadMore()
            runCurrent()
            val deleted = notes.last()

            all.value = notes - deleted
            runCurrent()

            assertFalse(deleted.uid in viewModel.uiState.value.memories.map { it.noteId })
            assertEquals(notes.size - 1, viewModel.uiState.value.memories.size)
        }

    @Test
    fun `recordings sharing a creation time across a page boundary are all reachable`() =
        runTest {
            val total = WearVoiceMemoriesViewModel.PAGE_SIZE + 2
            all.value = List(total) { audioNote(base) }
            val viewModel = viewModel()
            assertTrue(viewModel.uiState.value.hasMore)

            viewModel.loadMore()
            runCurrent()

            assertEquals(total, viewModel.uiState.value.memories.size)
        }

    @Test
    fun `loading more with nothing more does nothing`() =
        runTest {
            all.value = recordings(3)
            val viewModel = viewModel()

            viewModel.loadMore()
            runCurrent()

            verify(exactly = 0) { notesRepository.observeRecentAudioNotes(2 * WearVoiceMemoriesViewModel.PAGE_SIZE + 1) }
        }

    @Test
    fun `opening the list asks the phone for recordings it has not sent`() =
        runTest {
            viewModel()

            coVerify(exactly = 1) { dataLayerClient.sendMessage(WearAudioRequestPaths.SYNC_REQUEST_PATH, any()) }
        }

    /** [count] recordings, each an hour apart, newest first. */
    private fun recordings(count: Int) = List(count) { index -> audioNote(base - index.hours) }

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
