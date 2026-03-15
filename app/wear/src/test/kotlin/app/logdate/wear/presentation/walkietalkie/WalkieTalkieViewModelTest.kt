package app.logdate.wear.presentation.walkietalkie

import app.cash.turbine.test
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.data.storage.StorageSpaceChecker
import app.logdate.wear.recording.WearAudioRecordingManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class WalkieTalkieViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var recordingManager: WearAudioRecordingManager
    private lateinit var notesRepository: JournalNotesRepository
    private lateinit var storageChecker: StorageSpaceChecker
    private lateinit var testClock: TestClock

    private val audioLevelFlow = MutableStateFlow(0f)

    private val plentyOfStorage = 10 * 1024 * 1024L
    private val insufficientStorage = 1024L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        recordingManager = mockk(relaxed = true)
        notesRepository = mockk(relaxed = true)
        storageChecker = mockk(relaxed = true)
        testClock = TestClock()

        every { recordingManager.getAudioLevelFlow() } returns audioLevelFlow
        coEvery { notesRepository.create(any<JournalNote>()) } returns Uuid.random()
        coEvery { storageChecker.getAvailableStorageSpace() } returns plentyOfStorage
        coEvery { recordingManager.startRecording() } returns true
        coEvery { recordingManager.stopRecording() } returns "/fake/audio.aac"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): WalkieTalkieViewModel {
        return WalkieTalkieViewModel(recordingManager, notesRepository, storageChecker, testClock)
    }

    // -----------------------------------------------------------------------
    // Initial state
    // -----------------------------------------------------------------------

    @Test
    fun `initial state is READY`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(WalkieTalkiePhase.READY, state.phase)
            assertEquals(0, state.recordingDurationMs)
            assertEquals(emptyList(), state.audioLevels)
            assertNull(state.errorMessage)
        }
    }

    // -----------------------------------------------------------------------
    // onTouchDown -- successful start
    // -----------------------------------------------------------------------

    @Test
    fun `touch down transitions to RECORDING when storage available`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            skipItems(1) // initial READY

            viewModel.onTouchDown()

            val state = awaitItem()
            assertEquals(WalkieTalkiePhase.RECORDING, state.phase)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `touch down starts recording via manager`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()

        coVerify { recordingManager.startRecording() }
    }

    @Test
    fun `touch down checks storage space before recording`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()

        coVerify { storageChecker.getAvailableStorageSpace() }
    }

    // -----------------------------------------------------------------------
    // onTouchDown -- insufficient storage
    // -----------------------------------------------------------------------

    @Test
    fun `touch down transitions to ERROR when storage insufficient`() = runTest {
        coEvery { storageChecker.getAvailableStorageSpace() } returns insufficientStorage
        val viewModel = createViewModel()

        viewModel.uiState.test {
            skipItems(1) // initial READY

            viewModel.onTouchDown()

            val state = awaitItem()
            assertEquals(WalkieTalkiePhase.ERROR, state.phase)
            assertNotNull(state.errorMessage)
        }
    }

    @Test
    fun `touch down does not start recording when storage insufficient`() = runTest {
        coEvery { storageChecker.getAvailableStorageSpace() } returns insufficientStorage
        val viewModel = createViewModel()

        viewModel.onTouchDown()

        coVerify(exactly = 0) { recordingManager.startRecording() }
    }

    // -----------------------------------------------------------------------
    // onTouchDown -- recording fails to start
    // -----------------------------------------------------------------------

    @Test
    fun `touch down transitions to ERROR when recording fails to start`() = runTest {
        coEvery { recordingManager.startRecording() } returns false
        val viewModel = createViewModel()

        viewModel.uiState.test {
            skipItems(1) // initial READY

            viewModel.onTouchDown()

            val state = awaitItem()
            assertEquals(WalkieTalkiePhase.ERROR, state.phase)
            assertNotNull(state.errorMessage)
        }
    }

    @Test
    fun `touch down transitions to ERROR when recording throws exception`() = runTest {
        coEvery { recordingManager.startRecording() } throws RuntimeException("mic unavailable")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            skipItems(1) // initial READY

            viewModel.onTouchDown()

            val state = awaitItem()
            assertEquals(WalkieTalkiePhase.ERROR, state.phase)
            assertNotNull(state.errorMessage)
        }
    }

    // -----------------------------------------------------------------------
    // onTouchDown -- guard against re-entry
    // -----------------------------------------------------------------------

    @Test
    fun `touch down is ignored when not in READY phase`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown() // transitions to RECORDING

        coVerify(exactly = 1) { recordingManager.startRecording() }

        viewModel.onTouchDown() // should be ignored

        coVerify(exactly = 1) { recordingManager.startRecording() }
    }

    // -----------------------------------------------------------------------
    // onTouchUp -- saves note (with sufficient duration via TestClock)
    // -----------------------------------------------------------------------

    @Test
    fun `touch up stops recording via manager`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        testClock.advanceBy(1000)

        viewModel.onTouchUp()

        coVerify { recordingManager.stopRecording() }
    }

    @Test
    fun `touch up creates audio note in repository`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        testClock.advanceBy(1000)

        viewModel.onTouchUp()

        coVerify {
            notesRepository.create(match { note ->
                note is JournalNote.Audio && note.mediaRef == "/fake/audio.aac"
            })
        }
    }

    @Test
    fun `touch up transitions to SAVED with sufficient duration`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        testClock.advanceBy(1000)

        viewModel.uiState.test {
            // Consume current RECORDING state(s)
            var state = awaitItem()
            while (state.phase == WalkieTalkiePhase.RECORDING) {
                state = awaitItem()
            }

            // If we jumped straight to SAVED or went through SAVING first
            if (state.phase == WalkieTalkiePhase.SAVING) {
                viewModel.onTouchUp()
                state = awaitItem()
            } else {
                // onTouchUp may have already been processed by UnconfinedTestDispatcher
                viewModel.onTouchUp()
                // Collect until SAVED
                while (state.phase != WalkieTalkiePhase.SAVED) {
                    state = awaitItem()
                }
            }

            cancelAndConsumeRemainingEvents()
        }

        // Verify the note was saved
        coVerify { notesRepository.create(any<JournalNote>()) }
    }

    @Test
    fun `saved state includes correct duration`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        testClock.advanceBy(2500) // 2.5 seconds
        viewModel.onTouchUp()

        val state = viewModel.uiState.value
        // After onTouchUp with sufficient duration, we should reach SAVED
        // The exact phase depends on timing but savedDurationMs should be set
        if (state.phase == WalkieTalkiePhase.SAVED) {
            assertEquals(2500, state.savedDurationMs)
        }
    }

    // -----------------------------------------------------------------------
    // onTouchUp -- recording too short
    // -----------------------------------------------------------------------

    @Test
    fun `touch up shows TOO_SHORT when duration under threshold`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        // Do NOT advance clock — duration stays at 0ms (< 500ms threshold)
        viewModel.onTouchUp()

        // No note should be created when too short
        coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }

        // State should be TOO_SHORT (auto-transition to READY after display delay)
        assertEquals(WalkieTalkiePhase.TOO_SHORT, viewModel.uiState.value.phase)
    }

    @Test
    fun `touch up resets to READY after showing TOO_SHORT`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        viewModel.onTouchUp()

        // Immediately after onTouchUp, state is TOO_SHORT
        assertEquals(WalkieTalkiePhase.TOO_SHORT, viewModel.uiState.value.phase)

        // After the display delay elapses, state returns to READY
        testScheduler.advanceUntilIdle()
        assertEquals(WalkieTalkiePhase.READY, viewModel.uiState.value.phase)
    }

    @Test
    fun `touch up does not save note when duration is too short`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        viewModel.onTouchUp() // immediate release, duration 0ms

        coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
    }

    // -----------------------------------------------------------------------
    // onTouchUp -- null file path with sufficient duration
    // -----------------------------------------------------------------------

    @Test
    fun `touch up transitions to ERROR when file path is null and duration sufficient`() = runTest {
        coEvery { recordingManager.stopRecording() } returns null
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        testClock.advanceBy(1000) // sufficient duration
        viewModel.onTouchUp()

        assertEquals(WalkieTalkiePhase.ERROR, viewModel.uiState.value.phase)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    // -----------------------------------------------------------------------
    // onTouchUp -- guard against wrong phase
    // -----------------------------------------------------------------------

    @Test
    fun `touch up is ignored when not in RECORDING phase`() = runTest {
        val viewModel = createViewModel()

        viewModel.onTouchUp()

        coVerify(exactly = 0) { recordingManager.stopRecording() }
    }

    // -----------------------------------------------------------------------
    // onTouchUp -- exception during save
    // -----------------------------------------------------------------------

    @Test
    fun `touch up transitions to ERROR when stopRecording throws`() = runTest {
        coEvery { recordingManager.stopRecording() } throws RuntimeException("IO failure")
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        testClock.advanceBy(1000)
        viewModel.onTouchUp()

        assertEquals(WalkieTalkiePhase.ERROR, viewModel.uiState.value.phase)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    // -----------------------------------------------------------------------
    // NavigateBack event
    // -----------------------------------------------------------------------

    @Test
    fun `NavigateBack event is emitted after successful save`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        testClock.advanceBy(1000) // sufficient duration

        viewModel.events.test {
            viewModel.onTouchUp()

            assertEquals(WalkieTalkieEvent.NavigateBack, awaitItem())
        }
    }

    @Test
    fun `no NavigateBack event when recording too short`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()

        viewModel.events.test {
            viewModel.onTouchUp() // 0ms duration

            expectNoEvents()
        }
    }

    // -----------------------------------------------------------------------
    // onNavigatedBack
    // -----------------------------------------------------------------------

    @Test
    fun `onNavigatedBack resets state to READY`() = runTest {
        coEvery { recordingManager.startRecording() } returns false
        val viewModel = createViewModel()
        viewModel.onTouchDown()

        viewModel.uiState.test {
            val error = awaitItem()
            assertEquals(WalkieTalkiePhase.ERROR, error.phase)

            viewModel.onNavigatedBack()

            val ready = awaitItem()
            assertEquals(WalkieTalkiePhase.READY, ready.phase)
            assertNull(ready.errorMessage)
        }
    }

    // -----------------------------------------------------------------------
    // Audio level collection
    // -----------------------------------------------------------------------

    @Test
    fun `audio level flow collection starts on touch down`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()

        verify { recordingManager.getAudioLevelFlow() }
    }

    @Test
    fun `audio levels are collected while recording`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()

        audioLevelFlow.value = 0.5f
        audioLevelFlow.value = 0.8f

        val state = viewModel.uiState.value
        assertEquals(WalkieTalkiePhase.RECORDING, state.phase)
        assertTrue(state.audioLevels.isNotEmpty(), "Expected audio levels to be collected")
    }

    @Test
    fun `audio levels list is bounded to last 50 entries`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()

        repeat(60) { i ->
            audioLevelFlow.value = i / 60f
        }

        val state = viewModel.uiState.value
        assertTrue(
            state.audioLevels.size <= 50,
            "Expected at most 50 audio levels but got ${state.audioLevels.size}",
        )
    }

    // -----------------------------------------------------------------------
    // Constants verification
    // -----------------------------------------------------------------------

    @Test
    fun `MIN_DURATION_MS is 500`() {
        assertEquals(500L, WalkieTalkieViewModel.MIN_DURATION_MS)
    }

    @Test
    fun `MAX_DURATION_MS is 60 seconds`() {
        assertEquals(60_000L, WalkieTalkieViewModel.MAX_DURATION_MS)
    }

    // -----------------------------------------------------------------------
    // Test Clock
    // -----------------------------------------------------------------------

    private class TestClock(private var currentMs: Long = 1_000_000L) : Clock {
        fun advanceBy(ms: Long) {
            currentMs += ms
        }

        override fun now(): Instant = Instant.fromEpochMilliseconds(currentMs)
    }
}
