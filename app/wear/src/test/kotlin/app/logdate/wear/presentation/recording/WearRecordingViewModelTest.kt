package app.logdate.wear.presentation.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.wear.haptic.WearHapticEngine
import app.logdate.wear.health.NoteHealthAnnotator
import app.logdate.wear.location.WearLocationCaptureCoordinator
import app.logdate.wear.presentation.common.SaveFeedback
import app.logdate.wear.recording.RecordingStartFailure
import app.logdate.wear.recording.WearRecorder
import app.logdate.wear.sync.WearDataLayerClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests [WearRecordingViewModel]: the gesture that starts and stops a recording, saving with an
 * undo window, and what the screen is told when something goes wrong.
 *
 * A press starts recording. A quick lift latches it until the next tap, and a long hold is
 * push-to-talk that saves on release. Nothing the user recorded may be dropped silently, so every
 * failure lands in the state as a specific error.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearRecordingViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val recorder = FakeRecorder()
    private val notesRepository = mockk<JournalNotesRepository>(relaxed = true)
    private val durationResolver = mockk<AudioDurationResolver>()
    private val noteHealthAnnotator = mockk<NoteHealthAnnotator>(relaxed = true)
    private val dataLayerClient = mockk<WearDataLayerClient>(relaxed = true)
    private val locationCaptureCoordinator = mockk<WearLocationCaptureCoordinator>()
    private val haptics = mockk<WearHapticEngine>(relaxed = true)
    private val hintStore = FakeHintStore()
    private val clock = TestClock()
    private val deletedFiles = mutableListOf<String>()
    private val viewModelStore = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { dataLayerClient.isPhoneConnected(any()) } returns false
        coEvery { locationCaptureCoordinator.captureForJournalEntry() } returns null
        coEvery { durationResolver.resolveDurationMs(any()) } returns 4_200L
        coEvery { notesRepository.create(any<JournalNote>()) } returns Uuid.random()
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    /** Created through a [ViewModelProvider] so clearing [viewModelStore] runs the real `onCleared`. */
    private fun createViewModel(): WearRecordingViewModel {
        val viewModel =
            WearRecordingViewModel(
                recorder = recorder,
                notesRepository = notesRepository,
                durationResolver = durationResolver,
                noteHealthAnnotator = noteHealthAnnotator,
                dataLayerClient = dataLayerClient,
                locationCaptureCoordinator = locationCaptureCoordinator,
                haptics = haptics,
                hintStore = hintStore,
                clock = clock,
                deleteFile = { deletedFiles += it },
            )
        val factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
            }
        return ViewModelProvider(viewModelStore, factory)["viewModel-${Uuid.random()}", WearRecordingViewModel::class.java]
    }

    private fun TestScope.press(viewModel: WearRecordingViewModel) {
        viewModel.onPress()
        runCurrent()
    }

    private fun TestScope.release(
        viewModel: WearRecordingViewModel,
        afterMs: Long,
    ) {
        clock.advanceMs(afterMs)
        viewModel.onRelease()
        runCurrent()
    }

    /** A tap: press, then lift straight away, leaving the recording latched. */
    private fun TestScope.tapToStart(viewModel: WearRecordingViewModel) {
        press(viewModel)
        release(viewModel, afterMs = 120)
    }

    // -----------------------------------------------------------------------
    // Starting
    // -----------------------------------------------------------------------

    @Test
    fun `initial state is ready`() =
        runTest {
            val viewModel = createViewModel()

            assertEquals(RecordingPhase.READY, viewModel.uiState.value.phase)
        }

    @Test
    fun `gesture hint shows until it has been seen`() =
        runTest {
            assertTrue(createViewModel().uiState.value.showGestureHint)

            hintStore.seen = true

            assertFalse(createViewModel().uiState.value.showGestureHint)
        }

    @Test
    fun `pressing the record surface starts recording at once`() =
        runTest {
            val viewModel = createViewModel()

            press(viewModel)

            assertEquals(RecordingPhase.RECORDING, viewModel.uiState.value.phase)
            assertEquals(1, recorder.starts)
            verify { haptics.startRecording() }
        }

    @Test
    fun `phase is starting until the recorder confirms`() =
        runTest {
            recorder.startDelay = 500.milliseconds
            val viewModel = createViewModel()

            viewModel.onPress()
            advanceTimeBy(100)

            assertEquals(RecordingPhase.STARTING, viewModel.uiState.value.phase)
        }

    @Test
    fun `a second press while starting does not start twice`() =
        runTest {
            recorder.startDelay = 500.milliseconds
            val viewModel = createViewModel()

            viewModel.onPress()
            viewModel.onPress()
            advanceTimeBy(600)
            runCurrent()

            assertEquals(1, recorder.starts)
        }

    @Test
    fun `a start the recorder refuses is reported with its reason`() =
        runTest {
            val reasons =
                mapOf(
                    RecordingStartFailure.MICROPHONE_PERMISSION_DENIED to RecordingError.MICROPHONE_PERMISSION_DENIED,
                    RecordingStartFailure.NOT_ENOUGH_STORAGE to RecordingError.NOT_ENOUGH_STORAGE,
                    RecordingStartFailure.RECORDER_UNAVAILABLE to RecordingError.RECORDER_UNAVAILABLE,
                )
            for ((failure, expected) in reasons) {
                recorder.startResult = false
                recorder.lastStartFailure = failure
                val viewModel = createViewModel()

                press(viewModel)

                assertEquals(RecordingPhase.ERROR, viewModel.uiState.value.phase)
                assertEquals(expected, viewModel.uiState.value.error)
            }
        }

    @Test
    fun `pressing after an error tries again`() =
        runTest {
            recorder.startResult = false
            recorder.lastStartFailure = RecordingStartFailure.RECORDER_UNAVAILABLE
            val viewModel = createViewModel()
            press(viewModel)
            recorder.startResult = true

            press(viewModel)

            assertEquals(RecordingPhase.RECORDING, viewModel.uiState.value.phase)
            assertNull(viewModel.uiState.value.error)
        }

    // -----------------------------------------------------------------------
    // Tap and hold
    // -----------------------------------------------------------------------

    @Test
    fun `lifting quickly latches the recording`() =
        runTest {
            val viewModel = createViewModel()

            tapToStart(viewModel)

            assertEquals(RecordingPhase.RECORDING, viewModel.uiState.value.phase)
            assertTrue(viewModel.uiState.value.isLatched)
            assertEquals(0, recorder.stops)
        }

    @Test
    fun `tapping again stops and saves a latched recording`() =
        runTest {
            val viewModel = createViewModel()
            tapToStart(viewModel)

            press(viewModel)

            assertEquals(RecordingPhase.SAVED, viewModel.uiState.value.phase)
            assertEquals(1, recorder.stops)
        }

    @Test
    fun `holding then lifting saves the recording`() =
        runTest {
            val viewModel = createViewModel()
            press(viewModel)

            release(viewModel, afterMs = WearRecordingViewModel.HOLD_THRESHOLD_MS + 600)

            assertEquals(RecordingPhase.SAVED, viewModel.uiState.value.phase)
            assertFalse(viewModel.uiState.value.isLatched)
        }

    @Test
    fun `a hold is not latched when it ends exactly at the threshold`() =
        runTest {
            val viewModel = createViewModel()
            press(viewModel)

            release(viewModel, afterMs = WearRecordingViewModel.HOLD_THRESHOLD_MS)

            assertEquals(RecordingPhase.SAVED, viewModel.uiState.value.phase)
        }

    @Test
    fun `lifting before the recorder confirms still latches a tap`() =
        runTest {
            recorder.startDelay = 500.milliseconds
            val viewModel = createViewModel()
            viewModel.onPress()
            advanceTimeBy(100)
            clock.advanceMs(100)
            viewModel.onRelease()

            advanceTimeBy(600)
            runCurrent()

            assertEquals(RecordingPhase.RECORDING, viewModel.uiState.value.phase)
            assertTrue(viewModel.uiState.value.isLatched)
        }

    @Test
    fun `lifting after a long hold before the recorder confirms saves`() =
        runTest {
            recorder.startDelay = 1.seconds
            val viewModel = createViewModel()
            viewModel.onPress()
            advanceTimeBy(100)
            clock.advanceMs(900)
            viewModel.onRelease()

            advanceTimeBy(1_100)
            runCurrent()

            assertEquals(RecordingPhase.SAVED, viewModel.uiState.value.phase)
        }

    @Test
    fun `lifting a latched recording changes nothing`() =
        runTest {
            val viewModel = createViewModel()
            tapToStart(viewModel)

            release(viewModel, afterMs = 2_000)

            assertEquals(RecordingPhase.RECORDING, viewModel.uiState.value.phase)
            assertEquals(0, recorder.stops)
        }

    // -----------------------------------------------------------------------
    // Saving
    // -----------------------------------------------------------------------

    @Test
    fun `saving creates an audio note from the finalized file`() =
        runTest {
            recorder.stopPath = "/files/audio_notes/recording_1.m4a"
            val created = slot<JournalNote>()
            coEvery { notesRepository.create(capture(created)) } returns Uuid.random()
            val viewModel = createViewModel()
            tapToStart(viewModel)

            press(viewModel)

            val note = created.captured as JournalNote.Audio
            assertEquals("/files/audio_notes/recording_1.m4a", note.mediaRef)
            assertEquals(4_200L, note.durationMs)
            assertEquals(viewModel.uiState.value.undoableNoteId, note.uid)
        }

    @Test
    fun `duration comes from the file when it can be read`() =
        runTest {
            coEvery { durationResolver.resolveDurationMs("/fake/audio.m4a") } returns 7_300L
            val viewModel = createViewModel()
            tapToStart(viewModel)
            recorder.elapsedFlow.value = 9.seconds

            press(viewModel)

            assertEquals(7_300L, viewModel.uiState.value.savedDurationMs)
        }

    @Test
    fun `duration falls back to the recorder clock when the file cannot be read`() =
        runTest {
            coEvery { durationResolver.resolveDurationMs(any()) } returns null
            val viewModel = createViewModel()
            tapToStart(viewModel)
            recorder.elapsedFlow.value = 9.seconds

            press(viewModel)

            assertEquals(9_000L, viewModel.uiState.value.savedDurationMs)
        }

    @Test
    fun `saving records where the phone sync stands`() =
        runTest {
            coEvery { dataLayerClient.isPhoneConnected(any()) } returns true
            val connected = createViewModel()
            tapToStart(connected)
            press(connected)
            assertEquals(SaveFeedback.SYNCING_TO_PHONE, connected.uiState.value.saveFeedback)

            coEvery { dataLayerClient.isPhoneConnected(any()) } returns false
            val offline = createViewModel()
            tapToStart(offline)
            press(offline)
            assertEquals(SaveFeedback.SAVED_LOCALLY, offline.uiState.value.saveFeedback)
        }

    @Test
    fun `saving includes the captured location`() =
        runTest {
            val location = NoteLocation(coordinates = NoteCoordinates(latitude = 37.77, longitude = -122.41))
            coEvery { locationCaptureCoordinator.captureForJournalEntry() } returns location
            val created = slot<JournalNote>()
            coEvery { notesRepository.create(capture(created)) } returns Uuid.random()
            val viewModel = createViewModel()
            tapToStart(viewModel)

            press(viewModel)

            assertEquals(location, created.captured.location)
        }

    @Test
    fun `a location that takes too long does not hold up the save`() =
        runTest {
            coEvery { locationCaptureCoordinator.captureForJournalEntry() } coAnswers {
                delay(60_000)
                null
            }
            val created = slot<JournalNote>()
            coEvery { notesRepository.create(capture(created)) } returns Uuid.random()
            val viewModel = createViewModel()
            tapToStart(viewModel)

            viewModel.onPress()
            advanceTimeBy(WearRecordingViewModel.LOCATION_TIMEOUT_MS + 100)

            assertEquals(RecordingPhase.SAVED, viewModel.uiState.value.phase)
            assertNull(created.captured.location)
        }

    @Test
    fun `saving annotates the note with health data`() =
        runTest {
            val created = slot<JournalNote>()
            coEvery { notesRepository.create(capture(created)) } returns Uuid.random()
            val viewModel = createViewModel()
            tapToStart(viewModel)

            press(viewModel)

            coVerify { noteHealthAnnotator.annotate(created.captured.uid) }
        }

    @Test
    fun `saving marks the gesture hint as seen`() =
        runTest {
            val viewModel = createViewModel()
            tapToStart(viewModel)

            press(viewModel)

            assertTrue(hintStore.seen)
            assertFalse(viewModel.uiState.value.showGestureHint)
        }

    @Test
    fun `a recording that produced no file is reported and creates no note`() =
        runTest {
            recorder.stopPath = null
            val viewModel = createViewModel()
            tapToStart(viewModel)

            press(viewModel)

            assertEquals(RecordingPhase.ERROR, viewModel.uiState.value.phase)
            assertEquals(RecordingError.SAVE_FAILED, viewModel.uiState.value.error)
            coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
        }

    @Test
    fun `a note that cannot be stored is reported and its file is left for recovery`() =
        runTest {
            coEvery { notesRepository.create(any<JournalNote>()) } throws IllegalStateException("database closed")
            val viewModel = createViewModel()
            tapToStart(viewModel)

            press(viewModel)

            assertEquals(RecordingError.SAVE_FAILED, viewModel.uiState.value.error)
            assertTrue(deletedFiles.isEmpty())
        }

    @Test
    fun `a recording shorter than the minimum is discarded as too short`() =
        runTest {
            coEvery { durationResolver.resolveDurationMs(any()) } returns 300L
            recorder.stopPath = "/files/audio_notes/short.m4a"
            val viewModel = createViewModel()
            tapToStart(viewModel)

            press(viewModel)

            assertEquals(RecordingPhase.TOO_SHORT, viewModel.uiState.value.phase)
            assertEquals(listOf("/files/audio_notes/short.m4a"), deletedFiles)
            coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
            verify { haptics.rejection() }
        }

    @Test
    fun `too short returns to ready after a moment`() =
        runTest {
            coEvery { durationResolver.resolveDurationMs(any()) } returns 300L
            val viewModel = createViewModel()
            tapToStart(viewModel)
            press(viewModel)

            advanceTimeBy(WearRecordingViewModel.TOO_SHORT_DISPLAY_MS + 100)

            assertEquals(RecordingPhase.READY, viewModel.uiState.value.phase)
        }

    // -----------------------------------------------------------------------
    // Undo
    // -----------------------------------------------------------------------

    @Test
    fun `undo removes the note and deletes its file`() =
        runTest {
            val audio = audioNote("/files/audio_notes/undo.m4a")
            coEvery { notesRepository.getNoteById(any()) } returnsMany listOf(audio, null)
            val viewModel = createViewModel()
            tapToStart(viewModel)
            press(viewModel)
            val noteId = viewModel.uiState.value.undoableNoteId!!

            viewModel.onUndo()
            runCurrent()

            coVerify { notesRepository.removeById(noteId) }
            assertEquals(listOf("/files/audio_notes/undo.m4a"), deletedFiles)
            assertEquals(RecordingPhase.READY, viewModel.uiState.value.phase)
        }

    @Test
    fun `undo leaves the file alone while the note still exists`() =
        runTest {
            val audio = audioNote("/files/audio_notes/still-there.m4a")
            coEvery { notesRepository.getNoteById(any()) } returns audio
            val viewModel = createViewModel()
            tapToStart(viewModel)
            press(viewModel)

            viewModel.onUndo()
            runCurrent()

            assertTrue(deletedFiles.isEmpty())
        }

    @Test
    fun `the undo window closes on its own`() =
        runTest {
            val viewModel = createViewModel()
            tapToStart(viewModel)
            press(viewModel)

            advanceTimeBy(WearRecordingViewModel.UNDO_WINDOW_MS + 100)

            assertEquals(RecordingPhase.READY, viewModel.uiState.value.phase)
            assertNull(viewModel.uiState.value.undoableNoteId)
        }

    @Test
    fun `undo does nothing once the window has closed`() =
        runTest {
            val viewModel = createViewModel()
            tapToStart(viewModel)
            press(viewModel)
            advanceTimeBy(WearRecordingViewModel.UNDO_WINDOW_MS + 100)

            viewModel.onUndo()
            runCurrent()

            coVerify(exactly = 0) { notesRepository.removeById(any()) }
        }

    @Test
    fun `recording again keeps the previous note`() =
        runTest {
            val viewModel = createViewModel()
            tapToStart(viewModel)
            press(viewModel)

            press(viewModel)

            assertEquals(RecordingPhase.RECORDING, viewModel.uiState.value.phase)
            assertNull(viewModel.uiState.value.undoableNoteId)
            coVerify(exactly = 0) { notesRepository.removeById(any()) }
            advanceTimeBy(WearRecordingViewModel.UNDO_WINDOW_MS + 100)
            assertEquals(RecordingPhase.RECORDING, viewModel.uiState.value.phase)
        }

    // -----------------------------------------------------------------------
    // Pause, discard, interruptions
    // -----------------------------------------------------------------------

    @Test
    fun `pausing and resuming follow the recorder`() =
        runTest {
            val viewModel = createViewModel()
            tapToStart(viewModel)

            viewModel.onPauseToggle()
            runCurrent()
            assertEquals(RecordingPhase.PAUSED, viewModel.uiState.value.phase)
            assertFalse(viewModel.uiState.value.pausedByInterruption)

            viewModel.onPauseToggle()
            runCurrent()
            assertEquals(RecordingPhase.RECORDING, viewModel.uiState.value.phase)
            verify { haptics.pause() }
            verify { haptics.resume() }
        }

    @Test
    fun `pressing while paused resumes`() =
        runTest {
            val viewModel = createViewModel()
            tapToStart(viewModel)
            viewModel.onPauseToggle()
            runCurrent()

            press(viewModel)

            assertEquals(RecordingPhase.RECORDING, viewModel.uiState.value.phase)
            assertEquals(1, recorder.resumes)
        }

    @Test
    fun `a pause caused by another app is reported as an interruption`() =
        runTest {
            val viewModel = createViewModel()
            tapToStart(viewModel)

            recorder.pausedFlow.value = true
            recorder.interruptedFlow.value = true
            runCurrent()

            assertEquals(RecordingPhase.PAUSED, viewModel.uiState.value.phase)
            assertTrue(viewModel.uiState.value.pausedByInterruption)
        }

    @Test
    fun `discarding drops the recording without creating a note`() =
        runTest {
            val viewModel = createViewModel()
            tapToStart(viewModel)

            viewModel.onDiscard()
            runCurrent()

            assertEquals(RecordingPhase.READY, viewModel.uiState.value.phase)
            assertEquals(1, recorder.discards)
            assertEquals(0, recorder.stops)
            coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
        }

    @Test
    fun `discarding a paused recording works too`() =
        runTest {
            val viewModel = createViewModel()
            tapToStart(viewModel)
            viewModel.onPauseToggle()
            runCurrent()

            viewModel.onDiscard()
            runCurrent()

            assertEquals(RecordingPhase.READY, viewModel.uiState.value.phase)
            assertEquals(1, recorder.discards)
        }

    @Test
    fun `a recording the recorder ends on its own is saved`() =
        runTest {
            val viewModel = createViewModel()
            tapToStart(viewModel)

            recorder.recordingFlow.value = false
            runCurrent()

            assertEquals(RecordingPhase.SAVED, viewModel.uiState.value.phase)
            assertEquals(1, recorder.stops)
        }

    @Test
    fun `discarding is not mistaken for the recorder ending on its own`() =
        runTest {
            val viewModel = createViewModel()
            tapToStart(viewModel)

            viewModel.onDiscard()
            runCurrent()

            assertEquals(0, recorder.stops)
            coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
        }

    // -----------------------------------------------------------------------
    // Live readouts and lifecycle
    // -----------------------------------------------------------------------

    @Test
    fun `elapsed time and input level follow the recorder while recording`() =
        runTest {
            val viewModel = createViewModel()
            tapToStart(viewModel)

            recorder.elapsedFlow.value = 12.seconds
            recorder.levelFlow.value = 0.6f
            runCurrent()

            assertEquals(12_000L, viewModel.uiState.value.recordingDurationMs)
            assertEquals(0.6f, viewModel.uiState.value.audioLevels.last())
        }

    @Test
    fun `input level history is capped`() =
        runTest {
            val viewModel = createViewModel()
            tapToStart(viewModel)

            repeat(80) { index ->
                recorder.levelFlow.value = (index % 10) / 10f + 0.01f
                runCurrent()
            }

            assertEquals(50, viewModel.uiState.value.audioLevels.size)
        }

    @Test
    fun `a haptic warns as the length limit approaches`() =
        runTest {
            val viewModel = createViewModel()
            tapToStart(viewModel)

            recorder.elapsedFlow.value = 29.minutes + 1.seconds
            runCurrent()

            verify(exactly = 1) { haptics.warning() }
            assertEquals(RecordingPhase.RECORDING, viewModel.uiState.value.phase)
        }

    @Test
    fun `clearing the screen while recording asks the recorder to stop`() =
        runTest {
            val viewModel = createViewModel()
            tapToStart(viewModel)

            viewModelStore.clear()

            assertEquals(1, recorder.stopRequests)
        }

    // -----------------------------------------------------------------------
    // Fakes
    // -----------------------------------------------------------------------

    private fun audioNote(path: String): JournalNote.Audio {
        val now = clock.now()
        return JournalNote.Audio(mediaRef = path, creationTimestamp = now, lastUpdated = now, durationMs = 4_200)
    }

    private class FakeHintStore : RecordingHintStore {
        var seen = false

        override fun hasSeenHint(): Boolean = seen

        override fun markHintSeen() {
            seen = true
        }
    }

    private class TestClock : Clock {
        private var nowMs = 1_710_000_000_000L

        fun advanceMs(delta: Long) {
            nowMs += delta
        }

        override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)
    }

    private class FakeRecorder : WearRecorder {
        val recordingFlow = MutableStateFlow(false)
        val pausedFlow = MutableStateFlow(false)
        val interruptedFlow = MutableStateFlow(false)
        val levelFlow = MutableStateFlow(0f)
        val elapsedFlow = MutableStateFlow(Duration.ZERO)

        override val isRecording: StateFlow<Boolean> = recordingFlow
        override val isPaused: StateFlow<Boolean> = pausedFlow
        override val pausedByInterruption: StateFlow<Boolean> = interruptedFlow
        override val audioLevel: StateFlow<Float> = levelFlow
        override val elapsed: StateFlow<Duration> = elapsedFlow
        override var lastStartFailure: RecordingStartFailure? = null

        var startResult = true
        var startDelay: Duration = Duration.ZERO
        var stopPath: String? = "/fake/audio.m4a"
        var starts = 0
        var stops = 0
        var resumes = 0
        var discards = 0
        var stopRequests = 0

        override suspend fun start(): Boolean {
            starts++
            if (startDelay > Duration.ZERO) delay(startDelay)
            if (startResult) recordingFlow.value = true
            return startResult
        }

        override suspend fun stop(): String? {
            stops++
            recordingFlow.value = false
            pausedFlow.value = false
            return stopPath
        }

        override suspend fun pause(): Boolean {
            pausedFlow.value = true
            return true
        }

        override suspend fun resume(): Boolean {
            resumes++
            pausedFlow.value = false
            interruptedFlow.value = false
            return true
        }

        override suspend fun discard() {
            discards++
            recordingFlow.value = false
            pausedFlow.value = false
        }

        override fun requestStop() {
            stopRequests++
        }
    }
}
