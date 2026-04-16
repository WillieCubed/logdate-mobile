package app.logdate.client

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.aakira.napier.Napier

private const val HEALTH_CONNECT_PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

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
