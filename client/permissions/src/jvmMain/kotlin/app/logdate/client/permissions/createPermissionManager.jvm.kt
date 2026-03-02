@file:Suppress("ktlint:standard:filename")

package app.logdate.client.permissions

/**
 * Creates a JVM-specific permission manager
 */
actual fun createPermissionManager(): PermissionManager = JvmPermissionManager()
