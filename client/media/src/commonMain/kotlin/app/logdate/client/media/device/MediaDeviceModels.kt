package app.logdate.client.media.device

/**
 * User-facing media device state shared by camera, recording, and playback UI.
 */
data class MediaDeviceUiState(
    val id: String,
    val label: String,
    val kind: MediaDeviceKind,
    val category: MediaDeviceCategory,
    val isAvailable: Boolean = true,
    val isExternal: Boolean = false,
)

enum class MediaDeviceKind {
    CAMERA,
    AUDIO_INPUT,
    AUDIO_OUTPUT,
}

enum class MediaDeviceCategory {
    SYSTEM_DEFAULT,
    BUILT_IN,
    FRONT_CAMERA,
    BACK_CAMERA,
    USB,
    BLUETOOTH,
    WIRED,
    EXTERNAL,
}

data class MediaDeviceSelectionUiState(
    val kind: MediaDeviceKind,
    val devices: List<MediaDeviceUiState>,
    val selectedDeviceId: String?,
    val isSelectionControllable: Boolean = true,
    val routeControlMessage: String? = null,
) {
    val selectedDevice: MediaDeviceUiState?
        get() = devices.firstOrNull { it.id == selectedDeviceId } ?: devices.firstOrNull { it.isAvailable }
}

object DefaultMediaDevices {
    val backCamera =
        MediaDeviceUiState(
            id = "camera-back",
            label = "Back camera",
            kind = MediaDeviceKind.CAMERA,
            category = MediaDeviceCategory.BACK_CAMERA,
        )

    val frontCamera =
        MediaDeviceUiState(
            id = "camera-front",
            label = "Front camera",
            kind = MediaDeviceKind.CAMERA,
            category = MediaDeviceCategory.FRONT_CAMERA,
        )

    val systemMicrophone =
        MediaDeviceUiState(
            id = "audio-input-system",
            label = "System microphone",
            kind = MediaDeviceKind.AUDIO_INPUT,
            category = MediaDeviceCategory.SYSTEM_DEFAULT,
        )

    val systemOutput =
        MediaDeviceUiState(
            id = "audio-output-system",
            label = "System output",
            kind = MediaDeviceKind.AUDIO_OUTPUT,
            category = MediaDeviceCategory.SYSTEM_DEFAULT,
        )
}
