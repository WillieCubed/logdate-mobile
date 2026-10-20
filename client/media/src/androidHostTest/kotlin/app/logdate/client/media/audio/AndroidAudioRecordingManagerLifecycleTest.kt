package app.logdate.client.media.audio

import app.logdate.client.media.audio.transcription.TranscriptionResult
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.repository.transcription.TranscriptionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
internal class AndroidAudioRecordingManagerLifecycleTest : RecordingManagerTestFixture() {
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
                    override suspend fun persistRecordingTranscript(
                        work: app.logdate.client.repository.transcription.TranscriptionWorkToken,
                        document: app.logdate.client.repository.transcription.TranscriptDocument,
                        status: TranscriptionStatus,
                    ): app.logdate.client.repository.transcription.TranscriptionWorkToken {
                        val noteId = work.noteId
                        val text = document.plainText
                        try {
                            if (text == "Old transcript") {
                                oldWriteStarted.complete(Unit)
                                oldWriteGate.await()
                            }
                            stored.update { it + (noteId to text) }
                            if (text == "Middle transcript") middleWriteFinished.complete(Unit)
                            if (text == "New transcript") newWriteFinished.complete(Unit)
                            return work
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
}
