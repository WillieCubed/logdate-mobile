package app.logdate.screenshots.audit.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.media.device.MediaDeviceUiState
import app.logdate.screenshots.common.LargeScreenAuditPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.ui.media.MediaDeviceSelector
import app.logdate.ui.media.MediaDeviceSelectorSheet
import app.logdate.ui.media.MediaRouteSettingsAction
import com.android.tools.screenshot.PreviewTest

private val cameraSelection =
    MediaDeviceSelectionUiState(
        kind = MediaDeviceKind.CAMERA,
        devices =
            listOf(
                MediaDeviceUiState(
                    id = "back-camera",
                    label = "Back camera",
                    kind = MediaDeviceKind.CAMERA,
                    category = MediaDeviceCategory.BACK_CAMERA,
                ),
                MediaDeviceUiState(
                    id = "front-camera",
                    label = "Front camera",
                    kind = MediaDeviceKind.CAMERA,
                    category = MediaDeviceCategory.FRONT_CAMERA,
                ),
                MediaDeviceUiState(
                    id = "usb-camera",
                    label = "USB camera",
                    kind = MediaDeviceKind.CAMERA,
                    category = MediaDeviceCategory.USB,
                    isExternal = true,
                ),
            ),
        selectedDeviceId = "back-camera",
    )

private val audioOutputSelection =
    MediaDeviceSelectionUiState(
        kind = MediaDeviceKind.AUDIO_OUTPUT,
        devices =
            listOf(
                MediaDeviceUiState(
                    id = "phone-speaker",
                    label = "Phone speaker",
                    kind = MediaDeviceKind.AUDIO_OUTPUT,
                    category = MediaDeviceCategory.BUILT_IN,
                ),
                MediaDeviceUiState(
                    id = "bluetooth-headphones",
                    label = "Bluetooth headphones",
                    kind = MediaDeviceKind.AUDIO_OUTPUT,
                    category = MediaDeviceCategory.BLUETOOTH,
                    isExternal = true,
                ),
            ),
        selectedDeviceId = "phone-speaker",
        isSelectionControllable = false,
        routeControlMessage = "LogDate shows detected outputs here. Use Android's output switcher to route playback.",
    )

private val audioInputSelection =
    MediaDeviceSelectionUiState(
        kind = MediaDeviceKind.AUDIO_INPUT,
        devices =
            listOf(
                MediaDeviceUiState(
                    id = "phone-mic",
                    label = "Phone microphone",
                    kind = MediaDeviceKind.AUDIO_INPUT,
                    category = MediaDeviceCategory.BUILT_IN,
                ),
                MediaDeviceUiState(
                    id = "usb-mic",
                    label = "USB microphone",
                    kind = MediaDeviceKind.AUDIO_INPUT,
                    category = MediaDeviceCategory.USB,
                    isExternal = true,
                ),
                MediaDeviceUiState(
                    id = "bluetooth-headset",
                    label = "Bluetooth headset",
                    kind = MediaDeviceKind.AUDIO_INPUT,
                    category = MediaDeviceCategory.BLUETOOTH,
                    isExternal = true,
                ),
            ),
        selectedDeviceId = "phone-mic",
    )

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A08_MediaSelectorCameraCompactRouteChip() {
    ScreenshotTheme {
        AuditMediaRouteSurface {
            MediaDeviceSelector(
                selection = cameraSelection,
                onDeviceSelected = {},
                label = "Camera",
            )
        }
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A09_MediaSelectorCameraExpanded() {
    ScreenshotTheme {
        AuditMediaRouteSurface {
            MediaDeviceSelectorSheet(
                selection = cameraSelection,
                title = "Camera",
                onDeviceSelected = {},
                systemSettingsAction = null,
                onDismiss = {},
            )
        }
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A10_MediaSelectorMicrophoneExpanded() {
    ScreenshotTheme {
        AuditMediaRouteSurface {
            MediaDeviceSelectorSheet(
                selection = audioInputSelection,
                title = "Microphone",
                onDeviceSelected = {},
                systemSettingsAction = null,
                onDismiss = {},
            )
        }
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A11_MediaSelectorOutputSystemControlled() {
    ScreenshotTheme {
        AuditMediaRouteSurface {
            MediaDeviceSelectorSheet(
                selection = audioOutputSelection,
                title = "Audio output",
                onDeviceSelected = {},
                systemSettingsAction =
                    MediaRouteSettingsAction(
                        label = "Open sound settings",
                        onClick = {},
                    ),
                onDismiss = {},
            )
        }
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A12_MediaSelectorCompactRouteChip() {
    ScreenshotTheme {
        AuditMediaRouteSurface {
            MediaDeviceSelector(
                selection = audioOutputSelection,
                onDeviceSelected = {},
                label = "Audio output",
            )
        }
    }
}

@Composable
private fun AuditMediaRouteSurface(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 520.dp),
            content = content,
        )
    }
}
