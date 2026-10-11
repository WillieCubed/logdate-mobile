package app.logdate.wear.e2e

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.wear.v2.surfaces.PackageManagerComponentSwitch
import app.logdate.wear.v2.surfaces.SurfaceCatalog
import app.logdate.wear.v2.surfaces.SurfaceComponents
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Swaps the watch's surfaces through the real package manager on a Wear OS emulator. */
@RunWith(AndroidJUnit4::class)
class SurfaceComponentsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val components = SurfaceComponents(PackageManagerComponentSwitch(context))

    @After
    fun restoreTheCurrentApp() {
        components.apply(newAppOn = false)
    }

    @Test
    fun every_catalog_entry_is_a_component_of_the_app() {
        (SurfaceCatalog.NEW_APP_ONLY + SurfaceCatalog.CURRENT_APP_ONLY).forEach { className ->
            context.packageManager.getServiceInfo(
                ComponentName(context.packageName, className),
                PackageManager.ComponentInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
            )
        }
    }

    @Test
    fun the_new_app_retires_the_surfaces_it_replaces() {
        components.apply(newAppOn = true)

        SurfaceCatalog.CURRENT_APP_ONLY.forEach { assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state(it), it) }
        SurfaceCatalog.NEW_APP_ONLY.forEach { assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, state(it), it) }
    }

    @Test
    fun the_current_app_offers_its_own_surfaces() {
        components.apply(newAppOn = false)

        SurfaceCatalog.CURRENT_APP_ONLY.forEach { assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, state(it), it) }
        SurfaceCatalog.NEW_APP_ONLY.forEach { assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state(it), it) }
    }

    private fun state(className: String): Int =
        context.packageManager.getComponentEnabledSetting(ComponentName(context.packageName, className))
}
