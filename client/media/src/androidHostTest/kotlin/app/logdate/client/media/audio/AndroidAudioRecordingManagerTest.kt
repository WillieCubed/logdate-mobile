package app.logdate.client.media.audio

import app.logdate.client.media.audio.transcription.TranscriptionFailure
import app.logdate.client.media.audio.transcription.TranscriptionResult
import app.logdate.client.media.audio.transcription.TranscriptionStartResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
internal class AndroidAudioRecordingManagerTest : RecordingManagerTestFixture() {
    @Test
    fun `a saved unattended recording does not block the next recording`() =
        testScope.runTest {
            val lifecycle =
                object : RecordingDraftLifecycle {
                    override suspend fun prepare(
                        blockId: Uuid,
                        path: String,
                    ) = Unit

                    override suspend fun checkpoint(path: String) = Unit

                    override suspend fun complete(
                        path: String,
                        durationMs: Long,
                        quietly: Boolean,
                    ) = Uuid.random()
                }
            val manager = buildManager(draftSession = lifecycle)
            startAndConfirm(manager, Uuid.random())
            controller.emit(
                RecordingServiceState(
                    isRecording = false,
                    durationSeconds = 120,
                    recordedFilePath = "/recordings/default.m4a",
                    quietSaveTriggered = true,
                ),
            )
            advanceUntilIdle()
            val next = launch { manager.startRecording(Uuid.random()) }
            runCurrent()
            try {
                assertEquals(2, controller.startCalls)
            } finally {
                controller.connect(RecordingServiceState(isRecording = true))
                next.join()
                manager.stopRecording()
            }
        }

    @Test
    fun `quiet stop releases the service even when finalization produced no file`() =
        testScope.runTest {
            val manager = buildManager()
            val starter = launch { manager.startRecording(Uuid.random()) }
            runCurrent()
            controller.connect(RecordingServiceState(isRecording = true))
            starter.join()
            controller.stopResult = null
            controller.emit(RecordingServiceState(isRecording = false, quietSaveTriggered = true, failureMessage = "Cannot finalize"))
            advanceUntilIdle()
            assertEquals(1, controller.shutdownCalls)
            assertFalse(controller.microphoneActive)
        }

    @Test
    fun `the service stays alive until the recording draft is durable`() =
        testScope.runTest {
            val saved = CompletableDeferred<Unit>()
            val draft = Uuid.random()
            val lifecycle =
                object : RecordingDraftLifecycle {
                    override suspend fun prepare(
                        blockId: Uuid,
                        path: String,
                    ) = Unit

                    override suspend fun checkpoint(path: String) = Unit

                    override suspend fun complete(
                        path: String,
                        durationMs: Long,
                        quietly: Boolean,
                    ): Uuid {
                        saved.await()
                        return draft
                    }
                }
            val manager = buildManager(draftSession = lifecycle)
            val starter = launch { manager.startRecording(Uuid.random()) }
            runCurrent()
            controller.connect(RecordingServiceState(isRecording = true, durationSeconds = 120))
            starter.join()
            controller.emit(
                RecordingServiceState(
                    isRecording = false,
                    durationSeconds = 120,
                    recordedFilePath = "/recordings/default.m4a",
                    quietSaveTriggered = true,
                ),
            )
            runCurrent()
            try {
                assertEquals(0, controller.shutdownCalls)
            } finally {
                saved.complete(Unit)
            }
            advanceUntilIdle()
            assertEquals(1, controller.shutdownCalls)
        }

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
    fun `start persists the allocated recording target before opening the recorder`() =
        testScope.runTest {
            val manager = buildManager()
            val noteId = Uuid.random()
            var persistedPath: String? = null
            var targetWasPersistedBeforeCapture = false
            val starter =
                launch {
                    assertTrue(
                        manager.startRecording(noteId) { path ->
                            persistedPath = path
                            targetWasPersistedBeforeCapture = controller.startCalls == 0
                            true
                        },
                    )
                }
            runCurrent()

            assertTrue(targetWasPersistedBeforeCapture)
            assertTrue(persistedPath.orEmpty().endsWith(".m4a"))
            assertEquals(1, controller.startCalls)
            controller.connect(RecordingServiceState(isRecording = true))
            advanceUntilIdle()
            starter.join()
        }

    @Test
    fun `start does not open the recorder when target persistence fails`() =
        testScope.runTest {
            val manager = buildManager()
            val result = manager.startRecording(Uuid.random()) { false }

            assertFalse(result)
            assertEquals(0, controller.startCalls)
            assertFalse(controller.microphoneActive)
        }

    @Test
    fun `a service error during start fails the start and leaves the manager usable`() =
        testScope.runTest {
            val manager = buildManager()
            val starter = launch { manager.startRecording() }
            runCurrent()
            controller.connect(RecordingServiceState(isRecording = false, failureMessage = "Failed to start recording"))
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
}
