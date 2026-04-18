package app.logdate.wear.presentation.recording

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.data.storage.StorageSpaceChecker
import app.logdate.wear.health.NoteHealthAnnotator
import app.logdate.wear.recording.WearAudioRecordingManager
import app.logdate.wear.sync.WearDataLayerClient
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
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class WearRecordingViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var recordingManager: WearAudioRecordingManager
    private lateinit var notesRepository: JournalNotesRepository
    private lateinit var storageChecker: StorageSpaceChecker
    private lateinit var noteHealthAnnotator: NoteHealthAnnotator
    private lateinit var dataLayerClient: WearDataLayerClient
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
        noteHealthAnnotator = mockk(relaxed = true)
        dataLayerClient = mockk(relaxed = true)
        testClock = TestClock()

        coEvery { dataLayerClient.isPhoneConnected(any()) } returns false

        every { recordingManager.getAudioLevelFlow() } returns audioLevelFlow
        coEvery { notesRepository.create(any<JournalNote>()) } returns Uuid.random()
        coEvery { storageChecker.getAvailableStorageSpace() } returns plentyOfStorage
        coEvery { recordingManager.startRecording() } returns true
        coEvery { recordingManager.pauseRecording() } returns true
        coEvery { recordingManager.resumeRecording() } returns true
        coEvery { recordingManager.stopRecording() } returns "/fake/audio.aac"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): WearRecordingViewModel =
        WearRecordingViewModel(recordingManager, notesRepository, storageChecker, noteHealthAnnotator, dataLayerClient, testClock)

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
    fun `initial state is READY`() =
        runTest {
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
    fun `touch down transitions to RECORDING when storage available`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onTouchDown()
            advanceTimeBy(200)

            assertEquals(RecordingPhase.RECORDING, viewModel.uiState.value.phase)
            viewModel.cancelScope()
        }

    @Test
    fun `touch down starts recording via manager`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onTouchDown()
            advanceTimeBy(200)

            coVerify { recordingManager.startRecording() }
            viewModel.cancelScope()
        }

    @Test
    fun `touch down checks storage space before recording`() =
        runTest {
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
    fun `touch down transitions to ERROR when storage insufficient`() =
        runTest {
            coEvery { storageChecker.getAvailableStorageSpace() } returns insufficientStorage
            val viewModel = createViewModel()

            viewModel.onTouchDown()
            advanceUntilIdle()

            assertEquals(RecordingPhase.ERROR, viewModel.uiState.value.phase)
            assertNotNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `touch down does not start recording when storage insufficient`() =
        runTest {
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
    fun `touch down transitions to ERROR when recording fails to start`() =
        runTest {
            coEvery { recordingManager.startRecording() } returns false
            val viewModel = createViewModel()

            viewModel.onTouchDown()
            advanceUntilIdle()

            assertEquals(RecordingPhase.ERROR, viewModel.uiState.value.phase)
            assertNotNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `touch down transitions to ERROR when recording throws exception`() =
        runTest {
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
    fun `touch down is ignored when in RECORDING phase`() =
        runTest {
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
    // onTouchUp -- pauses recording (release to pause)
    // -----------------------------------------------------------------------

    @Test
    fun `touch up pauses recording via manager`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onTouchDown()
            advanceTimeBy(200)
            testClock.advanceBy(1000)

            viewModel.onTouchUp()
            advanceTimeBy(200)

            coVerify { recordingManager.pauseRecording() }
            viewModel.cancelScope()
        }

    @Test
    fun `touch up transitions to PAUSED with sufficient duration`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onTouchDown()
            advanceTimeBy(200)
            testClock.advanceBy(1000)

            viewModel.onTouchUp()
            advanceTimeBy(200)

            assertEquals(RecordingPhase.PAUSED, viewModel.uiState.value.phase)
            viewModel.cancelScope()
        }

    @Test
    fun `paused state includes accumulated duration`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onTouchDown()
            advanceTimeBy(200)
            testClock.advanceBy(2500)

            viewModel.onTouchUp()
            advanceTimeBy(200)

            assertEquals(2500, viewModel.uiState.value.recordingDurationMs)
            viewModel.cancelScope()
        }

    @Test
    fun `touch up does not create note in repository`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onTouchDown()
            advanceTimeBy(200)
            testClock.advanceBy(1000)

            viewModel.onTouchUp()
            advanceTimeBy(200)

            coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
            viewModel.cancelScope()
        }

    // -----------------------------------------------------------------------
    // onTouchUp -- recording too short
    // -----------------------------------------------------------------------

    @Test
    fun `touch up shows TOO_SHORT when duration under threshold`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onTouchDown()
            advanceTimeBy(200)
            // Do NOT advance test clock — duration stays at 0ms (< 500ms threshold)
            viewModel.onTouchUp()
            advanceTimeBy(50)

            coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
            assertEquals(RecordingPhase.TOO_SHORT, viewModel.uiState.value.phase)
            viewModel.cancelScope()
        }

    @Test
    fun `touch up resets to READY after showing TOO_SHORT`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onTouchDown()
            advanceTimeBy(200)
            viewModel.onTouchUp()
            advanceTimeBy(50)

            assertEquals(RecordingPhase.TOO_SHORT, viewModel.uiState.value.phase)

            advanceTimeBy(2000)
            assertEquals(RecordingPhase.READY, viewModel.uiState.value.phase)
            viewModel.cancelScope()
        }

    @Test
    fun `touch up does not save note when duration is too short`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onTouchDown()
            advanceTimeBy(200)
            viewModel.onTouchUp()
            advanceTimeBy(2000)

            coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
            viewModel.cancelScope()
        }

    // -----------------------------------------------------------------------
    // onTouchUp -- pause fails
    // -----------------------------------------------------------------------

    @Test
    fun `touch up transitions to ERROR when pause fails`() =
        runTest {
            coEvery { recordingManager.pauseRecording() } returns false
            val viewModel = createViewModel()
            viewModel.onTouchDown()
            advanceTimeBy(200)
            testClock.advanceBy(1000)

            viewModel.onTouchUp()
            advanceTimeBy(200)

            assertEquals(RecordingPhase.ERROR, viewModel.uiState.value.phase)
            viewModel.cancelScope()
        }

    // -----------------------------------------------------------------------
    // onTouchUp -- guard against wrong phase
    // -----------------------------------------------------------------------

    @Test
    fun `touch up is ignored when not in RECORDING phase`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onTouchUp()
            advanceUntilIdle()

            coVerify(exactly = 0) { recordingManager.pauseRecording() }
            coVerify(exactly = 0) { recordingManager.stopRecording() }
        }

    // -----------------------------------------------------------------------
    // Resume from PAUSED (touch down while paused)
    // -----------------------------------------------------------------------

    @Test
    fun `touch down from PAUSED resumes recording`() =
        runTest {
            val viewModel = createViewModel()
            // Start -> pause
            viewModel.onTouchDown()
            advanceTimeBy(200)
            testClock.advanceBy(1000)
            viewModel.onTouchUp()
            advanceTimeBy(200)
            assertEquals(RecordingPhase.PAUSED, viewModel.uiState.value.phase)

            // Resume
            viewModel.onTouchDown()
            advanceTimeBy(200)

            coVerify { recordingManager.resumeRecording() }
            assertEquals(RecordingPhase.RECORDING, viewModel.uiState.value.phase)
            viewModel.cancelScope()
        }

    @Test
    fun `duration accumulates across pause-resume cycles`() =
        runTest {
            val viewModel = createViewModel()
            // First segment: 1000ms
            viewModel.onTouchDown()
            advanceTimeBy(200)
            testClock.advanceBy(1000)
            viewModel.onTouchUp()
            advanceTimeBy(200)
            assertEquals(1000, viewModel.uiState.value.recordingDurationMs)

            // Second segment: 500ms
            testClock.advanceBy(5000) // paused time doesn't count
            viewModel.onTouchDown()
            advanceTimeBy(200)
            testClock.advanceBy(500)
            viewModel.onTouchUp()
            advanceTimeBy(200)

            assertEquals(1500, viewModel.uiState.value.recordingDurationMs)
            viewModel.cancelScope()
        }

    @Test
    fun `touch down from PAUSED transitions to ERROR when resume fails`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onTouchDown()
            advanceTimeBy(200)
            testClock.advanceBy(1000)
            viewModel.onTouchUp()
            advanceTimeBy(200)

            coEvery { recordingManager.resumeRecording() } returns false
            viewModel.onTouchDown()
            advanceTimeBy(200)

            assertEquals(RecordingPhase.ERROR, viewModel.uiState.value.phase)
            viewModel.cancelScope()
        }

    // -----------------------------------------------------------------------
    // save() -- explicit save from PAUSED
    // -----------------------------------------------------------------------

    @Test
    fun `save stops recording and creates audio note`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onTouchDown()
            advanceTimeBy(200)
            testClock.advanceBy(2000)
            viewModel.onTouchUp()
            advanceTimeBy(200)

            viewModel.save()
            advanceTimeBy(2000)

            coVerify { recordingManager.stopRecording() }
            coVerify {
                notesRepository.create(
                    match { note ->
                        note is JournalNote.Audio && note.mediaRef == "/fake/audio.aac"
                    },
                )
            }
        }

    @Test
    fun `save transitions through SAVING to SAVED`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onTouchDown()
            advanceTimeBy(200)
            testClock.advanceBy(2000)
            viewModel.onTouchUp()
            advanceTimeBy(200)

            viewModel.save()
            advanceTimeBy(2000)

            assertEquals(RecordingPhase.SAVED, viewModel.uiState.value.phase)
        }

    @Test
    fun `save includes accumulated duration`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onTouchDown()
            advanceTimeBy(200)
            testClock.advanceBy(3000)
            viewModel.onTouchUp()
            advanceTimeBy(200)

            viewModel.save()
            advanceTimeBy(2000)

            assertEquals(3000, viewModel.uiState.value.savedDurationMs)
        }

    @Test
    fun `save emits NavigateBack after delay`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onTouchDown()
            advanceTimeBy(200)
            testClock.advanceBy(1000)
            viewModel.onTouchUp()
            advanceTimeBy(200)

            viewModel.events.test {
                viewModel.save()
                advanceTimeBy(2000)

                assertEquals(RecordingScreenEvent.NavigateBack, awaitItem())
            }
        }

    @Test
    fun `save annotates note with health data`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onTouchDown()
            advanceTimeBy(200)
            testClock.advanceBy(2000)
            viewModel.onTouchUp()
            advanceTimeBy(200)

            viewModel.save()
            advanceTimeBy(2000)

            coVerify { noteHealthAnnotator.annotate(any()) }
        }

    @Test
    fun `save is ignored when not PAUSED`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.save()
            advanceUntilIdle()

            coVerify(exactly = 0) { recordingManager.stopRecording() }
        }

    @Test
    fun `save transitions to ERROR when file path is null`() =
        runTest {
            coEvery { recordingManager.stopRecording() } returns null
            val viewModel = createViewModel()
            viewModel.onTouchDown()
            advanceTimeBy(200)
            testClock.advanceBy(1000)
            viewModel.onTouchUp()
            advanceTimeBy(200)

            viewModel.save()
            advanceTimeBy(200)

            assertEquals(RecordingPhase.ERROR, viewModel.uiState.value.phase)
            viewModel.cancelScope()
        }

    // -----------------------------------------------------------------------
    // discard()
    // -----------------------------------------------------------------------

    @Test
    fun `discard stops recording and resets to READY`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onTouchDown()
            advanceTimeBy(200)
            testClock.advanceBy(1000)
            viewModel.onTouchUp()
            advanceTimeBy(200)

            viewModel.discard()
            advanceTimeBy(200)

            coVerify { recordingManager.stopRecording() }
            assertEquals(RecordingPhase.READY, viewModel.uiState.value.phase)
            coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
        }

    @Test
    fun `discard is ignored when in READY phase`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.discard()
            advanceUntilIdle()

            coVerify(exactly = 0) { recordingManager.stopRecording() }
        }

    // -----------------------------------------------------------------------
    // onNavigatedBack
    // -----------------------------------------------------------------------

    @Test
    fun `onNavigatedBack resets state to READY`() =
        runTest {
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
    fun `audio level flow collection starts on touch down`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onTouchDown()
            advanceTimeBy(200)

            verify { recordingManager.getAudioLevelFlow() }
            viewModel.cancelScope()
        }

    @Test
    fun `audio levels are collected while recording`() =
        runTest {
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
    fun `audio levels list is bounded to last 50 entries`() =
        runTest {
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

    private class TestClock(
        private var currentMs: Long = 1_000_000L,
    ) : Clock {
        fun advanceBy(ms: Long) {
            currentMs += ms
        }

        override fun now(): Instant = Instant.fromEpochMilliseconds(currentMs)
    }
}
