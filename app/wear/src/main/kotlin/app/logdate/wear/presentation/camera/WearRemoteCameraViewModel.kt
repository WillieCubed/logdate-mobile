package app.logdate.wear.presentation.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
)

class WearRemoteCameraViewModel(
    private val dataLayerClient: WearDataLayerClient,
) : ViewModel() {
    companion object {
        const val PATH_CAMERA_OPEN = "/logdate/camera/open"
        const val PATH_CAMERA_CAPTURE = "/logdate/camera/capture"
        const val PATH_CAMERA_CLOSE = "/logdate/camera/close"
    }

    private val _uiState = MutableStateFlow(RemoteCameraUiState())
    val uiState: StateFlow<RemoteCameraUiState> = _uiState.asStateFlow()

    fun requestCamera() {
        val currentPhase = _uiState.value.phase
        if (currentPhase != RemoteCameraPhase.IDLE) return

        viewModelScope.launch {
            _uiState.update { it.copy(phase = RemoteCameraPhase.REQUESTING) }

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
            _uiState.update { it.copy(phase = RemoteCameraPhase.CAPTURING) }

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

                _uiState.update { it.copy(phase = RemoteCameraPhase.PREVIEW) }
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
        _uiState.update { it.copy(phase = RemoteCameraPhase.READY) }
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
}
