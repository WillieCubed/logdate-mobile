package app.logdate.screenshots.settings

import androidx.compose.runtime.Composable
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
fun DevicesRoute_Loading() {
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
fun DevicesRoute_Populated() {
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
fun DevicesRoute_RenameDialog() {
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
fun DevicesRoute_RemoveDialog() {
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
fun DevicesRoute_ResetDialog() {
    ScreenshotTheme {
        DevicesScreenContent(
            onBackClick = {},
            uiState = DevicesUiState(devices = devices),
            showResetDialog = true,
        )
    }
}
