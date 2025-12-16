package app.logdate.client.permissions

/**
 * JVM implementation of the PermissionManager.
 * JVM platforms typically don't have a permission system like mobile platforms,
 * so this extends the StubPermissionManager.
 */
class JvmPermissionManager : StubPermissionManager()