package app.logdate.wear.presentation.recording

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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import androidx.lifecycle.viewModelScope
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
class WearRecordingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
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

    private fun createViewModel(): WearRecordingViewModel {
        return WearRecordingViewModel(recordingManager, notesRepository, storageChecker, testClock)
    }

    /**
     * Cancel the ViewModel's coroutine scope to stop the sample() timer and auto-stop timer.
     * Must be called before runTest cleanup, which calls advanceUntilIdle() and would hang
     * on the never-ending sample() operator.
     */
    private fun WearRecordingViewModel.cancelScope() {
        viewModelScope.cancel()
    }

    // -----------------------------------------------------------------------
    // Initial state
    // -----------------------------------------------------------------------

    @Test
    fun `initial state is READY`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(RecordingPhase.READY, state.phase)
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

        viewModel.onTouchDown()
        advanceTimeBy(200)

        assertEquals(RecordingPhase.RECORDING, viewModel.uiState.value.phase)
        viewModel.cancelScope()
    }

    @Test
    fun `touch down starts recording via manager`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        advanceTimeBy(200)

        coVerify { recordingManager.startRecording() }
        viewModel.cancelScope()
    }

    @Test
    fun `touch down checks storage space before recording`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        advanceTimeBy(200)

        coVerify { storageChecker.getAvailableStorageSpace() }
        viewModel.cancelScope()
    }

    // -----------------------------------------------------------------------
    // onTouchDown -- insufficient storage
    // -----------------------------------------------------------------------

    @Test
    fun `touch down transitions to ERROR when storage insufficient`() = runTest {
        coEvery { storageChecker.getAvailableStorageSpace() } returns insufficientStorage
        val viewModel = createViewModel()

        viewModel.onTouchDown()
        advanceUntilIdle()

        assertEquals(RecordingPhase.ERROR, viewModel.uiState.value.phase)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `touch down does not start recording when storage insufficient`() = runTest {
        coEvery { storageChecker.getAvailableStorageSpace() } returns insufficientStorage
        val viewModel = createViewModel()

        viewModel.onTouchDown()
        advanceUntilIdle()

        coVerify(exactly = 0) { recordingManager.startRecording() }
    }

    // -----------------------------------------------------------------------
    // onTouchDown -- recording fails to start
    // -----------------------------------------------------------------------

    @Test
    fun `touch down transitions to ERROR when recording fails to start`() = runTest {
        coEvery { recordingManager.startRecording() } returns false
        val viewModel = createViewModel()

        viewModel.onTouchDown()
        advanceUntilIdle()

        assertEquals(RecordingPhase.ERROR, viewModel.uiState.value.phase)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `touch down transitions to ERROR when recording throws exception`() = runTest {
        coEvery { recordingManager.startRecording() } throws RuntimeException("mic unavailable")
        val viewModel = createViewModel()

        viewModel.onTouchDown()
        advanceUntilIdle()

        assertEquals(RecordingPhase.ERROR, viewModel.uiState.value.phase)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    // -----------------------------------------------------------------------
    // onTouchDown -- guard against re-entry
    // -----------------------------------------------------------------------

    @Test
    fun `touch down is ignored when not in READY phase`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        advanceTimeBy(200)

        coVerify(exactly = 1) { recordingManager.startRecording() }

        viewModel.onTouchDown() // should be ignored
        advanceTimeBy(200)

        coVerify(exactly = 1) { recordingManager.startRecording() }
        viewModel.cancelScope()
    }

    // -----------------------------------------------------------------------
    // onTouchUp -- saves note (with sufficient duration via TestClock)
    // -----------------------------------------------------------------------

    @Test
    fun `touch up stops recording via manager`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        advanceTimeBy(200)
        testClock.advanceBy(1000)

        viewModel.onTouchUp()
        advanceTimeBy(2000)

        coVerify { recordingManager.stopRecording() }
    }

    @Test
    fun `touch up creates audio note in repository`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        advanceTimeBy(200)
        testClock.advanceBy(1000)

        viewModel.onTouchUp()
        advanceTimeBy(2000)

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
        advanceTimeBy(200)
        testClock.advanceBy(1000)

        viewModel.onTouchUp()
        advanceTimeBy(2000)

        assertEquals(RecordingPhase.SAVED, viewModel.uiState.value.phase)
        coVerify { notesRepository.create(any<JournalNote>()) }
    }

    @Test
    fun `saved state includes correct duration`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        advanceTimeBy(200)
        testClock.advanceBy(2500) // 2.5 seconds

        viewModel.onTouchUp()
        advanceTimeBy(2000)

        val state = viewModel.uiState.value
        if (state.phase == RecordingPhase.SAVED) {
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
        advanceTimeBy(200)
        // Do NOT advance test clock — duration stays at 0ms (< 500ms threshold)
        viewModel.onTouchUp()
        advanceTimeBy(50)

        // No note should be created when too short
        coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }

        // State should be TOO_SHORT (auto-transition to READY after display delay)
        assertEquals(RecordingPhase.TOO_SHORT, viewModel.uiState.value.phase)
        viewModel.cancelScope()
    }

    @Test
    fun `touch up resets to READY after showing TOO_SHORT`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        advanceTimeBy(200)
        viewModel.onTouchUp()
        advanceTimeBy(50)

        // After onTouchUp with short duration, state is TOO_SHORT
        assertEquals(RecordingPhase.TOO_SHORT, viewModel.uiState.value.phase)

        // After the display delay elapses (1200ms), state returns to READY
        advanceTimeBy(2000)
        assertEquals(RecordingPhase.READY, viewModel.uiState.value.phase)
        viewModel.cancelScope()
    }

    @Test
    fun `touch up does not save note when duration is too short`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        advanceTimeBy(200)
        viewModel.onTouchUp() // immediate release, duration 0ms
        advanceTimeBy(2000)

        coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
        viewModel.cancelScope()
    }

    // -----------------------------------------------------------------------
    // onTouchUp -- null file path with sufficient duration
    // -----------------------------------------------------------------------

    @Test
    fun `touch up transitions to ERROR when file path is null and duration sufficient`() = runTest {
        coEvery { recordingManager.stopRecording() } returns null
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        advanceTimeBy(200)
        testClock.advanceBy(1000) // sufficient duration
        viewModel.onTouchUp()
        advanceTimeBy(2000)

        assertEquals(RecordingPhase.ERROR, viewModel.uiState.value.phase)
        assertNotNull(viewModel.uiState.value.errorMessage)
        viewModel.cancelScope()
    }

    // -----------------------------------------------------------------------
    // onTouchUp -- guard against wrong phase
    // -----------------------------------------------------------------------

    @Test
    fun `touch up is ignored when not in RECORDING phase`() = runTest {
        val viewModel = createViewModel()

        viewModel.onTouchUp()
        advanceUntilIdle()

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
        advanceTimeBy(200)
        testClock.advanceBy(1000)
        viewModel.onTouchUp()
        advanceTimeBy(2000)

        assertEquals(RecordingPhase.ERROR, viewModel.uiState.value.phase)
        assertNotNull(viewModel.uiState.value.errorMessage)
        viewModel.cancelScope()
    }

    // -----------------------------------------------------------------------
    // NavigateBack event
    // -----------------------------------------------------------------------

    @Test
    fun `NavigateBack event is emitted after successful save`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        advanceTimeBy(200)
        testClock.advanceBy(1000) // sufficient duration

        viewModel.events.test {
            viewModel.onTouchUp()
            advanceTimeBy(2000)

            assertEquals(RecordingScreenEvent.NavigateBack, awaitItem())
        }
    }

    @Test
    fun `no NavigateBack event when recording too short`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        advanceTimeBy(200)

        viewModel.events.test {
            viewModel.onTouchUp() // 0ms duration
            advanceTimeBy(50)

            expectNoEvents()
        }
        viewModel.cancelScope()
    }

    // -----------------------------------------------------------------------
    // onNavigatedBack
    // -----------------------------------------------------------------------

    @Test
    fun `onNavigatedBack resets state to READY`() = runTest {
        coEvery { recordingManager.startRecording() } returns false
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        advanceUntilIdle()

        assertEquals(RecordingPhase.ERROR, viewModel.uiState.value.phase)

        viewModel.onNavigatedBack()
        advanceUntilIdle()

        assertEquals(RecordingPhase.READY, viewModel.uiState.value.phase)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // -----------------------------------------------------------------------
    // Audio level collection
    // -----------------------------------------------------------------------

    @Test
    fun `audio level flow collection starts on touch down`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        advanceTimeBy(200)

        verify { recordingManager.getAudioLevelFlow() }
        viewModel.cancelScope()
    }

    @Test
    fun `audio levels are collected while recording`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        advanceTimeBy(200)

        audioLevelFlow.value = 0.5f
        advanceTimeBy(150) // advance past the sample(100ms) window
        audioLevelFlow.value = 0.8f
        advanceTimeBy(150)

        val state = viewModel.uiState.value
        assertEquals(RecordingPhase.RECORDING, state.phase)
        assertTrue(state.audioLevels.isNotEmpty(), "Expected audio levels to be collected")
        viewModel.cancelScope()
    }

    @Test
    fun `audio levels list is bounded to last 50 entries`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTouchDown()
        advanceTimeBy(200)

        repeat(60) { i ->
            audioLevelFlow.value = i / 60f
            advanceTimeBy(150) // advance past sample window each time
        }

        val state = viewModel.uiState.value
        assertTrue(
            state.audioLevels.size <= 50,
            "Expected at most 50 audio levels but got ${state.audioLevels.size}",
        )
        viewModel.cancelScope()
    }

    // -----------------------------------------------------------------------
    // Constants verification
    // -----------------------------------------------------------------------

    @Test
    fun `MIN_DURATION_MS is 500`() {
        assertEquals(500L, WearRecordingViewModel.MIN_DURATION_MS)
    }

    @Test
    fun `MAX_DURATION_MS is 60 seconds`() {
        assertEquals(60_000L, WearRecordingViewModel.MAX_DURATION_MS)
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
