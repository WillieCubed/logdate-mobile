package app.logdate.wear.presentation.audio

import android.app.Application
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.wear.data.storage.StorageSpaceChecker
import app.logdate.wear.health.NoteHealthAnnotator
import app.logdate.wear.location.WearLocationCaptureCoordinator
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
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class AudioRecordingViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var recordingManager: WearAudioRecordingManager
    private lateinit var notesRepository: JournalNotesRepository
    private lateinit var storageChecker: StorageSpaceChecker
    private lateinit var noteHealthAnnotator: NoteHealthAnnotator
    private lateinit var locationCaptureCoordinator: WearLocationCaptureCoordinator

    private val audioLevelFlow = MutableStateFlow(0f)

    private val plentyOfStorage = 10 * 1024 * 1024L
    private val insufficientStorage = 1024L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        recordingManager = mockk(relaxed = true)
        notesRepository = mockk(relaxed = true)
        storageChecker = mockk(relaxed = true)
        noteHealthAnnotator = mockk(relaxed = true)
        locationCaptureCoordinator = mockk(relaxed = true)

        every { recordingManager.getAudioLevelFlow() } returns audioLevelFlow
        coEvery { notesRepository.create(any<JournalNote>()) } returns Uuid.random()
        coEvery { locationCaptureCoordinator.captureForJournalEntry() } returns null
        coEvery { storageChecker.getAvailableStorageSpace() } returns plentyOfStorage
        coEvery { recordingManager.startRecording() } returns true
        coEvery { recordingManager.pauseRecording() } returns true
        coEvery { recordingManager.resumeRecording() } returns true
        coEvery { recordingManager.stopRecording() } returns "/fake/audio.m4a"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AudioRecordingViewModel =
        AudioRecordingViewModel(
            application,
            recordingManager,
            notesRepository,
            storageChecker,
            noteHealthAnnotator,
            locationCaptureCoordinator,
        )

    private fun AudioRecordingViewModel.cancelScope() {
        viewModelScope.cancel()
    }

    // -----------------------------------------------------------------------
    // Initial state
    // -----------------------------------------------------------------------

    @Test
    fun `initial state is idle`() =
        runTest {
            val viewModel = createViewModel()
            val state = viewModel.uiState.value

            assertFalse(state.isRecording)
            assertFalse(state.isPaused)
            assertFalse(state.isLoading)
            assertFalse(state.navigateBack)
            assertEquals(0, state.durationMs)
            assertEquals(emptyList(), state.audioLevels)
            assertNull(state.errorMessage)
        }

    // -----------------------------------------------------------------------
    // startRecording -- success
    // -----------------------------------------------------------------------

    @Test
    fun `startRecording transitions to recording state`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.startRecording()
            advanceTimeBy(200)

            val state = viewModel.uiState.value
            assertTrue(state.isRecording)
            assertFalse(state.isLoading)
            assertFalse(state.isPaused)
            viewModel.cancelScope()
        }

    @Test
    fun `startRecording checks storage before starting`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.startRecording()
            advanceTimeBy(200)

            coVerify { storageChecker.getAvailableStorageSpace() }
            coVerify { recordingManager.startRecording() }
            viewModel.cancelScope()
        }

    @Test
    fun `startRecording starts audio level collection`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.startRecording()
            advanceTimeBy(200)

            verify { recordingManager.getAudioLevelFlow() }
            viewModel.cancelScope()
        }

    // -----------------------------------------------------------------------
    // startRecording -- insufficient storage
    // -----------------------------------------------------------------------

    @Test
    fun `startRecording shows error when storage insufficient`() =
        runTest {
            coEvery { storageChecker.getAvailableStorageSpace() } returns insufficientStorage
            val viewModel = createViewModel()

            viewModel.startRecording()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isRecording)
            assertFalse(state.isLoading)
            assertNotNull(state.errorMessage)
            coVerify(exactly = 0) { recordingManager.startRecording() }
        }

    // -----------------------------------------------------------------------
    // startRecording -- recording fails
    // -----------------------------------------------------------------------

    @Test
    fun `startRecording shows error when manager returns false`() =
        runTest {
            coEvery { recordingManager.startRecording() } returns false
            val viewModel = createViewModel()

            viewModel.startRecording()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isRecording)
            assertFalse(state.isLoading)
            assertNotNull(state.errorMessage)
        }

    @Test
    fun `startRecording shows error when manager throws`() =
        runTest {
            coEvery { recordingManager.startRecording() } throws RuntimeException("mic error")
            val viewModel = createViewModel()

            viewModel.startRecording()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isRecording)
            assertFalse(state.isLoading)
            assertNotNull(state.errorMessage)
        }

    // -----------------------------------------------------------------------
    // stopRecording -- success
    // -----------------------------------------------------------------------

    @Test
    fun `stopRecording saves note and navigates back`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.startRecording()
            advanceTimeBy(200)

            viewModel.stopRecording()
            advanceTimeBy(200)

            coVerify { recordingManager.stopRecording() }
            coVerify { notesRepository.create(any<JournalNote>()) }

            val state = viewModel.uiState.value
            assertFalse(state.isRecording)
            assertFalse(state.isLoading)
            assertTrue(state.navigateBack)
            viewModel.cancelScope()
        }

    @Test
    fun `stopRecording creates Audio note with correct file path`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.startRecording()
            advanceTimeBy(200)

            viewModel.stopRecording()
            advanceTimeBy(200)

            coVerify {
                notesRepository.create(
                    match { note ->
                        note is JournalNote.Audio && note.mediaRef == "/fake/audio.m4a"
                    },
                )
            }
            viewModel.cancelScope()
        }

    @Test
    fun `stopRecording attaches current watch location to audio note`() =
        runTest {
            val capturedLocation =
                NoteLocation(
                    coordinates =
                        NoteCoordinates(
                            latitude = 37.7749,
                            longitude = -122.4194,
                            altitude = 14.0,
                        ),
                )
            coEvery { locationCaptureCoordinator.captureForJournalEntry() } returns capturedLocation
            val viewModel = createViewModel()
            viewModel.startRecording()
            advanceTimeBy(200)

            viewModel.stopRecording()
            advanceTimeBy(200)

            coVerify {
                notesRepository.create(
                    match { note ->
                        note is JournalNote.Audio && note.location == capturedLocation
                    },
                )
            }
            viewModel.cancelScope()
        }

    @Test
    fun `stopRecording annotates note with health data`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.startRecording()
            advanceTimeBy(200)

            viewModel.stopRecording()
            advanceTimeBy(200)

            coVerify { noteHealthAnnotator.annotate(any()) }
            viewModel.cancelScope()
        }

    // -----------------------------------------------------------------------
    // stopRecording -- null path
    // -----------------------------------------------------------------------

    @Test
    fun `stopRecording shows error when file path is null`() =
        runTest {
            coEvery { recordingManager.stopRecording() } returns null
            val viewModel = createViewModel()
            viewModel.startRecording()
            advanceTimeBy(200)

            viewModel.stopRecording()
            advanceTimeBy(200)

            val state = viewModel.uiState.value
            assertFalse(state.isRecording)
            assertNotNull(state.errorMessage)
            assertFalse(state.navigateBack)
            viewModel.cancelScope()
        }

    // -----------------------------------------------------------------------
    // stopRecording -- exception
    // -----------------------------------------------------------------------

    @Test
    fun `stopRecording shows error when exception thrown`() =
        runTest {
            coEvery { recordingManager.stopRecording() } throws RuntimeException("IO error")
            val viewModel = createViewModel()
            viewModel.startRecording()
            advanceTimeBy(200)

            viewModel.stopRecording()
            advanceTimeBy(200)

            val state = viewModel.uiState.value
            assertFalse(state.isRecording)
            assertNotNull(state.errorMessage)
            viewModel.cancelScope()
        }

    // -----------------------------------------------------------------------
    // pauseRecording
    // -----------------------------------------------------------------------

    @Test
    fun `pauseRecording updates isPaused to true`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.startRecording()
            advanceTimeBy(200)

            viewModel.pauseRecording()
            advanceTimeBy(200)

            assertTrue(viewModel.uiState.value.isPaused)
            viewModel.cancelScope()
        }

    @Test
    fun `pauseRecording calls manager`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.startRecording()
            advanceTimeBy(200)

            viewModel.pauseRecording()
            advanceTimeBy(200)

            coVerify { recordingManager.pauseRecording() }
            viewModel.cancelScope()
        }

    @Test
    fun `pauseRecording does not update state when manager returns false`() =
        runTest {
            coEvery { recordingManager.pauseRecording() } returns false
            val viewModel = createViewModel()
            viewModel.startRecording()
            advanceTimeBy(200)

            viewModel.pauseRecording()
            advanceTimeBy(200)

            assertFalse(viewModel.uiState.value.isPaused)
            viewModel.cancelScope()
        }

    // -----------------------------------------------------------------------
    // resumeRecording
    // -----------------------------------------------------------------------

    @Test
    fun `resumeRecording updates isPaused to false`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.startRecording()
            advanceTimeBy(200)
            viewModel.pauseRecording()
            advanceTimeBy(200)
            assertTrue(viewModel.uiState.value.isPaused)

            viewModel.resumeRecording()
            advanceTimeBy(200)

            assertFalse(viewModel.uiState.value.isPaused)
            viewModel.cancelScope()
        }

    @Test
    fun `resumeRecording calls manager`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.startRecording()
            advanceTimeBy(200)

            viewModel.resumeRecording()
            advanceTimeBy(200)

            coVerify { recordingManager.resumeRecording() }
            viewModel.cancelScope()
        }

    // -----------------------------------------------------------------------
    // cancelRecording
    // -----------------------------------------------------------------------

    @Test
    fun `cancelRecording releases manager and navigates back`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.startRecording()
            advanceTimeBy(200)

            viewModel.cancelRecording()
            advanceTimeBy(200)

            verify { recordingManager.release() }
            val state = viewModel.uiState.value
            assertFalse(state.isRecording)
            assertFalse(state.isPaused)
            assertTrue(state.navigateBack)
            assertEquals(0, state.durationMs)
            viewModel.cancelScope()
        }

    @Test
    fun `cancelRecording does not save note`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.startRecording()
            advanceTimeBy(200)

            viewModel.cancelRecording()
            advanceTimeBy(200)

            coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
            viewModel.cancelScope()
        }

    // -----------------------------------------------------------------------
    // Audio level collection
    // -----------------------------------------------------------------------

    @Test
    fun `audio levels are collected while recording`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.startRecording()
            advanceTimeBy(200)

            audioLevelFlow.value = 0.5f
            advanceTimeBy(150)
            audioLevelFlow.value = 0.8f
            advanceTimeBy(150)

            val state = viewModel.uiState.value
            assertTrue(state.isRecording)
            assertTrue(state.audioLevels.isNotEmpty())
            viewModel.cancelScope()
        }

    @Test
    fun `audio levels are bounded to 50 entries`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.startRecording()
            advanceTimeBy(200)

            repeat(60) { i ->
                audioLevelFlow.value = i / 60f
                advanceTimeBy(150)
            }

            assertTrue(
                viewModel.uiState.value.audioLevels.size <= 50,
                "Expected at most 50 audio levels",
            )
            viewModel.cancelScope()
        }

    @Test
    fun `duration increases while recording`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.startRecording()
            advanceTimeBy(200)

            audioLevelFlow.value = 0.1f
            advanceTimeBy(500)
            audioLevelFlow.value = 0.2f
            advanceTimeBy(500)

            assertTrue(
                viewModel.uiState.value.durationMs > 0,
                "Expected duration to increase while recording",
            )
            viewModel.cancelScope()
        }

    // -----------------------------------------------------------------------
    // Loading state
    // -----------------------------------------------------------------------

    @Test
    fun `isLoading is false in initial state`() =
        runTest {
            val viewModel = createViewModel()
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun `isLoading is false after recording starts`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.startRecording()
            advanceTimeBy(200)

            assertFalse(viewModel.uiState.value.isLoading)
            viewModel.cancelScope()
        }

    @Test
    fun `isLoading is false after recording stops`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.startRecording()
            advanceTimeBy(200)
            viewModel.stopRecording()
            advanceTimeBy(200)

            assertFalse(viewModel.uiState.value.isLoading)
            viewModel.cancelScope()
        }
}
