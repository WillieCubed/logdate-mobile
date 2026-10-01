package app.logdate.client.e2e

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.logdate.client.feature.widgets.NewEntryWidgetConfigActivity
import app.logdate.client.feature.widgets.WidgetInstanceSettings
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class WidgetPhotoPermissionFlowTest {
    @Test
    fun `granting photos returns to New Entry shape setup`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val permission =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        assumeTrue(context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
        val widgetId = 7199
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            context.startActivity(
                Intent(context, NewEntryWidgetConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            assertTrue(
                device.wait(Until.hasObject(By.text("Allow photos")), 20_000L),
                "Photo action absent: title=${device.hasObject(By.text("New Entry shape"))}, " +
                    "full=${context.checkSelfPermission(permission)}, " +
                    "selected=${if (Build.VERSION.SDK_INT >= 34) context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else -1}",
            )
            device.findObject(By.text("Allow photos")).click()
            val allow =
                listOf(
                    "com.google.android.permissioncontroller:id/permission_allow_all_button",
                    "com.android.permissioncontroller:id/permission_allow_all_button",
                    "com.google.android.permissioncontroller:id/permission_allow_button",
                    "com.android.permissioncontroller:id/permission_allow_button",
                ).firstNotNullOfOrNull { id -> device.wait(Until.findObject(By.res(id)), 2_000L) }
                    ?: device.wait(Until.findObject(By.text("Allow all photos")), 5_000L)
                    ?: device.wait(Until.findObject(By.text("Allow")), 5_000L)
            assertTrue(allow != null, "Android did not offer photo access")
            allow.click()
            assertTrue(device.wait(Until.hasObject(By.text("New Entry shape")), 10_000L))
            assertTrue(device.hasObject(By.text("Save")), "Permission result closed widget setup")
        } finally {
            WidgetInstanceSettings(context).remove(widgetId)
        }
    }
}
