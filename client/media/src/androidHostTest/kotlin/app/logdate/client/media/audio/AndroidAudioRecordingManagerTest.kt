package app.logdate.client.media.audio

import app.logdate.client.media.audio.tagging.NoopAudioTaggingService
import app.logdate.client.media.audio.transcription.InstallTimeTranscriptionService
import app.logdate.client.media.audio.transcription.TranscriptionFailure
import app.logdate.client.media.audio.transcription.TranscriptionResult
import app.logdate.client.media.audio.transcription.TranscriptionService
import app.logdate.client.media.audio.transcription.TranscriptionStartResult
import app.logdate.client.media.device.AudioRouteRepository
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.repository.audio.AudioTag
import app.logdate.client.repository.audio.AudioTagRepository
import app.logdate.client.repository.transcription.TranscriptionData
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.repository.transcription.TranscriptionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Session state machine tests for [AndroidAudioRecordingManager] against a fake service.
 *
 * The bugs these guard against: a start that reported success before the recorder ran,
 * a file path handed back before the recorder had finalized it, a singleton that disabled
 * itself after one stop failure, and a stop from the notification that lost the recording.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidAudioRecordingManagerTest {
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val controller = FakeRecordingServiceController()

    private fun buildManager(
        transcription: TranscriptionService? = null,
        repository: TranscriptionRepository = FakeTranscriptionRepository(),
        storage: AudioStorage = FakeAudioStorage(),
        routes: FakeAudioRouteRepository = FakeAudioRouteRepository(),
    ): AndroidAudioRecordingManager =
        AndroidAudioRecordingManager(
            audioStorage = storage,
            transcriptionRepository = repository,
            audioTaggingService = NoopAudioTaggingService,
            audioTagRepository = FakeAudioTagRepository(),
            audioRouteRepository = routes,
            serviceController = controller,
            workDispatcher = dispatcher,
            startTimeout = 5.seconds,
        ).also { manager -> transcription?.let(manager::setTranscriptionService) }

    @Test
    fun `start reports success only once the service confirms the recorder is running`() =
        testScope.runTest {
            val manager = buildManager()
            var result: Boolean? = null
            val starter = launch { result = manager.startRecording(Uuid.random()) }
            runCurrent()

            assertNull(result, "Must wait for the service instead of assuming the recorder started")
            assertFalse(manager.isRecording)

            controller.connect(RecordingServiceState(isRecording = true))
            advanceUntilIdle()

            assertEquals(true, result)
            assertTrue(manager.isRecording)
            assertTrue(manager.getRecordingStateFlow().value)
            starter.join()
        }

    @Test
    fun `a service error during start fails the start and leaves the manager usable`() =
        testScope.runTest {
            val manager = buildManager()
            val starter = launch { manager.startRecording() }
            runCurrent()
            controller.connect(RecordingServiceState(isRecording = false, error = "Failed to start recording"))
            advanceUntilIdle()
            starter.join()

            assertFalse(manager.isRecording)
            assertEquals(1, controller.shutdownCalls, "A failed start must release the service")

            // The next attempt is a fresh session, not a permanently wedged singleton.
            val retry = launch { manager.startRecording() }
            runCurrent()
            controller.connect(RecordingServiceState(isRecording = true))
            advanceUntilIdle()
            retry.join()
            assertTrue(manager.isRecording)
        }

    @Test
    fun `a service that never connects times out the start`() =
        testScope.runTest {
            val manager = buildManager()
            var result: Boolean? = null
            val starter = launch { result = manager.startRecording() }
            advanceTimeBy(6.seconds)
            advanceUntilIdle()
            starter.join()

            assertEquals(false, result)
            assertFalse(manager.isRecording)
            assertEquals(1, controller.shutdownCalls)
        }

    @Test
    fun `stop finalizes the file through the binder before the service is torn down`() =
        testScope.runTest {
            val manager = buildManager()
            startAndConfirm(manager)
            controller.stopResult = "/recordings/one.m4a"

            val path = manager.stopRecording()
            advanceUntilIdle()

            assertEquals("/recordings/one.m4a", path)
            assertEquals(listOf("stopRecordingNow", "shutdown"), controller.stopOrder)
            assertFalse(manager.isRecording)
            assertFalse(manager.getRecordingStateFlow().value)
        }

    @Test
    fun `a second start while recording is refused without touching the service`() =
        testScope.runTest {
            val manager = buildManager()
            startAndConfirm(manager)
            val startsBefore = controller.startCalls

            assertFalse(manager.startRecording())
            advanceUntilIdle()

            assertEquals(startsBefore, controller.startCalls)
            assertTrue(manager.isRecording)
        }

    @Test
    fun `a recording the service ended on its own is still handed back by stop`() =
        testScope.runTest {
            val manager = buildManager()
            startAndConfirm(manager)

            // The notification's Stop action finalizes inside the service.
            controller.emit(RecordingServiceState(isRecording = false, recordedFilePath = "/recordings/notif.m4a"))
            advanceUntilIdle()

            assertFalse(manager.getRecordingStateFlow().value, "The editor must learn the session ended")
            controller.stopResult = null

            val path = manager.stopRecording()
            advanceUntilIdle()

            assertEquals("/recordings/notif.m4a", path)
            assertEquals(1, controller.shutdownCalls)
        }

    @Test
    fun `a stop that fails inside the service returns null and the next start works`() =
        testScope.runTest {
            val manager = buildManager()
            startAndConfirm(manager)
            controller.stopThrows = IllegalStateException("stop failed")

            val path = manager.stopRecording()
            advanceUntilIdle()

            assertNull(path)
            assertFalse(manager.isRecording)
            assertEquals(1, controller.shutdownCalls)

            controller.stopThrows = null
            startAndConfirm(manager)
            assertTrue(manager.isRecording)
        }

    @Test
    fun `stopping while idle does not touch the service`() =
        testScope.runTest {
            val manager = buildManager()

            assertNull(manager.stopRecording())
            advanceUntilIdle()

            assertEquals(0, controller.shutdownCalls)
            assertTrue(controller.stopOrder.isEmpty())
        }

    @Test
    fun `stop still returns the file when transcription teardown throws`() =
        testScope.runTest {
            val transcription = FakeTranscriptionService(stopThrows = true)
            val manager = buildManager(transcription)
            startAndConfirm(manager)
            controller.stopResult = "/recordings/two.m4a"

            val path = manager.stopRecording()
            advanceUntilIdle()

            assertEquals("/recordings/two.m4a", path)
            assertFalse(manager.isRecording)
        }

    @Test
    fun `rebinding the same transcription service does not restart its warm-up`() =
        testScope.runTest {
            val transcription = FakeTranscriptionService()
            val manager = buildManager(transcription)
            advanceUntilIdle()
            assertEquals(1, transcription.warmUpCalls)

            manager.setTranscriptionService(transcription)
            manager.setTranscriptionService(transcription)
            advanceUntilIdle()

            assertEquals(1, transcription.warmUpCalls)
        }

    @Test
    fun `start does not wait on transcription setup to report the recorder is running`() =
        testScope.runTest {
            val startGate = CompletableDeferred<Unit>()
            val manager = buildManager(FakeTranscriptionService(startGate = startGate))

            var result: Boolean? = null
            val starter = launch { result = manager.startRecording(Uuid.random()) }
            runCurrent()
            controller.connect(RecordingServiceState(isRecording = true))
            advanceUntilIdle()

            assertEquals(true, result, "Recording must be confirmed without waiting on a second microphone acquisition")
            starter.join()

            startGate.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun `stop right after start waits for transcription setup before tearing it down`() =
        testScope.runTest {
            val startGate = CompletableDeferred<Unit>()
            val order = mutableListOf<String>()
            val manager = buildManager(FakeTranscriptionService(startGate = startGate, callOrder = order))
            controller.stopResult = "/recordings/quick.m4a"

            startAndConfirm(manager)
            val stopper = launch { manager.stopRecording() }
            runCurrent()

            assertTrue(order.isEmpty(), "Stop must not race a transcription start still in flight")

            startGate.complete(Unit)
            advanceUntilIdle()
            stopper.join()

            assertEquals(listOf("startLiveTranscription", "stopLiveTranscription"), order)
        }

    @Test
    fun `a recording without a live transcription start does not adopt a previous stop replay`() =
        testScope.runTest {
            val transcription =
                FakeTranscriptionService(
                    liveStartResult = TranscriptionStartResult.Failed(TranscriptionFailure.NotAvailable),
                    liveStopResult = TranscriptionResult.Success("A previous note's transcript", isFinal = true),
                )
            val manager = buildManager(transcription)
            startAndConfirm(manager)
            manager.stopRecording()
            runCurrent()

            assertNull(manager.getTranscriptionFlow().first())
        }

    @Test
    fun `outgoing live refinement stays with its note while a new recorder awaits confirmation`() =
        testScope.runTest {
            val repository = FakeTranscriptionRepository()
            val transcription = FakeTranscriptionService()
            val manager = buildManager(transcription, repository)
            val firstNoteId = Uuid.random()
            startAndConfirm(manager, firstNoteId)
            manager.stopRecording()
            val secondNoteId = Uuid.random()
            val starter = launch { manager.startRecording(secondNoteId) }
            runCurrent()
            assertEquals(secondNoteId, manager.currentRecordingTargetNoteId)
            assertFalse(manager.isRecording)

            transcription.emit(TranscriptionResult.Success("First note's final refinement", isFinal = true))
            runCurrent()
            controller.connect(RecordingServiceState(isRecording = true))
            runCurrent()
            starter.join()
            val saved = repository.nextUpdate.await()

            assertEquals(firstNoteId, saved.first)
            assertEquals("First note's final refinement", saved.second)
            assertNull(manager.getTranscriptionFlow().first(), "An outgoing refinement must not appear in the new recording")
        }

    @Test
    fun `newer transcription for a note cancels its older pending write across intervening sessions`() =
        testScope.runTest {
            val oldWriteStarted = CompletableDeferred<Unit>()
            val oldWriteGate = CompletableDeferred<Unit>()
            val oldWriteFinished = CompletableDeferred<Unit>()
            val middleWriteFinished = CompletableDeferred<Unit>()
            val newWriteFinished = CompletableDeferred<Unit>()
            val stored = MutableStateFlow<Map<Uuid, String?>>(emptyMap())
            val repository =
                object : TranscriptionRepository by FakeTranscriptionRepository() {
                    override suspend fun updateTranscription(
                        noteId: Uuid,
                        text: String?,
                        status: TranscriptionStatus,
                        errorMessage: String?,
                    ): Boolean {
                        try {
                            if (text == "Old transcript") {
                                oldWriteStarted.complete(Unit)
                                oldWriteGate.await()
                            }
                            stored.update { it + (noteId to text) }
                            if (text == "Middle transcript") middleWriteFinished.complete(Unit)
                            if (text == "New transcript") newWriteFinished.complete(Unit)
                            return true
                        } finally {
                            if (text == "Old transcript") oldWriteFinished.complete(Unit)
                        }
                    }
                }
            val transcription =
                FakeTranscriptionService(
                    fileRecoveries =
                        mapOf(
                            "/recordings/first.m4a" to
                                CompletableDeferred<TranscriptionResult>(
                                    TranscriptionResult.Success("Old transcript", isFinal = true),
                                ),
                            "/recordings/second.m4a" to
                                CompletableDeferred<TranscriptionResult>(
                                    TranscriptionResult.Success("Middle transcript", isFinal = true),
                                ),
                            "/recordings/third.m4a" to
                                CompletableDeferred<TranscriptionResult>(
                                    TranscriptionResult.Success("New transcript", isFinal = true),
                                ),
                        ),
                )
            val manager = buildManager(transcription, repository)
            val firstNoteId = Uuid.random()
            startAndConfirm(manager, firstNoteId)
            controller.stopResult = "/recordings/first.m4a"
            manager.stopRecording()
            runCurrent()
            oldWriteStarted.await()

            startAndConfirm(manager, Uuid.random())
            controller.stopResult = "/recordings/second.m4a"
            manager.stopRecording()
            runCurrent()
            middleWriteFinished.await()

            startAndConfirm(manager, firstNoteId)
            controller.stopResult = "/recordings/third.m4a"
            manager.stopRecording()
            runCurrent()
            newWriteFinished.await()
            oldWriteGate.complete(Unit)
            oldWriteFinished.await()

            assertEquals("New transcript", stored.value[firstNoteId])
        }

    @Test
    fun `background stop retains its completed recording for the owner to recover`() =
        testScope.runTest {
            val manager = buildManager()
            val noteId = Uuid.random()
            startAndConfirm(manager, noteId)
            val recordingPath = manager.currentRecordingPath
            controller.stopResult = "/recordings/background.m4a"

            manager.requestStopRecording()
            runCurrent()

            assertFalse(manager.isRecording)
            assertEquals(noteId, manager.currentRecordingTargetNoteId)
            assertEquals(recordingPath, manager.currentRecordingPath)
            controller.stopResult = null
            assertEquals("/recordings/background.m4a", manager.stopRecording())
            assertNull(manager.currentRecordingPath)
        }

    @Test
    fun `cancellation during transcription teardown retains the completed recording`() =
        testScope.runTest {
            val stopGate = CompletableDeferred<Unit>()
            val manager = buildManager(FakeTranscriptionService(stopGate = stopGate))
            val noteId = Uuid.random()
            startAndConfirm(manager, noteId)
            controller.stopResult = "/recordings/teardown.m4a"
            val stopper = launch { manager.stopRecording() }
            runCurrent()
            assertFalse(controller.microphoneActive)

            stopper.cancelAndJoin()
            stopGate.complete(Unit)
            runCurrent()

            assertEquals(noteId, manager.currentRecordingTargetNoteId)
            controller.stopResult = null
            assertEquals("/recordings/teardown.m4a", manager.stopRecording())
        }

    @Test
    fun `release closes the microphone even when transcription setup is still waiting`() =
        testScope.runTest {
            val manager = buildManager(FakeTranscriptionService(startGate = CompletableDeferred()))
            startAndConfirm(manager)

            manager.release()
            runCurrent()

            assertFalse(controller.microphoneActive)
            assertFalse(manager.isRecording)
        }

    @Test
    fun `an old background stop request cannot stop a newer recording session`() =
        testScope.runTest {
            val manager = buildManager()
            startAndConfirm(manager)
            manager.requestStopRecording()
            manager.stopRecording()
            val newNoteId = Uuid.random()
            val starter = launch(start = CoroutineStart.UNDISPATCHED) { manager.startRecording(newNoteId) }
            controller.connect(RecordingServiceState(isRecording = true))
            runCurrent()
            starter.join()

            assertTrue(controller.microphoneActive)
            assertTrue(manager.isRecording)
            assertEquals(newNoteId, manager.currentRecordingTargetNoteId)
        }

    @Test
    fun `cancelling stop during transcription setup closes the microphone and retains the finalized file`() =
        testScope.runTest {
            val startGate = CompletableDeferred<Unit>()
            val manager = buildManager(FakeTranscriptionService(startGate = startGate))
            val noteId = Uuid.random()
            startAndConfirm(manager, noteId)
            controller.stopResult = "/recordings/cancelled-stop.m4a"
            val stopper = launch { manager.stopRecording() }
            runCurrent()

            stopper.cancelAndJoin()
            runCurrent()

            assertFalse(controller.microphoneActive)
            assertFalse(manager.isRecording)
            assertEquals(noteId, manager.currentRecordingTargetNoteId)
            controller.stopResult = null
            assertEquals("/recordings/cancelled-stop.m4a", manager.stopRecording())
            assertNull(manager.currentRecordingTargetNoteId)
        }

    @Test
    fun `a new start cannot discard a completed recording awaiting handoff`() =
        testScope.runTest {
            val manager = buildManager()
            val noteId = Uuid.random()
            startAndConfirm(manager, noteId)
            controller.emit(RecordingServiceState(isRecording = false, recordedFilePath = "/recordings/pending.m4a"))
            runCurrent()
            val startsBefore = controller.startCalls
            val starter = launch { assertFalse(manager.startRecording(Uuid.random())) }
            runCurrent()
            controller.connect(RecordingServiceState(isRecording = true))
            advanceUntilIdle()
            starter.join()

            assertEquals(startsBefore, controller.startCalls)
            assertEquals(noteId, manager.currentRecordingTargetNoteId)
            controller.stopResult = null
            assertEquals("/recordings/pending.m4a", manager.stopRecording())
        }

    @Test
    fun `recording ownership remains available until the session is finalized`() =
        testScope.runTest {
            val manager = buildManager()
            val noteId = Uuid.random()
            assertNull(manager.currentRecordingTargetNoteId)

            startAndConfirm(manager, noteId)
            assertEquals(noteId, manager.currentRecordingTargetNoteId)

            manager.stopRecording()
            assertNull(manager.currentRecordingTargetNoteId)
        }

    @Test
    fun `notification pause and resume reach the manager paused state`() =
        testScope.runTest {
            val manager = buildManager()
            val observed = mutableListOf<Boolean>()
            backgroundScope.launch { manager.getRecordingPausedFlow().collect { observed += it } }
            runCurrent()
            startAndConfirm(manager)
            assertFalse(manager.currentRecordingPaused)

            controller.emit(RecordingServiceState(isRecording = true, isPaused = true))
            runCurrent()
            assertTrue(manager.currentRecordingPaused)

            controller.emit(RecordingServiceState(isRecording = true, isPaused = false))
            runCurrent()
            assertFalse(manager.currentRecordingPaused)
            assertEquals(listOf(false, true, false), observed)
        }

    @Test
    fun `cancelling a start awaiting service confirmation releases the recording session`() =
        testScope.runTest {
            val manager = buildManager()
            val startingStates = mutableListOf<Boolean>()
            backgroundScope.launch { manager.getRecordingStartingFlow().collect { startingStates += it } }
            runCurrent()
            val starter = launch { manager.startRecording(Uuid.random()) }
            runCurrent()
            assertTrue(controller.microphoneActive)
            assertTrue(manager.isStartingRecording)

            starter.cancelAndJoin()
            advanceUntilIdle()

            assertTrue(starter.isCancelled)
            assertFalse(controller.microphoneActive, "The microphone must close when its start caller disappears")
            assertNull(manager.currentRecordingPath)
            assertFalse(manager.isRecording)
            assertFalse(manager.isStartingRecording)
            assertEquals(listOf(false, true, false), startingStates)

            startAndConfirm(manager)
            assertTrue(manager.isRecording)
            assertFalse(manager.isStartingRecording)
        }

    @Test
    fun `cancellation creating a recording target propagates to the caller`() =
        testScope.runTest {
            val storage =
                object : AudioStorage {
                    override suspend fun createRecordingTarget(extension: String): AudioRecordingTarget =
                        throw CancellationException("Editor closed")
                }
            val manager = buildManager(storage = storage)

            assertFailsWith<CancellationException> { manager.startRecording() }
            assertFalse(controller.microphoneActive)
            assertNull(manager.currentRecordingPath)
        }

    @Test
    fun `delayed file transcription stays attached to the session that recorded its file`() =
        testScope.runTest {
            val recovery = CompletableDeferred<TranscriptionResult>()
            val repository = FakeTranscriptionRepository()
            val transcription = FakeTranscriptionService(fileRecovery = recovery)
            val manager = buildManager(transcription, repository)
            val firstNoteId = Uuid.random()
            val secondNoteId = Uuid.random()
            startAndConfirm(manager, firstNoteId)
            controller.stopResult = "/recordings/first.m4a"
            manager.stopRecording()
            runCurrent()

            startAndConfirm(manager, secondNoteId)
            controller.stopResult = "/recordings/second.m4a"
            manager.stopRecording()
            runCurrent()
            recovery.complete(TranscriptionResult.Success("First recording transcript", isFinal = true))
            advanceUntilIdle()

            val saved = repository.nextUpdate.await()
            assertEquals(firstNoteId, saved.first)
            assertEquals("First recording transcript", saved.second)
            assertNull(manager.getTranscriptionFlow().first(), "An older session must not overwrite the current transcript")
        }

    @Test
    fun `switching microphone updates recorder and transcription without restarting the session`() =
        testScope.runTest {
            val routes = FakeAudioRouteRepository()
            val transcription = FakeTranscriptionService()
            val manager = buildManager(transcription, routes = routes)
            startAndConfirm(manager)
            val path = manager.currentRecordingPath
            routes.selectInputDevice("usb")
            advanceUntilIdle()
            assertEquals("usb", controller.inputRequests.last())
            assertEquals("usb", transcription.inputRequests.last())
            assertEquals(1, controller.startCalls)
            assertTrue(controller.stopOrder.isEmpty())
            assertEquals(path, manager.currentRecordingPath)
            assertTrue(manager.isRecording)
        }

    @Test
    fun `reported active route does not overwrite the requested microphone`() =
        testScope.runTest {
            val routes = FakeAudioRouteRepository()
            val transcription = FakeTranscriptionService()
            val manager = buildManager(transcription, routes = routes)
            startAndConfirm(manager)
            routes.selectInputDevice("usb")
            advanceUntilIdle()
            controller.emit(RecordingServiceState(isRecording = true, routedInputDeviceId = "built-in"))
            advanceUntilIdle()
            assertEquals(Triple("built-in", true, null), routes.reported)
            assertEquals("usb", controller.inputRequests.last())
            assertEquals("built-in", transcription.inputRequests.last())
        }

    @Test
    fun `route failure does not disable later microphone selections or stop recording`() =
        testScope.runTest {
            val routes = FakeAudioRouteRepository()
            val manager = buildManager(routes = routes)
            startAndConfirm(manager)
            controller.inputUpdateThrows = IllegalStateException("routing failed")
            routes.selectInputDevice("usb")
            advanceUntilIdle()
            assertTrue(manager.isRecording)
            assertTrue(requireNotNull(routes.reported?.third).contains("switch"))
            controller.inputUpdateThrows = null
            routes.selectInputDevice("headset")
            advanceUntilIdle()
            assertEquals("headset", controller.inputRequests.last())
        }

    @Test
    fun `mic selection after finishing does not reach the stopped session`() =
        testScope.runTest {
            val routes = FakeAudioRouteRepository()
            val manager = buildManager(routes = routes)
            startAndConfirm(manager)
            manager.stopRecording()
            advanceUntilIdle()
            val calls = controller.inputRequests.size
            routes.selectInputDevice("usb")
            advanceUntilIdle()
            assertEquals(calls, controller.inputRequests.size)
            assertEquals(Triple(null, false, null), routes.reported)
        }

    @Test
    fun `failed switch can be retried by selecting the same microphone`() =
        testScope.runTest {
            val routes = FakeAudioRouteRepository()
            val manager = buildManager(routes = routes)
            startAndConfirm(manager)
            controller.inputUpdateThrows = IllegalStateException("routing failed")
            routes.selectInputDevice("usb")
            advanceUntilIdle()
            controller.inputUpdateThrows = null
            val calls = controller.inputRequests.size
            routes.selectInputDevice("usb")
            advanceUntilIdle()
            assertEquals(calls + 1, controller.inputRequests.size)
            assertEquals("usb", controller.inputRequests.last())
        }

    @Test
    fun `unconfirmed transcription input recovers text from saved audio instead of persisting the wrong live transcript`() =
        testScope.runTest {
            val routes = FakeAudioRouteRepository()
            val repository = FakeTranscriptionRepository()
            val transcription =
                FakeTranscriptionService(
                    allowFileRecovery = true,
                    fallbackResult = TranscriptionResult.Success("Text from the recorded input", isFinal = true),
                    liveStopResult = TranscriptionResult.Success("Text from a different input", isFinal = true),
                )
            val facade = InstallTimeTranscriptionService(testScope.backgroundScope) { transcription }
            val manager = buildManager(facade, repository, routes = routes)
            startAndConfirm(manager)
            transcription.inputRoutingAccepted = false
            routes.selectInputDevice("usb")
            advanceUntilIdle()
            transcription.emit(TranscriptionResult.Success("Text from a different input"))
            advanceUntilIdle()
            assertFalse(repository.nextUpdate.isCompleted)
            manager.stopRecording()
            advanceUntilIdle()
            assertEquals("Text from the recorded input", repository.nextUpdate.await().second)
            assertEquals(1, transcription.fileTranscriptionCalls)
        }

    @Test
    fun `resuming retries a microphone selection rejected while paused`() =
        testScope.runTest {
            val routes = FakeAudioRouteRepository()
            val manager = buildManager(routes = routes)
            startAndConfirm(manager)
            assertTrue(manager.pauseRecording())
            controller.inputUpdateThrows = IllegalStateException("route unavailable while paused")
            routes.selectInputDevice("usb")
            advanceUntilIdle()
            controller.inputUpdateThrows = null
            val calls = controller.inputRequests.size
            assertTrue(manager.resumeRecording())
            advanceUntilIdle()
            assertEquals(calls + 1, controller.inputRequests.size)
            assertEquals("usb", controller.inputRequests.last())
            assertTrue(manager.isRecording)
            assertEquals(1, controller.startCalls)
        }

    private suspend fun TestScope.startAndConfirm(
        manager: AndroidAudioRecordingManager,
        noteId: Uuid = Uuid.random(),
    ) {
        val starter = launch { assertTrue(manager.startRecording(noteId)) }
        runCurrent()
        controller.connect(RecordingServiceState(isRecording = true))
        advanceUntilIdle()
        starter.join()
    }
}

private class FakeRecordingServiceController : RecordingServiceController {
    private val state = MutableStateFlow<RecordingServiceState?>(null)
    override val serviceState: StateFlow<RecordingServiceState?> = state.asStateFlow()

    var microphoneActive = false
    var startCalls = 0
    var shutdownCalls = 0
    var stopResult: String? = "/recordings/default.m4a"
    var stopThrows: Exception? = null
    val stopOrder = mutableListOf<String>()

    override fun start(
        outputPath: String,
        inputDeviceId: String?,
    ): Boolean {
        startCalls++
        microphoneActive = true
        return true
    }

    /** Simulates the service connecting with [initial] as its current state. */
    fun connect(initial: RecordingServiceState) {
        state.value = initial
    }

    fun emit(next: RecordingServiceState) {
        state.value = next
    }

    override fun stopRecordingNow(): String? {
        stopOrder += "stopRecordingNow"
        stopThrows?.let { throw it }
        val current = state.value
        if (current != null && current.isRecording) {
            state.value = current.copy(isRecording = false, recordedFilePath = stopResult)
        }
        return stopResult
    }

    override fun shutdown() {
        stopOrder += "shutdown"
        shutdownCalls++
        microphoneActive = false
        state.value = null
    }

    override fun pause(): Boolean = true

    override fun resume(): Boolean = true

    val inputRequests = mutableListOf<String?>()
    var inputUpdateThrows: Exception? = null

    override fun updatePreferredInputDevice(inputDeviceId: String?) {
        inputUpdateThrows?.let { throw it }
        inputRequests += inputDeviceId
    }
}

private class FakeAudioStorage : AudioStorage {
    override suspend fun createRecordingTarget(extension: String): AudioRecordingTarget =
        AudioRecordingTarget(path = "/recordings/${Uuid.random()}.$extension")
}

private class FakeAudioRouteRepository : AudioRouteRepository {
    private val selection =
        MediaDeviceSelectionUiState(
            kind = MediaDeviceKind.AUDIO_INPUT,
            devices = emptyList(),
            selectedDeviceId = null,
        )
    override val inputDevices: StateFlow<MediaDeviceSelectionUiState> = MutableStateFlow(selection)
    override val outputDevices: StateFlow<MediaDeviceSelectionUiState> = MutableStateFlow(selection)

    private val requested = MutableSharedFlow<String?>(replay = 1).apply { tryEmit(null) }
    override val requestedInputDeviceIds: SharedFlow<String?> = requested
    var reported: Triple<String?, Boolean, String?>? = null

    override fun updateRecordingInputDevice(
        deviceId: String?,
        isRecording: Boolean,
        error: String?,
    ) {
        reported = Triple(deviceId, isRecording, error)
    }

    override fun selectInputDevice(deviceId: String) {
        requested.tryEmit(deviceId)
    }

    override fun selectOutputDevice(deviceId: String) = Unit
}

private class FakeTranscriptionRepository : TranscriptionRepository {
    val nextUpdate = CompletableDeferred<Pair<Uuid, String?>>()

    override suspend fun requestTranscription(noteId: Uuid): Boolean = true

    override suspend fun getTranscription(noteId: Uuid): TranscriptionData? = null

    override fun observeTranscription(noteId: Uuid): Flow<TranscriptionData?> = flowOf(null)

    override suspend fun getPendingTranscriptions(): List<TranscriptionData> = emptyList()

    override suspend fun updateTranscription(
        noteId: Uuid,
        text: String?,
        status: TranscriptionStatus,
        errorMessage: String?,
    ): Boolean {
        nextUpdate.complete(noteId to text)
        return true
    }

    override suspend fun deleteTranscription(noteId: Uuid): Boolean = true
}

private class FakeAudioTagRepository : AudioTagRepository {
    override suspend fun replaceTagsForNote(
        noteId: Uuid,
        tags: List<AudioTag>,
    ) = Unit

    override suspend fun getTagsForNote(noteId: Uuid): List<AudioTag> = emptyList()

    override fun observeTagsForNote(noteId: Uuid): Flow<List<AudioTag>> = flowOf(emptyList())

    override suspend fun findNotesBySoundName(soundName: String): List<Uuid> = emptyList()
}

private class FakeTranscriptionService(
    private val stopThrows: Boolean = false,
    private val startGate: CompletableDeferred<Unit>? = null,
    private val stopGate: CompletableDeferred<Unit>? = null,
    private val callOrder: MutableList<String>? = null,
    private val fileRecovery: CompletableDeferred<TranscriptionResult>? = null,
    private val fileRecoveries: Map<String, CompletableDeferred<TranscriptionResult>> = emptyMap(),
    private val liveStartResult: TranscriptionStartResult = TranscriptionStartResult.Started,
    private val liveStopResult: TranscriptionResult = TranscriptionResult.Cancelled,
    private val allowFileRecovery: Boolean = false,
    private val fallbackResult: TranscriptionResult = TranscriptionResult.Cancelled,
) : TranscriptionService {
    private val results = MutableSharedFlow<TranscriptionResult>(replay = 1)
    var warmUpCalls = 0
    val inputRequests = mutableListOf<String?>()

    var inputRoutingAccepted = true
    var fileTranscriptionCalls = 0

    override fun updatePreferredInputDevice(deviceId: String?): Boolean {
        inputRequests += deviceId
        return inputRoutingAccepted
    }

    override fun getTranscriptionFlow(): SharedFlow<TranscriptionResult> = results

    suspend fun emit(result: TranscriptionResult) {
        results.emit(result)
    }

    override suspend fun startLiveTranscription(): TranscriptionStartResult {
        startGate?.await()
        callOrder?.add("startLiveTranscription")
        return liveStartResult
    }

    override suspend fun stopLiveTranscription(): TranscriptionResult {
        stopGate?.await()
        callOrder?.add("stopLiveTranscription")
        if (stopThrows) throw IllegalStateException("transcription stop failed")
        return liveStopResult
    }

    override suspend fun transcribeAudioFile(audioUri: String): TranscriptionResult {
        fileTranscriptionCalls++
        return if (audioUri in fileRecoveries) {
            fileRecoveries.getValue(audioUri).await()
        } else if (audioUri == "/recordings/first.m4a") {
            fileRecovery?.await() ?: TranscriptionResult.Cancelled
        } else {
            fallbackResult
        }
    }

    override suspend fun cancelTranscription() = Unit

    override fun getSupportedLanguages(): List<String> = listOf("en-US")

    override fun setLanguage(languageCode: String) = Unit

    override val supportsLiveTranscription: Boolean = fileRecovery == null && fileRecoveries.isEmpty()

    override val supportsFileTranscription: Boolean = allowFileRecovery || fileRecovery != null || fileRecoveries.isNotEmpty()

    override suspend fun resetTranscription() = Unit

    override suspend fun warmUp() {
        warmUpCalls++
    }

    override fun release() = Unit
}
