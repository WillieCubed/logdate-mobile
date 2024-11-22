package app.logdate.core.permission

/**
 * Provides information about the app's permissions.
 *
 * For example, this may be used to check if the app can access the camera or location.
 */
interface PermissionProvider {
    /**
     * Determines whether the app has the given permission.
     *
     * If the current platform does not support permissions, this method will always return `True`.
     *
     * @param permission The permission to check.
     * @return `True` if the app has the given permission.
     */
    fun hasPermission(permission: AppPermission): Boolean
}