package app.logdate.client.media.device

import app.logdate.client.text.UiText
import app.logdate.client.text.toUiText
import logdate.client.media.generated.resources.Res
import logdate.client.media.generated.resources.media_device_back_camera
import logdate.client.media.generated.resources.media_device_front_camera
import logdate.client.media.generated.resources.media_device_system_microphone
import logdate.client.media.generated.resources.media_device_system_output

/**
 * User-facing media device state shared by camera, recording, and playback UI.
 */
data class MediaDeviceUiState(
    val id: String,
    /** The name the platform reports for the device, or LogDate's own name when it reports none. */
    val label: UiText,
    val kind: MediaDeviceKind,
    val category: MediaDeviceCategory,
    val isAvailable: Boolean = true,
    val isExternal: Boolean = false,
    /**
     * Identifies the physical device behind this entry.
     *
     * Android reports one entry per audio *profile*, so a single pair of earbuds appears three
     * times — A2DP, hands-free and LE Audio — each with the same name. Entries sharing a
     * [groupId] are the same hardware and collapse to one row.
     *
     * Null means "this entry is its own device", resolved through [groupKey]. It is deliberately
     * not defaulted to [id]: a default is evaluated once at construction, so `copy(id = ...)`
     * would carry the original device's group and silently collapse two unrelated devices.
     */
    val groupId: String? = null,
    /**
     * Which entry wins when several share a [groupId]. Higher is better; LE Audio beats A2DP,
     * which beats hands-free. Meaningless across groups.
     */
    val qualityRank: Int = 0,
) {
    /** The group this entry belongs to; its own [id] unless it shares hardware with another. */
    val groupKey: String get() = groupId ?: id
}

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
    val routeControlMessage: UiText? = null,
    val isSelectionConfirmed: Boolean = true,
) {
    val selectedDevice: MediaDeviceUiState?
        get() =
            if (isSelectionConfirmed) {
                devices.firstOrNull {
                    it.id == selectedDeviceId
                } ?: devices.firstOrNull { it.isAvailable }
            } else {
                null
            }
}

object DefaultMediaDevices {
    val backCamera =
        MediaDeviceUiState(
            id = "camera-back",
            label = Res.string.media_device_back_camera.toUiText(),
            kind = MediaDeviceKind.CAMERA,
            category = MediaDeviceCategory.BACK_CAMERA,
        )

    val frontCamera =
        MediaDeviceUiState(
            id = "camera-front",
            label = Res.string.media_device_front_camera.toUiText(),
            kind = MediaDeviceKind.CAMERA,
            category = MediaDeviceCategory.FRONT_CAMERA,
        )

    val systemMicrophone =
        MediaDeviceUiState(
            id = "audio-input-system",
            label = Res.string.media_device_system_microphone.toUiText(),
            kind = MediaDeviceKind.AUDIO_INPUT,
            category = MediaDeviceCategory.SYSTEM_DEFAULT,
        )

    val systemOutput =
        MediaDeviceUiState(
            id = "audio-output-system",
            label = Res.string.media_device_system_output.toUiText(),
            kind = MediaDeviceKind.AUDIO_OUTPUT,
            category = MediaDeviceCategory.SYSTEM_DEFAULT,
        )
}
