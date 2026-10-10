package app.logdate.wear.v2.recorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.storage.MediaCollection
import app.logdate.client.media.storage.MediaDirectories
import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.capture.Feeling
import app.logdate.wear.health.NoteHealthAnnotator
import app.logdate.wear.location.WearLocationCaptureCoordinator
import app.logdate.wear.recording.AudioMomentSaver
import app.logdate.wear.recording.FakeWearRecorder
import app.logdate.wear.recording.RecordingStartFailure
import app.logdate.wear.v2.haptics.RecordedHaptics
import app.logdate.wear.v2.permissions.PermissionPromptStore
import app.logdate.wear.v2.permissions.PermissionPrompts
import app.logdate.wear.v2.permissions.PermissionResult
import app.logdate.wear.v2.permissions.WatchPermission
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests [RecorderViewModel] through the signals the record gesture sends and the controls Today
 * shows. Nothing recorded may be dropped silently, so every failure is a specific state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecorderViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val recorder = FakeWearRecorder()
    private val notesRepository = mockk<JournalNotesRepository>(relaxed = true)
    private val durationResolver = mockk<AudioDurationResolver>()
    private val locationCaptureCoordinator = mockk<WearLocationCaptureCoordinator>()
    private val haptics = RecordedHaptics()
    private val granted = mutableSetOf(WatchPermission.MICROPHONE, WatchPermission.NOTIFICATIONS)
    private val asked = mutableSetOf<WatchPermission>()
    private val savedFeelings = mutableListOf<Feeling>()
    private val removedNotes = mutableListOf<Uuid>()
    private val deletedFiles = mutableListOf<String>()
    private val viewModelStore = ViewModelStore()
    private val applicationScope = CoroutineScope(SupervisorJob() + testDispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { durationResolver.resolveDurationMs(any()) } returns 4_200L
        coEvery { locationCaptureCoordinator.captureForJournalEntry() } returns null
        coEvery { notesRepository.create(any<JournalNote>()) } returns Uuid.random()
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        applicationScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `the recorder starts ready`() = runTest { assertEquals(RecorderState.Ready, createViewModel().state.value) }

    @Test
    fun `a press starts the recorder at once but shows nothing yet`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onPressed()
            runCurrent()

            assertEquals(RecorderState.Arming, viewModel.state.value)
            assertEquals(1, recorder.starts)
            assertTrue(haptics.played.isEmpty())
        }

    @Test
    fun `a tap makes it a locked recording`() =
        runTest {
            val viewModel = createViewModel()

            tap(viewModel)

            val live = assertIs<RecorderState.Live>(viewModel.state.value)
            assertEquals(LiveMode.LOCKED, live.mode)
            assertEquals(1f, live.lockProgress)
            assertEquals(listOf("recordingStarted"), haptics.played)
        }

    @Test
    fun `holding past the timeout is hold to talk and sliding up locks it`() =
        runTest {
            val viewModel = createViewModel()
            hold(viewModel)
            assertEquals(LiveMode.HOLDING, assertIs<RecorderState.Live>(viewModel.state.value).mode)

            viewModel.onLockProgress(0.6f)
            assertEquals(0.6f, assertIs<RecorderState.Live>(viewModel.state.value).lockProgress)

            viewModel.onLocked()
            assertEquals(LiveMode.LOCKED, assertIs<RecorderState.Live>(viewModel.state.value).mode)
            assertEquals(listOf("recordingStarted", "recordingLocked"), haptics.played)
        }

    @Test
    fun `lifting the finger while holding saves the recording`() =
        runTest {
            val viewModel = createViewModel()
            hold(viewModel)

            viewModel.onReleased()
            runCurrent()

            val saved = assertIs<RecorderState.Saved>(viewModel.state.value)
            assertEquals(4_200.milliseconds, saved.duration)
            assertTrue(saved.feelingsOffered)
            assertEquals(1, recorder.stops)
            assertEquals(listOf("recordingStarted", "recordingFinished", "saveSucceeded"), haptics.played)
        }

    @Test
    fun `a scroll before the timeout discards the recording without a trace`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onPressed()
            runCurrent()

            viewModel.onYielded()
            runCurrent()

            assertEquals(RecorderState.Ready, viewModel.state.value)
            assertEquals(1, recorder.discards)
            assertTrue(haptics.played.isEmpty())
            coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
        }

    @Test
    fun `a scroll while the recorder is still starting discards it once it has started`() =
        runTest {
            recorder.startDelay = 500.milliseconds
            val viewModel = createViewModel()
            viewModel.onPressed()
            advanceTimeBy(100)

            viewModel.onYielded()
            viewModel.onPressed()
            assertEquals(RecorderState.Arming, viewModel.state.value)

            advanceTimeBy(600)
            runCurrent()
            assertEquals(RecorderState.Ready, viewModel.state.value)
            assertEquals(1, recorder.starts)
            assertEquals(1, recorder.discards)
        }

    @Test
    fun `lifting before the recorder confirms still saves`() =
        runTest {
            recorder.startDelay = 1.seconds
            val viewModel = createViewModel()
            viewModel.onPressed()
            advanceTimeBy(100)
            viewModel.onCommitted()
            viewModel.onReleased()

            advanceTimeBy(1_100)
            runCurrent()

            assertIs<RecorderState.Saved>(viewModel.state.value)
            assertEquals(1, recorder.stops)
        }

    @Test
    fun `without the microphone a press starts nothing and the decision asks for it`() =
        runTest {
            granted -= WatchPermission.MICROPHONE
            val viewModel = createViewModel()
            val requests = collectRequests(viewModel)

            viewModel.onPressed()
            viewModel.onTapped()
            runCurrent()

            assertEquals(0, recorder.starts)
            assertEquals(RecorderState.Ready, viewModel.state.value)
            assertEquals(listOf(WatchPermission.MICROPHONE), requests)
        }

    @Test
    fun `granting the microphone starts a locked recording`() =
        runTest {
            granted -= WatchPermission.MICROPHONE
            val viewModel = createViewModel()
            viewModel.onPressed()
            viewModel.onCommitted()
            granted += WatchPermission.MICROPHONE

            viewModel.onPermissionResult(WatchPermission.MICROPHONE, PermissionResult.GRANTED)
            runCurrent()

            assertEquals(LiveMode.LOCKED, assertIs<RecorderState.Live>(viewModel.state.value).mode)
            assertEquals(1, recorder.starts)
        }

    @Test
    fun `refusing the microphone explains it and blocking it points to Settings`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.onPermissionResult(WatchPermission.MICROPHONE, PermissionResult.DENIED)
            assertEquals(RecorderState.Failed(RecorderFailure.MICROPHONE_DENIED), viewModel.state.value)

            viewModel.onPermissionResult(WatchPermission.MICROPHONE, PermissionResult.BLOCKED)
            assertEquals(RecorderState.Failed(RecorderFailure.MICROPHONE_BLOCKED), viewModel.state.value)
        }

    @Test
    fun `trying again after a refusal asks for the microphone again`() =
        runTest {
            val viewModel = createViewModel()
            val requests = collectRequests(viewModel)
            viewModel.onPermissionResult(WatchPermission.MICROPHONE, PermissionResult.DENIED)

            viewModel.retry()
            runCurrent()

            assertEquals(listOf(WatchPermission.MICROPHONE), requests)
        }

    @Test
    fun `a scroll without the microphone asks for nothing`() =
        runTest {
            granted -= WatchPermission.MICROPHONE
            val viewModel = createViewModel()
            val requests = collectRequests(viewModel)

            viewModel.onPressed()
            viewModel.onYielded()
            runCurrent()

            assertTrue(requests.isEmpty())
        }

    @Test
    fun `the first locked recording asks for notifications once`() =
        runTest {
            granted -= WatchPermission.NOTIFICATIONS
            val viewModel = createViewModel()
            val requests = collectRequests(viewModel)

            recordAndStop(viewModel)
            tap(viewModel)
            runCurrent()

            assertEquals(listOf(WatchPermission.NOTIFICATIONS), requests)
        }

    @Test
    fun `a refused start shows its reason once the gesture decides`() =
        runTest {
            recorder.startResult = false
            recorder.lastStartFailure = RecordingStartFailure.NOT_ENOUGH_STORAGE
            val viewModel = createViewModel()
            viewModel.onPressed()
            runCurrent()
            assertEquals(RecorderState.Arming, viewModel.state.value)

            viewModel.onCommitted()

            assertEquals(RecorderState.Failed(RecorderFailure.NOT_ENOUGH_STORAGE), viewModel.state.value)
            assertEquals(listOf("actionRejected"), haptics.played)
        }

    @Test
    fun `a refused start under a scroll shows nothing`() =
        runTest {
            recorder.startResult = false
            recorder.lastStartFailure = RecordingStartFailure.RECORDER_UNAVAILABLE
            val viewModel = createViewModel()
            viewModel.onPressed()
            runCurrent()

            viewModel.onYielded()

            assertEquals(RecorderState.Ready, viewModel.state.value)
            assertTrue(haptics.played.isEmpty())
        }

    @Test
    fun `stop saves a locked recording but not hold to talk`() =
        runTest {
            val viewModel = createViewModel()
            hold(viewModel)
            viewModel.stop()
            runCurrent()
            assertIs<RecorderState.Live>(viewModel.state.value)

            viewModel.onLocked()
            viewModel.stop()
            runCurrent()

            assertIs<RecorderState.Saved>(viewModel.state.value)
        }

    @Test
    fun `pause and resume follow the recorder`() =
        runTest {
            val viewModel = createViewModel()
            tap(viewModel)

            viewModel.togglePause()
            runCurrent()
            assertTrue(assertIs<RecorderState.Live>(viewModel.state.value).paused)

            viewModel.togglePause()
            runCurrent()
            assertFalse(assertIs<RecorderState.Live>(viewModel.state.value).paused)
            assertEquals(listOf("recordingStarted", "recordingPaused", "recordingResumed"), haptics.played)
        }

    @Test
    fun `a pause another app causes is reported as one`() =
        runTest {
            val viewModel = createViewModel()
            tap(viewModel)

            recorder.pausedFlow.value = true
            recorder.interruptedFlow.value = true
            runCurrent()

            val live = assertIs<RecorderState.Live>(viewModel.state.value)
            assertTrue(live.paused)
            assertTrue(live.pausedByInterruption)
        }

    @Test
    fun `discarding takes two taps and leaves no note`() =
        runTest {
            val viewModel = createViewModel()
            tap(viewModel)

            viewModel.discard()
            assertTrue(assertIs<RecorderState.Live>(viewModel.state.value).confirmingDiscard)
            viewModel.discard()
            runCurrent()

            assertEquals(RecorderState.Ready, viewModel.state.value)
            assertEquals(1, recorder.discards)
            assertEquals(0, recorder.stops)
            assertEquals("confirmDestruction", haptics.played.last())
            coVerify(exactly = 0) { notesRepository.create(any<JournalNote>()) }
        }

    @Test
    fun `the discard question goes away after three seconds`() =
        runTest {
            val viewModel = createViewModel()
            tap(viewModel)
            viewModel.discard()

            advanceTimeBy(RecorderViewModel.DISCARD_CONFIRM_MS + 1)
            runCurrent()

            assertFalse(assertIs<RecorderState.Live>(viewModel.state.value).confirmingDiscard)
            assertEquals(0, recorder.discards)
        }

    @Test
    fun `a recording the recorder ends on its own is saved`() =
        runTest {
            val viewModel = createViewModel()
            tap(viewModel)

            recorder.recordingFlow.value = false
            runCurrent()

            assertIs<RecorderState.Saved>(viewModel.state.value)
            assertEquals(1, recorder.stops)
        }

    @Test
    fun `a recording too short to save says so and goes back to ready`() =
        runTest {
            coEvery { durationResolver.resolveDurationMs(any()) } returns 300L
            val viewModel = createViewModel()

            recordAndStop(viewModel)
            assertEquals(RecorderState.TooShort, viewModel.state.value)
            assertEquals("actionRejected", haptics.played.last())

            advanceTimeBy(RecorderViewModel.TOO_SHORT_DISPLAY_MS + 100)
            runCurrent()
            assertEquals(RecorderState.Ready, viewModel.state.value)
        }

    @Test
    fun `a save that fails is tried again on the same file`() =
        runTest {
            var failures = 1
            val created = mutableListOf<JournalNote>()
            coEvery { notesRepository.create(any<JournalNote>()) } answers {
                if (failures-- > 0) throw IllegalStateException("database closed")
                created += firstArg<JournalNote>()
                Uuid.random()
            }
            val viewModel = createViewModel()
            recordAndStop(viewModel)
            assertEquals(RecorderState.Failed(RecorderFailure.SAVE_FAILED), viewModel.state.value)
            assertFalse(viewModel.state.value.canStartRecording)

            viewModel.retry()
            runCurrent()

            assertIs<RecorderState.Saved>(viewModel.state.value)
            assertEquals(1, recorder.starts)
            assertEquals(1, recorder.stops)
            assertEquals(FakeWearRecorder.DEFAULT_PATH, (created.single() as JournalNote.Audio).mediaRef)
        }

    @Test
    fun `a recording that produced no file is reported as lost`() =
        runTest {
            recorder.stopPath = null
            val viewModel = createViewModel()

            recordAndStop(viewModel)

            assertEquals(RecorderState.Failed(RecorderFailure.RECORDING_LOST), viewModel.state.value)
        }

    @Test
    fun `undo removes the saved recording`() =
        runTest {
            val viewModel = createViewModel()
            recordAndStop(viewModel)
            val saved = assertIs<RecorderState.Saved>(viewModel.state.value)
            coEvery { notesRepository.getNoteById(saved.noteId) } returnsMany listOf(audio(saved.noteId), null)

            viewModel.undo()
            runCurrent()

            assertEquals(RecorderState.Ready, viewModel.state.value)
            assertEquals(listOf(saved.noteId), removedNotes)
            assertTrue(FakeWearRecorder.DEFAULT_PATH in deletedFiles)
            assertEquals("confirmDestruction", haptics.played.last())
        }

    @Test
    fun `undo deletes the file a portable media reference names`() =
        runTest {
            val viewModel = createViewModel(mediaFiles = mediaFiles())
            recordAndStop(viewModel)
            val saved = assertIs<RecorderState.Saved>(viewModel.state.value)
            coEvery { notesRepository.getNoteById(saved.noteId) } returnsMany
                listOf(audio(saved.noteId, "logdate-media://recordings/recording_fake.m4a"), null)

            viewModel.undo()
            runCurrent()

            assertEquals(listOf("/files/audio_notes/recording_fake.m4a"), deletedFiles)
            assertEquals(listOf(saved.noteId), removedNotes)
        }

    @Test
    fun `the feelings go after four seconds and the saved state after five`() =
        runTest {
            val viewModel = createViewModel()
            recordAndStop(viewModel)

            advanceTimeBy(4_001)
            runCurrent()
            assertFalse(assertIs<RecorderState.Saved>(viewModel.state.value).feelingsOffered)

            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(RecorderState.Ready, viewModel.state.value)
        }

    @Test
    fun `choosing a feeling saves a check-in beside the recording`() =
        runTest {
            val viewModel = createViewModel()
            recordAndStop(viewModel)

            viewModel.chooseFeeling(Feeling.GOOD)
            runCurrent()

            assertEquals(listOf(Feeling.GOOD), savedFeelings)
            val saved = assertIs<RecorderState.Saved>(viewModel.state.value)
            assertEquals(Feeling.GOOD, saved.feeling)
            assertFalse(saved.feelingsOffered)
        }

    @Test
    fun `recording again leaves the note just saved`() =
        runTest {
            val viewModel = createViewModel()
            recordAndStop(viewModel)

            tap(viewModel)

            assertIs<RecorderState.Live>(viewModel.state.value)
            coVerify(exactly = 0) { notesRepository.removeById(any()) }
            assertEquals(2, recorder.starts)
        }

    @Test
    fun `a haptic warns once as the length limit approaches`() =
        runTest {
            val viewModel = createViewModel()
            tap(viewModel)

            recorder.elapsedFlow.value = 29.minutes + 1.seconds
            runCurrent()
            recorder.elapsedFlow.value = 29.minutes + 2.seconds
            runCurrent()

            assertEquals(1, haptics.played.count { it == "limitApproaching" })
        }

    @Test
    fun `time and input level follow the recorder and the level history is capped`() =
        runTest {
            val viewModel = createViewModel()
            tap(viewModel)

            repeat(80) { index ->
                recorder.levelFlow.value = (index % 10) / 10f + 0.01f
                runCurrent()
            }
            recorder.elapsedFlow.value = 12.seconds
            runCurrent()

            val live = assertIs<RecorderState.Live>(viewModel.state.value)
            assertEquals(50, live.levels.size)
            assertEquals(12.seconds, live.elapsed)
        }

    @Test
    fun `a recording running when the activity finishes is saved`() =
        runTest {
            val viewModel = createViewModel()
            tap(viewModel)

            viewModelStore.clear()
            runCurrent()

            assertEquals(1, recorder.stops)
            coVerify(exactly = 1) { notesRepository.create(any<JournalNote>()) }
        }

    @Test
    fun `a press the activity finishing interrupts is discarded`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.onPressed()
            runCurrent()

            viewModelStore.clear()
            runCurrent()

            assertEquals(1, recorder.discards)
            assertEquals(0, recorder.stops)
        }

    @Test
    fun `a failed save is tried once more when the activity finishes`() =
        runTest {
            var failures = 1
            coEvery { notesRepository.create(any<JournalNote>()) } answers {
                if (failures-- > 0) throw IllegalStateException("database closed")
                Uuid.random()
            }
            val viewModel = createViewModel()
            recordAndStop(viewModel)

            viewModelStore.clear()
            runCurrent()

            coVerify(exactly = 2) { notesRepository.create(any<JournalNote>()) }
        }

    /** Created through a [ViewModelProvider] so clearing [viewModelStore] runs the real `onCleared`. */
    private fun createViewModel(mediaFiles: MediaFileResolver? = null): RecorderViewModel {
        val viewModel =
            RecorderViewModel(
                recorder = recorder,
                saver =
                    AudioMomentSaver(
                        notesRepository = notesRepository,
                        durationResolver = durationResolver,
                        noteHealthAnnotator = mockk<NoteHealthAnnotator>(relaxed = true),
                        locationCaptureCoordinator = locationCaptureCoordinator,
                        deleteFile = { deletedFiles += it },
                    ),
                notesRepository = notesRepository,
                removalNotifier = { noteId -> removedNotes += noteId },
                feelingSaver = { feeling ->
                    savedFeelings += feeling
                    true
                },
                haptics = haptics,
                permissions = PermissionPrompts(checker = { it in granted }, store = InMemoryPromptStore(asked)),
                applicationScope = applicationScope,
                deleteFile = { deletedFiles += it },
                mediaFiles = mediaFiles,
            )
        val factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
            }
        return ViewModelProvider(viewModelStore, factory)["recorder-${Uuid.random()}", RecorderViewModel::class.java]
    }

    private fun TestScope.tap(viewModel: RecorderViewModel) {
        viewModel.onPressed()
        runCurrent()
        viewModel.onTapped()
        runCurrent()
    }

    private fun TestScope.hold(viewModel: RecorderViewModel) {
        viewModel.onPressed()
        runCurrent()
        viewModel.onCommitted()
        runCurrent()
    }

    private fun TestScope.recordAndStop(viewModel: RecorderViewModel) {
        tap(viewModel)
        viewModel.stop()
        runCurrent()
    }

    private fun TestScope.collectRequests(viewModel: RecorderViewModel): List<WatchPermission> {
        val requests = mutableListOf<WatchPermission>()
        backgroundScope.launch { viewModel.permissionRequests.collect { requests += it } }
        runCurrent()
        return requests
    }

    private fun audio(
        noteId: Uuid,
        mediaRef: String = FakeWearRecorder.DEFAULT_PATH,
    ): JournalNote.Audio {
        val now = Instant.fromEpochMilliseconds(1_710_000_000_000L)
        return JournalNote.Audio(
            mediaRef = mediaRef,
            uid = noteId,
            creationTimestamp = now,
            lastUpdated = now,
            durationMs = 4_200,
        )
    }

    private fun mediaFiles() =
        MediaFileResolver(
            directories =
                object : MediaDirectories {
                    override fun directory(collection: MediaCollection): String =
                        when (collection) {
                            MediaCollection.Library -> "/files/media"
                            MediaCollection.Recordings -> "/files/audio_notes"
                        }
                },
            fileExists = { true },
        )

    private class InMemoryPromptStore(
        private val asked: MutableSet<WatchPermission>,
    ) : PermissionPromptStore {
        override fun hasAsked(permission: WatchPermission): Boolean = permission in asked

        override fun markAsked(permission: WatchPermission) {
            asked += permission
        }
    }
}
