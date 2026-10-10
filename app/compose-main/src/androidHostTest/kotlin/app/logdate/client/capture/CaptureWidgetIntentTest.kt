package app.logdate.client.capture

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import app.logdate.client.feature.widgets.CaptureWidgetButton
import app.logdate.client.feature.widgets.captureWidgetIntent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class CaptureWidgetIntentTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val mainWindow = ComponentName(context.packageName, "app.logdate.client.MainActivity")

    @Before
    fun registerMainWindow() {
        val packageManager = shadowOf(context.packageManager)
        packageManager.addActivityIfNotPresent(mainWindow)
        packageManager.addIntentFilterForActivity(
            mainWindow,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }

    @Test
    fun `each capture button is addressed to the main window`() {
        for (button in CaptureWidgetButton.entries) {
            assertEquals(mainWindow, captureWidgetIntent(context, button).component)
        }
    }

    @Test
    fun `a capture button still opens the main window after Glance gives it a data URI`() {
        // Glance sets a glance-action: URI on each clickable intent, which no capture filter declares.
        val sent = captureWidgetIntent(context, CaptureWidgetButton.Write).setData(Uri.parse("glance-action:/0/1"))

        val resolved = assertNotNull(context.packageManager.resolveActivity(sent, 0))

        assertEquals(mainWindow.className, resolved.activityInfo.name)
    }
}
