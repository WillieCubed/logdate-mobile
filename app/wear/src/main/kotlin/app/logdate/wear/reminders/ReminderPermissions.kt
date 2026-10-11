package app.logdate.wear.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** What turning reminders on asks for: notifications to show them, physical activity to see a workout end. */
val REMINDER_PERMISSIONS: Array<String> = arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACTIVITY_RECOGNITION)

/** Whether the permissions reminders use are granted. */
interface ReminderPermissions {
    fun notificationsGranted(): Boolean

    fun activityRecognitionGranted(): Boolean
}

class AndroidReminderPermissions(
    private val context: Context,
) : ReminderPermissions {
    override fun notificationsGranted(): Boolean = granted(Manifest.permission.POST_NOTIFICATIONS)

    override fun activityRecognitionGranted(): Boolean = granted(Manifest.permission.ACTIVITY_RECOGNITION)

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
