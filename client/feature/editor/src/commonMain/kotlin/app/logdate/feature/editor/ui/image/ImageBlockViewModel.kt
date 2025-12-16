package app.logdate.feature.editor.ui.image

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.github.aakira.napier.Napier

/**
 * ViewModel for handling image block operations.
 *
 * This ViewModel provides methods for selecting images and working
 * with the selected images within the editor.
 */
class ImageBlockViewModel(
    private val imagePickerService: ImagePickerService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ImagePickerUiState())
    val uiState: StateFlow<ImagePickerUiState> = _uiState.asStateFlow()
    
    /**
     * Selects an image from the device's storage.
     */
    fun pickImage() {
        viewModelScope.launch {
            try {
                val uri = imagePickerService.pickImage()
                if (uri != null) {
                    Napier.d("Image picked: $uri")
                    _uiState.value = _uiState.value.copy(
                        selectedImageUri = uri,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                Napier.e("Error picking image", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to pick image: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Captures a new image using the device camera.
     */
    fun captureImage() {
        viewModelScope.launch {
            try {
                val uri = imagePickerService.captureImage()
                if (uri != null) {
                    Napier.d("Image captured: $uri")
                    _uiState.value = _uiState.value.copy(
                        selectedImageUri = uri,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                Napier.e("Error capturing image", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to capture image: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Clears the currently selected image.
     */
    fun clearSelectedImage() {
        _uiState.value = _uiState.value.copy(
            selectedImageUri = null,
            errorMessage = null
        )
    }
}

/**
 * UI state for the image picker functionality.
 */
data class ImagePickerUiState(
    val selectedImageUri: String? = null,
    val errorMessage: String? = null
)