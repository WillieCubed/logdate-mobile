package app.logdate.client.sync.datalayer

import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.media.device.MediaDeviceUiState

object RemoteCameraDeviceDataMapper {
    const val PATH_CAMERA_DEVICES = "/logdate/camera/devices"

    private const val KEY_COUNT = "count"
    private const val KEY_SELECTED_DEVICE_ID = "selectedDeviceId"

    private fun keyDeviceId(index: Int) = "device_${index}_id"

    private fun keyDeviceLabel(index: Int) = "device_${index}_label"

    private fun keyDeviceCategory(index: Int) = "device_${index}_category"

    private fun keyDeviceAvailable(index: Int) = "device_${index}_available"

    private fun keyDeviceExternal(index: Int) = "device_${index}_external"

    fun toDataMap(selection: MediaDeviceSelectionUiState): Map<String, String> {
        val data = mutableMapOf<String, String>()
        val cameraDevices = selection.devices.filter { it.kind == MediaDeviceKind.CAMERA }
        data[KEY_COUNT] = cameraDevices.size.toString()
        selection.selectedDeviceId?.let { data[KEY_SELECTED_DEVICE_ID] = it }
        cameraDevices.forEachIndexed { index, device ->
            data[keyDeviceId(index)] = device.id
            data[keyDeviceLabel(index)] = device.label
            data[keyDeviceCategory(index)] = device.category.name
            data[keyDeviceAvailable(index)] = device.isAvailable.toString()
            data[keyDeviceExternal(index)] = device.isExternal.toString()
        }
        return data
    }

    fun fromDataMap(data: Map<String, String>): MediaDeviceSelectionUiState {
        val count = data[KEY_COUNT]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val devices =
            (0 until count).mapNotNull { index ->
                val id = data[keyDeviceId(index)] ?: return@mapNotNull null
                val label = data[keyDeviceLabel(index)] ?: id
                val category =
                    data[keyDeviceCategory(index)]
                        ?.let { runCatching { MediaDeviceCategory.valueOf(it) }.getOrNull() }
                        ?: MediaDeviceCategory.EXTERNAL
                MediaDeviceUiState(
                    id = id,
                    label = label,
                    kind = MediaDeviceKind.CAMERA,
                    category = category,
                    isAvailable = data[keyDeviceAvailable(index)]?.toBooleanStrictOrNull() ?: true,
                    isExternal = data[keyDeviceExternal(index)]?.toBooleanStrictOrNull() ?: false,
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
