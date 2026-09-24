package app.logdate.feature.core.settings.account.recovery

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberIsChangingConfiguration(): () -> Boolean {
    val activity = LocalContext.current.findActivity()
    return remember(activity) { { activity?.isChangingConfigurations == true } }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
