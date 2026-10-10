package app.logdate.wear.v2.permissions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import io.github.aakira.napier.Napier

/** Opens LogDate's page in the system settings, where a permission refused for good can be turned on. */
fun openAppSettings(context: Context) {
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    openFirstAvailable(context, listOf(appDetails, Intent(Settings.ACTION_SETTINGS)))
}

/** Opens the system storage screen, or the main settings on a watch without one. */
fun openStorageSettings(context: Context) {
    openFirstAvailable(context, listOf(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), Intent(Settings.ACTION_SETTINGS)))
}

private fun openFirstAvailable(
    context: Context,
    intents: List<Intent>,
) {
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            Napier.w("No screen for ${intent.action} on this watch", e)
        }
    }
    Napier.e("No settings screen could be opened on this watch")
}
