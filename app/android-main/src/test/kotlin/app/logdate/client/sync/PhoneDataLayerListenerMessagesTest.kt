package app.logdate.client.sync

import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import app.logdate.feature.editor.ui.camera.CameraRemoteCommand
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneDataLayerListenerMessagesTest : PhoneDataLayerListenerFixture() {
    @Test
    fun `camera open message launches camera`() =
        runTest {
            var launched = false
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    onLaunchCamera = { launched = true },
                )

            service.onMessageReceived(message(path = "/logdate/camera/open"))
            advanceUntilIdle()

            assertTrue(launched)
        }

    @Test
    fun `camera capture message sends capture command and does not sync work`() =
        runTest {
            var launched = false
            val cameraCommands = mutableListOf<CameraRemoteCommand>()
            val syncBridge = mockk<PhoneWearSyncBridge>(relaxed = true)
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    syncBridge = syncBridge,
                    onLaunchCamera = { launched = true },
                    onCameraCommand = { cameraCommands.add(it) },
                )

            service.onMessageReceived(message(path = "/logdate/camera/capture"))
            advanceUntilIdle()

            assertFalse(launched)
            assertTrue(cameraCommands.contains(CameraRemoteCommand.Capture))
            coVerify(exactly = 0) { syncBridge.publishNotesToWatch(any()) }
            coVerify(exactly = 0) { syncBridge.streamAudioToWatch(any(), any()) }
        }

    @Test
    fun `camera close message sends close command and does not sync work`() =
        runTest {
            var launched = false
            val cameraCommands = mutableListOf<CameraRemoteCommand>()
            val syncBridge = mockk<PhoneWearSyncBridge>(relaxed = true)
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    syncBridge = syncBridge,
                    onLaunchCamera = { launched = true },
                    onCameraCommand = { cameraCommands.add(it) },
                )

            service.onMessageReceived(message(path = "/logdate/camera/close"))
            advanceUntilIdle()

            assertFalse(launched)
            assertTrue(cameraCommands.contains(CameraRemoteCommand.Close))
            coVerify(exactly = 0) { syncBridge.publishNotesToWatch(any()) }
            coVerify(exactly = 0) { syncBridge.streamAudioToWatch(any(), any()) }
        }

    @Test
    fun `camera switch message sends switch command`() =
        runTest {
            val cameraCommands = mutableListOf<CameraRemoteCommand>()
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    onCameraCommand = { cameraCommands.add(it) },
                )

            service.onMessageReceived(message(path = "/logdate/camera/switch"))
            advanceUntilIdle()

            assertTrue(cameraCommands.contains(CameraRemoteCommand.SwitchBuiltInCamera))
        }

    @Test
    fun `camera select message sends built-in category command`() =
        runTest {
            val cameraCommands = mutableListOf<CameraRemoteCommand>()
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    onCameraCommand = { cameraCommands.add(it) },
                )

            service.onMessageReceived(
                message(
                    path = "/logdate/camera/select",
                    data = "front".encodeToByteArray(),
                ),
            )
            advanceUntilIdle()

            assertTrue(
                cameraCommands.contains(
                    CameraRemoteCommand.SelectCameraCategory(MediaDeviceCategory.FRONT_CAMERA),
                ),
            )
        }

    @Test
    fun `camera select message sends exact device command`() =
        runTest {
            val cameraCommands = mutableListOf<CameraRemoteCommand>()
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    onCameraCommand = { cameraCommands.add(it) },
                )

            service.onMessageReceived(
                message(
                    path = "/logdate/camera/select",
                    data = "device:usb-camera-1".encodeToByteArray(),
                ),
            )
            advanceUntilIdle()

            assertTrue(
                cameraCommands.contains(
                    CameraRemoteCommand.SelectCameraDevice("usb-camera-1"),
                ),
            )
        }

    @Test
    fun `sync request message publishes notes to watch`() =
        runTest {
            val syncBridge = mockk<PhoneWearSyncBridge>(relaxed = true)
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    syncBridge = syncBridge,
                )

            service.onMessageReceived(message(path = WearAudioRequestPaths.SYNC_REQUEST_PATH, sourceNodeId = "watch-node"))
            advanceUntilIdle()

            coVerify(exactly = 1) { syncBridge.publishNotesToWatch("watch-node") }
        }

    @Test
    fun `a window watch requests its bounded library without a legacy pull`() =
        runTest {
            val syncBridge = mockk<PhoneWearSyncBridge>(relaxed = true)
            val requested = mutableListOf<String>()
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    syncBridge = syncBridge,
                    windowPull = {
                        requested += it
                        true
                    },
                )
            service.onMessageReceived(message(path = WearAudioRequestPaths.SYNC_REQUEST_PATH, sourceNodeId = "watch-node"))
            advanceUntilIdle()
            assertEquals(listOf("watch-node"), requested)
            coVerify(exactly = 0) { syncBridge.publishNotesToWatch(any()) }
        }

    @Test
    fun `audio request message streams requested note to watch`() =
        runTest {
            val noteId = Uuid.random()
            val syncBridge = mockk<PhoneWearSyncBridge>(relaxed = true)
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    syncBridge = syncBridge,
                )

            service.onMessageReceived(
                message(
                    path = WearAudioRequestPaths.audioRequestPath(noteId),
                    sourceNodeId = "watch-node",
                ),
            )
            advanceUntilIdle()

            coVerify(exactly = 1) {
                syncBridge.streamAudioToWatch(
                    noteId = noteId,
                    sourceNodeId = "watch-node",
                )
            }
        }

    @Test
    fun `unknown message path is ignored`() =
        runTest {
            var launched = false
            val syncBridge = mockk<PhoneWearSyncBridge>(relaxed = true)
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    syncBridge = syncBridge,
                    onLaunchCamera = { launched = true },
                )

            service.onMessageReceived(message(path = "/logdate/unknown"))
            advanceUntilIdle()

            assertFalse(launched)
            coVerify(exactly = 0) { syncBridge.publishNotesToWatch(any()) }
            coVerify(exactly = 0) { syncBridge.streamAudioToWatch(any(), any()) }
        }

    @Test
    fun `invalid audio request path does not invoke sync bridge`() =
        runTest {
            val syncBridge = mockk<PhoneWearSyncBridge>(relaxed = true)
            val service =
                TestPhoneDataLayerListenerService(
                    serviceScope = this,
                    syncBridge = syncBridge,
                )

            service.onMessageReceived(
                message(
                    path = "/logdate/notes/not-a-uuid/audio/request",
                    sourceNodeId = "watch-node",
                ),
            )
            advanceUntilIdle()

            coVerify(exactly = 0) { syncBridge.streamAudioToWatch(any(), any()) }
            coVerify(exactly = 0) { syncBridge.publishNotesToWatch(any()) }
        }
}
