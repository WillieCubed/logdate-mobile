package app.logdate.client.permissions

/**
 * Creates a JVM-specific permission manager
 */
actual fun createPermissionManager(): PermissionManager {
    return JvmPermissionManager()
}