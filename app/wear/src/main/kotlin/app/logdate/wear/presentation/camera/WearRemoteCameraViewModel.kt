package app.logdate.wear.presentation.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.media.device.MediaDeviceUiState
import app.logdate.client.sync.datalayer.RemoteCameraCaptureResult
import app.logdate.wear.sync.WearDataLayerClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RemoteCameraPhase {
    IDLE,
    REQUESTING,
    READY,
    CAPTURING,
    PREVIEW,
    ERROR,
}

data class RemoteCameraUiState(
    val phase: RemoteCameraPhase = RemoteCameraPhase.IDLE,
    val errorMessage: String? = null,
    val navigateBack: Boolean = false,
    val selectedCameraLabel: String = "Back camera",
    val selectedCameraDeviceId: String? = defaultRemoteCameraSelection().selectedDeviceId,
    val availableCameras: List<MediaDeviceUiState> = defaultRemoteCameraSelection().devices,
    val captureStatusMessage: String? = null,
)

class WearRemoteCameraViewModel(
    private val dataLayerClient: WearDataLayerClient,
    private val cameraDeviceStore: RemoteCameraDeviceStore = WearRemoteCameraDeviceStore,
    private val captureResultStore: RemoteCameraCaptureResultStore = WearRemoteCameraCaptureResultStore,
) : ViewModel() {
    companion object {
        const val PATH_CAMERA_OPEN = "/logdate/camera/open"
        const val PATH_CAMERA_CAPTURE = "/logdate/camera/capture"
        const val PATH_CAMERA_SWITCH = "/logdate/camera/switch"
        const val PATH_CAMERA_SELECT = "/logdate/camera/select"
        const val PATH_CAMERA_CLOSE = "/logdate/camera/close"
        private const val CAMERA_SELECT_FRONT = "front"
        private const val CAMERA_SELECT_BACK = "back"
        private const val CAMERA_SELECT_DEVICE_PREFIX = "device:"
    }

    private val _uiState = MutableStateFlow(RemoteCameraUiState())
    val uiState: StateFlow<RemoteCameraUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            cameraDeviceStore.cameraSelection.collect { selection ->
                applyCameraSelection(selection)
            }
        }
        viewModelScope.launch {
            captureResultStore.captureResults.collect { result ->
                applyCaptureResult(result)
            }
        }
    }

    fun requestCamera() {
        val currentPhase = _uiState.value.phase
        if (currentPhase != RemoteCameraPhase.IDLE) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = RemoteCameraPhase.REQUESTING,
                    errorMessage = null,
                    captureStatusMessage = null,
                )
            }

            try {
                val connected = dataLayerClient.isPhoneConnected()
                if (!connected) {
                    _uiState.update {
                        it.copy(
                            phase = RemoteCameraPhase.ERROR,
                            errorMessage = "Phone not found",
                        )
                    }
                    return@launch
                }

                val sent = dataLayerClient.sendMessage(PATH_CAMERA_OPEN)
                if (!sent) {
                    _uiState.update {
                        it.copy(
                            phase = RemoteCameraPhase.ERROR,
                            errorMessage = "Failed to open camera on phone",
                        )
                    }
                    return@launch
                }

                _uiState.update { it.copy(phase = RemoteCameraPhase.READY) }
                Napier.d("Remote camera opened on phone")
            } catch (e: Exception) {
                Napier.w("Failed to request remote camera", e)
                _uiState.update {
                    it.copy(
                        phase = RemoteCameraPhase.ERROR,
                        errorMessage = "Failed to connect: ${e.message}",
                    )
                }
            }
        }
    }

    fun capture() {
        if (_uiState.value.phase != RemoteCameraPhase.READY) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = RemoteCameraPhase.CAPTURING,
                    errorMessage = null,
                    captureStatusMessage = null,
                )
            }

            try {
                val sent = dataLayerClient.sendMessage(PATH_CAMERA_CAPTURE)
                if (!sent) {
                    _uiState.update {
                        it.copy(
                            phase = RemoteCameraPhase.ERROR,
                            errorMessage = "Failed to capture photo",
                        )
                    }
                    return@launch
                }

                Napier.d("Photo capture requested")
            } catch (e: Exception) {
                Napier.w("Failed to capture photo", e)
                _uiState.update {
                    it.copy(
                        phase = RemoteCameraPhase.ERROR,
                        errorMessage = "Capture failed: ${e.message}",
                    )
                }
            }
        }
    }

    fun captureMore() {
        if (_uiState.value.phase != RemoteCameraPhase.PREVIEW) return
        _uiState.update {
            it.copy(
                phase = RemoteCameraPhase.READY,
                captureStatusMessage = null,
            )
        }
    }

    fun selectBackCamera() {
        selectCamera(
            payload = CAMERA_SELECT_BACK,
            label = "Back camera",
            deviceId = _uiState.value.availableCameras.firstOrNull { it.label == "Back camera" }?.id,
        )
    }

    fun selectFrontCamera() {
        selectCamera(
            payload = CAMERA_SELECT_FRONT,
            label = "Front camera",
            deviceId = _uiState.value.availableCameras.firstOrNull { it.label == "Front camera" }?.id,
        )
    }

    fun switchCamera() {
        if (_uiState.value.phase != RemoteCameraPhase.READY) return

        viewModelScope.launch {
            try {
                val sent = dataLayerClient.sendMessage(PATH_CAMERA_SWITCH)
                if (sent) {
                    _uiState.update { state ->
                        state.copy(
                            selectedCameraLabel =
                                if (state.selectedCameraLabel == "Front camera") {
                                    "Back camera"
                                } else {
                                    "Front camera"
                                },
                        )
                    }
                } else {
                    showCameraSelectionError()
                }
            } catch (e: Exception) {
                Napier.w("Failed to switch remote camera", e)
                showCameraSelectionError()
            }
        }
    }

    fun selectCameraDevice(deviceId: String) {
        val camera = _uiState.value.availableCameras.firstOrNull { it.id == deviceId } ?: return
        selectCamera(
            payload = CAMERA_SELECT_DEVICE_PREFIX + camera.id,
            label = camera.label,
            deviceId = camera.id,
        )
    }

    fun dismiss() {
        viewModelScope.launch {
            val wasActive =
                _uiState.value.phase != RemoteCameraPhase.IDLE &&
                    _uiState.value.phase != RemoteCameraPhase.ERROR
            if (wasActive) {
                dataLayerClient.sendMessage(PATH_CAMERA_CLOSE)
            }
            _uiState.update {
                RemoteCameraUiState(
                    phase = RemoteCameraPhase.IDLE,
                    navigateBack = true,
                )
            }
        }
    }

    private fun selectCamera(
        payload: String,
        label: String,
        deviceId: String? = null,
    ) {
        if (_uiState.value.phase != RemoteCameraPhase.READY) return

        viewModelScope.launch {
            try {
                val sent = dataLayerClient.sendMessage(PATH_CAMERA_SELECT, payload.encodeToByteArray())
                if (sent) {
                    _uiState.update {
                        it.copy(
                            selectedCameraLabel = label,
                            selectedCameraDeviceId = deviceId ?: it.selectedCameraDeviceId,
                        )
                    }
                } else {
                    showCameraSelectionError()
                }
            } catch (e: Exception) {
                Napier.w("Failed to select remote camera", e)
                showCameraSelectionError()
            }
        }
    }

    private fun showCameraSelectionError() {
        _uiState.update {
            it.copy(
                phase = RemoteCameraPhase.ERROR,
                errorMessage = "Failed to switch camera",
            )
        }
    }

    private fun applyCameraSelection(selection: MediaDeviceSelectionUiState) {
        val selected = selection.selectedDevice
        _uiState.update {
            it.copy(
                selectedCameraLabel = selected?.label ?: it.selectedCameraLabel,
                selectedCameraDeviceId = selected?.id ?: selection.selectedDeviceId,
                availableCameras = selection.devices.ifEmpty { it.availableCameras },
            )
        }
    }

    private fun applyCaptureResult(result: RemoteCameraCaptureResult) {
        if (_uiState.value.phase != RemoteCameraPhase.CAPTURING) return

        _uiState.update {
            if (result.isSaved) {
                it.copy(
                    phase = RemoteCameraPhase.PREVIEW,
                    errorMessage = null,
                    captureStatusMessage = result.message.ifBlank { "Photo saved" },
                )
            } else {
                it.copy(
                    phase = RemoteCameraPhase.ERROR,
                    errorMessage = result.message.ifBlank { "Capture failed" },
                    captureStatusMessage = null,
                )
            }
        }
    }
}
