package app.logdate.client.device.identity

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * The name a person knows this device by: the one they set in system settings, or the
 * manufacturer and model when they never set one.
 */
fun Context.userVisibleDeviceName(): String =
    runCatching { Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: "${Build.MANUFACTURER} ${Build.MODEL}"
