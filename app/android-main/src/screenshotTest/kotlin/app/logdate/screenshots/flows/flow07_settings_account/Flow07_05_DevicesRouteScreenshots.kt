package app.logdate.screenshots.flows.flow07_settings_account

import androidx.compose.runtime.Composable
import app.logdate.feature.core.settings.ui.devices.DeviceApprovalContent
import app.logdate.feature.core.settings.ui.devices.DeviceApprovalFailure
import app.logdate.feature.core.settings.ui.devices.DeviceApprovalUiState
import app.logdate.feature.core.settings.ui.devices.DeviceInfoUiState
import app.logdate.feature.core.settings.ui.devices.DevicesScreenContent
import app.logdate.feature.core.settings.ui.devices.DevicesUiState
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest
import kotlin.uuid.Uuid

private val devices =
    listOf(
        DeviceInfoUiState(
            id = Uuid.parse("00000000-0000-0000-0000-000000000071"),
            name = "Pixel 9 Pro",
            platformName = "Android",
            lastActiveFormatted = "Today",
            appVersion = "0.1.0",
            isCurrentDevice = true,
        ),
        DeviceInfoUiState(
            id = Uuid.parse("00000000-0000-0000-0000-000000000072"),
            name = "iPad mini",
            platformName = "iOS",
            lastActiveFormatted = "Yesterday",
            appVersion = "0.1.0",
            isCurrentDevice = false,
        ),
    )

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S01_DevicesLoading() {
    ScreenshotTheme {
        DevicesScreenContent(
            onBackClick = {},
            uiState = DevicesUiState(isLoading = true),
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S02_DevicesPopulated() {
    ScreenshotTheme {
        DevicesScreenContent(
            onBackClick = {},
            uiState = DevicesUiState(devices = devices),
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S03_DevicesRenameDialog() {
    ScreenshotTheme {
        DevicesScreenContent(
            onBackClick = {},
            uiState = DevicesUiState(devices = devices),
            showRenameDialog = true,
            selectedDevice = devices.first(),
            newDeviceName = devices.first().name,
            onNewDeviceNameChange = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S04_DevicesRemoveDialog() {
    ScreenshotTheme {
        DevicesScreenContent(
            onBackClick = {},
            uiState = DevicesUiState(devices = devices),
            showDeleteDialog = true,
            selectedDevice = devices.last(),
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S05_DevicesResetDialog() {
    ScreenshotTheme {
        DevicesScreenContent(
            onBackClick = {},
            uiState = DevicesUiState(devices = devices),
            showResetDialog = true,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S06_ConnectionConfirm() = ConnectionScene(DeviceApprovalUiState.Confirm("Test device", "Morgan", "123456"))

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S07_ConnectionWorking() = ConnectionScene(DeviceApprovalUiState.Working("Test device", connecting = true))

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S08_ConnectionApproved() = ConnectionScene(DeviceApprovalUiState.Done("Test device", connected = true))

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S09_ConnectionRejected() = ConnectionScene(DeviceApprovalUiState.Done("Test device", connected = false))

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S10_ConnectionExpired() = ConnectionScene(DeviceApprovalUiState.Failed(DeviceApprovalFailure.Expired))

@Composable
private fun ConnectionScene(state: DeviceApprovalUiState) {
    ScreenshotTheme {
        DevicesScreenContent(
            onBackClick = {},
            uiState = DevicesUiState(devices = devices),
            connectionAction = {
                DeviceApprovalContent(state, onConnectClick = {}, onApprove = {}, onReject = {}, onDismiss = {})
            },
        )
    }
}
