package app.logdate.client.permissions

import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stub implementation of the PermissionManager interface.
 * Used for platforms that don't have permission systems or for testing.
 * 
 * By default, this implementation reports all permissions as granted,
 * but can be configured to simulate denied permissions for testing.
 */
open class StubPermissionManager(
    // Set this to false to simulate denied permissions for testing
    private val alwaysGrantPermissions: Boolean = true,
    // Use this map to override specific permissions' behavior
    private val permissionOverrides: Map<PermissionType, Boolean> = emptyMap()
) : PermissionManager {
    
    private val permissionStatusMap = MutableStateFlow<Map<PermissionType, PermissionStatus>>(emptyMap())
    
    override fun isPermissionGranted(type: PermissionType): Boolean {
        // First check if there's an override for this permission type
        return permissionOverrides[type] ?: alwaysGrantPermissions
    }
    
    override fun arePermissionsGranted(types: Set<PermissionType>): Boolean {
        return types.all { isPermissionGranted(it) }
    }
    
    override fun observePermissions(types: Set<PermissionType>): StateFlow<Map<PermissionType, PermissionStatus>> {
        // Map each permission to its status based on configuration
        val statusMap = types.associateWith { 
            val granted = isPermissionGranted(it)
            if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED
        }
        
        permissionStatusMap.value = statusMap
        return permissionStatusMap.asStateFlow()
    }
    
    override fun requestPermission(type: PermissionType, onResult: (PermissionResult) -> Unit) {
        // Return result based on configuration
        val granted = isPermissionGranted(type)
        val status = if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED
        
        Napier.d("Stub permission manager ${if (granted) "granted" else "denied"} permission: $type")
        onResult(PermissionResult(type, status))
    }
    
    override fun requestPermissions(types: Set<PermissionType>, onResult: (List<PermissionResult>) -> Unit) {
        // Map each permission to its result based on configuration
        val results = types.map { 
            val granted = isPermissionGranted(it)
            val status = if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED
            PermissionResult(it, status)
        }
        
        Napier.d("Stub permission manager processed permission request: $types")
        onResult(results)
    }
    
    override fun openAppSettings() {
        // No-op in stub implementation
        Napier.d("Stub permission manager attempted to open app settings")
    }
    
    override fun shouldShowRationale(type: PermissionType): Boolean {
        // Return true if permission is denied and not in overrides
        // (simulating a first-time denial where rationale should be shown)
        return !alwaysGrantPermissions && type !in permissionOverrides
    }
    
    /**
     * For testing: Set the status of a specific permission
     */
    fun setPermissionGranted(type: PermissionType, granted: Boolean) {
        val mutableOverrides = permissionOverrides.toMutableMap()
        mutableOverrides[type] = granted
        
        // Update the status map if it contains this permission
        val currentMap = permissionStatusMap.value.toMutableMap()
        if (currentMap.containsKey(type)) {
            currentMap[type] = if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED
            permissionStatusMap.value = currentMap
        }
    }
}