package app.logdate.client.sensor.battery

/**
 * Represents the current state of the device's battery.
 *
 * @property level The battery level as a percentage between 0 and 100
 * @property isCharging Whether the device is currently charging
 * @property isPowerSaveMode Whether the device is in power save/low power mode
 */
data class BatteryState(
    val level: Int,
    val isCharging: Boolean,
    val isPowerSaveMode: Boolean
)