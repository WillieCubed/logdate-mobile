package app.logdate.client.permissions

import kotlinx.coroutines.flow.StateFlow

/**
 * Permission type definitions for different platform permission groups
 */
enum class PermissionType {
    LOCATION,
    CAMERA,
    MICROPHONE,
    STORAGE,
    NOTIFICATION,
    CALENDAR,
    CONTACTS,
    BIOMETRIC
}

/**
 * Status of a permission request
 */
enum class PermissionStatus {
    GRANTED,      // Permission is granted
    DENIED,       // Permission is denied and can be requested again
    PERMANENTLY_DENIED,  // Permission is denied and can't be requested again (user selected "Don't ask again")
    UNAVAILABLE,  // Permission is not available on this platform
    UNKNOWN       // Permission status could not be determined
}

/**
 * Result of a permission request operation
 */
data class PermissionResult(
    val type: PermissionType,
    val status: PermissionStatus,
    val shouldShowRationale: Boolean = false
)

/**
 * Interface for platform-specific permission management
 */
interface PermissionManager {
    /**
     * Checks if a permission has been granted
     *
     * @param type The type of permission to check
     * @return True if the permission is granted, false otherwise
     */
    fun isPermissionGranted(type: PermissionType): Boolean
    
    /**
     * Checks if a set of permissions has been granted
     *
     * @param types The types of permissions to check
     * @return True if all permissions are granted, false otherwise
     */
    fun arePermissionsGranted(types: Set<PermissionType>): Boolean
    
    /**
     * Returns a flow that emits the status of specified permissions
     *
     * @param types The types of permissions to observe
     * @return A flow of permission statuses
     */
    fun observePermissions(types: Set<PermissionType>): StateFlow<Map<PermissionType, PermissionStatus>>
    
    /**
     * Requests a permission
     *
     * @param type The type of permission to request
     * @param onResult Callback with the permission result
     */
    fun requestPermission(type: PermissionType, onResult: (PermissionResult) -> Unit)
    
    /**
     * Requests multiple permissions
     *
     * @param types The types of permissions to request
     * @param onResult Callback with the permission results
     */
    fun requestPermissions(types: Set<PermissionType>, onResult: (List<PermissionResult>) -> Unit)
    
    /**
     * Opens app settings to allow the user to grant permissions manually
     */
    fun openAppSettings()
    
    /**
     * Checks if rationale should be shown for a permission
     * 
     * @param type The permission type
     * @return True if rationale should be shown, false otherwise
     */
    fun shouldShowRationale(type: PermissionType): Boolean
}

/**
 * Creates a platform-specific permission manager
 */
expect fun createPermissionManager(): PermissionManager