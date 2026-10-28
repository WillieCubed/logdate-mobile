package app.logdate.client.ui

import android.app.Application
import android.content.res.Configuration
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.core.view.WindowInsetsControllerCompat
import app.logdate.ui.platform.enableTransparentSystemBars
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TransparentSystemBarsTest {
    @Test
    fun barColorsAreFullyTransparentAndContrastScrimsAreDisabled() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = controller.get()
            activity.enableTransparentSystemBars()
            assertEquals(0, Color.alpha(activity.window.statusBarColor))
            assertEquals(0, Color.alpha(activity.window.navigationBarColor))
            assertFalse(activity.window.isNavigationBarContrastEnforced, "Default edge-to-edge retains a three-button scrim")
            assertFalse(activity.window.isStatusBarContrastEnforced)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun configurationReplayKeepsOptionalContrastDisabled() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = controller.get()
            activity.enableTransparentSystemBars()
            activity.window.decorView.dispatchConfigurationChanged(Configuration(activity.resources.configuration))
            assertFalse(activity.window.isNavigationBarContrastEnforced, "AndroidX configuration replay must retain transparent navigation")
            assertFalse(activity.window.isStatusBarContrastEnforced)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun configurationReplayDoesNotOverrideExplicitCameraIconAppearance() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = controller.get()
            activity.enableTransparentSystemBars()
            val icons = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            icons.isAppearanceLightStatusBars = false
            icons.isAppearanceLightNavigationBars = false
            activity.window.decorView.dispatchConfigurationChanged(Configuration(activity.resources.configuration))
            assertFalse(icons.isAppearanceLightStatusBars, "A resize must not reset the camera's light status icons")
            assertFalse(icons.isAppearanceLightNavigationBars)
            assertFalse(activity.window.isNavigationBarContrastEnforced)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
