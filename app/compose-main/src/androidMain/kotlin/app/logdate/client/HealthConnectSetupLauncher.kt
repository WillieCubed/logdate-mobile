package app.logdate.client

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import io.github.aakira.napier.Napier

private const val HEALTH_CONNECT_PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

/**
 * Opens Health Connect's "Manage permissions" page for this app.
 *
 * Called after the user has already denied the sleep permission dialog — at that point,
 * relaunching the dialog is pointless (it either won't reappear or they'll deny it again).
 * Sending them directly to Health Connect settings lets them consciously grant access.
 *
 * On Android 14+ Health Connect is OS-integrated; on Android 13 it is a standalone app.
 * Both are handled here with a fallback to the health home screen.
 */
internal fun launchHealthConnectPermissions(context: Context) {
    val packageName = context.packageName

    // Android 14+ (API 34): Health Connect is part of the OS.
    val manageIntent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            // Android 13: Health Connect is a standalone app.
            Intent("androidx.health.connect.client.HEALTH_PERMISSIONS")
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                .setPackage(HEALTH_CONNECT_PROVIDER_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    val fallbackIntent =
        Intent("android.health.connect.action.HEALTH_HOME_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        context.startActivity(manageIntent)
    } catch (e: Exception) {
        Napier.w(e) { "Failed to open Health Connect permissions page; falling back to health home" }
        try {
            context.startActivity(fallbackIntent)
        } catch (fallback: Exception) {
            Napier.e(fallback) { "Failed to open Health Connect" }
        }
    }
}

internal fun launchHealthConnectSetup(context: Context) {
    val playStoreIntent =
        Intent(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage("com.android.vending")
            data =
                Uri.parse(
                    "market://details?id=$HEALTH_CONNECT_PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding",
                )
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    val fallbackIntent =
        Intent(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            data = Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PROVIDER_PACKAGE")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    try {
        context.startActivity(playStoreIntent)
    } catch (playStoreError: Exception) {
        Napier.w(playStoreError) { "Failed to launch Health Connect Play Store listing; falling back to web" }
        try {
            context.startActivity(fallbackIntent)
        } catch (fallbackError: Exception) {
            Napier.e(fallbackError) { "Failed to launch Health Connect setup flow" }
        }
    }
}
