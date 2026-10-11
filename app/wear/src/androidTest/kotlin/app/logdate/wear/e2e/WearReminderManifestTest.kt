package app.logdate.wear.e2e

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.wear.reminders.WorkoutEndListenerService
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Checks the installed manifest has what watch reminders need, and still lacks background location. */
@RunWith(AndroidJUnit4::class)
class WearReminderManifestTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun phone_background_location_is_not_started_by_watch_boot() {
        val receivers =
            context.packageManager.queryBroadcastReceivers(
                Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(context.packageName),
                PackageManager.ResolveInfoFlags.of(0L),
            )
        assertFalse(receivers.any { it.activityInfo.name == "app.logdate.client.location.tracking.LocationTrackingBootReceiver" })
    }

    @Test
    fun reminders_can_ask_for_physical_activity_and_restore_after_a_restart() {
        val requested = requestedPermissions()

        assertTrue(Manifest.permission.ACTIVITY_RECOGNITION in requested)
        assertTrue(Manifest.permission.RECEIVE_BOOT_COMPLETED in requested)
        assertFalse(Manifest.permission.ACCESS_BACKGROUND_LOCATION in requested)
    }

    @Test
    fun a_restart_reaches_the_reminders_receiver() {
        val receivers =
            context.packageManager.queryBroadcastReceivers(
                Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(context.packageName),
                PackageManager.ResolveInfoFlags.of(0L),
            )

        assertTrue(receivers.any { it.activityInfo.name == RESTORE_RECEIVER }, receivers.map { it.activityInfo.name }.toString())
    }

    @Test
    fun only_health_services_can_bind_the_workout_listener() {
        val service =
            context.packageManager.getServiceInfo(
                ComponentName(context, WorkoutEndListenerService::class.java),
                PackageManager.ComponentInfoFlags.of(0L),
            )

        assertEquals(PASSIVE_DATA_BINDING, service.permission)
    }

    private fun requestedPermissions(): List<String> =
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            .requestedPermissions
            .orEmpty()
            .toList()

    private companion object {
        const val RESTORE_RECEIVER = "app.logdate.wear.reminders.ReminderRestoreReceiver"
        const val PASSIVE_DATA_BINDING = "com.google.android.wearable.healthservices.permission.PASSIVE_DATA_BINDING"
    }
}
