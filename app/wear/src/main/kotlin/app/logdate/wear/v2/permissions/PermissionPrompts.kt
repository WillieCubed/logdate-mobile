package app.logdate.wear.v2.permissions

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/** Whether a permission is granted right now. */
fun interface PermissionChecker {
    fun isGranted(permission: WatchPermission): Boolean
}

/** [PermissionChecker] over the system's own record. */
class AndroidPermissionChecker(
    private val context: Context,
) : PermissionChecker {
    override fun isGranted(permission: WatchPermission): Boolean =
        ContextCompat.checkSelfPermission(context, permission.manifestName()) == PackageManager.PERMISSION_GRANTED
}

/** Records which optional permissions were already asked for, so each is asked at most once. */
interface PermissionPromptStore {
    fun hasAsked(permission: WatchPermission): Boolean

    fun markAsked(permission: WatchPermission)
}

/** [PermissionPromptStore] in the watch's own SharedPreferences file, [PREFERENCES_NAME]. */
class SharedPreferencesPermissionPromptStore(
    private val preferences: SharedPreferences,
) : PermissionPromptStore {
    override fun hasAsked(permission: WatchPermission): Boolean = preferences.getBoolean(key(permission), false)

    override fun markAsked(permission: WatchPermission) {
        preferences.edit { putBoolean(key(permission), true) }
    }

    private fun key(permission: WatchPermission): String = "asked_${permission.storedName}"

    companion object {
        const val PREFERENCES_NAME = "wear_permission_prompts"
    }
}

/**
 * When to ask for a permission. One a feature can't work without (the microphone to record) is
 * asked each time it is missing at the point of use; check it with [isGranted]. An optional one
 * (notifications for the recording on the watch face) is asked once, the first time its feature
 * runs without it; [takeOptionalPrompt] decides.
 */
class PermissionPrompts(
    private val checker: PermissionChecker,
    private val store: PermissionPromptStore,
) {
    fun isGranted(permission: WatchPermission): Boolean = checker.isGranted(permission)

    /** True once, when [permission] is missing and was never asked for; it counts as asked from then on. */
    fun takeOptionalPrompt(permission: WatchPermission): Boolean {
        if (checker.isGranted(permission)) return false
        if (store.hasAsked(permission)) return false
        store.markAsked(permission)
        return true
    }
}
