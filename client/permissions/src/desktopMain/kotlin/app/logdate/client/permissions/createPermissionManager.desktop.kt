package app.logdate.client.permissions

/**
 * Creates a Desktop-specific permission manager
 */
actual fun createPermissionManager(): PermissionManager {
    return DesktopPermissionManager()
}