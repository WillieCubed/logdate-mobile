package app.logdate.ui.watch

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.aakira.napier.Napier

@Composable
actual fun rememberWatchSetupLauncher(): ((WatchSetupProvider) -> Boolean)? {
    val context = LocalContext.current
    return remember(context) { { provider -> launchCompanion(context, provider) } }
}

private fun launchCompanion(
    context: Context,
    provider: WatchSetupProvider,
): Boolean {
    val installed = context.packageManager.getLaunchIntentForPackage(provider.packageName)
    if (installed != null && launch(context, installed)) return true
    if (launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${provider.packageName}")))) return true
    return launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${provider.packageName}")))
}

private fun launch(
    context: Context,
    intent: Intent,
): Boolean =
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (failure: Exception) {
        Napier.w("Could not open watch companion setup", failure)
        false
    }
