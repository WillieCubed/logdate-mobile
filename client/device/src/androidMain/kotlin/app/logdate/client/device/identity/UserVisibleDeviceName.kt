package app.logdate.client.device.identity

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Prefer the system's user-visible name. Some emulator images expose internal model names;
 * use a neutral label instead of passing those through to the UI or sync registration.
 */
fun Context.userVisibleDeviceName(): String {
    val configuredName =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            runCatching { Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
        } else {
            null
        }
    return safeDeviceDisplayName(configuredName, "").ifBlank {
        safeDeviceDisplayName(Build.MODEL, "Android device")
    }
}
