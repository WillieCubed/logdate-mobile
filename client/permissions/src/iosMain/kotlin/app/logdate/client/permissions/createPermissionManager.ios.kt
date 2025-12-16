package app.logdate.client.permissions

/**
 * Creates an iOS-specific permission manager
 */
actual fun createPermissionManager(): PermissionManager {
    return IosPermissionManager()
}