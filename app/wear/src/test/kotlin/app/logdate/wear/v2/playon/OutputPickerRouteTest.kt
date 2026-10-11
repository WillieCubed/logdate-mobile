package app.logdate.wear.v2.playon

import org.junit.Test
import kotlin.test.assertEquals

class OutputPickerRouteTest {
    @Test
    fun `the system output switcher comes first`() {
        assertEquals(OutputPickerRoute.OUTPUT_SWITCHER, outputPickerRoute(switcherAvailable = true, bluetoothSettingsAvailable = true))
    }

    @Test
    fun `without the switcher Bluetooth settings open instead`() {
        assertEquals(OutputPickerRoute.BLUETOOTH_SETTINGS, outputPickerRoute(switcherAvailable = false, bluetoothSettingsAvailable = true))
    }

    @Test
    fun `with neither there is nothing to offer`() {
        assertEquals(OutputPickerRoute.NONE, outputPickerRoute(switcherAvailable = false, bluetoothSettingsAvailable = false))
    }
}
