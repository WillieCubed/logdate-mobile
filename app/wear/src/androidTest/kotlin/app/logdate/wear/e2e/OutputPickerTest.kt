package app.logdate.wear.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.wear.v2.playon.OutputPickerRoute
import app.logdate.wear.v2.playon.SystemOutputPicker
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotEquals

/**
 * Checks on a Wear OS 5 emulator that the system has an output picker the app can open: the system
 * output switcher, or Bluetooth settings. Instrumentation sees these whether or not the manifest's
 * `<queries>` entries exist, so `OutputPickerManifestTest` checks those.
 */
@RunWith(AndroidJUnit4::class)
class OutputPickerTest {
    @Test
    fun an_output_picker_is_visible_to_the_app() {
        val picker = SystemOutputPicker(ApplicationProvider.getApplicationContext<Context>())

        assertNotEquals(OutputPickerRoute.NONE, picker.route)
    }
}
