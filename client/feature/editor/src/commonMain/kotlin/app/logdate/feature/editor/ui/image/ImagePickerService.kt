package app.logdate.feature.editor.ui.image

/**
 * Service interface for platform-specific image picking functionality.
 * 
 * This interface defines methods for selecting images from device storage
 * and capturing new images with the camera.
 */
interface ImagePickerService {
    /**
     * Selects an image from the device storage.
     * 
     * @return The URI of the selected image, or null if selection was cancelled
     */
    suspend fun pickImage(): String?
    
    /**
     * Captures a new image using the device camera.
     * 
     * @return The URI of the captured image, or null if capture was cancelled
     */
    suspend fun captureImage(): String?
}