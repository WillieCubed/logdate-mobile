package app.logdate.client.permissions

/**
 * Desktop implementation of the PermissionManager.
 * Desktop typically doesn't have a permission system like mobile platforms,
 * so this extends the StubPermissionManager with alwaysGrantPermissions=true
 * to ensure all permissions are always granted.
 */
class DesktopPermissionManager : StubPermissionManager(
    alwaysGrantPermissions = true,
    permissionOverrides = emptyMap()
)