package app.logdate.client.media.audio

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
internal class RecordingDraftLifecycleTest : RecordingManagerTestFixture() {
    @Test
    fun `queued audio publication does not complete the draft or announce success`() =
        testScope.runTest {
            var completed = false
            val session =
                object : SuccessfulSession() {
                    override suspend fun complete(
                        path: String,
                        durationMs: Long,
                        quietly: Boolean,
                    ): Uuid {
                        completed = true
                        return id
                    }
                }
            val notifications = mutableListOf<Uuid>()
            val manager = buildManager(draftSession = session, finalizedFileAvailable = { false }, onQuietSave = { notifications += it })
            startAndConfirm(manager)
            controller.emit(
                RecordingServiceState(isRecording = false, recordedFilePath = "/recordings/default.m4a", quietSaveTriggered = true),
            )
            advanceUntilIdle()
            assertFalse(completed)
            assertTrue(notifications.isEmpty())
        }

    @Test
    fun `metadata checkpoints follow active time and pause transitions`() =
        testScope.runTest {
            var checkpoints = 0
            val session =
                object : SuccessfulSession() {
                    override suspend fun checkpoint(path: String) {
                        checkpoints++
                    }
                }
            val manager = buildManager(draftSession = session)
            startAndConfirm(manager)
            controller.emit(RecordingServiceState(isRecording = true, durationSeconds = 29))
            runCurrent()
            assertEquals(0, checkpoints)
            controller.emit(RecordingServiceState(isRecording = true, durationSeconds = 30))
            runCurrent()
            assertEquals(1, checkpoints)
            controller.emit(RecordingServiceState(isRecording = true, durationSeconds = 30, isPaused = true))
            runCurrent()
            assertEquals(2, checkpoints)
            controller.emit(RecordingServiceState(isRecording = true, durationSeconds = 30, isPaused = false))
            runCurrent()
            assertEquals(3, checkpoints)
            controller.emit(RecordingServiceState(isRecording = true, durationSeconds = 59))
            runCurrent()
            assertEquals(3, checkpoints)
            controller.emit(RecordingServiceState(isRecording = true, durationSeconds = 60))
            runCurrent()
            assertEquals(4, checkpoints)
            manager.stopRecording()
        }

    @Test
    fun `editor handoff cannot announce a completed Quiet Save twice`() =
        testScope.runTest {
            val session = SuccessfulSession()
            val notifications = mutableListOf<Uuid>()
            val manager = buildManager(draftSession = session, onQuietSave = { notifications += it })
            startAndConfirm(manager)
            controller.emit(
                RecordingServiceState(isRecording = false, recordedFilePath = "/recordings/default.m4a", quietSaveTriggered = true),
            )
            advanceUntilIdle()
            manager.stopRecording()
            advanceUntilIdle()
            assertEquals(listOf(session.id), notifications)
        }

    @Test
    fun `microphone waits for durable preparation and stays off when preparation fails`() =
        testScope.runTest {
            val prepared = CompletableDeferred<Unit>()
            val session =
                object : RecordingDraftLifecycle {
                    override suspend fun prepare(
                        blockId: Uuid,
                        path: String,
                    ) {
                        prepared.await()
                        error("Disk full")
                    }

                    override suspend fun checkpoint(path: String) = Unit

                    override suspend fun complete(
                        path: String,
                        durationMs: Long,
                        quietly: Boolean,
                    ): Uuid? = null
                }
            val manager = buildManager(draftSession = session)
            var started = true
            val start = launch { started = manager.startRecording(Uuid.random()) }
            runCurrent()
            assertEquals(0, controller.startCalls)
            prepared.complete(Unit)
            start.join()
            assertFalse(started)
            assertFalse(controller.microphoneActive)
            assertEquals(0, controller.startCalls)
        }

    @Test
    fun `Quiet Save preference is sampled once at recording start`() =
        testScope.runTest {
            var enabled = true
            val session = SuccessfulSession()
            val manager = buildManager(draftSession = session, quietSaveEnabled = { enabled })
            startAndConfirm(manager)
            assertTrue(manager.continuesWithoutEditor)
            enabled = false
            runCurrent()
            assertEquals(listOf(true), controller.quietSaveConfigurations)
            manager.stopRecording()
            startAndConfirm(manager)
            assertFalse(manager.continuesWithoutEditor)
            assertEquals(listOf(true, false), controller.quietSaveConfigurations)
            manager.stopRecording()
        }

    @Test
    fun `success is announced only after draft persistence finishes`() =
        testScope.runTest {
            val saved = CompletableDeferred<Unit>()
            val session =
                object : SuccessfulSession() {
                    override suspend fun complete(
                        path: String,
                        durationMs: Long,
                        quietly: Boolean,
                    ): Uuid {
                        saved.await()
                        return id
                    }
                }
            val notifications = mutableListOf<Uuid>()
            val manager = buildManager(draftSession = session, onQuietSave = { notifications += it })
            startAndConfirm(manager)
            controller.emit(
                RecordingServiceState(isRecording = false, recordedFilePath = "/recordings/default.m4a", quietSaveTriggered = true),
            )
            runCurrent()
            assertTrue(notifications.isEmpty())
            saved.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(session.id), notifications)
        }

    @Test
    fun `failed draft persistence does not announce success`() =
        testScope.runTest {
            val session =
                object : SuccessfulSession() {
                    override suspend fun complete(
                        path: String,
                        durationMs: Long,
                        quietly: Boolean,
                    ): Uuid = error("Disk full")
                }
            val notifications = mutableListOf<Uuid>()
            val manager = buildManager(draftSession = session, onQuietSave = { notifications += it })
            startAndConfirm(manager)
            controller.emit(
                RecordingServiceState(isRecording = false, recordedFilePath = "/recordings/default.m4a", quietSaveTriggered = true),
            )
            advanceUntilIdle()
            assertTrue(notifications.isEmpty())
            assertFalse(controller.microphoneActive)
        }

    private open class SuccessfulSession : RecordingDraftLifecycle {
        val id = Uuid.random()

        override suspend fun prepare(
            blockId: Uuid,
            path: String,
        ) = Unit

        override suspend fun checkpoint(path: String) = Unit

        override suspend fun complete(
            path: String,
            durationMs: Long,
            quietly: Boolean,
        ) = id
    }
}
