package app.logdate.feature.editor.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import app.logdate.client.media.device.DefaultMediaDevices
import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.media.device.MediaDeviceUiState
import app.logdate.feature.editor.ui.audio.ActiveRecordingDisplay
import app.logdate.ui.media.MediaDeviceSelectorTags
import app.logdate.ui.theme.LogDateTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class ActiveRecordingInputSelectionTest {
    @Test
    fun `microphone can switch while recording without restarting or losing the transcript`() = verifySelection(false)

    @Test
    fun `microphone can switch while paused without finishing the take`() = verifySelection(true)

    @Test
    fun `switcher only appears for distinct available controllable recording inputs and closes on disconnect`() =
        runSkikoComposeUiTest(size = Size(390f, 780f)) {
            val builtIn = MediaDeviceUiState("built-in", "Phone microphone", MediaDeviceKind.AUDIO_INPUT, MediaDeviceCategory.BUILT_IN)
            val usb = MediaDeviceUiState("usb", "USB microphone", MediaDeviceKind.AUDIO_INPUT, MediaDeviceCategory.USB)
            val selection = mutableStateOf(MediaDeviceSelectionUiState(MediaDeviceKind.AUDIO_INPUT, listOf(builtIn), builtIn.id))
            var changes = 0
            setContent {
                LogDateTheme(dynamicColor = false) {
                    ActiveRecordingDisplay(
                        listOf(.2f, .4f),
                        42.seconds,
                        {},
                        {},
                        {},
                        inputSelection = selection.value,
                        onInputSelected = { changes++ },
                        transcriptionText = "A memory worth keeping.",
                    )
                }
            }
            val switcher = MediaDeviceSelectorTags.chip("Microphone")
            val noAlternatives =
                listOf(
                    emptyList(),
                    listOf(DefaultMediaDevices.systemMicrophone),
                    listOf(DefaultMediaDevices.systemMicrophone, builtIn),
                    listOf(builtIn, usb.copy(isAvailable = false)),
                    listOf(builtIn, usb.copy(kind = MediaDeviceKind.AUDIO_OUTPUT)),
                    listOf(usb.copy(groupId = "headset"), usb.copy(id = "usb-profile", groupId = "headset")),
                )
            for (devices in noAlternatives) {
                selection.value = selection.value.copy(devices = devices)
                waitForIdle()
                onNodeWithTag(switcher).assertDoesNotExist()
            }
            selection.value = selection.value.copy(devices = listOf(builtIn, usb), isSelectionControllable = false)
            waitForIdle()
            onNodeWithTag(switcher).assertDoesNotExist()
            selection.value = selection.value.copy(isSelectionControllable = true)
            waitForIdle()
            onNodeWithTag(switcher).assertIsDisplayed().performClick()
            onNodeWithTag(MediaDeviceSelectorTags.sheet("Microphone")).assertIsDisplayed()
            selection.value = selection.value.copy(devices = listOf(builtIn))
            waitForIdle()
            onNodeWithTag(switcher).assertDoesNotExist()
            onNodeWithTag(MediaDeviceSelectorTags.sheet("Microphone")).assertDoesNotExist()
            onNodeWithText("A memory worth keeping.").assertIsDisplayed()
            onNodeWithText("00:42").assertIsDisplayed()
            onNodeWithContentDescription("Pause").assertIsDisplayed()
            onNodeWithText("Finish").assertIsDisplayed()
            assertEquals(0, changes)
        }

    private fun verifySelection(paused: Boolean) =
        runSkikoComposeUiTest(size = Size(390f, 780f)) {
            val builtIn = MediaDeviceUiState("built-in", "Built-in microphone", MediaDeviceKind.AUDIO_INPUT, MediaDeviceCategory.BUILT_IN)
            val usb = MediaDeviceUiState("usb", "USB microphone", MediaDeviceKind.AUDIO_INPUT, MediaDeviceCategory.USB, isExternal = true)
            val selection = mutableStateOf(MediaDeviceSelectionUiState(MediaDeviceKind.AUDIO_INPUT, listOf(builtIn, usb), builtIn.id))
            var restarts = 0
            var finishes = 0
            setContent {
                LogDateTheme(dynamicColor = false) {
                    ActiveRecordingDisplay(
                        listOf(.2f, .4f),
                        42.seconds,
                        { restarts++ },
                        {},
                        { finishes++ },
                        inputSelection = selection.value,
                        onInputSelected = { selection.value = selection.value.copy(selectedDeviceId = it) },
                        transcriptionText = "A memory worth keeping.",
                        isPaused = paused,
                    )
                }
            }
            onNodeWithText("Live on-device").assertDoesNotExist()
            onNodeWithText("00:42").assertIsDisplayed()
            if (!paused) onNodeWithText("Recording").assertIsDisplayed()
            val pause = onNodeWithContentDescription(if (paused) "Resume" else "Pause").getUnclippedBoundsInRoot()
            val finish = onNodeWithText("Finish").getUnclippedBoundsInRoot()
            if (paused) {
                val restart = onNodeWithContentDescription("Restart recording").getUnclippedBoundsInRoot()
                assertTrue(restart.bottom < pause.top, "Restart is available while paused")
            } else {
                onNodeWithContentDescription("Restart recording").assertDoesNotExist()
            }
            assertTrue((finish.right - finish.left) > (pause.right - pause.left) * 1.5f, "Finish owns the primary surface width")
            val input = onNodeWithTag(MediaDeviceSelectorTags.chip("Microphone")).getUnclippedBoundsInRoot()
            val transcript = onNodeWithText("A memory worth keeping.").getUnclippedBoundsInRoot()
            assertTrue(input.top > transcript.bottom, "Microphone control stays in the bottom recording dock")
            assertTrue(input.bottom < pause.top, "Input selection stays above the transport actions")
            assertTrue(finish.left > pause.right, "Pause and Finish remain separately tappable")
            onNodeWithTag(MediaDeviceSelectorTags.chip("Microphone")).assertIsEnabled().performClick()
            onNodeWithTag(MediaDeviceSelectorTags.deviceRow("Microphone", usb.id)).performClick()
            onNodeWithTag(MediaDeviceSelectorTags.sheet("Microphone")).assertDoesNotExist()
            onNodeWithText("USB microphone").assertIsDisplayed()
            onNodeWithText("A memory worth keeping.").assertIsDisplayed()
            assertEquals(usb.id, selection.value.selectedDeviceId)
            assertEquals(0, restarts)
            assertEquals(0, finishes)
            val directory = File(System.getProperty("logdate.review.screenshots", "/private/tmp/logdate-review-screenshots"))
            directory.mkdirs()
            File(directory, "microphone-switched-${if (paused) "paused" else "recording"}.png").writeBytes(
                requireNotNull(Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData()).bytes,
            )
        }
}
