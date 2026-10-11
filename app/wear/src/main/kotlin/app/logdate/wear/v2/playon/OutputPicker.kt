package app.logdate.wear.v2.playon

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import io.github.aakira.napier.Napier

/** Where the person chooses an output: the system's output switcher, or Bluetooth settings. */
enum class OutputPickerRoute {
    OUTPUT_SWITCHER,
    BLUETOOTH_SETTINGS,
    NONE,
}

/** The picker to offer, preferring the system's own output switcher. */
fun outputPickerRoute(
    switcherAvailable: Boolean,
    bluetoothSettingsAvailable: Boolean,
): OutputPickerRoute =
    when {
        switcherAvailable -> OutputPickerRoute.OUTPUT_SWITCHER
        bluetoothSettingsAvailable -> OutputPickerRoute.BLUETOOTH_SETTINGS
        else -> OutputPickerRoute.NONE
    }

/** Opens the system's choice of audio outputs: the watch speaker or headphones. */
interface OutputPicker {
    val route: OutputPickerRoute

    /** @return false when no picker could open. */
    fun open(): Boolean
}

/**
 * [OutputPicker] over the system: the output switcher of Wear OS 4 and later, launched only on a
 * system activity, and otherwise Bluetooth settings that close once an audio device connects.
 */
class SystemOutputPicker(
    private val context: Context,
) : OutputPicker {
    override val route: OutputPickerRoute
        get() = outputPickerRoute(switcherComponent() != null, resolves(bluetoothSettingsIntent()))

    override fun open(): Boolean {
        val switcher = switcherComponent()
        val intent =
            when {
                switcher != null -> outputSwitcherIntent().setComponent(switcher)
                resolves(bluetoothSettingsIntent()) -> bluetoothSettingsIntent()
                else -> return false
            }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Napier.e("Couldn't open the output picker", e)
            false
        } catch (e: SecurityException) {
            Napier.e("The system refused to open the output picker", e)
            false
        }
    }

    private fun outputSwitcherIntent(): Intent =
        Intent(ACTION_MEDIA_OUTPUT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_PACKAGE_NAME, context.packageName)

    private fun bluetoothSettingsIntent(): Intent =
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(EXTRA_CONNECTION_ONLY, true)
            .putExtra(EXTRA_CLOSE_ON_CONNECT, true)
            .putExtra(EXTRA_FILTER_TYPE, FILTER_TYPE_AUDIO)

    private fun switcherComponent(): ComponentName? =
        context.packageManager
            .queryIntentActivities(outputSwitcherIntent(), PackageManager.ResolveInfoFlags.of(0L))
            .mapNotNull { it.activityInfo }
            .firstOrNull { info -> info.applicationInfo != null && info.applicationInfo.flags and SYSTEM_APP_FLAGS != 0 }
            ?.let { info -> ComponentName(info.packageName, info.name) }

    private fun resolves(intent: Intent): Boolean =
        context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L)).isNotEmpty()

    companion object {
        const val ACTION_MEDIA_OUTPUT = "com.android.settings.panel.action.MEDIA_OUTPUT"
        const val EXTRA_PACKAGE_NAME = "com.android.settings.panel.extra.PACKAGE_NAME"
        private const val EXTRA_CONNECTION_ONLY = "EXTRA_CONNECTION_ONLY"
        private const val EXTRA_CLOSE_ON_CONNECT = "EXTRA_CLOSE_ON_CONNECT"
        private const val EXTRA_FILTER_TYPE = "android.bluetooth.devicepicker.extra.FILTER_TYPE"
        private const val FILTER_TYPE_AUDIO = 1
        private const val SYSTEM_APP_FLAGS = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
    }
}
