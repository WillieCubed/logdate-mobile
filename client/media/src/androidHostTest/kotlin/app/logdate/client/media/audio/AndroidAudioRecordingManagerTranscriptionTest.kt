package app.logdate.client.media.audio

import app.logdate.client.media.audio.transcription.InstallTimeTranscriptionService
import app.logdate.client.media.audio.transcription.TranscriptionResult
import app.logdate.client.text.toUiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import logdate.client.media.generated.resources.Res
import logdate.client.media.generated.resources.media_device_microphone_switch_failed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class AndroidAudioRecordingManagerTranscriptionTest : RecordingManagerTestFixture() {
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
            assertEquals(Res.string.media_device_microphone_switch_failed.toUiText(), routes.reported?.third)
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
}
