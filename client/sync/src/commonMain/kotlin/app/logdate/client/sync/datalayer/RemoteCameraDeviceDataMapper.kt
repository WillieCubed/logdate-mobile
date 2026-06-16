package app.logdate.client.sync.datalayer

import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.media.device.MediaDeviceUiState

object RemoteCameraDeviceDataMapper {
    const val PATH_CAMERA_DEVICES = "/logdate/camera/devices"

    private const val KEY_COUNT = "count"
    private const val KEY_SELECTED_DEVICE_ID = "selectedDeviceId"
    private const val KEY_DEVICE_ID = "device_%d_id"
    private const val KEY_DEVICE_LABEL = "device_%d_label"
    private const val KEY_DEVICE_CATEGORY = "device_%d_category"
    private const val KEY_DEVICE_AVAILABLE = "device_%d_available"
    private const val KEY_DEVICE_EXTERNAL = "device_%d_external"

    fun toDataMap(selection: MediaDeviceSelectionUiState): Map<String, String> {
        val data = mutableMapOf<String, String>()
        val cameraDevices = selection.devices.filter { it.kind == MediaDeviceKind.CAMERA }
        data[KEY_COUNT] = cameraDevices.size.toString()
        selection.selectedDeviceId?.let { data[KEY_SELECTED_DEVICE_ID] = it }
        cameraDevices.forEachIndexed { index, device ->
            data[KEY_DEVICE_ID.format(index)] = device.id
            data[KEY_DEVICE_LABEL.format(index)] = device.label
            data[KEY_DEVICE_CATEGORY.format(index)] = device.category.name
            data[KEY_DEVICE_AVAILABLE.format(index)] = device.isAvailable.toString()
            data[KEY_DEVICE_EXTERNAL.format(index)] = device.isExternal.toString()
        }
        return data
    }

    fun fromDataMap(data: Map<String, String>): MediaDeviceSelectionUiState {
        val count = data[KEY_COUNT]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val devices =
            (0 until count).mapNotNull { index ->
                val id = data[KEY_DEVICE_ID.format(index)] ?: return@mapNotNull null
                val label = data[KEY_DEVICE_LABEL.format(index)] ?: id
                val category =
                    data[KEY_DEVICE_CATEGORY.format(index)]
                        ?.let { runCatching { MediaDeviceCategory.valueOf(it) }.getOrNull() }
                        ?: MediaDeviceCategory.EXTERNAL
                MediaDeviceUiState(
                    id = id,
                    label = label,
                    kind = MediaDeviceKind.CAMERA,
                    category = category,
                    isAvailable = data[KEY_DEVICE_AVAILABLE.format(index)]?.toBooleanStrictOrNull() ?: true,
                    isExternal = data[KEY_DEVICE_EXTERNAL.format(index)]?.toBooleanStrictOrNull() ?: false,
                )
            }
        return MediaDeviceSelectionUiState(
            kind = MediaDeviceKind.CAMERA,
            devices = devices,
            selectedDeviceId = data[KEY_SELECTED_DEVICE_ID],
            isSelectionControllable = true,
        )
    }
}
