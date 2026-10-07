package app.logdate.feature.core.settings.ui.devices

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DeviceApprovalContentTest {
    @Test
    fun approvalKeepsProgressAndCompletionInTheConnectionDialog() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            val state = mutableStateOf<DeviceApprovalUiState>(DeviceApprovalUiState.Confirm("Test device", "Morgan", "123456"))
            setContent {
                LogDateTheme(darkTheme = false) {
                    DeviceApprovalContent(
                        state = state.value,
                        onConnectClick = {},
                        onApprove = { state.value = DeviceApprovalUiState.Working("Test device", connecting = true) },
                        onReject = {},
                        onDismiss = { state.value = DeviceApprovalUiState.Idle },
                    )
                }
            }
            onNodeWithText("Connect").performClick()
            onNodeWithTag("device-connection-dialog").assertIsDisplayed()
            onNodeWithText("Connecting Test device…").assertIsDisplayed()
            onNodeWithTag("connect-device-status").assertDoesNotExist()
            saveScreenshot("phone-progress", onNodeWithTag("device-connection-dialog").captureToImage())

            runOnIdle { state.value = DeviceApprovalUiState.Done("Test device", connected = true) }
            onNodeWithTag("device-connection-dialog").assertIsDisplayed()
            onNodeWithText("Connection approved").assertIsDisplayed()
            onNodeWithText("Test device").assertIsDisplayed()
            onNodeWithText("Test device is connected.").assertDoesNotExist()
            saveScreenshot("phone-complete", onNodeWithTag("device-connection-dialog").captureToImage())
            onNodeWithText("Done").performClick()
            onNodeWithTag("device-connection-dialog").assertDoesNotExist()
            onNodeWithTag("connect-device-action").assertIsDisplayed()
            assertEquals(DeviceApprovalUiState.Idle, state.value)
        }

    @Test
    fun rejectedRequestHasItsOwnDismissibleResult() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            var dismissals = 0
            setContent {
                LogDateTheme(darkTheme = true) {
                    DeviceApprovalContent(
                        state = DeviceApprovalUiState.Done("Test device", connected = false),
                        onConnectClick = {},
                        onApprove = {},
                        onReject = {},
                        onDismiss = { dismissals++ },
                    )
                }
            }
            onNodeWithText("Request rejected").assertIsDisplayed()
            onNodeWithText("Connection approved").assertDoesNotExist()
            onNodeWithTag("connect-device-status").assertDoesNotExist()
            onNodeWithText("Done").performClick()
            assertEquals(1, dismissals)
            saveScreenshot("phone-rejected-dark", onNodeWithTag("device-connection-dialog").captureToImage())
        }

    @Test
    fun expiredCodeOffersASpecificRescanAction() =
        runDesktopComposeUiTest(width = 720, height = 900) {
            var scans = 0
            setContent {
                LogDateTheme(darkTheme = false) {
                    DeviceApprovalContent(
                        state = DeviceApprovalUiState.Failed(DeviceApprovalFailure.Expired),
                        onConnectClick = { scans++ },
                        onApprove = {},
                        onReject = {},
                        onDismiss = {},
                    )
                }
            }
            onNodeWithText("Couldn't connect").assertIsDisplayed()
            onNodeWithText("Scan again").performClick()
            assertEquals(1, scans)
            onNodeWithTag("connect-device-status").assertDoesNotExist()
            saveScreenshot("tablet-expired", onNodeWithTag("device-connection-dialog").captureToImage())
        }

    private fun saveScreenshot(
        name: String,
        image: ImageBitmap,
    ) {
        val directory = System.getenv("LOGDATE_DEVICE_SCREENSHOT_DIR") ?: return
        val pixels = image.toPixelMap()
        val output = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) output.setRGB(x, y, pixels[x, y].toArgb())
        }
        val file = File(directory, "$name.png")
        file.parentFile.mkdirs()
        ImageIO.write(output, "png", file)
    }
}
